package dev.jason.gboardpatches.extension.spacebargesture;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSpacebarLanguageNavigatorTest {
    @Test
    public void previousIndexIsNoOpWhenListHasZeroOrOneLanguage() {
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(0, 0));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(0, 1));
    }

    @Test
    public void previousIndexWrapsAroundForTwoThreeAndFourLanguages() {
        // 2 languages: 0 -> 1 -> 0
        Assert.assertEquals(1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(0, 2));
        Assert.assertEquals(0, GboardSpacebarLanguageNavigator.resolvePreviousIndex(1, 2));

        // 3 languages: full backward cycle 0 -> 2 -> 1 -> 0
        int idx3 = 0;
        idx3 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx3, 3);
        Assert.assertEquals(2, idx3);
        idx3 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx3, 3);
        Assert.assertEquals(1, idx3);
        idx3 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx3, 3);
        Assert.assertEquals(0, idx3);

        // 4 languages: full backward cycle 0 -> 3 -> 2 -> 1 -> 0
        int idx4 = 0;
        idx4 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx4, 4);
        Assert.assertEquals(3, idx4);
        idx4 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx4, 4);
        Assert.assertEquals(2, idx4);
        idx4 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx4, 4);
        Assert.assertEquals(1, idx4);
        idx4 = GboardSpacebarLanguageNavigator.resolvePreviousIndex(idx4, 4);
        Assert.assertEquals(0, idx4);
    }

    @Test
    public void previousIndexRejectsOutOfBoundsIndices() {
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(-1, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(3, 3));
        Assert.assertEquals(-1, GboardSpacebarLanguageNavigator.resolvePreviousIndex(10, 4));
    }

    @Test
    public void sameImePackageGuardStrictlyPreventsExternalIme() {
        Assert.assertTrue(GboardSpacebarLanguageNavigator.isSameImePackage(
                "com.google.android.inputmethod.latin",
                "com.google.android.inputmethod.latin"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                "com.google.android.inputmethod.latin",
                "com.google.android.marvin.talkback"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                null,
                "com.google.android.inputmethod.latin"));
        Assert.assertFalse(GboardSpacebarLanguageNavigator.isSameImePackage(
                "",
                ""));
    }
}
