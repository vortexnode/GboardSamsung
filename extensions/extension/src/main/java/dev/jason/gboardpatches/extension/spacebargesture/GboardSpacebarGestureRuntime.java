package dev.jason.gboardpatches.extension.spacebargesture;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Standalone runtime touch coordinator for Samsung-like spacebar gestures, invoked directly from
 * {@code KeyboardViewHolder.dispatchTouchEvent} via the selected-only touch composer for
 * non-header keyboard holders.
 */
public final class GboardSpacebarGestureRuntime {
    private static final String HEADER_ID_NAME = "keyboard_header_view_holder";
    private static final Map<View, HolderSession> SESSIONS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> CANCELLING = ThreadLocal.withInitial(() -> false);
    private static volatile int headerId;
    private static volatile Handler mainHandler;

    private GboardSpacebarGestureRuntime() {
    }

    public static boolean isCancelling() {
        return Boolean.TRUE.equals(CANCELLING.get());
    }

    public static boolean onHolderTouch(View holder, MotionEvent event) {
        if (holder == null || event == null || isCancelling() || isHeaderHolder(holder)) {
            return false;
        }
        int action = event.getActionMasked();
        HolderSession session = SESSIONS.get(holder);

        if (action == MotionEvent.ACTION_DOWN) {
            if (session != null) {
                resetSession(session);
            }
            if (!holder.isShown()
                    || !GboardSpacebarKeyHitTester.isSpaceKeyAt(
                            holder, event.getRawX(), event.getRawY())) {
                return false;
            }
            if (session == null) {
                session = new HolderSession();
                SESSIONS.put(holder, session);
            }
            session.lastDownTime = event.getDownTime();
            session.lastLocalX = event.getX();
            session.lastLocalY = event.getY();
            session.state.beginSpaceTracking(
                    event.getPointerId(0),
                    event.getRawX(),
                    event.getRawY(),
                    event.getEventTime());
            scheduleLongPress(holder, session);
            // Pass ACTION_DOWN through untouched so short taps insert a normal single space.
            return false;
        }

        if (session == null || session.state.getState() == GboardSpacebarGestureState.State.IDLE) {
            return false;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            cancelPendingLongPress(session);
            session.clearActiveEditor();
            return session.state.onPointerDown();
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            cancelPendingLongPress(session);
            session.clearActiveEditor();
            return session.state.onUpOrCancel();
        }

        if (action != MotionEvent.ACTION_MOVE) {
            return session.state.isConsumingStream();
        }

        if (!holder.isShown() || event.getPointerCount() != 1) {
            resetSession(session);
            return false;
        }

        int pointerIndex = event.findPointerIndex(session.state.getActivePointerId());
        if (pointerIndex < 0) {
            resetSession(session);
            return false;
        }

        session.lastLocalX = event.getX(pointerIndex);
        session.lastLocalY = event.getY(pointerIndex);

        if (session.state.getState() == GboardSpacebarGestureState.State.CURSOR_MODE) {
            InputMethodService service = resolveInputMethodService(holder);
            InputConnection connection = service == null ? null : service.getCurrentInputConnection();
            EditorInfo currentEditor = service == null ? null : service.getCurrentInputEditorInfo();
            if (connection == null || hasEditorChanged(session, currentEditor)) {
                resetSession(session);
                return false;
            }
        }

        float density = holder.getResources().getDisplayMetrics().density;
        float touchSlop = ViewConfiguration.get(holder.getContext()).getScaledTouchSlop();
        GboardSpacebarGestureState.MoveResult result = session.state.onMove(
                event.getPointerId(pointerIndex),
                event.getRawX(),
                event.getRawY(),
                density,
                touchSlop,
                event.getEventTime());

        if (session.state.getState() != GboardSpacebarGestureState.State.SPACE_TRACKING) {
            cancelPendingLongPress(session);
        }

        if (result.sendCancel) {
            dispatchSyntheticCancel(holder, event);
        }

        if (result.languageDirection != GboardSpacebarGestureState.LanguageDirection.NONE) {
            InputMethodService service = resolveInputMethodService(holder);
            if (result.languageDirection == GboardSpacebarGestureState.LanguageDirection.NEXT) {
                GboardSpacebarLanguageNavigator.switchNext(service);
            } else if (result.languageDirection
                    == GboardSpacebarGestureState.LanguageDirection.PREVIOUS) {
                GboardSpacebarLanguageNavigator.switchPrevious(service);
            }
        }

        if (result.dpadSteps != 0) {
            InputMethodService service = resolveInputMethodService(holder);
            InputConnection connection = service == null ? null : service.getCurrentInputConnection();
            if (connection == null) {
                resetSession(session);
                return false;
            }
            GboardSpacebarCursorSender.sendHorizontalSteps(connection, result.dpadSteps);
        }

        return result.consumed;
    }

    public static void reset() {
        for (HolderSession session : SESSIONS.values()) {
            if (session != null) {
                resetSession(session);
            }
        }
        SESSIONS.clear();
        headerId = 0;
    }

    private static boolean isHeaderHolder(View holder) {
        int expectedId = headerId;
        if (expectedId != 0) {
            return holder.getId() == expectedId;
        }
        if (holder.getId() == View.NO_ID) {
            return false;
        }
        try {
            if (HEADER_ID_NAME.equals(holder.getResources().getResourceEntryName(holder.getId()))) {
                headerId = holder.getId();
                return true;
            }
        } catch (Resources.NotFoundException ignored) {
        }
        return false;
    }

    private static InputMethodService resolveInputMethodService(View view) {
        if (view == null) {
            return null;
        }
        Context current = view.getContext();
        while (current instanceof ContextWrapper wrapper) {
            if (current instanceof InputMethodService ims) {
                return ims;
            }
            Context base = wrapper.getBaseContext();
            if (base == current || base == null) {
                break;
            }
            current = base;
        }
        return null;
    }

    private static void scheduleLongPress(View holder, HolderSession session) {
        cancelPendingLongPress(session);
        Handler handler = getMainHandler();
        if (handler == null) {
            return;
        }
        Runnable runnable = () -> onLongPressTimeout(holder, session);
        session.pendingLongPress = runnable;
        handler.postDelayed(runnable, GboardSpacebarGestureState.LONG_PRESS_TIMEOUT_MS);
    }

    private static void onLongPressTimeout(View holder, HolderSession session) {
        session.pendingLongPress = null;
        if (!holder.isShown()
                || session.state.getState() != GboardSpacebarGestureState.State.SPACE_TRACKING) {
            return;
        }
        GboardSpacebarGestureState.LongPressResult result = session.state.onLongPressTimeout();
        if (!result.activated) {
            return;
        }

        if (result.sendCancel) {
            dispatchSyntheticCancelFromTimeout(holder, session);
        }

        InputMethodService service = resolveInputMethodService(holder);
        InputConnection connection = service == null ? null : service.getCurrentInputConnection();
        if (connection == null) {
            // ACTION_CANCEL was already delivered so no space will be inserted; terminate mode cleanly.
            resetSession(session);
            return;
        }

        session.captureActiveEditor(service.getCurrentInputEditorInfo());
        if (result.finishComposing) {
            GboardSpacebarCursorSender.finishComposingText(connection);
        }
    }

    private static void dispatchSyntheticCancel(View holder, MotionEvent sourceEvent) {
        MotionEvent cancel = MotionEvent.obtain(sourceEvent);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        dispatchSyntheticCancelInternal(holder, cancel);
    }

    private static void dispatchSyntheticCancelFromTimeout(View holder, HolderSession session) {
        long now = SystemClock.uptimeMillis();
        long downTime = session.lastDownTime > 0L ? session.lastDownTime : now;
        MotionEvent cancel = MotionEvent.obtain(
                downTime,
                now,
                MotionEvent.ACTION_CANCEL,
                session.lastLocalX,
                session.lastLocalY,
                0);
        dispatchSyntheticCancelInternal(holder, cancel);
    }

    private static void dispatchSyntheticCancelInternal(View holder, MotionEvent cancel) {
        CANCELLING.set(true);
        try {
            holder.dispatchTouchEvent(cancel);
        } finally {
            CANCELLING.remove();
            cancel.recycle();
        }
    }

    private static void cancelPendingLongPress(HolderSession session) {
        Runnable runnable = session.pendingLongPress;
        if (runnable != null) {
            Handler handler = getMainHandler();
            if (handler != null) {
                handler.removeCallbacks(runnable);
            }
            session.pendingLongPress = null;
        }
    }

    private static Handler getMainHandler() {
        Handler handler = mainHandler;
        if (handler == null) {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                handler = new Handler(looper);
                mainHandler = handler;
            }
        }
        return handler;
    }

    private static void resetSession(HolderSession session) {
        cancelPendingLongPress(session);
        session.state.reset();
        session.clearActiveEditor();
    }

    private static boolean hasEditorChanged(HolderSession session, EditorInfo current) {
        if (!session.hasCapturedEditor) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (current == session.capturedEditorRef) {
            return false;
        }
        if (current.fieldId != session.capturedFieldId
                || current.inputType != session.capturedInputType) {
            return true;
        }
        return !equalsNullable(current.packageName, session.capturedPackageName);
    }

    private static boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static final class HolderSession {
        final GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        Runnable pendingLongPress;
        long lastDownTime;
        float lastLocalX;
        float lastLocalY;
        boolean hasCapturedEditor;
        EditorInfo capturedEditorRef;
        int capturedFieldId;
        int capturedInputType;
        String capturedPackageName;

        void captureActiveEditor(EditorInfo editorInfo) {
            if (editorInfo == null) {
                clearActiveEditor();
                return;
            }
            hasCapturedEditor = true;
            capturedEditorRef = editorInfo;
            capturedFieldId = editorInfo.fieldId;
            capturedInputType = editorInfo.inputType;
            capturedPackageName = editorInfo.packageName;
        }

        void clearActiveEditor() {
            hasCapturedEditor = false;
            capturedEditorRef = null;
            capturedFieldId = 0;
            capturedInputType = 0;
            capturedPackageName = null;
        }
    }
}
