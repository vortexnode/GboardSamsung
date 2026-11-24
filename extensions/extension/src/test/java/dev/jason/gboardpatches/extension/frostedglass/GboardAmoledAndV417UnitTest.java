package dev.jason.gboardpatches.extension.frostedglass;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntime;
import dev.jason.gboardpatches.extension.advancedvoice.GboardAdvancedVoiceRuntimeSettings;
import dev.jason.gboardpatches.extension.longpressquickactions.GboardLongPressQuickActionsRuntime;

public final class GboardAmoledAndV417UnitTest {
    @Test
    public void amoledBlackOnlyActivatesInNightModeWhenFrostedGlassIsOff() {
        Assert.assertTrue(GboardAmoledBlackRuntime.shouldApplyForState(true, false, true));
        Assert.assertFalse(GboardAmoledBlackRuntime.shouldApplyForState(false, false, true));
        Assert.assertFalse(GboardAmoledBlackRuntime.shouldApplyForState(true, true, true));
        Assert.assertFalse(GboardAmoledBlackRuntime.shouldApplyForState(true, false, false));
    }

    @Test
    public void amoledBlackTagClassificationPreservesActionKeysAndCandidates() {
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledSurfaceTag(".keyboard-base-area"));
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledSurfaceTag(".keyboard-base-area.keyboard-outline"));
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledSurfaceTag(".keyboard-header-area"));
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledSurfaceTag(".keyboard-body-area"));
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledKeyTag(".keytop"));
        Assert.assertTrue(GboardAmoledBlackRuntime.isAmoledKeyTag(".space_bar"));
        Assert.assertFalse(GboardAmoledBlackRuntime.isAmoledKeyTag(".keytop.for-action-key"));
        Assert.assertFalse(GboardAmoledBlackRuntime.isAmoledKeyTag(".background-icon.for-action-key"));
        Assert.assertFalse(GboardAmoledBlackRuntime.isAmoledKeyTag(".keytop.for-candidate-key"));
    }

    @Test
    public void disableVoiceBeepRuntimeHonorsOverride() {
        try {
            GboardAdvancedVoiceRuntimeSettings.setDisableVoiceBeepOverrideForTest(false);
            Assert.assertFalse(GboardAdvancedVoiceRuntime.shouldSuppressVoiceBeep());
            GboardAdvancedVoiceRuntimeSettings.setDisableVoiceBeepOverrideForTest(true);
            Assert.assertTrue(GboardAdvancedVoiceRuntime.shouldSuppressVoiceBeep());
        } finally {
            GboardAdvancedVoiceRuntimeSettings.clearEnabledOverrideForTest();
        }
    }

    @Test
    public void disableEnterOneHandedOnlyBlocksLongPressSwitchToOneHandedMode() {
        Assert.assertTrue(GboardLongPressQuickActionsRuntime.shouldSuppressEnterOneHandedMode(
                "LONG_PRESS", -10057, true));
        Assert.assertFalse(GboardLongPressQuickActionsRuntime.shouldSuppressEnterOneHandedMode(
                "LONG_PRESS", -10057, false));
        Assert.assertFalse(GboardLongPressQuickActionsRuntime.shouldSuppressEnterOneHandedMode(
                "PRESS", -10057, true));
        Assert.assertFalse(GboardLongPressQuickActionsRuntime.shouldSuppressEnterOneHandedMode(
                "LONG_PRESS", 66, true));
    }
}
