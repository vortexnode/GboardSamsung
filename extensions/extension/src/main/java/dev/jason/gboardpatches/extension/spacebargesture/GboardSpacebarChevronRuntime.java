package dev.jason.gboardpatches.extension.spacebargesture;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders passive visual language-switch chevrons ({@code ‹  ›}) symmetrically on both sides of
 * the active language label on Gboard's spacebar keycap using {@link View#getOverlay()}.
 *
 * <p>Design guarantees:
 * <ul>
 *   <li>100% passive: attaches no touch listeners, intercepts no {@code MotionEvent}s, never
 *       mutates {@link TextView#setText}, never alters accessibility text, never mutates keycap
 *       backgrounds (safe with Frosted Glass / Custom Theme), and never calls
 *       {@link View#requestLayout()}.</li>
 *   <li>Strict opt-in: when disabled (default), attaches zero drawables and zero view listeners to
 *       the spacebar {@code SoftKeyView}.</li>
 *   <li>Double recycling barrier: detaches immediately when {@code SoftKeyView} is rebound to a
 *       non-space key, and re-validates key identity and setting state before drawing each frame.</li>
 *   <li>Adaptive geometry: dynamically reads the live {@code .space_bar} keycap bounds, active
 *       language {@link TextView} bounds, and current text color on every draw/layout pass,
 *       scaling down or hiding cleanly when space is insufficient (long names, Floating, Split,
 *       One-handed, large font scale).</li>
 * </ul>
 */
public final class GboardSpacebarChevronRuntime {
    private static final String SPACE_BAR_TAG = ".space_bar";
    private static final String SPACE_BAR_TAG_PREFIX = ".space_bar.";

    private static final float MIN_KEYCAP_WIDTH_DP = 72f;
    private static final float MIN_KEYCAP_HEIGHT_DP = 22f;

    private static final float PREFERRED_CHEVRON_HEIGHT_RATIO = 0.23f;
    private static final float NORMAL_CHEVRON_MIN_HEIGHT_DP = 8.0f;
    private static final float NORMAL_CHEVRON_MAX_HEIGHT_DP = 10.5f;
    private static final float COMPACT_CHEVRON_MIN_HEIGHT_DP = 6.5f;
    private static final float CHEVRON_WIDTH_TO_HEIGHT_RATIO = 0.54f;

    private static final float STROKE_TO_HEIGHT_RATIO = 0.19f;
    private static final float MIN_STROKE_DP = 1.25f;
    private static final float MAX_STROKE_DP = 2.0f;

    private static final float PREFERRED_EDGE_INSET_RATIO = 0.085f;
    private static final float PREFERRED_EDGE_INSET_MIN_DP = 10.0f;
    private static final float PREFERRED_EDGE_INSET_MAX_DP = 16.0f;
    private static final float NORMAL_MIN_EDGE_INSET_DP = 8.0f;
    private static final float NORMAL_MIN_TEXT_GAP_DP = 6.0f;
    private static final float COMPACT_MIN_EDGE_INSET_DP = 6.0f;
    private static final float COMPACT_MIN_TEXT_GAP_DP = 5.0f;
    private static final float VERTICAL_EDGE_PADDING_DP = 2.0f;

    private static final float CHEVRON_ALPHA_MULTIPLIER = 0.85f;
    private static final int MIN_VISIBLE_CHEVRON_ALPHA = 64;
    private static final int FALLBACK_DARK_MODE_COLOR = 0xCCE8EAED;
    private static final int FALLBACK_LIGHT_MODE_COLOR = 0xCC3C4043;

    /**
     * Tracks spacebar {@code SoftKeyView}s seen via {@link #afterSoftKeyBind(View)} so a live
     * toggle in Settings can attach or clear overlays immediately without waiting for a rebind.
     */
    private static final Map<View, Boolean> TRACKED_SPACEBAR_VIEWS = new WeakHashMap<>();

    /**
     * Active overlay + listener attachments; empty whenever the feature is disabled.
     */
    private static final Map<View, ChevronAttachment> ACTIVE_ATTACHMENTS = new WeakHashMap<>();

    private static volatile SpaceKeyPredicate spaceKeyPredicate =
            GboardSpacebarKeyHitTester::isSpaceSoftKeyView;

    interface SpaceKeyPredicate {
        boolean isSpaceKey(View view);
    }

    private GboardSpacebarChevronRuntime() {
    }

    /**
     * Invoked after {@code SoftKeyView.s(SoftKeyDef, long)Z} binds key metadata.
     */
    public static void afterSoftKeyBind(View softKeyView) {
        if (softKeyView == null) {
            return;
        }
        try {
            boolean isSpaceKey = spaceKeyPredicate.isSpaceKey(softKeyView);
            if (!isSpaceKey) {
                // Recycling barrier #1: unconditionally clean up any previously attached chevron
                // overlay if Gboard recycled this SoftKeyView slot for another key.
                synchronized (TRACKED_SPACEBAR_VIEWS) {
                    TRACKED_SPACEBAR_VIEWS.remove(softKeyView);
                }
                detachAttachment(softKeyView);
                return;
            }

            synchronized (TRACKED_SPACEBAR_VIEWS) {
                TRACKED_SPACEBAR_VIEWS.put(softKeyView, Boolean.TRUE);
            }

            boolean enabled = GboardSpacebarGestureSettings.isSpacebarLanguageChevronsEnabled(
                    softKeyView.getContext());
            if (!enabled) {
                detachAttachment(softKeyView);
                return;
            }
            attachOrRefresh(softKeyView);
        } catch (Throwable ignored) {
            // Decorative overlay hooks must never disrupt key binding or keyboard inflation.
        }
    }

    /**
     * Immediately applies a Settings toggle change to all live tracked spacebar views.
     */
    public static void onChevronsPreferenceChanged(boolean enabled) {
        Runnable applyAll = () -> {
            List<View> snapshot;
            synchronized (TRACKED_SPACEBAR_VIEWS) {
                snapshot = new ArrayList<>(TRACKED_SPACEBAR_VIEWS.keySet());
            }
            for (View view : snapshot) {
                if (view == null) {
                    continue;
                }
                if (!enabled || !spaceKeyPredicate.isSpaceKey(view)) {
                    detachAttachment(view);
                } else if (view.isAttachedToWindow()) {
                    attachOrRefresh(view);
                }
            }
            if (!enabled) {
                List<View> remaining;
                synchronized (ACTIVE_ATTACHMENTS) {
                    remaining = new ArrayList<>(ACTIVE_ATTACHMENTS.keySet());
                }
                for (View view : remaining) {
                    detachAttachment(view);
                }
            }
        };

        runOnMainThread(applyAll);
    }

    private static void attachOrRefresh(View host) {
        ChevronAttachment existing;
        synchronized (ACTIVE_ATTACHMENTS) {
            existing = ACTIVE_ATTACHMENTS.get(host);
        }
        if (existing != null) {
            existing.attachOverlayAndListeners(host);
            existing.syncBounds(host);
            existing.drawable.invalidateSelf();
            host.invalidate();
            return;
        }

        ViewOverlay overlay = host.getOverlay();
        if (overlay == null) {
            return;
        }

        SpacebarChevronDrawable drawable = new SpacebarChevronDrawable(host);
        ChevronAttachment attachment = new ChevronAttachment(host, drawable);
        synchronized (ACTIVE_ATTACHMENTS) {
            ChevronAttachment prior = ACTIVE_ATTACHMENTS.put(host, attachment);
            if (prior != null) {
                prior.detachCompletely(host);
            }
        }
        attachment.attachOverlayAndListeners(host);
        host.addOnAttachStateChangeListener(attachment.attachListener);
        host.invalidate();
    }

    private static void detachAttachment(View host) {
        if (host == null) {
            return;
        }
        ChevronAttachment attachment;
        synchronized (ACTIVE_ATTACHMENTS) {
            attachment = ACTIVE_ATTACHMENTS.remove(host);
        }
        if (attachment != null) {
            attachment.detachCompletely(host);
        }
    }

    private static void scheduleDetach(View host) {
        if (host == null) {
            return;
        }
        WeakReference<View> ref = new WeakReference<>(host);
        Runnable cleanup = () -> {
            View target = ref.get();
            if (target != null) {
                detachAttachment(target);
            }
        };
        try {
            if (!host.post(cleanup)) {
                runOnMainThread(cleanup);
            }
        } catch (Throwable ignored) {
            runOnMainThread(cleanup);
        }
    }

    private static void runOnMainThread(Runnable action) {
        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null && Looper.myLooper() != mainLooper) {
                new Handler(mainLooper).post(action);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to synchronous execution in JVM unit tests without Android Looper.
        }
        action.run();
    }

    /**
     * Resolves the visual keycap rect inside {@code host} coordinates, preferring Gboard's
     * {@code .space_bar} tagged child view and falling back to {@code host}'s padded bounds.
     */
    static boolean resolveKeycapRect(View host, RectF outRect) {
        if (host == null || outRect == null) {
            return false;
        }
        int hostWidth = host.getWidth();
        int hostHeight = host.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0) {
            return false;
        }

        View spaceBarChild = findSpaceBarChild(host);
        if (spaceBarChild != null
                && spaceBarChild.getWidth() > 0
                && spaceBarChild.getHeight() > 0
                && mapDescendantRectToHost(spaceBarChild, host, outRect)) {
            clampRectToBounds(outRect, 0f, 0f, hostWidth, hostHeight);
            return outRect.width() > 0f && outRect.height() > 0f;
        }

        outRect.set(
                host.getPaddingLeft(),
                host.getPaddingTop(),
                hostWidth - host.getPaddingRight(),
                hostHeight - host.getPaddingBottom());
        return outRect.width() > 0f && outRect.height() > 0f;
    }

    private static View findSpaceBarChild(View root) {
        if (root == null) {
            return null;
        }
        if (isSpaceBarTag(root.getTag())) {
            return root;
        }
        if (root instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View child = group.getChildAt(index);
                if (child == null || child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                View found = findSpaceBarChild(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static boolean isSpaceBarTag(Object rawTag) {
        if (!(rawTag instanceof String tag)) {
            return false;
        }
        return SPACE_BAR_TAG.equals(tag) || tag.startsWith(SPACE_BAR_TAG_PREFIX);
    }

    /**
     * Finds the primary visible, non-empty language label {@link TextView} inside {@code host}.
     * Never mutates the view or its text.
     */
    static TextView findPrimaryLanguageLabel(View host, RectF keycapRect) {
        if (!(host instanceof ViewGroup)) {
            return null;
        }
        List<TextView> candidates = new ArrayList<>();
        collectVisibleNonEmptyTextViews(host, candidates);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        float targetCenterX = keycapRect != null ? keycapRect.centerX() : host.getWidth() / 2f;
        TextView best = null;
        float bestTextSize = -1f;
        float bestCenterDistance = Float.MAX_VALUE;
        RectF scratch = new RectF();

        for (TextView candidate : candidates) {
            float textSize = candidate.getTextSize();
            float centerDistance = Float.MAX_VALUE;
            if (mapDescendantRectToHost(candidate, host, scratch)) {
                centerDistance = Math.abs(scratch.centerX() - targetCenterX);
            }
            boolean clearlyLarger = textSize > bestTextSize + 0.5f;
            boolean similarSizeCloser = Math.abs(textSize - bestTextSize) <= 0.5f
                    && centerDistance < bestCenterDistance;
            if (best == null || clearlyLarger || similarSizeCloser) {
                best = candidate;
                bestTextSize = textSize;
                bestCenterDistance = centerDistance;
            }
        }
        return best;
    }

    private static void collectVisibleNonEmptyTextViews(View current, List<TextView> out) {
        if (current == null || current.getVisibility() != View.VISIBLE
                || current.getAlpha() <= 0.01f) {
            return;
        }
        if (current instanceof TextView textView) {
            if (textView.getWidth() > 0 && textView.getHeight() > 0) {
                CharSequence rawText = textView.getText();
                if (rawText != null && hasVisibleCharacters(rawText)) {
                    out.add(textView);
                }
            }
            return;
        }
        if (current instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collectVisibleNonEmptyTextViews(group.getChildAt(index), out);
            }
        }
    }

    private static boolean hasVisibleCharacters(CharSequence text) {
        for (int index = 0; index < text.length(); index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the rendered text bounding box of {@code labelView} in {@code host} coordinates.
     */
    static boolean resolveLanguageTextRect(View host, TextView labelView, RectF outRect) {
        if (host == null || labelView == null || outRect == null) {
            return false;
        }
        if (!mapDescendantRectToHost(labelView, host, outRect)) {
            return false;
        }
        float contentLeft = outRect.left + labelView.getCompoundPaddingLeft();
        float contentTop = outRect.top + labelView.getCompoundPaddingTop();
        float contentRight = outRect.right - labelView.getCompoundPaddingRight();
        float contentBottom = outRect.bottom - labelView.getCompoundPaddingBottom();
        float availableWidth = contentRight - contentLeft;
        float availableHeight = contentBottom - contentTop;
        if (availableWidth <= 0f || availableHeight <= 0f) {
            return false;
        }

        Layout layout = labelView.getLayout();
        if (layout != null && layout.getLineCount() > 0) {
            float minLineLeft = Float.MAX_VALUE;
            float maxLineRight = -Float.MAX_VALUE;
            int lineCount = layout.getLineCount();
            for (int line = 0; line < lineCount; line++) {
                float lineLeft = layout.getLineLeft(line);
                float lineRight = layout.getLineRight(line);
                if (lineRight > lineLeft) {
                    if (lineLeft < minLineLeft) {
                        minLineLeft = lineLeft;
                    }
                    if (lineRight > maxLineRight) {
                        maxLineRight = lineRight;
                    }
                }
            }
            if (maxLineRight > minLineLeft) {
                float scrollX = labelView.getScrollX();
                float left = clamp(contentLeft + minLineLeft - scrollX, contentLeft, contentRight);
                float right = clamp(
                        contentLeft + maxLineRight - scrollX, contentLeft, contentRight);
                if (right > left) {
                    outRect.set(left, contentTop, right, contentBottom);
                    return true;
                }
            }
        }

        CharSequence rawText = labelView.getText();
        String trimmed = rawText == null ? "" : rawText.toString().trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        float measuredWidth = 0f;
        if (labelView.getPaint() != null) {
            measuredWidth = labelView.getPaint().measureText(trimmed);
        }
        if (measuredWidth <= 0f) {
            measuredWidth = availableWidth;
        }
        float clampedWidth = Math.min(measuredWidth, availableWidth);
        float centerX = (contentLeft + contentRight) / 2f;
        outRect.set(
                centerX - clampedWidth / 2f,
                contentTop,
                centerX + clampedWidth / 2f,
                contentBottom);
        return true;
    }

    private static boolean mapDescendantRectToHost(View descendant, View host, RectF outRect) {
        if (descendant == null || host == null || outRect == null) {
            return false;
        }
        float left = 0f;
        float top = 0f;
        View current = descendant;
        while (current != null && current != host) {
            left += current.getLeft() + current.getTranslationX();
            top += current.getTop() + current.getTranslationY();
            ViewParent parent = current.getParent();
            if (!(parent instanceof View parentView)) {
                return false;
            }
            left -= parentView.getScrollX();
            top -= parentView.getScrollY();
            current = parentView;
        }
        if (current != host) {
            return false;
        }
        outRect.set(left, top, left + descendant.getWidth(), top + descendant.getHeight());
        return true;
    }

    /**
     * Pure, deterministic symmetric chevron geometry solver.
     *
     * <p>Guarantees:
     * <ul>
     *   <li>Left and right chevrons are always symmetric relative to the keycap edges.</li>
     *   <li>Chevrons never overlap the language text rect and never cross the keycap bounds.</li>
     *   <li>Automatically scales down in tighter corridors (e.g. long language names, One-handed,
     *       Floating) and hides cleanly when space is insufficient (e.g. narrow Split halves).</li>
     * </ul>
     */
    static ChevronLayout computeLayout(RectF keycapRect, RectF textRect, float density) {
        if (keycapRect == null || textRect == null) {
            return ChevronLayout.HIDDEN;
        }
        return computeLayout(
                keycapRect.left, keycapRect.top, keycapRect.right, keycapRect.bottom,
                textRect.left, textRect.top, textRect.right, textRect.bottom,
                density);
    }

    static ChevronLayout computeLayout(
            float keycapLeft,
            float keycapTop,
            float keycapRight,
            float keycapBottom,
            float textLeft,
            float textTop,
            float textRight,
            float textBottom,
            float density) {
        if (density <= 0f) {
            return ChevronLayout.HIDDEN;
        }
        float keycapWidth = keycapRight - keycapLeft;
        float keycapHeight = keycapBottom - keycapTop;
        if (keycapWidth < MIN_KEYCAP_WIDTH_DP * density
                || keycapHeight < MIN_KEYCAP_HEIGHT_DP * density) {
            return ChevronLayout.HIDDEN;
        }
        if (textRight - textLeft <= 0f) {
            return ChevronLayout.HIDDEN;
        }

        float leftCorridor = textLeft - keycapLeft;
        float rightCorridor = keycapRight - textRight;
        float symmetricCorridor = Math.min(leftCorridor, rightCorridor);
        if (symmetricCorridor <= 0f) {
            return ChevronLayout.HIDDEN;
        }

        float normalHeight = clamp(
                keycapHeight * PREFERRED_CHEVRON_HEIGHT_RATIO,
                NORMAL_CHEVRON_MIN_HEIGHT_DP * density,
                NORMAL_CHEVRON_MAX_HEIGHT_DP * density);
        float normalWidth = normalHeight * CHEVRON_WIDTH_TO_HEIGHT_RATIO;
        float normalMinEdgeInset = NORMAL_MIN_EDGE_INSET_DP * density;
        float normalMinTextGap = NORMAL_MIN_TEXT_GAP_DP * density;
        float normalNeeded = normalMinEdgeInset + normalWidth + normalMinTextGap;

        float compactMinHeight = COMPACT_CHEVRON_MIN_HEIGHT_DP * density;
        float compactMinWidth = compactMinHeight * CHEVRON_WIDTH_TO_HEIGHT_RATIO;
        float compactMinEdgeInset = COMPACT_MIN_EDGE_INSET_DP * density;
        float compactMinTextGap = COMPACT_MIN_TEXT_GAP_DP * density;
        float compactNeeded = compactMinEdgeInset + compactMinWidth + compactMinTextGap;

        if (symmetricCorridor < compactNeeded) {
            return ChevronLayout.HIDDEN;
        }

        final float chevronHeight;
        final float chevronWidth;
        final float edgeInset;

        if (symmetricCorridor >= normalNeeded) {
            chevronHeight = normalHeight;
            chevronWidth = normalWidth;
            float preferredInset = clamp(
                    keycapWidth * PREFERRED_EDGE_INSET_RATIO,
                    PREFERRED_EDGE_INSET_MIN_DP * density,
                    PREFERRED_EDGE_INSET_MAX_DP * density);
            float maxSafeInset = symmetricCorridor - chevronWidth - normalMinTextGap;
            edgeInset = clamp(preferredInset, normalMinEdgeInset, maxSafeInset);
        } else {
            float fraction = (normalNeeded > compactNeeded)
                    ? clamp((symmetricCorridor - compactNeeded) / (normalNeeded - compactNeeded),
                    0f, 1f)
                    : 0f;
            chevronHeight = compactMinHeight + (normalHeight - compactMinHeight) * fraction;
            chevronWidth = chevronHeight * CHEVRON_WIDTH_TO_HEIGHT_RATIO;
            float maxSafeInset = symmetricCorridor - chevronWidth - compactMinTextGap;
            edgeInset = clamp(
                    (compactMinEdgeInset + maxSafeInset) / 2f,
                    compactMinEdgeInset,
                    maxSafeInset);
        }

        float strokeWidth = clamp(
                chevronHeight * STROKE_TO_HEIGHT_RATIO,
                MIN_STROKE_DP * density,
                MAX_STROKE_DP * density);

        float halfHeight = chevronHeight / 2f;
        float verticalPad = VERTICAL_EDGE_PADDING_DP * density + strokeWidth;
        float minCenterY = keycapTop + halfHeight + verticalPad;
        float maxCenterY = keycapBottom - halfHeight - verticalPad;
        if (maxCenterY < minCenterY) {
            return ChevronLayout.HIDDEN;
        }
        float textCenterY = (textTop + textBottom) / 2f;
        float centerY = clamp(textCenterY, minCenterY, maxCenterY);

        float leftChevronLeft = keycapLeft + edgeInset;
        float leftChevronRight = leftChevronLeft + chevronWidth;
        float rightChevronRight = keycapRight - edgeInset;
        float rightChevronLeft = rightChevronRight - chevronWidth;

        // Final invariant check: never overlap text and keep strict left/right ordering.
        if (leftChevronRight > textLeft || rightChevronLeft < textRight
                || leftChevronRight >= rightChevronLeft) {
            return ChevronLayout.HIDDEN;
        }

        return new ChevronLayout(
                true,
                leftChevronLeft,
                leftChevronRight,
                rightChevronLeft,
                rightChevronRight,
                centerY - halfHeight,
                centerY,
                centerY + halfHeight,
                strokeWidth);
    }

    /**
     * Derives a theme-matched chevron color from the live language label text color, falling back
     * safely to night/day neutral contrast if the label color is transparent.
     */
    static int resolveChevronColor(int labelTextColor, float labelAlpha, Context context) {
        return resolveChevronColorArgb(labelTextColor, labelAlpha, isNightMode(context));
    }

    static int resolveChevronColorArgb(int labelTextColor, float labelAlpha, boolean nightMode) {
        int baseAlpha = (labelTextColor >>> 24) & 0xFF;
        if (baseAlpha > 0) {
            float effectiveAlphaFactor = clamp(labelAlpha, 0f, 1f);
            int modulatedAlpha = Math.round(
                    baseAlpha * effectiveAlphaFactor * CHEVRON_ALPHA_MULTIPLIER);
            if (effectiveAlphaFactor > 0.01f && modulatedAlpha < MIN_VISIBLE_CHEVRON_ALPHA) {
                modulatedAlpha = Math.min(baseAlpha, MIN_VISIBLE_CHEVRON_ALPHA);
            }
            modulatedAlpha = Math.max(0, Math.min(255, modulatedAlpha));
            return (modulatedAlpha << 24) | (labelTextColor & 0x00FFFFFF);
        }
        return nightMode ? FALLBACK_DARK_MODE_COLOR : FALLBACK_LIGHT_MODE_COLOR;
    }

    private static boolean isNightMode(Context context) {
        if (context == null) {
            return false;
        }
        try {
            Resources resources = context.getResources();
            if (resources == null || resources.getConfiguration() == null) {
                return false;
            }
            return (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clampRectToBounds(
            RectF rect, float minLeft, float minTop, float maxRight, float maxBottom) {
        rect.left = Math.max(minLeft, rect.left);
        rect.top = Math.max(minTop, rect.top);
        rect.right = Math.min(maxRight, rect.right);
        rect.bottom = Math.min(maxBottom, rect.bottom);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static float densityOf(View view) {
        try {
            Resources resources = view.getResources();
            if (resources != null && resources.getDisplayMetrics() != null
                    && resources.getDisplayMetrics().density > 0f) {
                return resources.getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {
            // Fall back to mdpi.
        }
        return 1f;
    }

    static final class ChevronLayout {
        static final ChevronLayout HIDDEN = new ChevronLayout(
                false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

        final boolean visible;
        final float leftChevronLeft;
        final float leftChevronRight;
        final float rightChevronLeft;
        final float rightChevronRight;
        final float topY;
        final float centerY;
        final float bottomY;
        final float strokeWidth;

        ChevronLayout(
                boolean visible,
                float leftChevronLeft,
                float leftChevronRight,
                float rightChevronLeft,
                float rightChevronRight,
                float topY,
                float centerY,
                float bottomY,
                float strokeWidth) {
            this.visible = visible;
            this.leftChevronLeft = leftChevronLeft;
            this.leftChevronRight = leftChevronRight;
            this.rightChevronLeft = rightChevronLeft;
            this.rightChevronRight = rightChevronRight;
            this.topY = topY;
            this.centerY = centerY;
            this.bottomY = bottomY;
            this.strokeWidth = strokeWidth;
        }
    }

    private static final class ChevronAttachment {
        final SpacebarChevronDrawable drawable;
        final View.OnLayoutChangeListener layoutListener;
        final View.OnAttachStateChangeListener attachListener;
        private boolean overlayAttached;

        ChevronAttachment(View host, SpacebarChevronDrawable drawable) {
            this.drawable = drawable;
            WeakReference<View> hostRef = new WeakReference<>(host);
            this.layoutListener = (v, left, top, right, bottom,
                    oldLeft, oldTop, oldRight, oldBottom) -> {
                drawable.setBounds(0, 0, Math.max(0, right - left), Math.max(0, bottom - top));
                drawable.invalidateSelf();
            };
            this.attachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    View target = hostRef.get();
                    View effective = target != null ? target : v;
                    if (!GboardSpacebarGestureSettings.isSpacebarLanguageChevronsEnabled(
                            effective.getContext())
                            || !spaceKeyPredicate.isSpaceKey(effective)) {
                        detachAttachment(effective);
                        return;
                    }
                    attachOverlayAndListeners(effective);
                    syncBounds(effective);
                    drawable.invalidateSelf();
                    effective.invalidate();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    View target = hostRef.get();
                    suspendOverlay(target != null ? target : v);
                }
            };
        }

        void attachOverlayAndListeners(View host) {
            if (host == null || overlayAttached) {
                return;
            }
            ViewOverlay overlay = host.getOverlay();
            if (overlay == null) {
                return;
            }
            syncBounds(host);
            overlay.add(drawable);
            host.addOnLayoutChangeListener(layoutListener);
            overlayAttached = true;
        }

        void syncBounds(View host) {
            if (host == null) {
                return;
            }
            drawable.setBounds(0, 0, Math.max(0, host.getWidth()), Math.max(0, host.getHeight()));
        }

        void suspendOverlay(View host) {
            if (host == null) {
                return;
            }
            overlayAttached = false;
            try {
                host.removeOnLayoutChangeListener(layoutListener);
            } catch (Throwable ignored) {
                // Safe cleanup.
            }
            try {
                ViewOverlay overlay = host.getOverlay();
                if (overlay != null) {
                    overlay.remove(drawable);
                }
            } catch (Throwable ignored) {
                // Safe cleanup.
            }
            try {
                host.invalidate();
            } catch (Throwable ignored) {
                // Safe cleanup.
            }
        }

        void detachCompletely(View host) {
            suspendOverlay(host);
            try {
                host.removeOnAttachStateChangeListener(attachListener);
            } catch (Throwable ignored) {
                // Safe cleanup.
            }
        }
    }

    static final class SpacebarChevronDrawable extends Drawable {
        private final WeakReference<View> hostRef;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path leftPath = new Path();
        private final Path rightPath = new Path();
        private final RectF keycapRect = new RectF();
        private final RectF textRect = new RectF();
        private int drawableAlpha = 255;
        private ChevronLayout lastDrawnLayout = ChevronLayout.HIDDEN;
        private int lastDrawnColor = 0;

        SpacebarChevronDrawable(View host) {
            this.hostRef = new WeakReference<>(host);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            lastDrawnLayout = ChevronLayout.HIDDEN;
            lastDrawnColor = 0;
            if (canvas == null) {
                return;
            }
            View host = hostRef.get();
            if (host == null || host.getVisibility() != View.VISIBLE
                    || host.getWidth() <= 0 || host.getHeight() <= 0
                    || host.getAlpha() <= 0.01f) {
                return;
            }
            if (!host.isAttachedToWindow()) {
                return;
            }

            // Recycling barrier #2: re-check setting & key identity before drawing any pixel.
            if (!GboardSpacebarGestureSettings.isSpacebarLanguageChevronsEnabled(
                    host.getContext())
                    || !spaceKeyPredicate.isSpaceKey(host)) {
                scheduleDetach(host);
                return;
            }

            if (!resolveKeycapRect(host, keycapRect)) {
                return;
            }
            TextView labelView = findPrimaryLanguageLabel(host, keycapRect);
            if (labelView == null) {
                return;
            }
            if (!resolveLanguageTextRect(host, labelView, textRect)) {
                return;
            }

            float density = densityOf(host);
            ChevronLayout layout = computeLayout(keycapRect, textRect, density);
            if (!layout.visible) {
                return;
            }

            int color = resolveChevronColor(
                    labelView.getCurrentTextColor(),
                    labelView.getAlpha(),
                    host.getContext());
            int effectiveAlpha = (Color.alpha(color) * drawableAlpha) / 255;
            if (effectiveAlpha <= 0) {
                return;
            }

            paint.setColor(Color.argb(
                    effectiveAlpha,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)));
            paint.setStrokeWidth(layout.strokeWidth);

            leftPath.rewind();
            leftPath.moveTo(layout.leftChevronRight, layout.topY);
            leftPath.lineTo(layout.leftChevronLeft, layout.centerY);
            leftPath.lineTo(layout.leftChevronRight, layout.bottomY);

            rightPath.rewind();
            rightPath.moveTo(layout.rightChevronLeft, layout.topY);
            rightPath.lineTo(layout.rightChevronRight, layout.centerY);
            rightPath.lineTo(layout.rightChevronLeft, layout.bottomY);

            canvas.drawPath(leftPath, paint);
            canvas.drawPath(rightPath, paint);

            lastDrawnLayout = layout;
            lastDrawnColor = paint.getColor();
        }

        ChevronLayout getLastDrawnLayoutForTesting() {
            return lastDrawnLayout;
        }

        int getLastDrawnColorForTesting() {
            return lastDrawnColor;
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // --- Package-private test helpers ---

    static void setSpaceKeyPredicateForTesting(SpaceKeyPredicate predicate) {
        spaceKeyPredicate = predicate != null
                ? predicate
                : GboardSpacebarKeyHitTester::isSpaceSoftKeyView;
    }

    static boolean hasActiveAttachmentForTesting(View host) {
        synchronized (ACTIVE_ATTACHMENTS) {
            return ACTIVE_ATTACHMENTS.containsKey(host);
        }
    }

    static SpacebarChevronDrawable getActiveDrawableForTesting(View host) {
        synchronized (ACTIVE_ATTACHMENTS) {
            ChevronAttachment attachment = ACTIVE_ATTACHMENTS.get(host);
            return attachment == null ? null : attachment.drawable;
        }
    }

    static int activeAttachmentCountForTesting() {
        synchronized (ACTIVE_ATTACHMENTS) {
            return ACTIVE_ATTACHMENTS.size();
        }
    }

    static void resetForTesting() {
        List<View> active;
        synchronized (ACTIVE_ATTACHMENTS) {
            active = new ArrayList<>(ACTIVE_ATTACHMENTS.keySet());
        }
        for (View view : active) {
            detachAttachment(view);
        }
        synchronized (TRACKED_SPACEBAR_VIEWS) {
            TRACKED_SPACEBAR_VIEWS.clear();
        }
        spaceKeyPredicate = GboardSpacebarKeyHitTester::isSpaceSoftKeyView;
    }
}
