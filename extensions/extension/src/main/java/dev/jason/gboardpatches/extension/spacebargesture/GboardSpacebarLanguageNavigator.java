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
 * Switches languages strictly within the active Gboard IME using ONE unified ordered list and ONE
 * verified Gboard-only switch mechanism for both directions:
 * <ul>
 *   <li>Reads Gboard's confirmed internal rotation list:
 *       {@code Lpga.F(Context)} -> {@code Lpga.C()} (current {@code Lpev}) ->
 *       {@code Lpga.Y} ({@code Lrek}) -> {@code Lrek.j(current)} -> {@code Lrek.b}.</li>
 *   <li>{@code NEXT}: {@code (index + 1) % size} (matching Gboard's {@code Lrek.k(current, boolean)}).</li>
 *   <li>{@code PREVIOUS}: {@code (index - 1 + size) % size} on the exact same {@code Lrek.b} list.</li>
 *   <li>Extracts {@code Lpev.f()} ({@link InputMethodSubtype}), verifies that both the target
 *       {@link InputMethodInfo} and enabled subtype strictly belong to the active Gboard IME, and
 *       switches via {@code Lsex.k} or verified {@code imm.setInputMethodAndSubtype}.</li>
 *   <li>Never calls {@code Lpga.az}, {@code imm.setInputMethod}, or {@code imm.switchToNextInputMethod},
 *       so an external IME can never be selected and {@code NEXT}/{@code PREVIOUS} never diverge in order.</li>
 * </ul>
 */
public final class GboardSpacebarLanguageNavigator {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-spacebar-lang] ";
    private static final int MAX_WARN_LOGS = 20;

    private static final String MANAGER_CLASS = "pga";
    private static final String ENTRY_CLASS = "pev";
    private static final String ROTATION_LIST_CLASS = "rek";
    private static final String SUBTYPE_SWITCHER_CLASS = "sex";

    private static final Map<ClassLoader, WeakReference<LanguageRotationHandles>> HANDLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger WARN_LOG_COUNT = new AtomicInteger();

    private GboardSpacebarLanguageNavigator() {
    }

    /**
     * Computes the cyclic next index in Gboard's ordered language rotation list.
     *
     * @return {@code -1} if switching is impossible ({@code listSize <= 1} or invalid index).
     */
    public static int resolveNextIndex(int currentIndex, int listSize) {
        return resolveTargetIndex(currentIndex, listSize, +1);
    }

    /**
     * Computes the cyclic previous index in Gboard's ordered language rotation list.
     *
     * @return {@code -1} if switching is impossible ({@code listSize <= 1} or invalid index).
     */
    public static int resolvePreviousIndex(int currentIndex, int listSize) {
        return resolveTargetIndex(currentIndex, listSize, -1);
    }

    /**
     * Computes the cyclic target index for a non-zero directional step ({@code +1} for NEXT,
     * {@code -1} for PREVIOUS) in Gboard's ordered language rotation list.
     *
     * @return {@code -1} if switching is impossible ({@code listSize <= 1}, {@code step == 0},
     *     invalid index, or target wraps back to {@code currentIndex}).
     */
    public static int resolveTargetIndex(int currentIndex, int listSize, int step) {
        if (listSize <= 1 || currentIndex < 0 || currentIndex >= listSize || step == 0) {
            return -1;
        }
        int targetIndex = ((currentIndex + step) % listSize + listSize) % listSize;
        return targetIndex == currentIndex ? -1 : targetIndex;
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

    public static boolean switchNext(InputMethodService service) {
        return switchByStep(service, +1, "NEXT");
    }

    public static boolean switchPrevious(InputMethodService service) {
        return switchByStep(service, -1, "PREVIOUS");
    }

    @SuppressWarnings("deprecation")
    private static boolean switchByStep(
            InputMethodService service, int step, String directionLabel) {
        if (service == null) {
            return false;
        }
        IBinder token = resolveWindowToken(service);
        InputMethodManager imm = resolveInputMethodManager(service);
        if (token == null || imm == null) {
            logWarn(directionLabel + " aborted: missing IME token or InputMethodManager", null);
            return false;
        }
        InputMethodInfo gboardInfo = resolveCurrentGboardImeInfo(service, imm);
        if (gboardInfo == null) {
            logWarn(directionLabel + " aborted: unable to verify active Gboard InputMethodInfo",
                    null);
            return false;
        }

        try {
            LanguageRotationHandles handles = resolveHandles(service.getClass().getClassLoader());
            Context appContext = service.getApplicationContext();
            Object pgaInstance = handles.getManagerMethod.invoke(
                    null, appContext != null ? appContext : service);
            if (pgaInstance == null) {
                logWarn(directionLabel + " aborted: Lpga.F(Context) returned null", null);
                return false;
            }

            Object rotationList = handles.rotationListField.get(pgaInstance);
            if (rotationList == null) {
                logWarn(directionLabel + " aborted: Lpga.Y (Lrek) is null", null);
                return false;
            }

            RotationSwitchBackend<Object, InputMethodSubtype> backend =
                    new GboardReflectionBackend(
                            service,
                            imm,
                            token,
                            gboardInfo,
                            handles,
                            pgaInstance,
                            rotationList);
            return switchWithBackend(backend, step, directionLabel);
        } catch (Throwable throwable) {
            logWarn(directionLabel + " failed; staying on current Gboard language", throwable);
            return false;
        }
    }

    static <E, S> boolean switchWithBackend(
            RotationSwitchBackend<E, S> backend, int step, String directionLabel) {
        if (backend == null || step == 0) {
            return false;
        }
        String servicePackage = backend.getServicePackageName();
        String targetImePackage = backend.getTargetImePackageName();
        String targetImeId = backend.getTargetImeId();
        if (!isSameImePackage(servicePackage, targetImePackage)
                || targetImeId == null
                || targetImeId.isEmpty()) {
            logWarn(directionLabel + " aborted: target IME does not belong to active Gboard", null);
            return false;
        }

        E currentEntry = backend.getCurrentEntry();
        List<E> entries = backend.getRotationEntries();
        if (currentEntry == null || entries == null || entries.size() <= 1) {
            return false;
        }

        int currentIndex = backend.indexOfEntry(currentEntry);
        int targetIndex = resolveTargetIndex(currentIndex, entries.size(), step);
        if (targetIndex < 0) {
            logWarn(directionLabel + " aborted: current Lpev index not found (" + currentIndex
                    + " of " + entries.size() + ")", null);
            return false;
        }

        E targetEntry = entries.get(targetIndex);
        if (targetEntry == null
                || targetEntry == currentEntry
                || targetEntry.equals(currentEntry)) {
            return false;
        }

        S targetSubtype = backend.extractSubtype(targetEntry);
        if (targetSubtype == null) {
            logWarn(directionLabel + " aborted: target Lpev.f() did not return InputMethodSubtype",
                    null);
            return false;
        }

        S verifiedSubtype = backend.findEnabledGboardSubtype(targetSubtype);
        if (verifiedSubtype == null) {
            logWarn(directionLabel
                    + " aborted: target subtype is not enabled for current Gboard IME", null);
            return false;
        }

        if (backend.trySwitchViaGboardHelper(verifiedSubtype)) {
            return true;
        }

        // Final package check before invoking setInputMethodAndSubtype strictly on Gboard ID.
        if (!isSameImePackage(backend.getServicePackageName(), backend.getTargetImePackageName())) {
            return false;
        }
        backend.switchViaInputMethodManager(targetImeId, verifiedSubtype);
        return true;
    }

    private static boolean trySwitchViaGboardHelper(
            LanguageRotationHandles handles,
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

    @SuppressWarnings("unchecked")
    private static List<Object> extractEntryList(Object rawEntries) {
        if (rawEntries instanceof List<?> list) {
            return (List<Object>) list;
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

    private static LanguageRotationHandles resolveHandles(ClassLoader classLoader)
            throws Throwable {
        if (classLoader == null) {
            throw new IllegalStateException("Missing ClassLoader for Gboard language manager");
        }
        synchronized (HANDLES) {
            WeakReference<LanguageRotationHandles> reference = HANDLES.get(classLoader);
            LanguageRotationHandles cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            LanguageRotationHandles created = new LanguageRotationHandles(classLoader);
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

    interface RotationSwitchBackend<E, S> {
        String getServicePackageName();

        String getTargetImePackageName();

        String getTargetImeId();

        E getCurrentEntry();

        int indexOfEntry(E currentEntry);

        List<E> getRotationEntries();

        S extractSubtype(E entry);

        S findEnabledGboardSubtype(S targetSubtype);

        boolean trySwitchViaGboardHelper(S verifiedSubtype);

        void switchViaInputMethodManager(String gboardImeId, S verifiedSubtype);
    }

    private static final class GboardReflectionBackend
            implements RotationSwitchBackend<Object, InputMethodSubtype> {
        private final InputMethodService service;
        private final InputMethodManager imm;
        private final IBinder token;
        private final InputMethodInfo gboardInfo;
        private final LanguageRotationHandles handles;
        private final Object pgaInstance;
        private final Object rotationList;

        GboardReflectionBackend(
                InputMethodService service,
                InputMethodManager imm,
                IBinder token,
                InputMethodInfo gboardInfo,
                LanguageRotationHandles handles,
                Object pgaInstance,
                Object rotationList) {
            this.service = service;
            this.imm = imm;
            this.token = token;
            this.gboardInfo = gboardInfo;
            this.handles = handles;
            this.pgaInstance = pgaInstance;
            this.rotationList = rotationList;
        }

        @Override
        public String getServicePackageName() {
            return service.getPackageName();
        }

        @Override
        public String getTargetImePackageName() {
            return gboardInfo.getPackageName();
        }

        @Override
        public String getTargetImeId() {
            return gboardInfo.getId();
        }

        @Override
        public Object getCurrentEntry() {
            try {
                return handles.currentEntryMethod.invoke(pgaInstance);
            } catch (Throwable throwable) {
                throw new IllegalStateException("Lpga.C() invocation failed", throwable);
            }
        }

        @Override
        public int indexOfEntry(Object currentEntry) {
            try {
                Object indexResult = handles.indexOfEntryMethod.invoke(rotationList, currentEntry);
                return indexResult instanceof Integer index ? index : -1;
            } catch (Throwable throwable) {
                throw new IllegalStateException("Lrek.j(current) invocation failed", throwable);
            }
        }

        @Override
        public List<Object> getRotationEntries() {
            try {
                return extractEntryList(handles.rotationEntriesField.get(rotationList));
            } catch (Throwable throwable) {
                throw new IllegalStateException("Lrek.b read failed", throwable);
            }
        }

        @Override
        public InputMethodSubtype extractSubtype(Object entry) {
            try {
                Object subtypeObject = handles.entrySubtypeMethod.invoke(entry);
                return subtypeObject instanceof InputMethodSubtype subtype ? subtype : null;
            } catch (Throwable throwable) {
                throw new IllegalStateException("Lpev.f() invocation failed", throwable);
            }
        }

        @Override
        public InputMethodSubtype findEnabledGboardSubtype(InputMethodSubtype targetSubtype) {
            return findEnabledSubtypeForGboard(imm, gboardInfo, targetSubtype);
        }

        @Override
        public boolean trySwitchViaGboardHelper(InputMethodSubtype verifiedSubtype) {
            return GboardSpacebarLanguageNavigator.trySwitchViaGboardHelper(
                    handles, pgaInstance, gboardInfo, token, verifiedSubtype);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void switchViaInputMethodManager(
                String gboardImeId, InputMethodSubtype verifiedSubtype) {
            imm.setInputMethodAndSubtype(token, gboardImeId, verifiedSubtype);
        }
    }

    private static final class LanguageRotationHandles {
        final Method getManagerMethod;
        final Method currentEntryMethod;
        final Field rotationListField;
        final Method indexOfEntryMethod;
        final Field rotationEntriesField;
        final Method entrySubtypeMethod;
        final Method subtypeSwitchMethod;
        final Field subtypeSwitcherFieldOnManager;

        LanguageRotationHandles(ClassLoader classLoader) throws Throwable {
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
