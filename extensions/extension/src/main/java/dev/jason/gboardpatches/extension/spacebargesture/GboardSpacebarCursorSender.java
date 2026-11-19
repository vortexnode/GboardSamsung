package dev.jason.gboardpatches.extension.spacebargesture;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

/**
 * Dispatches balanced horizontal DPAD key events and composition finalization through the active
 * {@link InputConnection}, matching the proven key-event pattern in {@code GboardGlobeDragRuntime}.
 */
public final class GboardSpacebarCursorSender {
    private GboardSpacebarCursorSender() {
    }

    public static boolean finishComposingText(InputConnection connection) {
        if (connection == null) {
            return false;
        }
        try {
            return connection.finishComposingText();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Emits balanced {@code ACTION_DOWN}/{@code ACTION_UP} pairs strictly for
     * {@link KeyEvent#KEYCODE_DPAD_LEFT} (negative steps) or {@link KeyEvent#KEYCODE_DPAD_RIGHT}
     * (positive steps). Never emits vertical DPAD keycodes.
     */
    public static boolean sendHorizontalSteps(InputConnection connection, int signedSteps) {
        if (connection == null || signedSteps == 0) {
            return false;
        }
        int keyCode = signedSteps < 0
                ? KeyEvent.KEYCODE_DPAD_LEFT
                : KeyEvent.KEYCODE_DPAD_RIGHT;
        int count = Math.min(Math.abs(signedSteps),
                GboardSpacebarGestureState.MAX_DPAD_STEPS_PER_MOVE);
        boolean sentAny = false;
        for (int index = 0; index < count; index++) {
            if (!sendBalancedKeyEvent(connection, keyCode)) {
                break;
            }
            sentAny = true;
        }
        return sentAny;
    }

    private static boolean sendBalancedKeyEvent(InputConnection connection, int keyCode) {
        long now = SystemClock.uptimeMillis();
        boolean downAttempted = false;
        boolean upCompleted = false;
        try {
            downAttempted = true;
            if (!connection.sendKeyEvent(createKeyEvent(now, KeyEvent.ACTION_DOWN, keyCode))) {
                return false;
            }
            upCompleted = connection.sendKeyEvent(createKeyEvent(now, KeyEvent.ACTION_UP, keyCode));
            return upCompleted;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (downAttempted && !upCompleted) {
                try {
                    connection.sendKeyEvent(createKeyEvent(now, KeyEvent.ACTION_UP, keyCode));
                } catch (Throwable ignored) {
                    // Cleanup must never crash keyboard touch handling.
                }
            }
        }
    }

    private static KeyEvent createKeyEvent(long time, int action, int keyCode) {
        return new KeyEvent(
                time,
                time,
                action,
                keyCode,
                0,
                0,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD);
    }
}
