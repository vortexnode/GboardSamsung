package dev.jason.gboardpatches.extension.frostedglass;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

/**
 * Applies a true AMOLED pure-black (#000000) keyboard/header/nav-bar background with subtle
 * charcoal keycaps (#141414) while preserving Material You action-key accents.
 * Active only in Night Mode when enabled, user is unlocked (Direct Boot safe), and Frosted Glass is off.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class GboardAmoledBlackRuntime {
    static final int AMOLED_BACKGROUND_COLOR = 0xFF000000;
    static final int AMOLED_KEY_COLOR = 0xFF141414;

    private static final Map<Window, State> STATES = new WeakHashMap<>();
    private static volatile PorterDuffColorFilter keyColorFilter;

    private GboardAmoledBlackRuntime() {
    }

    private static PorterDuffColorFilter keyColorFilter() {
        PorterDuffColorFilter filter = keyColorFilter;
        if (filter == null) {
            filter = new PorterDuffColorFilter(AMOLED_KEY_COLOR, PorterDuff.Mode.SRC_IN);
            keyColorFilter = filter;
        }
        return filter;
    }

    static boolean shouldApply(Context context) {
        if (context == null || !GboardFlagRuntimeContext.isUserUnlocked(context)) {
            return false;
        }
        return shouldApplyForState(
                GboardFrostedGlassSettingsRuntime.isAmoledBlackEnabled(),
                GboardFrostedGlassSettingsRuntime.isEnabled(),
                isNightMode(context));
    }

    static boolean shouldApplyForState(
            boolean amoledEnabled,
            boolean frostedGlassEnabled,
            boolean nightMode) {
        return amoledEnabled && !frostedGlassEnabled && nightMode;
    }

    static boolean isNightMode(Context context) {
        if (context == null || context.getResources() == null
                || context.getResources().getConfiguration() == null) {
            return false;
        }
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static void afterConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        try {
            if (window == null) {
                return;
            }
            Context context = window.getContext();
            if (!GboardFlagRuntimeContext.isUserUnlocked(context)) {
                return;
            }
            if (fullscreen || candidatesOnly || !shouldApply(context)) {
                restore(window);
                return;
            }
            apply(window);
        } catch (Throwable ignored) {
            // Never disrupt Gboard window configuration.
        }
    }

    static void onWindowShown(Object receiver) {
        try {
            if (!(receiver instanceof InputMethodService service)) {
                return;
            }
            if (!GboardFlagRuntimeContext.isUserUnlocked(service)) {
                return;
            }
            Dialog dialog = service.getWindow();
            Window window = dialog == null ? null : dialog.getWindow();
            if (window == null) {
                return;
            }
            if (!shouldApply(service)) {
                restore(window);
                return;
            }
            apply(window);
        } catch (Throwable ignored) {
            // Never disrupt showing the IME.
        }
    }

    static void onWindowHidden(Object receiver) {
        try {
            if (!(receiver instanceof InputMethodService service)) {
                return;
            }
            if (!GboardFlagRuntimeContext.isUserUnlocked(service)) {
                return;
            }
            Dialog dialog = service.getWindow();
            Window window = dialog == null ? null : dialog.getWindow();
            if (window != null && !shouldApply(service)) {
                restore(window);
            }
        } catch (Throwable ignored) {
            // Never disrupt hiding the IME.
        }
    }

    static void onApplyNavBarExit(Window window) {
        try {
            if (window == null) {
                return;
            }
            if (!GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) {
                return;
            }
            if (!shouldApply(window.getContext())) {
                return;
            }
            applyNavBarBlack(window, stateFor(window));
        } catch (Throwable ignored) {
            // Never disrupt stock nav bar styling.
        }
    }

    private static void apply(Window window) {
        State state = stateFor(window);
        View decor = window.getDecorView();
        if (decor == null) {
            return;
        }
        applyNavBarBlack(window, state);
        applyToHierarchy(decor, state);
        if (state.layoutListener == null) {
            View.OnLayoutChangeListener listener =
                    (v, l, t, r, b, ol, ot, or, ob) -> {
                        try {
                            if (shouldApply(window.getContext())) {
                                applyToHierarchy(decor, state);
                                applyNavBarBlack(window, state);
                            } else {
                                restore(window);
                            }
                        } catch (Throwable ignored) {
                            // Never disrupt layout passes.
                        }
                    };
            state.layoutListener = listener;
            state.observedDecor = decor;
            decor.addOnLayoutChangeListener(listener);
        }
    }

    private static void applyNavBarBlack(Window window, State state) {
        if (!state.navCaptured) {
            state.originalNavColor = window.getNavigationBarColor();
            state.originalNavDividerColor = window.getNavigationBarDividerColor();
            state.originalNavContrast = window.isNavigationBarContrastEnforced();
            state.navCaptured = true;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (window.getNavigationBarColor() != AMOLED_BACKGROUND_COLOR) {
            window.setNavigationBarColor(AMOLED_BACKGROUND_COLOR);
        }
        if (window.getNavigationBarDividerColor() != AMOLED_BACKGROUND_COLOR) {
            window.setNavigationBarDividerColor(AMOLED_BACKGROUND_COLOR);
        }
        if (window.isNavigationBarContrastEnforced()) {
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(
                    0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    static void applyToHierarchy(View view, State state) {
        if (view == null) {
            return;
        }
        if (view.getTag() instanceof String tag) {
            if (isAmoledSurfaceTag(tag)) {
                applyBlackSurface(view, state);
            } else if (isAmoledKeyTag(tag)) {
                applyCharcoalKey(view, state);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToHierarchy(group.getChildAt(i), state);
            }
        }
    }

    static boolean isAmoledSurfaceTag(String tag) {
        if (tag == null) {
            return false;
        }
        return tag.equals(".keyboard-base-area")
                || tag.equals(".keyboard-base-area.keyboard-outline")
                || tag.contains(".keyboard-header-area")
                || tag.contains(".keyboard-body-area")
                || tag.startsWith(".candidates-area");
    }

    static boolean isAmoledKeyTag(String tag) {
        if (tag == null) {
            return false;
        }
        if (tag.contains(".for-action-key") || tag.contains(".for-candidate-key")) {
            return false;
        }
        return tag.equals(".keytop")
                || tag.startsWith(".keytop.")
                || tag.equals(".space_bar")
                || tag.startsWith(".space_bar.");
    }

    private static void applyBlackSurface(View view, State state) {
        Drawable current = view.getBackground();
        if (current instanceof AmoledBlackDrawable) {
            return;
        }
        state.originalSurfaceBackgrounds.put(view, current);
        view.setBackground(new AmoledBlackDrawable());
    }

    private static void applyCharcoalKey(View view, State state) {
        Drawable target = unwrapDrawable(view.getBackground());
        if (target == null || target.getAlpha() == 0) {
            return;
        }
        if (!state.tintedKeyDrawables.containsKey(target)) {
            target.mutate();
            state.tintedKeyDrawables.put(target, Boolean.TRUE);
        }
        target.setColorFilter(keyColorFilter());
    }

    static void restore(Window window) {
        State state;
        synchronized (STATES) {
            state = STATES.remove(window);
        }
        if (state == null) {
            return;
        }
        if (state.observedDecor != null && state.layoutListener != null) {
            state.observedDecor.removeOnLayoutChangeListener(state.layoutListener);
        }
        for (Map.Entry<View, Drawable> entry : state.originalSurfaceBackgrounds.entrySet()) {
            View view = entry.getKey();
            if (view != null && view.getBackground() instanceof AmoledBlackDrawable) {
                view.setBackground(entry.getValue());
            }
        }
        state.originalSurfaceBackgrounds.clear();
        for (Drawable drawable : state.tintedKeyDrawables.keySet()) {
            if (drawable != null) {
                drawable.clearColorFilter();
            }
        }
        state.tintedKeyDrawables.clear();
        if (state.navCaptured) {
            try {
                window.setNavigationBarColor(state.originalNavColor);
                window.setNavigationBarDividerColor(state.originalNavDividerColor);
                window.setNavigationBarContrastEnforced(state.originalNavContrast);
            } catch (Throwable ignored) {
                // Best-effort nav bar restore.
            }
        }
    }

    private static Drawable unwrapDrawable(Drawable drawable) {
        for (int depth = 0; depth < 16 && drawable instanceof DrawableWrapper wrapper; depth++) {
            drawable = wrapper.getDrawable();
        }
        return drawable;
    }

    private static State stateFor(Window window) {
        synchronized (STATES) {
            State state = STATES.get(window);
            if (state == null) {
                state = new State();
                STATES.put(window, state);
            }
            return state;
        }
    }

    private static final class AmoledBlackDrawable extends ColorDrawable {
        AmoledBlackDrawable() {
            super(AMOLED_BACKGROUND_COLOR);
        }
    }

    static final class State {
        final Map<View, Drawable> originalSurfaceBackgrounds = new WeakHashMap<>();
        final Map<Drawable, Boolean> tintedKeyDrawables = new IdentityHashMap<>();
        View observedDecor;
        View.OnLayoutChangeListener layoutListener;
        boolean navCaptured;
        int originalNavColor;
        int originalNavDividerColor;
        boolean originalNavContrast;
    }
}
