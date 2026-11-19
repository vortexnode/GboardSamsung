package dev.jason.gboardpatches.extension.spacebargesture;

import android.view.KeyEvent;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GboardSpacebarCursorSenderTest {
    @Test
    public void emitsBalancedDownUpPairsStrictlyForHorizontalDpad() {
        List<String> events = new ArrayList<>();
        boolean sent = GboardSpacebarCursorSender.sendHorizontalStepCount(
                KeyEvent.KEYCODE_DPAD_RIGHT, 2, (action, code) -> {
                    events.add(action + ":" + code);
                    return true;
                });

        Assert.assertTrue(sent);
        Assert.assertEquals(
                List.of(
                        KeyEvent.ACTION_DOWN + ":" + KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.ACTION_UP + ":" + KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.ACTION_DOWN + ":" + KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.ACTION_UP + ":" + KeyEvent.KEYCODE_DPAD_RIGHT),
                events);
    }

    @Test
    public void rejectsVerticalDpadKeycodes() {
        List<String> events = new ArrayList<>();
        Assert.assertFalse(GboardSpacebarCursorSender.sendHorizontalStepCount(
                KeyEvent.KEYCODE_DPAD_UP, 1, (action, code) -> {
                    events.add(action + ":" + code);
                    return true;
                }));
        Assert.assertFalse(GboardSpacebarCursorSender.sendHorizontalStepCount(
                KeyEvent.KEYCODE_DPAD_DOWN, 1, (action, code) -> {
                    events.add(action + ":" + code);
                    return true;
                }));
        Assert.assertTrue(events.isEmpty());
    }

    @Test
    public void clampsStepsToMaxDpadStepsPerMove() {
        List<Integer> actions = new ArrayList<>();
        boolean sent = GboardSpacebarCursorSender.sendHorizontalStepCount(
                KeyEvent.KEYCODE_DPAD_LEFT, 50, (action, code) -> {
                    actions.add(action);
                    return true;
                });

        Assert.assertTrue(sent);
        Assert.assertEquals(GboardSpacebarGestureState.MAX_DPAD_STEPS_PER_MOVE * 2, actions.size());
    }

    @Test
    public void failedDownDoesNotEmitOrphanUp() {
        List<Integer> actions = new ArrayList<>();
        boolean sent = GboardSpacebarCursorSender.sendBalancedKeyEvent(
                KeyEvent.KEYCODE_DPAD_LEFT, (action, code) -> {
                    actions.add(action);
                    return false;
                });

        Assert.assertFalse(sent);
        Assert.assertEquals(List.of(KeyEvent.ACTION_DOWN), actions);
    }

    @Test
    public void falseReturningUpDoesNotEmitDuplicateUp() {
        List<Integer> actions = new ArrayList<>();
        boolean sent = GboardSpacebarCursorSender.sendBalancedKeyEvent(
                KeyEvent.KEYCODE_DPAD_RIGHT, (action, code) -> {
                    actions.add(action);
                    return action == KeyEvent.ACTION_DOWN;
                });

        Assert.assertFalse(sent);
        Assert.assertEquals(List.of(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), actions);
    }

    @Test
    public void downThrowingExceptionDoesNotEmitOrphanUp() {
        List<Integer> actions = new ArrayList<>();
        boolean sent = GboardSpacebarCursorSender.sendBalancedKeyEvent(
                KeyEvent.KEYCODE_DPAD_LEFT, (action, code) -> {
                    actions.add(action);
                    throw new RuntimeException("IPC failure");
                });

        Assert.assertFalse(sent);
        Assert.assertEquals(List.of(KeyEvent.ACTION_DOWN), actions);
    }
}
