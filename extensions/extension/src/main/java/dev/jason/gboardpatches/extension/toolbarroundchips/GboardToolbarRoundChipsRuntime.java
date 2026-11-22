package dev.jason.gboardpatches.extension.toolbarroundchips;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Passive visual decorator that renders adaptive Samsung One UI 8-style circular backgrounds
 * behind standard Access Point icons in Gboard's top toolbar.
 *
 * <p>Verified against Gboard 18.5.4 XML/DEX:
 * <ul>
 *   <li>Top-bar Access Points are {@code AccessPointsBarItemSoftKeyView} (extends {@code SoftKeyView})
 *       containing a dedicated background {@link View} tagged
 *       {@code .access-point-bar-item-ripple-background.item-ripple} positioned behind the
 *       {@code .icon.for-access-point-icon} {@link ImageView}.</li>
 *   <li>Attaching a {@link ViewOverlay} drawable to that background {@link View} draws the circle
 *       behind the stock Google icon without mutating stock {@code background}/ripple drawables,
 *       touch listeners, drag-and-drop, or layout bounds.</li>
 *   <li>Rambler voice button ({@code .background-icon.for-action-key}), the {@code ⊞} menu button
 *       ({@code .access-point-entry-menu-icon} / {@code .access-point-menu-icon}), expanded menu
 *       items, candidate suggestions, and clipboard chips are strictly excluded.</li>
 * </ul>
 */
public final class GboardToolbarRoundChipsRuntime {
    static final String ACCESS_POINT_BAR_BG_TAG = ".access-point-bar-item-ripple-background";
    static final String ACCESS_POINT_ICON_TAG = ".icon.for-access-point-icon";

    private static final String ACTION_KEY_BG_TAG = ".background-icon.for-action-key";
    private static final String MENU_ENTRY_ICON_TAG = ".access-point-entry-menu-icon";
    private static final String MENU_ICON_TAG = ".access-point-menu-icon";
    private static final String CANDIDATES_AREA_TAG = ".candidates-area";
    private static final String CANDIDATE_KEY_TAG = ".for-candidate-key";

    private static final float MIN_SLOT_DP = 24.0f;
    private static final float MIN_DIAMETER_DP = 24.0f;
    private static final float MAX_DIAMETER_DP = 36.0f;
    private static final float SLOT_INSET_DP = 4.0f;
    private static final float MAX_ASPECT_RATIO = 1.6f;

    private static final int LIGHT_THEME_CHIP_ALPHA = 24;
    private static final int DARK_THEME_CHIP_ALPHA = 34;
    private static final int FALLBACK_LIGHT_ICON_COLOR = 0xFF1C1C1E;
    private static final int FALLBACK_DARK_ICON_COLOR = 0xFFFFFFFF;

    private static final Map<View, AttachedChip> ATTACHED_CHIPS = new WeakHashMap<>();
    private static final Map<View, Boolean> KNOWN_TOOLBAR_ITEMS = new WeakHashMap<>();

    private static volatile Predicate<View> settingEnabledPredicate =
            view -> GboardToolbarRoundChipsSettings.isEnabled(view.getContext());

    private GboardToolbarRoundChipsRuntime() {}

    /** Separate after-stock runtime entry point for bound SoftKeyView instances. */
    public static void afterSoftKeyBind(View softKeyView) {
        if (softKeyView == null) return;
        try {
            boolean enabled = settingEnabledPredicate.test(softKeyView);
            applyToSoftKeyView(softKeyView, enabled);
        } catch (Throwable ignored) {
            // Decorative toolbar chips must never disrupt key binding or keyboard stability.
        }
    }

    /** Immediately removes or re-applies chips when the user toggles the setting. */
    public static void onPreferenceChanged(boolean enabled) {
        Runnable update = () -> {
            if (!enabled) {
                List<View> hosts;
                synchronized (ATTACHED_CHIPS) {
                    hosts = new ArrayList<>(ATTACHED_CHIPS.keySet());
                }
                for (View host : hosts) {
                    if (host != null) {
                        detachChip(host);
                    }
                }
                return;
            }
            List<View> candidates;
            synchronized (KNOWN_TOOLBAR_ITEMS) {
                candidates = new ArrayList<>(KNOWN_TOOLBAR_ITEMS.keySet());
            }
            for (View host : candidates) {
                if (host != null) {
                    applyToSoftKeyView(host, true);
                }
            }
        };

        try {
            Looper mainLooper = Looper.getMainLooper();
            if (mainLooper != null && Looper.myLooper() != mainLooper) {
                new Handler(mainLooper).post(update);
                return;
            }
        } catch (Throwable ignored) {
            // Keep synchronous fallback usable in JVM unit tests.
        }
        update.run();
    }

    static void applyToSoftKeyView(View softKeyView, boolean enabled) {
        if (softKeyView == null) return;

        View bgTarget = findEligibleBackgroundTarget(softKeyView);
        if (bgTarget == null) {
            synchronized (KNOWN_TOOLBAR_ITEMS) {
                KNOWN_TOOLBAR_ITEMS.remove(softKeyView);
            }
            detachChip(softKeyView);
            return;
        }

        synchronized (KNOWN_TOOLBAR_ITEMS) {
            KNOWN_TOOLBAR_ITEMS.put(softKeyView, Boolean.TRUE);
        }

        if (!enabled) {
            detachChip(softKeyView);
            return;
        }

        attachOrUpdateChip(softKeyView, bgTarget);
    }

    private static void attachOrUpdateChip(View softKeyView, View bgTarget) {
        ViewOverlay overlay = bgTarget.getOverlay();
        if (overlay == null) return;

        AttachedChip existing;
        synchronized (ATTACHED_CHIPS) {
            existing = ATTACHED_CHIPS.get(softKeyView);
        }
        if (existing != null) {
            View existingBg = existing.bgTargetRef.get();
            if (existingBg == bgTarget) {
                existing.drawable.setBounds(
                        0, 0, Math.max(0, bgTarget.getWidth()), Math.max(0, bgTarget.getHeight()));
                bgTarget.invalidate();
                return;
            }
            detachChip(softKeyView);
        }

        RoundChipOverlayDrawable drawable = new RoundChipOverlayDrawable(softKeyView, bgTarget);
        drawable.setBounds(
                0, 0, Math.max(0, bgTarget.getWidth()), Math.max(0, bgTarget.getHeight()));
        View.OnLayoutChangeListener layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            drawable.setBounds(0, 0, Math.max(0, r - l), Math.max(0, b - t));
            v.invalidate();
        };

        overlay.add(drawable);
        bgTarget.addOnLayoutChangeListener(layoutListener);
        synchronized (ATTACHED_CHIPS) {
            ATTACHED_CHIPS.put(softKeyView, new AttachedChip(bgTarget, drawable, layoutListener));
        }
        bgTarget.invalidate();
    }

    private static void detachChip(View softKeyView) {
        AttachedChip attached;
        synchronized (ATTACHED_CHIPS) {
            attached = ATTACHED_CHIPS.remove(softKeyView);
        }
        if (attached == null) return;
        View bgTarget = attached.bgTargetRef.get();
        if (bgTarget == null) return;
        try {
            bgTarget.removeOnLayoutChangeListener(attached.layoutListener);
        } catch (Throwable ignored) {
            // Ignore detached view edge cases.
        }
        try {
            ViewOverlay overlay = bgTarget.getOverlay();
            if (overlay != null) {
                overlay.remove(attached.drawable);
            }
        } catch (Throwable ignored) {
            // Ignore detached view edge cases.
        }
        bgTarget.invalidate();
    }

    /**
     * Returns the dedicated top-bar Access Point ripple background view if and only if
     * {@code softKeyView} is a standard Access Point bar item (excluding Rambler, {@code ⊞} menu,
     * candidate keys, and expanded menu rows).
     */
    static View findEligibleBackgroundTarget(View softKeyView) {
        if (softKeyView == null || softKeyView.getVisibility() != View.VISIBLE) return null;
        if (hasExcludedAncestorTag(softKeyView)) return null;
        if (containsExcludedDescendantTag(softKeyView)) return null;
        return findViewWithTagToken(softKeyView, ACCESS_POINT_BAR_BG_TAG);
    }

    static ChipGeometry computeGeometry(View softKeyView, View bgTarget) {
        if (softKeyView == null || bgTarget == null) return null;
        if (softKeyView.getVisibility() != View.VISIBLE || bgTarget.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (!settingEnabledPredicate.test(softKeyView)) return null;
        if (findEligibleBackgroundTarget(softKeyView) != bgTarget) return null;

        int width = bgTarget.getWidth();
        int height = bgTarget.getHeight();
        if (width <= 0 || height <= 0) return null;

        // Exclude wide text/clipboard pills whose aspect ratio is not an icon slot.
        float aspectRatio = (float) width / (float) height;
        if (aspectRatio > MAX_ASPECT_RATIO) return null;

        float density = resolveDensity(bgTarget);
        float minDim = Math.min(width, height);
        if (minDim < MIN_SLOT_DP * density) return null;

        float diameter = clamp(
                minDim - SLOT_INSET_DP * density,
                MIN_DIAMETER_DP * density,
                MAX_DIAMETER_DP * density);
        if (diameter > minDim) {
            diameter = minDim;
        }

        int chipColor = resolveAdaptiveChipColor(softKeyView);
        return new ChipGeometry(width * 0.5f, height * 0.5f, diameter * 0.5f, chipColor);
    }

    static int resolveAdaptiveChipColor(View softKeyView) {
        int iconColor = resolveIconColor(softKeyView);
        int r = Color.red(iconColor);
        int g = Color.green(iconColor);
        int b = Color.blue(iconColor);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        int alpha = luminance >= 0.5 ? DARK_THEME_CHIP_ALPHA : LIGHT_THEME_CHIP_ALPHA;
        return Color.argb(alpha, r, g, b);
    }

    private static int resolveIconColor(View softKeyView) {
        ImageView iconView = findAccessPointIcon(softKeyView);
        if (iconView != null) {
            ColorStateList tintList = iconView.getImageTintList();
            if (tintList != null) {
                return tintList.getColorForState(
                        iconView.getDrawableState(), tintList.getDefaultColor());
            }
        }
        return isNightMode(softKeyView) ? FALLBACK_DARK_ICON_COLOR : FALLBACK_LIGHT_ICON_COLOR;
    }

    private static ImageView findAccessPointIcon(View root) {
        View tagged = findViewWithTagToken(root, ACCESS_POINT_ICON_TAG);
        if (tagged instanceof ImageView imageView) {
            return imageView;
        }
        return findFirstVisibleImageView(root);
    }

    private static ImageView findFirstVisibleImageView(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        if (root instanceof ImageView imageView) return imageView;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView found = findFirstVisibleImageView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasExcludedAncestorTag(View view) {
        ViewParent current = view.getParent();
        while (current instanceof View parentView) {
            if (hasTagToken(parentView, CANDIDATES_AREA_TAG)
                    || hasTagToken(parentView, CANDIDATE_KEY_TAG)) {
                return true;
            }
            current = parentView.getParent();
        }
        return false;
    }

    private static boolean containsExcludedDescendantTag(View root) {
        if (root == null) return false;
        if (hasTagToken(root, ACTION_KEY_BG_TAG)
                || hasTagToken(root, MENU_ENTRY_ICON_TAG)
                || hasTagToken(root, MENU_ICON_TAG)
                || hasTagToken(root, CANDIDATE_KEY_TAG)) {
            return true;
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsExcludedDescendantTag(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static View findViewWithTagToken(View root, String token) {
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        if (hasTagToken(root, token)) return root;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewWithTagToken(group.getChildAt(i), token);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasTagToken(View view, String token) {
        Object tag = view.getTag();
        if (!(tag instanceof String tagText)) return false;
        return tagText.equals(token)
                || tagText.startsWith(token + ".")
                || tagText.contains(token + ".")
                || tagText.endsWith(token);
    }

    private static boolean isNightMode(View view) {
        try {
            Resources resources = view.getResources();
            if (resources != null && resources.getConfiguration() != null) {
                int mask = resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return mask == Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {
            // Fall through to light fallback.
        }
        return false;
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static boolean isChipAttachedForTesting(View softKeyView) {
        synchronized (ATTACHED_CHIPS) {
            return ATTACHED_CHIPS.containsKey(softKeyView);
        }
    }

    static void setSettingEnabledPredicateForTesting(Predicate<View> predicate) {
        settingEnabledPredicate = predicate != null
                ? predicate
                : view -> GboardToolbarRoundChipsSettings.isEnabled(view.getContext());
    }

    static void resetForTesting() {
        List<View> hosts;
        synchronized (ATTACHED_CHIPS) {
            hosts = new ArrayList<>(ATTACHED_CHIPS.keySet());
        }
        for (View host : hosts) {
            if (host != null) detachChip(host);
        }
        synchronized (KNOWN_TOOLBAR_ITEMS) {
            KNOWN_TOOLBAR_ITEMS.clear();
        }
        setSettingEnabledPredicateForTesting(null);
    }

    private static final class AttachedChip {
        final WeakReference<View> bgTargetRef;
        final RoundChipOverlayDrawable drawable;
        final View.OnLayoutChangeListener layoutListener;

        AttachedChip(
                View bgTarget,
                RoundChipOverlayDrawable drawable,
                View.OnLayoutChangeListener layoutListener) {
            this.bgTargetRef = new WeakReference<>(bgTarget);
            this.drawable = drawable;
            this.layoutListener = layoutListener;
        }
    }

    static final class ChipGeometry {
        final float centerX;
        final float centerY;
        final float radius;
        final int color;

        ChipGeometry(float centerX, float centerY, float radius, int color) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.color = color;
        }
    }

    static final class RoundChipOverlayDrawable extends Drawable {
        private final WeakReference<View> hostRef;
        private final WeakReference<View> bgTargetRef;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RoundChipOverlayDrawable(View host, View bgTarget) {
            this.hostRef = new WeakReference<>(host);
            this.bgTargetRef = new WeakReference<>(bgTarget);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            View host = hostRef.get();
            View bgTarget = bgTargetRef.get();
            if (host == null || bgTarget == null) return;
            ChipGeometry geometry = computeGeometry(host, bgTarget);
            if (geometry == null) return;
            paint.setColor(geometry.color);
            canvas.drawCircle(geometry.centerX, geometry.centerY, geometry.radius, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
