package dev.jason.gboardpatches.extension.toolbarroundchips;

import android.content.Context;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardToolbarRoundChipsSettingsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        GboardToolbarRoundChipsSettings.invalidateCache();
        GboardToolbarRoundChipsSettings.writeEnabled(
                context, GboardToolbarRoundChipsSettings.DEFAULT_TOOLBAR_ROUND_CHIPS_ENABLED);
    }

    @After
    public void tearDown() {
        GboardToolbarRoundChipsSettings.invalidateCache();
    }

    @Test
    public void defaultIsFalseAndTogglePersistsCleanly() {
        Assert.assertFalse(GboardToolbarRoundChipsSettings.DEFAULT_TOOLBAR_ROUND_CHIPS_ENABLED);
        Assert.assertFalse(GboardToolbarRoundChipsSettings.readEnabled(context));
        Assert.assertFalse(GboardToolbarRoundChipsSettings.isEnabled(context));

        Assert.assertTrue(GboardToolbarRoundChipsSettings.writeEnabled(context, true));
        Assert.assertTrue(GboardToolbarRoundChipsSettings.readEnabled(context));
        Assert.assertTrue(GboardToolbarRoundChipsSettings.isEnabled(context));

        Assert.assertTrue(GboardToolbarRoundChipsSettings.writeEnabled(context, false));
        Assert.assertFalse(GboardToolbarRoundChipsSettings.readEnabled(context));
        Assert.assertFalse(GboardToolbarRoundChipsSettings.isEnabled(context));
    }
}
