package dev.jason.gboardpatches.extension.spacebargesture;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GboardSpacebarLanguageNavigatorTest {
    private static final String GBOARD_PKG = "com.google.android.inputmethod.latin";
    private static final String GBOARD_IME_ID =
            "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME";

    @Test
    public void nextAndPreviousIndicesAreNoOpWhenListHasZeroOrOneLanguage() {
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveNextIndex(0, 0));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveNextIndex(0, 1));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(0, 0));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(0, 1));
    }

    @Test
    public void nextAndPreviousIndicesRejectOutOfBoundsAndZeroStep() {
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveNextIndex(-1, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveNextIndex(3, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveNextIndex(10, 4));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(-1, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(3, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(10, 4));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolveTargetIndex(0, 3, 0));
    }

    @Test
    public void twoLanguagesSwitchNextAndPreviousOnUnifiedOrder() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru");

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("ru", backend.currentEntry);

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("en", backend.currentEntry);

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("ru", backend.currentEntry);

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("en", backend.currentEntry);

        Assert.assertEquals(Arrays.asList("sub:ru", "sub:en", "sub:ru", "sub:en"),
                backend.switchedSubtypes);
    }

    @Test
    public void threeLanguagesSwitchSequentiallyInBothDirectionsWithExactInverseOrder() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru", "uk");

        // Forward: en -> ru -> uk -> en
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("ru", backend.currentEntry);
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("uk", backend.currentEntry);
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("en", backend.currentEntry);

        // Backward on the exact same list: en -> uk -> ru -> en
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("uk", backend.currentEntry);
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("ru", backend.currentEntry);
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("en", backend.currentEntry);
    }

    @Test
    public void fourLanguagesWrapCyclicallyAcrossStartAndEnd() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru", "uk", "de");

        // Wrap across start: 0 (en) -> 3 (de)
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("de", backend.currentEntry);

        // Wrap across end: 3 (de) -> 0 (en)
        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("en", backend.currentEntry);

        // Full forward cycle: en -> ru -> uk -> de -> en
        List<String> forwardVisited = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
            forwardVisited.add(backend.currentEntry);
        }
        Assert.assertEquals(Arrays.asList("ru", "uk", "de", "en"), forwardVisited);

        // Full backward cycle: en -> de -> uk -> ru -> en
        List<String> backwardVisited = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
            backwardVisited.add(backend.currentEntry);
        }
        Assert.assertEquals(Arrays.asList("de", "uk", "ru", "en"), backwardVisited);
    }

    @Test
    public void singleLanguageListDoesNotTriggerSwitch() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en");

        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("en", backend.currentEntry);
        Assert.assertTrue(backend.switchedSubtypes.isEmpty());
    }

    @Test
    public void invalidCurrentIndexDoesNotTriggerSwitch() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru", "de");
        backend.forcedIndex = -1;

        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertTrue(backend.switchedSubtypes.isEmpty());

        backend.forcedIndex = 3;
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertTrue(backend.switchedSubtypes.isEmpty());
    }

    @Test
    public void unavailableOrUnenabledSubtypeDoesNotTriggerSwitch() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru", "de");
        // Remove "ru" and "de" from Gboard's enabled subtype list
        backend.enabledSubtypes.remove("sub:ru");
        backend.enabledSubtypes.remove("sub:de");

        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("en", backend.currentEntry);
        Assert.assertTrue(backend.switchedSubtypes.isEmpty());
    }

    @Test
    public void foreignImePackageNeverExecutesSwitch() {
        FakeRotationBackend samsungIme = FakeRotationBackend.forGboard("en", "ru");
        samsungIme.targetImePackage = "com.samsung.android.honeyboard";

        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(samsungIme, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(samsungIme, -1, "PREVIOUS"));
        Assert.assertTrue(samsungIme.switchedSubtypes.isEmpty());

        FakeRotationBackend talkBackIme = FakeRotationBackend.forGboard("en", "ru");
        talkBackIme.targetImePackage = "com.google.android.marvin.talkback";

        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(talkBackIme, +1, "NEXT"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.switchWithBackend(talkBackIme, -1, "PREVIOUS"));
        Assert.assertTrue(talkBackIme.switchedSubtypes.isEmpty());
    }

    @Test
    public void fallsBackToVerifiedInputMethodManagerWhenGboardHelperReturnsFalse() {
        FakeRotationBackend backend = FakeRotationBackend.forGboard("en", "ru", "de");
        backend.helperSucceeds = false;

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, +1, "NEXT"));
        Assert.assertEquals("ru", backend.currentEntry);
        Assert.assertEquals(1, backend.helperAttempts);
        Assert.assertEquals(1, backend.immFallbackCalls);
        Assert.assertEquals(Collections.singletonList(GBOARD_IME_ID), backend.immFallbackImeIds);

        Assert.assertTrue(GboardSpacebarLanguageNavigator.switchWithBackend(backend, -1, "PREVIOUS"));
        Assert.assertEquals("en", backend.currentEntry);
        Assert.assertEquals(2, backend.helperAttempts);
        Assert.assertEquals(2, backend.immFallbackCalls);
    }

    @Test
    public void sameImePackageGuardStrictlyPreventsExternalIme() {
        Assert.assertTrue(GboardSpacebarLanguageNavigator.isSameImePackage(
                GBOARD_PKG, GBOARD_PKG));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                GBOARD_PKG, "com.google.android.marvin.talkback"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                GBOARD_PKG, "com.samsung.android.honeyboard"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                null, GBOARD_PKG));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                "", ""));
    }

    private static final class FakeRotationBackend
            implements GboardSpacebarLanguageNavigator.RotationSwitchBackend<String, String> {
        final String servicePackage = GBOARD_PKG;
        String targetImePackage = GBOARD_PKG;
        String targetImeId = GBOARD_IME_ID;
        final List<String> entries;
        final Map<String, String> entryToSubtype = new HashMap<>();
        final Map<String, String> subtypeToEntry = new HashMap<>();
        final Set<String> enabledSubtypes = new HashSet<>();
        final List<String> switchedSubtypes = new ArrayList<>();
        final List<String> immFallbackImeIds = new ArrayList<>();
        String currentEntry;
        Integer forcedIndex;
        boolean helperSucceeds = true;
        int helperAttempts;
        int immFallbackCalls;

        private FakeRotationBackend(List<String> entries) {
            this.entries = entries;
            this.currentEntry = entries.isEmpty() ? null : entries.get(0);
            for (String entry : entries) {
                String subtype = "sub:" + entry;
                entryToSubtype.put(entry, subtype);
                subtypeToEntry.put(subtype, entry);
                enabledSubtypes.add(subtype);
            }
        }

        static FakeRotationBackend forGboard(String... languages) {
            return new FakeRotationBackend(Arrays.asList(languages));
        }

        @Override
        public String getServicePackageName() {
            return servicePackage;
        }

        @Override
        public String getTargetImePackageName() {
            return targetImePackage;
        }

        @Override
        public String getTargetImeId() {
            return targetImeId;
        }

        @Override
        public String getCurrentEntry() {
            return currentEntry;
        }

        @Override
        public int indexOfEntry(String current) {
            if (forcedIndex != null) {
                return forcedIndex;
            }
            return entries.indexOf(current);
        }

        @Override
        public List<String> getRotationEntries() {
            return entries;
        }

        @Override
        public String extractSubtype(String entry) {
            return entryToSubtype.get(entry);
        }

        @Override
        public String findEnabledGboardSubtype(String targetSubtype) {
            return enabledSubtypes.contains(targetSubtype) ? targetSubtype : null;
        }

        @Override
        public boolean trySwitchViaGboardHelper(String verifiedSubtype) {
            helperAttempts++;
            if (!helperSucceeds) {
                return false;
            }
            switchedSubtypes.add(verifiedSubtype);
            currentEntry = subtypeToEntry.get(verifiedSubtype);
            return true;
        }

        @Override
        public void switchViaInputMethodManager(String gboardImeId, String verifiedSubtype) {
            immFallbackCalls++;
            immFallbackImeIds.add(gboardImeId);
            switchedSubtypes.add(verifiedSubtype);
            currentEntry = subtypeToEntry.get(verifiedSubtype);
        }
    }
}
