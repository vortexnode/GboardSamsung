package dev.jason.gboardpatches.extension.spacebargesture;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSpacebarGestureStateTest {
    private static final float DENSITY = 3.0f;
    private static final float TOUCH_SLOP = 24.0f; // 8dp; MIN_SWIPE_SLOP_DP = 16dp => 48px threshold.

    @Test
    public void shortTapDoesNotConsumeAndResetsToIdle() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);
        Assert.assertEquals(GboardSpacebarGestureState.State.SPACE_TRACKING, state.getState());

        // Small finger jitter below threshold must pass through untouched.
        GboardSpacebarGestureState.MoveResult jitter =
                state.onMove(0, 512f, 1804f, DENSITY, TOUCH_SLOP, 1040L);
        Assert.assertFalse(jitter.consumed);
        Assert.assertFalse(jitter.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.LanguageDirection.NONE,
                jitter.languageDirection);
        Assert.assertEquals(0, jitter.dpadSteps);

        // Lifting before swipe/long-press must not consume ACTION_UP (inserts normal single space).
        Assert.assertFalse(state.onUpOrCancel());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void swipeLeftToRightTriggersNextOnceAndBlocksLongPress() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);

        // Horizontal L->R drag past 48px threshold (16dp * 3).
        GboardSpacebarGestureState.MoveResult trigger =
                state.onMove(0, 560f, 1805f, DENSITY, TOUCH_SLOP, 1080L);
        Assert.assertTrue(trigger.consumed);
        Assert.assertTrue(trigger.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.LanguageDirection.NEXT,
                trigger.languageDirection);
        Assert.assertEquals(0, trigger.dpadSteps);
        Assert.assertEquals(GboardSpacebarGestureState.State.LANGUAGE_SWIPE_CONSUMED,
                state.getState());

        // Subsequent MOVE must stay consumed without firing another cancel or language switch.
        GboardSpacebarGestureState.MoveResult tailMove =
                state.onMove(0, 640f, 1805f, DENSITY, TOUCH_SLOP, 1120L);
        Assert.assertTrue(tailMove.consumed);
        Assert.assertFalse(tailMove.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.LanguageDirection.NONE,
                tailMove.languageDirection);
        Assert.assertEquals(0, tailMove.dpadSteps);

        // Long-press timeout firing after swipe must NOT enter cursor mode.
        GboardSpacebarGestureState.LongPressResult lateTimeout = state.onLongPressTimeout();
        Assert.assertFalse(lateTimeout.activated);
        Assert.assertEquals(GboardSpacebarGestureState.State.LANGUAGE_SWIPE_CONSUMED,
                state.getState());

        // ACTION_UP is consumed so no space is inserted.
        Assert.assertTrue(state.onUpOrCancel());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void swipeRightToLeftTriggersPreviousOnceAndConsumesTail() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);

        GboardSpacebarGestureState.MoveResult trigger =
                state.onMove(0, 440f, 1802f, DENSITY, TOUCH_SLOP, 1070L);
        Assert.assertTrue(trigger.consumed);
        Assert.assertTrue(trigger.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.LanguageDirection.PREVIOUS,
                trigger.languageDirection);
        Assert.assertEquals(GboardSpacebarGestureState.State.LANGUAGE_SWIPE_CONSUMED,
                state.getState());

        Assert.assertTrue(state.onUpOrCancel());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void longPressActivatesCursorModeAndSuppressesSpaceWithoutMovement() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);

        GboardSpacebarGestureState.LongPressResult activated = state.onLongPressTimeout();
        Assert.assertTrue(activated.activated);
        Assert.assertTrue(activated.sendCancel);
        Assert.assertTrue(activated.finishComposing);
        Assert.assertEquals(GboardSpacebarGestureState.State.CURSOR_MODE, state.getState());

        // Second timeout call is a no-op.
        Assert.assertFalse(state.onLongPressTimeout().activated);

        // Releasing with zero movement still consumes UP so no space is inserted.
        Assert.assertTrue(state.onUpOrCancel());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void cursorModeEmitsHorizontalStepsClampsBurstAndIgnoresVerticalDelta() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);
        Assert.assertTrue(state.onLongPressTimeout().activated);

        // Pure vertical movement must emit 0 DPAD steps while still consuming the event.
        GboardSpacebarGestureState.MoveResult vertical =
                state.onMove(0, 500f, 1650f, DENSITY, TOUCH_SLOP, 1340L);
        Assert.assertTrue(vertical.consumed);
        Assert.assertFalse(vertical.sendCancel);
        Assert.assertEquals(0, vertical.dpadSteps);

        // Slow precision right drag: 30px = 10dp -> exactly +1 step (DPAD_RIGHT).
        GboardSpacebarGestureState.MoveResult rightStep =
                state.onMove(0, 530f, 1650f, DENSITY, TOUCH_SLOP, 1400L);
        Assert.assertTrue(rightStep.consumed);
        Assert.assertFalse(rightStep.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.LanguageDirection.NONE,
                rightStep.languageDirection);
        Assert.assertEquals(1, rightStep.dpadSteps);

        // Slow precision left drag: -60px = -20dp over 100ms -> -2 steps (DPAD_LEFT).
        GboardSpacebarGestureState.MoveResult leftSteps =
                state.onMove(0, 470f, 1650f, DENSITY, TOUCH_SLOP, 1500L);
        Assert.assertTrue(leftSteps.consumed);
        Assert.assertEquals(-2, leftSteps.dpadSteps);

        // Huge fast jump must be clamped to MAX_DPAD_STEPS_PER_MOVE (never floods hundreds).
        GboardSpacebarGestureState.MoveResult burst =
                state.onMove(0, 2000f, 1650f, DENSITY, TOUCH_SLOP, 1510L);
        Assert.assertTrue(burst.consumed);
        Assert.assertEquals(GboardSpacebarGestureState.MAX_DPAD_STEPS_PER_MOVE, burst.dpadSteps);

        Assert.assertTrue(state.onUpOrCancel());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void cursorModeDirectionReversalClearsResidualAccumulator() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);
        state.onLongPressTimeout();

        // Accumulate +9dp (below 10dp step threshold).
        GboardSpacebarGestureState.MoveResult partialRight =
                state.onMove(0, 527f, 1800f, DENSITY, TOUCH_SLOP, 1380L);
        Assert.assertEquals(0, partialRight.dpadSteps);

        // Immediately reverse -10dp: must emit -1 step instead of only cancelling +9dp.
        GboardSpacebarGestureState.MoveResult reverseLeft =
                state.onMove(0, 497f, 1800f, DENSITY, TOUCH_SLOP, 1460L);
        Assert.assertEquals(-1, reverseLeft.dpadSteps);
    }

    @Test
    public void accelerationMultiplierIsBoundedAndMonotonic() {
        float slow = GboardSpacebarGestureState.computeAccelerationMultiplier(5f, 50L); // 0.1 dp/ms
        float mid = GboardSpacebarGestureState.computeAccelerationMultiplier(18f, 20L); // 0.9 dp/ms
        float fast = GboardSpacebarGestureState.computeAccelerationMultiplier(40f, 10L); // 4.0 dp/ms

        Assert.assertEquals(1.0f, slow, 0.001f);
        Assert.assertTrue(mid > 1.0f && mid < fast);
        Assert.assertEquals(2.25f, fast, 0.001f);
    }

    @Test
    public void secondPointerAbortsTrackingAndCursorMode() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);
        Assert.assertFalse(state.onPointerDown());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());

        state.beginSpaceTracking(0, 500f, 1800f, 2000L);
        state.onLongPressTimeout();
        Assert.assertEquals(GboardSpacebarGestureState.State.CURSOR_MODE, state.getState());
        Assert.assertFalse(state.onPointerDown());
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }

    @Test
    public void predominantlyVerticalDragAbortsTrackingWithoutTriggeringSwipeOrCursor() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);

        GboardSpacebarGestureState.MoveResult vertical =
                state.onMove(0, 505f, 1720f, DENSITY, TOUCH_SLOP, 1060L);
        Assert.assertFalse(vertical.consumed);
        Assert.assertFalse(vertical.sendCancel);
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
        Assert.assertFalse(state.onLongPressTimeout().activated);
    }

    @Test
    public void mismatchedPointerIdResetsState() {
        GboardSpacebarGestureState state = new GboardSpacebarGestureState();
        state.beginSpaceTracking(0, 500f, 1800f, 1000L);

        GboardSpacebarGestureState.MoveResult wrongPointer =
                state.onMove(1, 600f, 1800f, DENSITY, TOUCH_SLOP, 1050L);
        Assert.assertFalse(wrongPointer.consumed);
        Assert.assertEquals(GboardSpacebarGestureState.State.IDLE, state.getState());
    }
}
