package dev.jason.gboardpatches.extension.spacebargesture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
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
public final class GboardSpacebarChevronRuntimeTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        GboardSpacebarChevronRuntime.resetForTesting();
        GboardSpacebarGestureSettings.invalidateCache();
    }

    @After public void tearDown() {
        GboardSpacebarChevronRuntime.resetForTesting();
        GboardSpacebarGestureSettings.invalidateCache();
    }

    @Test public void disabledByDefaultLeavesTextViewUntouched() {
        KeycapPair pair = createKeycap("Русский", 420, 80, Color.BLACK);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, false);
        Drawable[] drawables = pair.label.getCompoundDrawables();
        Assert.assertNull(drawables[0]);
        Assert.assertNull(drawables[2]);
    }

    @Test public void wideSpacebarShowsSymmetricChevronsInCurrentTextColor() {
        KeycapPair pair = createKeycap("English (US)", 480, 80, Color.WHITE);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Drawable[] drawables = pair.label.getCompoundDrawables();
        Assert.assertTrue(drawables[0] instanceof GboardSpacebarChevronRuntime.ChevronDrawable);
        Assert.assertTrue(drawables[2] instanceof GboardSpacebarChevronRuntime.ChevronDrawable);
        Assert.assertEquals(Color.WHITE,
                ((GboardSpacebarChevronRuntime.ChevronDrawable) drawables[0]).getColor());
        Assert.assertEquals(Color.WHITE,
                ((GboardSpacebarChevronRuntime.ChevronDrawable) drawables[2]).getColor());
    }

    @Test public void emptyLabelDoesNotShowChevrons() {
        KeycapPair pair = createKeycap("   ", 480, 80, Color.BLACK);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertNull(pair.label.getCompoundDrawables()[0]);
        Assert.assertNull(pair.label.getCompoundDrawables()[2]);
    }

    @Test public void narrowKeycapHidesChevrons() {
        KeycapPair pair = createKeycap("English (United Kingdom)", 40, 80, Color.BLACK);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertNull(pair.label.getCompoundDrawables()[0]);
        Assert.assertNull(pair.label.getCompoundDrawables()[2]);
    }

    @Test public void recyclingAndDisablingRestoreOriginalDrawables() {
        Drawable top = new ColorDrawable(Color.GRAY);
        KeycapPair pair = createKeycap("Русский", 480, 80, Color.BLACK);
        pair.label.setCompoundDrawables(null, top, null, null);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertTrue(pair.label.getCompoundDrawables()[0]
                instanceof GboardSpacebarChevronRuntime.ChevronDrawable);

        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, false, true);
        Drawable[] recycled = pair.label.getCompoundDrawables();
        Assert.assertNull(recycled[0]);
        Assert.assertSame(top, recycled[1]);
        Assert.assertNull(recycled[2]);

        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, false);
        Assert.assertNull(pair.label.getCompoundDrawables()[0]);
        Assert.assertNull(pair.label.getCompoundDrawables()[2]);
    }

    @Test public void stockDrawableAddedAfterDecorationRemovesBothChevrons() {
        KeycapPair pair = createKeycap("Русский", 480, 80, Color.BLACK);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Drawable stockRight = new ColorDrawable(Color.RED);
        pair.label.setCompoundDrawables(null, null, stockRight, null);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertNull(pair.label.getCompoundDrawables()[0]);
        Assert.assertSame(stockRight, pair.label.getCompoundDrawables()[2]);
    }

    @Test public void labelPaddingIsIncludedInAvailableWidth() {
        KeycapPair pair = createKeycap("English (United Kingdom)", 480, 80, Color.BLACK);
        pair.label.setPadding(180, 0, 180, 0);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertNull(pair.label.getCompoundDrawables()[0]);
        Assert.assertNull(pair.label.getCompoundDrawables()[2]);
    }

    @Test public void existingStockDrawableIsNeverOverwritten() {
        Drawable stockLeft = new ColorDrawable(Color.BLUE);
        KeycapPair pair = createKeycap("Русский", 480, 80, Color.BLACK);
        pair.label.setCompoundDrawables(stockLeft, null, null, null);
        GboardSpacebarChevronRuntime.applyToSoftKeyView(pair.container, true, true);
        Assert.assertSame(stockLeft, pair.label.getCompoundDrawables()[0]);
        Assert.assertNull(pair.label.getCompoundDrawables()[2]);
    }

    private KeycapPair createKeycap(String text, int width, int height, int textColor) {
        FrameLayout container = new FrameLayout(context);
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(textColor);
        container.addView(label, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        container.measure(widthSpec, heightSpec);
        container.layout(0, 0, width, height);
        return new KeycapPair(container, label);
    }

    private static final class KeycapPair {
        final FrameLayout container;
        final TextView label;
        KeycapPair(FrameLayout container, TextView label) {
            this.container = container;
            this.label = label;
        }
    }
}
