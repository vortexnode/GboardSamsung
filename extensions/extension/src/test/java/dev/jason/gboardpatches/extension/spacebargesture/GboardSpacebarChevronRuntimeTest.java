package dev.jason.gboardpatches.extension.spacebargesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardSpacebarChevronRuntimeTest {

    @Before
    public void setUp() {
        GboardSpacebarChevronRuntime.resetForTesting();
        GboardSpacebarGlobeKeyRuntime.resetForTesting();
        GboardSpacebarGestureSettings.invalidateCache();
        clearSettings(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        GboardSpacebarChevronRuntime.resetForTesting();
        GboardSpacebarGlobeKeyRuntime.resetForTesting();
        GboardSpacebarGestureSettings.invalidateCache();
        clearSettings(RuntimeEnvironment.getApplication());
    }

    @Test
    public void disabledByDefaultAndLeavesSpacebarUntouched() {
        Context context = RuntimeEnvironment.getApplication();
        assertFalse(GboardSpacebarGestureSettings.DEFAULT_SPACEBAR_LANGUAGE_CHEVRONS_ENABLED);
        assertFalse(GboardSpacebarGestureSettings.readSpacebarLanguageChevronsEnabled(context));
        assertFalse(GboardSpacebarGestureSettings.isSpacebarLanguageChevronsEnabled(context));

        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", Color.WHITE)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);

            assertFalse(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));
            assertEquals(0, GboardSpacebarChevronRuntime.activeAttachmentCountForTesting());
            assertEquals("Русский", fixture.label.getText().toString());
        }
    }

    @Test
    public void enablingRendersSymmetricChevronsAndDisablingCleansUpImmediately() {
        Context context = RuntimeEnvironment.getApplication();
        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", 0xFFE8EAED)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);

            // Bind while OFF -> tracked, zero active overlay.
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            assertFalse(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));

            // Turn ON -> persists and attaches immediately.
            assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(
                    context, true));
            assertTrue(GboardSpacebarGestureSettings.readSpacebarLanguageChevronsEnabled(context));
            assertTrue(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));

            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);

            fixture.draw(drawable);
            GboardSpacebarChevronRuntime.ChevronLayout layout =
                    drawable.getLastDrawnLayoutForTesting();
            assertTrue(layout.visible);
            // Symmetric placement inside keycap (600px wide, .space_bar inset 12px on left/right).
            float leftInset = layout.leftChevronLeft - fixture.spaceBar.getLeft();
            float rightInset = fixture.spaceBar.getRight() - layout.rightChevronRight;
            assertEquals(leftInset, rightInset, 0.5f);
            assertTrue(layout.leftChevronRight < layout.rightChevronLeft);
            // Original label text and hierarchy remain untouched.
            assertEquals("Русский", fixture.label.getText().toString());
            assertEquals(2, fixture.softKey.getChildCount());
            assertEquals(fixture.backgroundDrawable, fixture.spaceBar.getBackground());

            // Turn OFF -> immediately removes overlay and clears attachments.
            assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(
                    context, false));
            assertFalse(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));
            assertEquals(0, GboardSpacebarChevronRuntime.activeAttachmentCountForTesting());
        }
    }

    @Test
    public void chevronsAndSpacebarSwipeSettingsAreCompletelyIndependent() {
        Context context = RuntimeEnvironment.getApplication();

        // Disable spacebar swipe first.
        assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageSwipeEnabled(context, false));
        assertFalse(GboardSpacebarGestureSettings.readSpacebarLanguageSwipeEnabled(context));

        // Enabling chevrons must NOT re-enable spacebar swipe.
        assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(
                context, true));
        assertFalse(GboardSpacebarGestureSettings.readSpacebarLanguageSwipeEnabled(context));
        assertTrue(GboardSpacebarGestureSettings.readSpacebarLanguageChevronsEnabled(context));

        // Chevrons still render when spacebar swipe is OFF.
        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "English (US)", Color.BLACK)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);
            fixture.draw(drawable);
            assertTrue(drawable.getLastDrawnLayoutForTesting().visible);
        }

        // Toggling spacebar swipe ON then OFF must NOT turn chevrons OFF.
        assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageSwipeEnabled(context, true));
        assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageSwipeEnabled(context, false));
        assertTrue(GboardSpacebarGestureSettings.readSpacebarLanguageChevronsEnabled(context));

        // Turning chevrons OFF must NOT change spacebar swipe or hide-globe.
        assertTrue(GboardSpacebarGestureSettings.writeHideLanguageSwitchKeyEnabled(context, true));
        assertTrue(GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(
                context, false));
        assertFalse(GboardSpacebarGestureSettings.readSpacebarLanguageSwipeEnabled(context));
        assertTrue(GboardSpacebarGestureSettings.readHideLanguageSwitchKeyEnabled(context));
    }

    @Test
    public void languageSettingsScreenPlacesChevronsAlongsideSwipeAndStaysUsableWhenSwipeOff() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageSwipeEnabled(context, false);
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, false);

        GboardLanguageSettingsFeature feature = new GboardLanguageSettingsFeature(context);
        GboardPatchesSettingsContract.Screen screen =
                feature.buildScreen(new GboardPatchesSettingsTestHost(context));

        assertEquals(1, screen.getSections().size());
        List<GboardPatchesSettingsContract.Row> rows = screen.getSections().get(0).getItems();
        assertEquals(3, rows.size());

        GboardPatchesSettingsContract.ToggleRow swipeRow =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(0);
        GboardPatchesSettingsContract.ToggleRow chevronsRow =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(1);
        GboardPatchesSettingsContract.ToggleRow hideGlobeRow =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(2);

        assertFalse(swipeRow.isChecked());
        assertTrue(chevronsRow.isEnabled());
        assertFalse(chevronsRow.isChecked());
        assertFalse(hideGlobeRow.isChecked());

        // Toggle chevrons ON via settings row action while swipe stays OFF.
        chevronsRow.getToggleAction().accept(true);
        assertTrue(GboardSpacebarGestureSettings.readSpacebarLanguageChevronsEnabled(context));
        assertFalse(GboardSpacebarGestureSettings.readSpacebarLanguageSwipeEnabled(context));
    }

    @Test
    public void adaptsColorToLightDarkAndCustomThemes() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, true);

        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", 0xFF1F1F1F)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);

            // Light theme (dark text).
            fixture.draw(drawable);
            int lightThemeColor = drawable.getLastDrawnColorForTesting();
            assertEquals(0x1F, Color.red(lightThemeColor));
            assertEquals(0x1F, Color.green(lightThemeColor));
            assertEquals(0x1F, Color.blue(lightThemeColor));
            assertTrue(Color.alpha(lightThemeColor) >= 64);

            // Dark theme (light text).
            fixture.label.setTextColor(0xFFE8EAED);
            fixture.draw(drawable);
            int darkThemeColor = drawable.getLastDrawnColorForTesting();
            assertEquals(0xE8, Color.red(darkThemeColor));
            assertEquals(0xEA, Color.green(darkThemeColor));
            assertEquals(0xED, Color.blue(darkThemeColor));

            // Custom / Dynamic Color theme.
            fixture.label.setTextColor(0xFF4A90E2);
            fixture.draw(drawable);
            int customThemeColor = drawable.getLastDrawnColorForTesting();
            assertEquals(0x4A, Color.red(customThemeColor));
            assertEquals(0x90, Color.green(customThemeColor));
            assertEquals(0xE2, Color.blue(customThemeColor));
        }
    }

    @Test
    public void updatesCleanlyWhenLanguageChangesWithoutMutatingText() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, true);

        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", Color.WHITE)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);

            fixture.draw(drawable);
            assertTrue(drawable.getLastDrawnLayoutForTesting().visible);
            assertEquals("Русский", fixture.label.getText().toString());

            // Switch language to English (US).
            fixture.label.setText("English (US)");
            fixture.relayout();
            fixture.draw(drawable);
            assertTrue(drawable.getLastDrawnLayoutForTesting().visible);
            assertEquals("English (US)", fixture.label.getText().toString());

            // Clear label (e.g., symbol spacebar without language name) -> hides chevrons cleanly.
            fixture.label.setText("");
            fixture.relayout();
            fixture.draw(drawable);
            assertFalse(drawable.getLastDrawnLayoutForTesting().visible);
        }
    }

    @Test
    public void geometrySolverScalesDownInTightSpaceAndHidesWhenInsufficient() {
        float density = 3.0f; // xxhdpi / Galaxy S22 class density.

        // Standard width spacebar (200dp x 44dp) with "Русский" (~56dp wide).
        RectF standardKeycap = new RectF(0f, 0f, 200f * density, 44f * density);
        RectF russianText = new RectF(72f * density, 12f * density, 128f * density, 32f * density);
        GboardSpacebarChevronRuntime.ChevronLayout normal =
                GboardSpacebarChevronRuntime.computeLayout(standardKeycap, russianText, density);
        assertTrue(normal.visible);
        assertTrue(normal.leftChevronRight <= russianText.left - 6f * density + 0.1f);
        assertTrue(normal.rightChevronLeft >= russianText.right + 6f * density - 0.1f);
        assertEquals(
                normal.leftChevronLeft - standardKeycap.left,
                standardKeycap.right - normal.rightChevronRight,
                0.5f);

        // Tight corridor (130dp wide keycap, 96dp long language name -> 17dp corridor per side):
        // scales down compactly without overlapping text.
        RectF tightKeycap = new RectF(0f, 0f, 130f * density, 40f * density);
        RectF longText = new RectF(17f * density, 10f * density, 113f * density, 30f * density);
        GboardSpacebarChevronRuntime.ChevronLayout compact =
                GboardSpacebarChevronRuntime.computeLayout(tightKeycap, longText, density);
        assertTrue(compact.visible);
        assertTrue((compact.bottomY - compact.topY) < (normal.bottomY - normal.topY));
        assertTrue(compact.leftChevronRight < longText.left);
        assertTrue(compact.rightChevronLeft > longText.right);

        // Very narrow split/floating spacebar (88dp wide, 74dp label -> 7dp corridor per side):
        // cleanly hidden with zero overlap or clipping.
        RectF narrowSplitKeycap = new RectF(0f, 0f, 88f * density, 40f * density);
        RectF narrowText = new RectF(7f * density, 10f * density, 81f * density, 30f * density);
        GboardSpacebarChevronRuntime.ChevronLayout hidden =
                GboardSpacebarChevronRuntime.computeLayout(narrowSplitKeycap, narrowText, density);
        assertFalse(hidden.visible);

        // Extremely narrow keycap below minimum width threshold (< 72dp).
        RectF tinyKeycap = new RectF(0f, 0f, 60f * density, 40f * density);
        RectF tinyText = new RectF(20f * density, 10f * density, 40f * density, 30f * density);
        assertFalse(GboardSpacebarChevronRuntime.computeLayout(tinyKeycap, tinyText, density)
                .visible);
    }

    @Test
    public void recyclingBarriersRemoveChevronsWhenViewReboundToNonSpaceKey() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, true);

        AtomicBoolean isSpace = new AtomicBoolean(true);
        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", Color.WHITE)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(
                    v -> v == fixture.softKey && isSpace.get());

            // Barrier #1: rebind to non-space key immediately detaches overlay.
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            assertTrue(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));

            isSpace.set(false);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            assertFalse(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));

            // Barrier #2: if key changes without afterSoftKeyBind, draw() draws nothing.
            isSpace.set(true);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);

            isSpace.set(false);
            fixture.draw(drawable);
            assertFalse(drawable.getLastDrawnLayoutForTesting().visible);
        }
    }

    @Test
    public void detachAndReattachLifecycleHandlesKeyboardHideAndReopen() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, true);

        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", Color.WHITE)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);
            GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable =
                    GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey);
            assertNotNull(drawable);

            // Simulate keyboard hide (detach from window).
            fixture.root.removeView(fixture.softKey);
            assertFalse(fixture.softKey.isAttachedToWindow());
            fixture.draw(drawable);
            assertFalse(drawable.getLastDrawnLayoutForTesting().visible);

            // Simulate keyboard reopen (re-attach same cached SoftKeyView).
            fixture.root.addView(fixture.softKey);
            fixture.relayout();
            assertTrue(fixture.softKey.isAttachedToWindow());
            fixture.draw(drawable);
            assertTrue(drawable.getLastDrawnLayoutForTesting().visible);

            // If disabled while detached, re-attach detaches cleanly and draws nothing.
            fixture.root.removeView(fixture.softKey);
            GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, false);
            fixture.root.addView(fixture.softKey);
            assertFalse(GboardSpacebarChevronRuntime.hasActiveAttachmentForTesting(fixture.softKey));
            assertNull(GboardSpacebarChevronRuntime.getActiveDrawableForTesting(fixture.softKey));
        }
    }

    @Test
    public void doesNotAttachTouchOrClickListenersToSpacebar() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSpacebarGestureSettings.writeSpacebarLanguageChevronsEnabled(context, true);

        try (SpacebarFixture fixture = new SpacebarFixture(600, 120, "Русский", Color.WHITE)) {
            GboardSpacebarChevronRuntime.setSpaceKeyPredicateForTesting(v -> v == fixture.softKey);
            GboardSpacebarChevronRuntime.afterSoftKeyBind(fixture.softKey);

            assertFalse(fixture.softKey.hasOnClickListeners());
            assertFalse(fixture.spaceBar.hasOnClickListeners());
            assertFalse(fixture.label.hasOnClickListeners());
            assertEquals("Русский", fixture.label.getText().toString());
        }
    }

    private static void clearSettings(Context context) {
        SharedPreferences prefs = GboardPatchesSettings.preferences(context);
        prefs.edit().clear().commit();
    }

    private static final class SpacebarFixture implements AutoCloseable {
        final ActivityController<Activity> controller;
        final FrameLayout root;
        final FrameLayout softKey;
        final View spaceBar;
        final ColorDrawable backgroundDrawable;
        final TextView label;
        final int width;
        final int height;

        SpacebarFixture(int width, int height, String languageLabel, int textColor) {
            this.width = width;
            this.height = height;
            this.controller = Robolectric.buildActivity(Activity.class).create().start().resume();
            Activity activity = controller.get();
            this.root = new FrameLayout(activity);
            activity.setContentView(root);

            this.softKey = new FrameLayout(activity);
            this.spaceBar = new View(activity);
            this.spaceBar.setTag(".space_bar");
            this.backgroundDrawable = new ColorDrawable(Color.DKGRAY);
            this.spaceBar.setBackground(backgroundDrawable);

            this.label = new TextView(activity);
            this.label.setText(languageLabel);
            this.label.setTextColor(textColor);
            this.label.setTextSize(14f);
            this.label.setGravity(Gravity.CENTER);

            this.softKey.addView(spaceBar);
            this.softKey.addView(label);
            this.root.addView(softKey);
            relayout();
        }

        void relayout() {
            int insetX = 12;
            int insetY = 8;
            softKey.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            softKey.layout(0, 0, width, height);
            spaceBar.layout(insetX, insetY, width - insetX, height - insetY);
            label.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            width - insetX * 2, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                            height - insetY * 2, View.MeasureSpec.EXACTLY));
            label.layout(insetX, insetY, width - insetX, height - insetY);
        }

        void draw(GboardSpacebarChevronRuntime.SpacebarChevronDrawable drawable) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);
        }

        @Override
        public void close() {
            controller.pause().stop().destroy();
        }
    }
}
