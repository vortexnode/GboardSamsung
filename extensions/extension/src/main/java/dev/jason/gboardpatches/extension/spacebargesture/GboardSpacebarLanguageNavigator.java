package dev.jason.gboardpatches.extension.spacebargesture;

import android.app.Dialog;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Switches strictly within the active Gboard IME:
 * - NEXT: direct {@link InputMethodManager#switchToNextInputMethod(IBinder, boolean)} with
 *   {@code onlyCurrentIme = true} (bypassing Gboard's {@code Lpga.az} TalkBack fallback).
 * - PREVIOUS: follows Gboard's confirmed internal rotation list ({@code Lpga.F(Context)} ->
 *   {@code Lpga.C()} -> {@code Lpga.Y} ({@code Lrek}) -> {@code Lrek.j(current)} ->
 *   {@code Lrek.b[index - 1]} -> {@code Lpev.f()}) and switches strictly inside Gboard's own
 *   {@link InputMethodInfo} via {@code Lsex.k} or verified {@code setInputMethodAndSubtype}.
 */
public final class GboardSpacebarLanguageNavigator {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-spacebar-lang] ";
    private static final int MAX_WARN_LOGS = 20;

    private static final String MANAGER_CLASS = "pga";
    private static final String ENTRY_CLASS = "pev";
    private static final String ROTATION_LIST_CLASS = "rek";
    private static final String SUBTYPE_SWITCHER_CLASS = "sex";

    private static final Map<ClassLoader, WeakReference<PreviousReflectionHandles>> HANDLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger WARN_LOG_COUNT = new AtomicInteger();

    private GboardSpacebarLanguageNavigator() {
    }

    /**
     * Computes the cyclic previous index in Gboard's ordered language rotation list.
     *
     * @return {@code -1} if switching is impossible ({@code listSize <= 1} or invalid index).
     */
    public static int resolvePreviousIndex(int currentIndex, int listSize) {
        if (listSize <= 1 || currentIndex < 0 || currentIndex >= listSize) {
            return -1;
        }
        return currentIndex == 0 ? listSize - 1 : currentIndex - 1;
    }

    /**
     * Verifies that a candidate {@link InputMethodInfo} strictly belongs to the active service's
     * package so an external IME can never be selected.
     */
    public static boolean isSameImePackage(String servicePackageName, String imePackageName) {
        return servicePackageName != null
                && !servicePackageName.isEmpty()
                && servicePackageName.equals(imePackageName);
    }

    @SuppressWarnings("deprecation")
    public static boolean switchNext(InputMethodService service) {
        if (service == null) {
            return false;
        }
        IBinder token = resolveWindowToken(service);
        InputMethodManager imm = resolveInputMethodManager(service);
        if (token == null || imm == null) {
            logWarn("NEXT aborted: missing IME token or InputMethodManager", null);
            return false;
        }
        try {
            // onlyCurrentIme MUST be true so Android never leaves Gboard for another IME.
            return imm.switchToNextInputMethod(token, true);
        } catch (Throwable throwable) {
            logWarn("NEXT failed via switchToNextInputMethod(token, true)", throwable);
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean switchPrevious(InputMethodService service) {
        if (service == null) {
            return false;
        }
        IBinder token = resolveWindowToken(service);
        InputMethodManager imm = resolveInputMethodManager(service);
        if (token == null || imm == null) {
            logWarn("PREVIOUS aborted: missing IME token or InputMethodManager", null);
            return false;
        }
        InputMethodInfo gboardInfo = resolveCurrentGboardImeInfo(service, imm);
        if (gboardInfo == null) {
            logWarn("PREVIOUS aborted: unable to verify active Gboard InputMethodInfo", null);
            return false;
        }

        try {
            PreviousReflectionHandles handles = resolveHandles(service.getClass().getClassLoader());
            Context appContext = service.getApplicationContext();
            Object pgaInstance = handles.getManagerMethod.invoke(
                    null, appContext != null ? appContext : service);
            if (pgaInstance == null) {
                logWarn("PREVIOUS aborted: Lpga.F(Context) returned null", null);
                return false;
            }

            Object currentEntry = handles.currentEntryMethod.invoke(pgaInstance);
            Object rotationList = handles.rotationListField.get(pgaInstance);
            if (currentEntry == null || rotationList == null) {
                logWarn("PREVIOUS aborted: current Lpev or Lpga.Y (Lrek) is null", null);
                return false;
            }

            Object indexResult = handles.indexOfEntryMethod.invoke(rotationList, currentEntry);
            if (!(indexResult instanceof Integer currentIndex)) {
                logWarn("PREVIOUS aborted: Lrek.j(current) did not return Integer", null);
                return false;
            }

            List<?> entries = extractEntryList(handles.rotationEntriesField.get(rotationList));
            if (entries.size() <= 1) {
                return false;
            }

            int previousIndex = resolvePreviousIndex(currentIndex, entries.size());
            if (previousIndex < 0) {
                logWarn("PREVIOUS aborted: current Lpev index not found (" + currentIndex
                        + " of " + entries.size() + ")", null);
                return false;
            }

            Object previousEntry = entries.get(previousIndex);
            if (previousEntry == null
                    || previousEntry == currentEntry
                    || previousEntry.equals(currentEntry)) {
                return false;
            }

            Object subtypeObject = handles.entrySubtypeMethod.invoke(previousEntry);
            if (!(subtypeObject instanceof InputMethodSubtype targetSubtype)) {
                logWarn("PREVIOUS aborted: previous Lpev.f() did not return InputMethodSubtype",
                        null);
                return false;
            }

            InputMethodSubtype verifiedSubtype =
                    findEnabledSubtypeForGboard(imm, gboardInfo, targetSubtype);
            if (verifiedSubtype == null) {
                logWarn("PREVIOUS aborted: target subtype is not enabled for current Gboard IME",
                        null);
                return false;
            }

            if (trySwitchViaGboardHelper(handles, pgaInstance, gboardInfo, token, verifiedSubtype)) {
                return true;
            }

            // Final package check before invoking setInputMethodAndSubtype strictly on Gboard ID.
            if (!isSameImePackage(service.getPackageName(), gboardInfo.getPackageName())) {
                return false;
            }
            imm.setInputMethodAndSubtype(token, gboardInfo.getId(), verifiedSubtype);
            return true;
        } catch (Throwable throwable) {
            logWarn("PREVIOUS failed; staying on current Gboard language", throwable);
            return false;
        }
    }

    private static boolean trySwitchViaGboardHelper(
            PreviousReflectionHandles handles,
            Object pgaInstance,
            InputMethodInfo gboardInfo,
            IBinder token,
            InputMethodSubtype subtype) {
        if (handles.subtypeSwitchMethod == null) {
            return false;
        }
        try {
            if (Modifier.isStatic(handles.subtypeSwitchMethod.getModifiers())) {
                Object result = handles.subtypeSwitchMethod.invoke(null, gboardInfo, token, subtype);
                return !(result instanceof Boolean ok) || ok;
            }
            if (handles.subtypeSwitcherFieldOnManager != null) {
                Object switcherInstance = handles.subtypeSwitcherFieldOnManager.get(pgaInstance);
                if (switcherInstance != null) {
                    Object result = handles.subtypeSwitchMethod.invoke(
                            switcherInstance, gboardInfo, token, subtype);
                    return !(result instanceof Boolean ok) || ok;
                }
            }
        } catch (Throwable throwable) {
            logWarn("Lsex.k invocation failed; falling back to verified setInputMethodAndSubtype",
                    throwable);
        }
        return false;
    }

    private static InputMethodInfo resolveCurrentGboardImeInfo(
            InputMethodService service, InputMethodManager imm) {
        String servicePackage = service.getPackageName();
        String serviceClassName = service.getClass().getName();
        List<InputMethodInfo> enabledImes = imm.getEnabledInputMethodList();
        if (enabledImes == null || enabledImes.isEmpty()) {
            return null;
        }
        InputMethodInfo packageFallback = null;
        for (InputMethodInfo info : enabledImes) {
            if (info == null || !isSameImePackage(servicePackage, info.getPackageName())) {
                continue;
            }
            if (serviceClassName.equals(info.getServiceName())) {
                return info;
            }
            if (packageFallback == null) {
                packageFallback = info;
            }
        }
        return packageFallback;
    }

    private static InputMethodSubtype findEnabledSubtypeForGboard(
            InputMethodManager imm, InputMethodInfo gboardInfo, InputMethodSubtype target) {
        List<InputMethodSubtype> enabledSubtypes =
                imm.getEnabledInputMethodSubtypeList(gboardInfo, true);
        if (enabledSubtypes == null || enabledSubtypes.isEmpty()) {
            return null;
        }
        for (InputMethodSubtype candidate : enabledSubtypes) {
            if (candidate != null && candidate.equals(target)) {
                return candidate;
            }
        }
        for (InputMethodSubtype candidate : enabledSubtypes) {
            if (candidate != null && candidate.hashCode() == target.hashCode()
                    && equalsNullable(candidate.getMode(), target.getMode())
                    && equalsNullable(candidate.getExtraValue(), target.getExtraValue())) {
                return candidate;
            }
        }
        return null;
    }

    private static List<?> extractEntryList(Object rawEntries) {
        if (rawEntries instanceof List<?> list) {
            return list;
        }
        if (rawEntries instanceof Object[] array) {
            return Arrays.asList(array);
        }
        return Collections.emptyList();
    }

    private static IBinder resolveWindowToken(InputMethodService service) {
        Dialog dialog = service.getWindow();
        if (dialog == null) {
            return null;
        }
        Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        WindowManager.LayoutParams attributes = window.getAttributes();
        return attributes == null ? null : attributes.token;
    }

    private static InputMethodManager resolveInputMethodManager(InputMethodService service) {
        Object manager = service.getSystemService(Context.INPUT_METHOD_SERVICE);
        return manager instanceof InputMethodManager imm ? imm : null;
    }

    private static PreviousReflectionHandles resolveHandles(ClassLoader classLoader)
            throws Throwable {
        if (classLoader == null) {
            throw new IllegalStateException("Missing ClassLoader for Gboard language manager");
        }
        synchronized (HANDLES) {
            WeakReference<PreviousReflectionHandles> reference = HANDLES.get(classLoader);
            PreviousReflectionHandles cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            PreviousReflectionHandles created = new PreviousReflectionHandles(classLoader);
            HANDLES.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    private static boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static void logWarn(String message, Throwable throwable) {
        try {
            if (WARN_LOG_COUNT.getAndIncrement() < MAX_WARN_LOGS) {
                if (throwable != null) {
                    Log.w(TAG, LOG_PREFIX + message, throwable);
                } else {
                    Log.w(TAG, LOG_PREFIX + message);
                }
            }
        } catch (Throwable ignored) {
            // Diagnostics must never crash IME touch handling.
        }
    }

    private static final class PreviousReflectionHandles {
        final Method getManagerMethod;
        final Method currentEntryMethod;
        final Field rotationListField;
        final Method indexOfEntryMethod;
        final Field rotationEntriesField;
        final Method entrySubtypeMethod;
        final Method subtypeSwitchMethod;
        final Field subtypeSwitcherFieldOnManager;

        PreviousReflectionHandles(ClassLoader classLoader) throws Throwable {
            Class<?> managerClass = Class.forName(MANAGER_CLASS, false, classLoader);
            Class<?> entryClass = Class.forName(ENTRY_CLASS, false, classLoader);
            Class<?> rotationListClass = Class.forName(ROTATION_LIST_CLASS, false, classLoader);

            getManagerMethod = managerClass.getDeclaredMethod("F", Context.class);
            if (!Modifier.isStatic(getManagerMethod.getModifiers())) {
                throw new NoSuchMethodException("Lpga.F(Context) must be static");
            }
            currentEntryMethod = managerClass.getDeclaredMethod("C");
            rotationListField = managerClass.getDeclaredField("Y");
            indexOfEntryMethod = rotationListClass.getDeclaredMethod("j", entryClass);
            rotationEntriesField = rotationListClass.getDeclaredField("b");
            entrySubtypeMethod = entryClass.getDeclaredMethod("f");

            getManagerMethod.setAccessible(true);
            currentEntryMethod.setAccessible(true);
            rotationListField.setAccessible(true);
            indexOfEntryMethod.setAccessible(true);
            rotationEntriesField.setAccessible(true);
            entrySubtypeMethod.setAccessible(true);

            Method resolvedSwitchMethod = null;
            Field resolvedSwitcherField = null;
            try {
                Class<?> switcherClass = Class.forName(SUBTYPE_SWITCHER_CLASS, false, classLoader);
                resolvedSwitchMethod = switcherClass.getDeclaredMethod(
                        "k", InputMethodInfo.class, IBinder.class, InputMethodSubtype.class);
                resolvedSwitchMethod.setAccessible(true);
                if (!Modifier.isStatic(resolvedSwitchMethod.getModifiers())) {
                    for (Field candidate : managerClass.getDeclaredFields()) {
                        if (switcherClass.isAssignableFrom(candidate.getType())) {
                            candidate.setAccessible(true);
                            resolvedSwitcherField = candidate;
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Optional: verified imm.setInputMethodAndSubtype fallback handles switching.
            }
            subtypeSwitchMethod = resolvedSwitchMethod;
            subtypeSwitcherFieldOnManager = resolvedSwitcherField;
        }
    }
}
