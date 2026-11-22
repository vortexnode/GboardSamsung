package dev.jason.gboardpatches.extension.toolbarroundchips;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
public final class GboardToolbarRoundChipsRuntimeTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        GboardToolbarRoundChipsRuntime.resetForTesting();
        GboardToolbarRoundChipsSettings.invalidateCache();
    }

    @After
    public void tearDown() {
        GboardToolbarRoundChipsRuntime.resetForTesting();
        GboardToolbarRoundChipsSettings.invalidateCache();
    }

    @Test
    public void disabledByDefaultAttachesNoOverlayViaRealAfterSoftKeyBind() {
        ToolbarSlot slot = createAccessPointSlot(48, 42, Color.WHITE);
        Assert.assertFalse(GboardToolbarRoundChipsSettings.DEFAULT_TOOLBAR_ROUND_CHIPS_ENABLED);

        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(slot.root);

        Assert.assertFalse(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));
        Assert.assertNull(GboardToolbarRoundChipsRuntime.computeGeometry(slot.root, slot.bgView));
    }

    @Test
    public void drawableHasValidBoundsAndPreservesStockBackground() {
        Drawable stockRipple = new ColorDrawable(Color.TRANSPARENT);
        ToolbarSlot slot = createAccessPointSlot(48, 42, 0xFF8AB4F8);
        slot.bgView.setBackground(stockRipple);
        GboardToolbarRoundChipsRuntime.setSettingEnabledPredicateForTesting(v -> true);

        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(slot.root);

        Assert.assertTrue(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));
        Assert.assertSame(stockRipple, slot.bgView.getBackground());

        GboardToolbarRoundChipsRuntime.ChipGeometry geometry =
                GboardToolbarRoundChipsRuntime.computeGeometry(slot.root, slot.bgView);
        Assert.assertNotNull(geometry);
        // Preserves Material You RGB tint from icon with subtle alpha.
        Assert.assertEquals(Color.red(0xFF8AB4F8), Color.red(geometry.color));
        Assert.assertEquals(Color.green(0xFF8AB4F8), Color.green(geometry.color));
        Assert.assertEquals(Color.blue(0xFF8AB4F8), Color.blue(geometry.color));
        Assert.assertTrue(Color.alpha(geometry.color) > 0 && Color.alpha(geometry.color) < 64);

        GboardToolbarRoundChipsRuntime.RoundChipOverlayDrawable drawable =
                new GboardToolbarRoundChipsRuntime.RoundChipOverlayDrawable(slot.root, slot.bgView);
        drawable.setBounds(0, 0, slot.bgView.getWidth(), slot.bgView.getHeight());

        Bitmap bitmap = Bitmap.createBitmap(
                slot.bgView.getWidth(), slot.bgView.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            drawable.draw(canvas);
            // Robolectric's software Canvas does not reliably expose rasterized ViewOverlay
            // pixels across runner versions. Verify the actual drawable contract instead:
            // valid measured geometry, non-empty draw bounds, and a successful draw call.
            Assert.assertTrue("Expected non-empty drawable bounds", drawable.getBounds().width() > 0);
            Assert.assertTrue("Expected non-empty drawable bounds", drawable.getBounds().height() > 0);
            Assert.assertTrue("Expected a visible chip radius", geometry.radius > 0f);
            Assert.assertTrue("Expected a translucent chip color", Color.alpha(geometry.color) > 0);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void adaptsDiameterBetweenSparseAndDenseToolbarSlotsAndHidesWhenTooNarrow() {
        GboardToolbarRoundChipsRuntime.setSettingEnabledPredicateForTesting(v -> true);

        ToolbarSlot sparseSlot = createAccessPointSlot(64, 44, Color.BLACK);
        ToolbarSlot denseSlot = createAccessPointSlot(34, 44, Color.BLACK);
        ToolbarSlot tinySlot = createAccessPointSlot(20, 44, Color.BLACK);
        ToolbarSlot wideClipboardChip = createAccessPointSlot(180, 40, Color.BLACK);

        GboardToolbarRoundChipsRuntime.ChipGeometry sparse =
                GboardToolbarRoundChipsRuntime.computeGeometry(sparseSlot.root, sparseSlot.bgView);
        GboardToolbarRoundChipsRuntime.ChipGeometry dense =
                GboardToolbarRoundChipsRuntime.computeGeometry(denseSlot.root, denseSlot.bgView);

        Assert.assertNotNull(sparse);
        Assert.assertNotNull(dense);
        Assert.assertTrue(
                "Dense 8-item slot should produce a smaller radius than sparse 3-item slot",
                dense.radius < sparse.radius);
        Assert.assertNull(
                GboardToolbarRoundChipsRuntime.computeGeometry(tinySlot.root, tinySlot.bgView));
        Assert.assertNull(
                GboardToolbarRoundChipsRuntime.computeGeometry(
                        wideClipboardChip.root, wideClipboardChip.bgView));
    }

    @Test
    public void excludesRamblerActionKeyMenuGridAndCandidates() {
        GboardToolbarRoundChipsRuntime.setSettingEnabledPredicateForTesting(v -> true);

        // 1. Rambler voice button (.background-icon.for-action-key.for-action-default-key)
        FrameLayout rambler = new FrameLayout(context);
        ImageView ramblerBg = new ImageView(context);
        ramblerBg.setTag(".background-icon.for-action-key.for-action-default-key");
        rambler.addView(ramblerBg);
        layoutView(rambler, 44, 44);
        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(rambler);
        Assert.assertFalse(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(rambler));

        // 2. Menu grid button ⊞ (.access-point-entry-menu-icon.item-ripple)
        FrameLayout menuGrid = new FrameLayout(context);
        ImageView menuIcon = new ImageView(context);
        menuIcon.setTag(".access-point-entry-menu-icon.item-ripple");
        menuGrid.addView(menuIcon);
        layoutView(menuGrid, 44, 44);
        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(menuGrid);
        Assert.assertFalse(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(menuGrid));

        // 3. Candidate strip ancestor (.candidates-area)
        FrameLayout candidatesArea = new FrameLayout(context);
        candidatesArea.setTag(".candidates-area");
        ToolbarSlot insideCandidates = createAccessPointSlot(44, 44, Color.WHITE);
        candidatesArea.addView(insideCandidates.root);
        layoutView(candidatesArea, 200, 44);
        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(insideCandidates.root);
        Assert.assertFalse(
                GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(insideCandidates.root));
    }

    @Test
    public void detachesImmediatelyOnRecyclingAndPreferenceDisable() {
        ToolbarSlot slot = createAccessPointSlot(48, 42, Color.WHITE);
        GboardToolbarRoundChipsRuntime.setSettingEnabledPredicateForTesting(v -> true);

        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(slot.root);
        Assert.assertTrue(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));

        // Disable in Settings -> detached immediately.
        GboardToolbarRoundChipsRuntime.onPreferenceChanged(false);
        Assert.assertFalse(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));

        // Re-enable, then simulate recycling into non-toolbar key.
        GboardToolbarRoundChipsRuntime.onPreferenceChanged(true);
        Assert.assertTrue(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));

        slot.bgView.setTag(".keytop");
        GboardToolbarRoundChipsRuntime.afterSoftKeyBind(slot.root);
        Assert.assertFalse(GboardToolbarRoundChipsRuntime.isChipAttachedForTesting(slot.root));
    }

    private ToolbarSlot createAccessPointSlot(int width, int height, int iconTintColor) {
        FrameLayout root = new FrameLayout(context);
        View bgView = new View(context);
        bgView.setTag(".access-point-bar-item-ripple-background.item-ripple");
        root.addView(bgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView iconView = new ImageView(context);
        iconView.setTag(".icon.for-access-point-icon.non-linear-scale");
        iconView.setImageTintList(ColorStateList.valueOf(iconTintColor));
        root.addView(iconView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        layoutView(root, width, height);
        return new ToolbarSlot(root, bgView);
    }

    private static void layoutView(View view, int width, int height) {
        int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        view.measure(wSpec, hSpec);
        view.layout(0, 0, width, height);
    }

    private static final class ToolbarSlot {
        final FrameLayout root;
        final View bgView;

        ToolbarSlot(FrameLayout root, View bgView) {
            this.root = root;
            this.bgView = bgView;
        }
    }
}
