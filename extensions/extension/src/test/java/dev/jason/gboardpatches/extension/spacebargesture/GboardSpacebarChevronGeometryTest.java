package dev.jason.gboardpatches.extension.spacebargesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure-JVM unit tests for {@link GboardSpacebarChevronRuntime}'s symmetric geometry solver and
 * theme color adaptation logic (runs without Android/Robolectric runtime dependencies).
 */
public final class GboardSpacebarChevronGeometryTest {

    @Test
    public void standardSpacebarProducesSymmetricBalancedChevrons() {
        float density = 3.0f; // Galaxy S22 xxhdpi class
        // 200dp x 44dp keycap with "Русский" (~56dp centered)
        GboardSpacebarChevronRuntime.ChevronLayout layout =
                GboardSpacebarChevronRuntime.computeLayout(
                        0f, 0f, 200f * density, 44f * density,
                        72f * density, 12f * density, 128f * density, 32f * density,
                        density);

        assertTrue(layout.visible);
        float leftInset = layout.leftChevronLeft;
        float rightInset = 200f * density - layout.rightChevronRight;
        assertEquals(leftInset, rightInset, 0.01f);
        assertEquals(16f * density, leftInset, 0.5f);
        assertTrue(layout.leftChevronRight <= 72f * density - 6f * density + 0.1f);
        assertTrue(layout.rightChevronLeft >= 128f * density + 6f * density - 0.1f);
        assertEquals(22f * density, layout.centerY, 0.5f);
        assertTrue(layout.strokeWidth >= 1.25f * density && layout.strokeWidth <= 2.0f * density);
    }

    @Test
    public void longLanguageNameAndOneHandedModeScaleDownWithoutOverlappingText() {
        float density = 3.0f;
        // 132dp wide keycap (e.g. One-handed / Floating) with 98dp label -> 17dp corridor/side
        GboardSpacebarChevronRuntime.ChevronLayout normal =
                GboardSpacebarChevronRuntime.computeLayout(
                        0f, 0f, 200f * density, 44f * density,
                        72f * density, 12f * density, 128f * density, 32f * density,
                        density);
        GboardSpacebarChevronRuntime.ChevronLayout compact =
                GboardSpacebarChevronRuntime.computeLayout(
                        0f, 0f, 132f * density, 40f * density,
                        17f * density, 10f * density, 115f * density, 30f * density,
                        density);

        assertTrue(compact.visible);
        float normalHeight = normal.bottomY - normal.topY;
        float compactHeight = compact.bottomY - compact.topY;
        assertTrue(compactHeight < normalHeight);
        assertTrue(compactHeight >= 6.5f * density - 0.1f);
        assertTrue(compact.leftChevronRight <= 17f * density - 5f * density + 0.1f);
        assertTrue(compact.rightChevronLeft >= 115f * density + 5f * density - 0.1f);
        assertEquals(compact.leftChevronLeft, 132f * density - compact.rightChevronRight, 0.01f);
    }

    @Test
    public void narrowSplitOrExtremeLongLabelHidesCleanly() {
        float density = 3.0f;
        // Split half keycap (88dp wide, 74dp text -> 7dp corridor/side)
        assertFalse(GboardSpacebarChevronRuntime.computeLayout(
                0f, 0f, 88f * density, 40f * density,
                7f * density, 10f * density, 81f * density, 30f * density,
                density).visible);

        // Keycap below minimum width (< 72dp)
        assertFalse(GboardSpacebarChevronRuntime.computeLayout(
                0f, 0f, 68f * density, 40f * density,
                24f * density, 10f * density, 44f * density, 30f * density,
                density).visible);

        // Keycap below minimum height (< 22dp)
        assertFalse(GboardSpacebarChevronRuntime.computeLayout(
                0f, 0f, 200f * density, 18f * density,
                72f * density, 4f * density, 128f * density, 14f * density,
                density).visible);

        // Empty text width
        assertFalse(GboardSpacebarChevronRuntime.computeLayout(
                0f, 0f, 200f * density, 44f * density,
                100f * density, 12f * density, 100f * density, 32f * density,
                density).visible);
    }

    @Test
    public void asymmetricTextBoundsStillProduceStrictlySymmetricChevronsWithoutOverlap() {
        float density = 2.0f;
        // Text shifted right: left corridor 60dp, right corridor 22dp -> constrained by 22dp
        GboardSpacebarChevronRuntime.ChevronLayout layout =
                GboardSpacebarChevronRuntime.computeLayout(
                        10f * density, 0f, 210f * density, 44f * density,
                        70f * density, 12f * density, 188f * density, 32f * density,
                        density);

        assertTrue(layout.visible);
        float leftInset = layout.leftChevronLeft - 10f * density;
        float rightInset = 210f * density - layout.rightChevronRight;
        assertEquals(leftInset, rightInset, 0.01f);
        assertTrue(layout.leftChevronRight < 70f * density);
        assertTrue(layout.rightChevronLeft > 188f * density);
    }

    @Test
    public void chevronColorAdaptsToLightDarkCustomAndTransparentFallback() {
        // Light theme (dark text)
        int light = GboardSpacebarChevronRuntime.resolveChevronColorArgb(0xFF1F1F1F, 1.0f, false);
        assertEquals(0x1F1F1F, light & 0x00FFFFFF);
        assertEquals(217, (light >>> 24) & 0xFF); // round(255 * 0.85)

        // Dark theme (light text)
        int dark = GboardSpacebarChevronRuntime.resolveChevronColorArgb(0xFFE8EAED, 1.0f, true);
        assertEquals(0xE8EAED, dark & 0x00FFFFFF);
        assertEquals(217, (dark >>> 24) & 0xFF);

        // Dynamic Color / Custom Theme tint preserved
        int custom = GboardSpacebarChevronRuntime.resolveChevronColorArgb(0xFF4A90E2, 1.0f, false);
        assertEquals(0x4A90E2, custom & 0x00FFFFFF);

        // Transparent text color falls back safely according to night mode
        int fallbackDark = GboardSpacebarChevronRuntime.resolveChevronColorArgb(0x00000000, 1.0f, true);
        int fallbackLight = GboardSpacebarChevronRuntime.resolveChevronColorArgb(0x00000000, 1.0f, false);
        assertTrue(((fallbackDark >>> 24) & 0xFF) > 0);
        assertTrue(((fallbackLight >>> 24) & 0xFF) > 0);
        assertTrue((fallbackDark & 0xFF) > (fallbackLight & 0xFF));
    }
}
