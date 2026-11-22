package dev.jason.gboardpatches.extension.spacebargesture;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Passive visual decoration for Gboard's existing spacebar label. */
public final class GboardSpacebarChevronRuntime {
    private static final float CHEVRON_WIDTH_DP = 5.5f;
    private static final float CHEVRON_HEIGHT_DP = 9.5f;
    private static final float STROKE_WIDTH_DP = 1.65f;
    private static final float MIN_SIDE_GAP_DP = 4.0f;
    private static final float MIN_EDGE_INSET_DP = 4.0f;
    private static final float COMPOUND_DRAWABLE_PADDING_DP = 10.0f;
    private static final Map<TextView, SavedDrawables> TRACKED_LABELS = new WeakHashMap<>();
    private static final Map<View, Boolean> LAYOUT_RETRY_REQUESTED = new WeakHashMap<>();

    private GboardSpacebarChevronRuntime() {}

    /** Separate after-stock runtime entry point for the bound SoftKeyView. */
    public static void afterSoftKeyBind(View softKeyView) {
        if (softKeyView == null) return;
        try {
            boolean enabled = GboardSpacebarGestureSettings.isSpacebarLanguageChevronsEnabled(
                    softKeyView.getContext());
            boolean isSpaceKey = GboardSpacebarKeyHitTester.isSpaceSoftKeyView(softKeyView);
            applyToSoftKeyView(softKeyView, isSpaceKey, enabled);
            if (enabled && isSpaceKey) {
                TextView label = findPrimarySpaceLabel(softKeyView);
                if (label != null) {
                    if (measuredWidth(softKeyView) <= 0) {
                        retryOnceAfterLayout(softKeyView, softKeyView);
                    }
                    if (measuredWidth(label) <= 0) {
                        retryOnceAfterLayout(label, softKeyView);
                    }
                }
            }
        } catch (Throwable ignored) {
            // A decorative feature must never break Gboard's key binding or layout.
        }
    }

    /** Removes decoration immediately when the user turns the setting off. */
    public static void onChevronsPreferenceChanged(boolean enabled) {
        if (enabled) return;
        Runnable restore = () -> {
            List<TextView> snapshot;
            synchronized (TRACKED_LABELS) {
                snapshot = new ArrayList<>(TRACKED_LABELS.keySet());
            }
            for (TextView label : snapshot) {
                if (label != null) restoreIfTracked(label);
            }
        };
        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null && Looper.myLooper() != mainLooper) {
                new Handler(mainLooper).post(restore);
                return;
            }
        } catch (Throwable ignored) {
            // Keep the synchronous fallback usable in JVM tests.
        }
        restore.run();
    }

    static void applyToSoftKeyView(
            View softKeyView, boolean isSpaceKey, boolean chevronsEnabled) {
        if (softKeyView == null) return;
        if (!isSpaceKey || !chevronsEnabled) {
            restoreAnyTrackedLabel(softKeyView);
            return;
        }

        TextView labelView = findPrimarySpaceLabel(softKeyView);
        if (labelView == null) {
            restoreAnyTrackedLabel(softKeyView);
            return;
        }
        CharSequence text = labelView.getText();
        if (TextUtils.isEmpty(text) || text.toString().trim().isEmpty()) {
            restoreIfTracked(labelView);
            return;
        }

        Drawable[] current = labelView.getCompoundDrawables();
        Drawable currentLeft = current[0];
        Drawable currentRight = current[2];
        // Never replace stock left/right icons. Restore our old decoration first if the
        // recycled TextView has since received a stock drawable.
        if ((currentLeft != null && !(currentLeft instanceof ChevronDrawable))
                || (currentRight != null && !(currentRight instanceof ChevronDrawable))) {
            restoreIfTracked(labelView);
            return;
        }

        float density = resolveDensity(labelView);
        int chevronWidthPx = Math.max(1, Math.round(CHEVRON_WIDTH_DP * density));
        int chevronHeightPx = Math.max(1, Math.round(CHEVRON_HEIGHT_DP * density));
        int compoundDrawablePaddingPx = Math.max(
                0, Math.round(COMPOUND_DRAWABLE_PADDING_DP * density));
        if (!hasEnoughHorizontalSpace(
                softKeyView, labelView, text, chevronWidthPx,
                compoundDrawablePaddingPx, density)) {
            restoreIfTracked(labelView);
            return;
        }

        int color = labelView.getCurrentTextColor();
        if (currentLeft instanceof ChevronDrawable left
                && currentRight instanceof ChevronDrawable right
                && left.matches(color, chevronWidthPx, chevronHeightPx)
                && right.matches(color, chevronWidthPx, chevronHeightPx)
                && labelView.getCompoundDrawablePadding() == compoundDrawablePaddingPx) {
            return;
        }

        synchronized (TRACKED_LABELS) {
            TRACKED_LABELS.putIfAbsent(
                    labelView, new SavedDrawables(
                            current[0], current[1], current[2], current[3],
                            labelView.getCompoundDrawablePadding()));
        }
        labelView.setCompoundDrawablePadding(compoundDrawablePaddingPx);
        labelView.setCompoundDrawables(
                new ChevronDrawable(true, color, chevronWidthPx, chevronHeightPx, density),
                current[1],
                new ChevronDrawable(false, color, chevronWidthPx, chevronHeightPx, density),
                current[3]);
    }

    /** Uses actual keycap and label content widths; unknown dimensions fail closed. */
    static boolean hasEnoughHorizontalSpace(
            View keycapView, TextView labelView, CharSequence text,
            int chevronWidthPx, int compoundDrawablePaddingPx, float density) {
        int availableWidth = measuredWidth(keycapView)
                - keycapView.getPaddingLeft() - keycapView.getPaddingRight();
        if (availableWidth <= 0) return false;

        // A wrap_content label is normally only as wide as its text, so use the
        // keycap width for that case. Fixed/match-parent labels also constrain
        // the usable width through their own measured content bounds.
        ViewGroup.LayoutParams labelLayoutParams = labelView.getLayoutParams();
        if (labelLayoutParams == null
                || labelLayoutParams.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            int labelContentWidth = measuredWidth(labelView)
                    - labelView.getPaddingLeft() - labelView.getPaddingRight();
            if (labelContentWidth > 0) {
                availableWidth = Math.min(availableWidth, labelContentWidth);
            }
        }

        // getMaxWidth() is a separate TextView constraint, including for wrap_content.
        int labelMaxWidth = labelView.getMaxWidth();
        if (labelMaxWidth > 0 && labelMaxWidth < availableWidth) {
            availableWidth = labelMaxWidth
                    - labelView.getPaddingLeft() - labelView.getPaddingRight();
        }
        if (availableWidth <= 0) return false;

        float textWidth = labelView.getPaint().measureText(text.toString());
        float perSideAllowance = chevronWidthPx + compoundDrawablePaddingPx
                + (MIN_SIDE_GAP_DP + MIN_EDGE_INSET_DP) * density;
        return availableWidth >= textWidth + 2f * perSideAllowance;
    }

    private static int measuredWidth(View view) {
        int width = view.getWidth();
        return width > 0 ? width : view.getMeasuredWidth();
    }

    /** One bounded retry after first layout; no polling and no repeated layout requests. */
    private static void retryOnceAfterLayout(View layoutTarget, View keyView) {
        synchronized (LAYOUT_RETRY_REQUESTED) {
            if (LAYOUT_RETRY_REQUESTED.containsKey(layoutTarget)) return;
            LAYOUT_RETRY_REQUESTED.put(layoutTarget, Boolean.TRUE);
        }
        layoutTarget.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(
                    View changedView, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                changedView.removeOnLayoutChangeListener(this);
                synchronized (LAYOUT_RETRY_REQUESTED) {
                    LAYOUT_RETRY_REQUESTED.remove(changedView);
                }
                afterSoftKeyBind(keyView);
            }
        });
    }

    private static TextView findPrimarySpaceLabel(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        if (root instanceof TextView textView) {
            return TextUtils.isEmpty(textView.getText()) ? null : textView;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findPrimarySpaceLabel(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void restoreAnyTrackedLabel(View root) {
        if (root instanceof TextView textView) {
            restoreIfTracked(textView);
        } else if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                restoreAnyTrackedLabel(group.getChildAt(i));
            }
        }
    }

    private static void restoreIfTracked(TextView labelView) {
        SavedDrawables saved;
        synchronized (TRACKED_LABELS) {
            saved = TRACKED_LABELS.remove(labelView);
        }
        if (saved == null) return;
        Drawable[] current = labelView.getCompoundDrawables();
        Drawable left = current[0] instanceof ChevronDrawable ? saved.left : current[0];
        Drawable right = current[2] instanceof ChevronDrawable ? saved.right : current[2];
        labelView.setCompoundDrawables(left, current[1], right, current[3]);
        labelView.setCompoundDrawablePadding(saved.compoundDrawablePadding);
    }

    private static float resolveDensity(View view) {
        try {
            Resources resources = view.getResources();
            if (resources != null && resources.getDisplayMetrics() != null
                    && resources.getDisplayMetrics().density > 0f) {
                return resources.getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {
            // Use density-independent test fallback.
        }
        return 1f;
    }

    static void resetForTesting() {
        synchronized (TRACKED_LABELS) {
            TRACKED_LABELS.clear();
        }
        synchronized (LAYOUT_RETRY_REQUESTED) {
            LAYOUT_RETRY_REQUESTED.clear();
        }
    }

    private static final class SavedDrawables {
        final Drawable left;
        final Drawable top;
        final Drawable right;
        final Drawable bottom;
        final int compoundDrawablePadding;

        SavedDrawables(
                Drawable left, Drawable top, Drawable right, Drawable bottom,
                int compoundDrawablePadding) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.compoundDrawablePadding = compoundDrawablePadding;
        }
    }

    static final class ChevronDrawable extends Drawable {
        private final int color;
        private final int widthPx;
        private final int heightPx;
        private final Paint paint;
        private final Path path = new Path();

        ChevronDrawable(boolean pointsLeft, int color, int widthPx, int heightPx, float density) {
            this.color = color;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(1f, STROKE_WIDTH_DP * density));
            setBounds(0, 0, widthPx, heightPx);

            float inset = paint.getStrokeWidth() * 0.5f;
            float left = inset;
            float right = Math.max(left + 1f, widthPx - inset);
            float top = inset;
            float bottom = Math.max(top + 1f, heightPx - inset);
            float middle = (top + bottom) * 0.5f;
            if (pointsLeft) {
                path.moveTo(right, top);
                path.lineTo(left, middle);
                path.lineTo(right, bottom);
            } else {
                path.moveTo(left, top);
                path.lineTo(right, middle);
                path.lineTo(left, bottom);
            }
        }

        boolean matches(int expectedColor, int expectedWidth, int expectedHeight) {
            return color == expectedColor && widthPx == expectedWidth && heightPx == expectedHeight;
        }

        int getColor() { return color; }

        @Override public int getIntrinsicWidth() { return widthPx; }
        @Override public int getIntrinsicHeight() { return heightPx; }
        @Override public void draw(Canvas canvas) { canvas.drawPath(path, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
