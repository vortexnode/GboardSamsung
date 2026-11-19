package dev.jason.gboardpatches.extension.spacebargesture;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resolves the {@code SoftKeyView} under a raw screen coordinate inside {@code KeyboardViewHolder}
 * and verifies that its {@code PRESS} action has real keycode {@link KeyEvent#KEYCODE_SPACE} (62)
 * via Gboard's {@code SoftKeyDef}/{@code ActionDef} metadata (never relying on layout-dependent IDs).
 */
public final class GboardSpacebarKeyHitTester {
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String SOFT_KEY_DEF_CLASS =
            "com.google.android.libraries.inputmethod.metadata.SoftKeyDef";
    private static final String ACTION_DEF_CLASS =
            "com.google.android.libraries.inputmethod.metadata.ActionDef";
    private static final String ACTION_TYPE_CLASS = "qae";
    private static final String ACTION_ENTRY_CLASS = "qbc";

    private static final Map<ClassLoader, WeakReference<MetadataHandles>> HANDLES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GboardSpacebarKeyHitTester() {
    }

    public static boolean isSpaceKeyAt(View holder, float rawX, float rawY) {
        if (holder == null || !holder.isShown()) {
            return false;
        }
        int[] location = new int[2];
        View softKeyView = findSoftKeyViewAt(holder, rawX, rawY, location);
        if (softKeyView == null) {
            return false;
        }
        return isSpaceSoftKeyView(softKeyView);
    }

    static boolean isSpaceSoftKeyView(View softKeyView) {
        if (softKeyView == null || !SOFT_KEY_VIEW_CLASS.equals(softKeyView.getClass().getName())) {
            return false;
        }
        try {
            MetadataHandles handles = resolveHandles(softKeyView.getClass().getClassLoader());
            Object softKeyDef = handles.softKeyMetadataField.get(softKeyView);
            if (softKeyDef == null) {
                return false;
            }
            Object pressAction = handles.exactActionLookupMethod.invoke(
                    softKeyDef, handles.pressActionType);
            if (pressAction == null) {
                return false;
            }
            int pressKeycode = handles.extractFirstKeycode(pressAction);
            return pressKeycode == KeyEvent.KEYCODE_SPACE;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static View findSoftKeyViewAt(View root, float rawX, float rawY, int[] location) {
        if (root == null || root.getVisibility() != View.VISIBLE || !root.isShown()) {
            return null;
        }
        if (!containsRawPoint(root, rawX, rawY, location)) {
            return null;
        }
        if (SOFT_KEY_VIEW_CLASS.equals(root.getClass().getName())) {
            return root;
        }
        if (root instanceof ViewGroup group) {
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                View match = findSoftKeyViewAt(group.getChildAt(index), rawX, rawY, location);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean containsRawPoint(View view, float rawX, float rawY, int[] location) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        return rawX >= left && rawX < left + width && rawY >= top && rawY < top + height;
    }

    private static MetadataHandles resolveHandles(ClassLoader classLoader) throws Throwable {
        if (classLoader == null) {
            throw new IllegalStateException("Missing ClassLoader for SoftKeyView metadata");
        }
        synchronized (HANDLES) {
            WeakReference<MetadataHandles> reference = HANDLES.get(classLoader);
            MetadataHandles cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            MetadataHandles created = new MetadataHandles(classLoader);
            HANDLES.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    private static final class MetadataHandles {
        final Field softKeyMetadataField;
        final Method exactActionLookupMethod;
        final Field actionEntriesField;
        final Field entryKeycodeField;
        final Object pressActionType;

        @SuppressWarnings({"rawtypes", "unchecked"})
        MetadataHandles(ClassLoader classLoader) throws Throwable {
            Class<?> softKeyViewClass = Class.forName(SOFT_KEY_VIEW_CLASS, false, classLoader);
            Class<?> softKeyDefClass = Class.forName(SOFT_KEY_DEF_CLASS, false, classLoader);
            Class<?> actionDefClass = Class.forName(ACTION_DEF_CLASS, false, classLoader);
            Class<?> actionTypeClass = Class.forName(ACTION_TYPE_CLASS, false, classLoader);
            Class<?> actionEntryClass = Class.forName(ACTION_ENTRY_CLASS, false, classLoader);

            softKeyMetadataField = softKeyViewClass.getDeclaredField("e");
            exactActionLookupMethod = softKeyDefClass.getDeclaredMethod("g", actionTypeClass);
            actionEntriesField = actionDefClass.getDeclaredField("f");
            entryKeycodeField = actionEntryClass.getDeclaredField("c");

            softKeyMetadataField.setAccessible(true);
            exactActionLookupMethod.setAccessible(true);
            actionEntriesField.setAccessible(true);
            entryKeycodeField.setAccessible(true);

            pressActionType = Enum.valueOf(
                    (Class<? extends Enum>) actionTypeClass.asSubclass(Enum.class), "PRESS");
        }

        int extractFirstKeycode(Object actionDef) throws IllegalAccessException {
            Object rawEntries = actionEntriesField.get(actionDef);
            Object firstEntry = null;
            if (rawEntries instanceof List<?> list && !list.isEmpty()) {
                firstEntry = list.get(0);
            } else if (rawEntries instanceof Object[] array && array.length > 0) {
                firstEntry = array[0];
            }
            return firstEntry == null ? 0 : entryKeycodeField.getInt(firstEntry);
        }
    }
}
