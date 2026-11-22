package dev.jason.gboardpatches.patches.gboard.shared

import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSoftKeyFamilySelectionStoreTest {
    @Test
    fun `take returns each selected feature once and clears the context`() {
        val store = GboardSoftKeyFamilySelectionStore<Any>()
        val context = Any()

        store.add(context, GboardSoftKeyFamilyFeature.CUSTOM_SYMBOLS)
        store.add(context, GboardSoftKeyFamilyFeature.CUSTOM_SYMBOLS)
        store.add(context, GboardSoftKeyFamilyFeature.LONG_PRESS_QUICK_ACTIONS)

        assertEquals(
            setOf(
                GboardSoftKeyFamilyFeature.CUSTOM_SYMBOLS,
                GboardSoftKeyFamilyFeature.LONG_PRESS_QUICK_ACTIONS,
            ),
            store.take(context).toSet(),
        )
        assertTrue(store.take(context).isEmpty())
    }

    @Test
    fun spacebarAfterStockDelegateInvokesGlobeAndChevronsIndependently() {
        val feature = GboardSoftKeyFamilyFeature.SAMSUNG_SPACEBAR_GESTURES
        assertEquals(
            listOf(
                RuntimeCallId.SPACEBAR_GESTURE_RUNTIME_AFTER_SOFT_KEY_BIND,
                RuntimeCallId.SPACEBAR_CHEVRON_RUNTIME_AFTER_SOFT_KEY_BIND,
                RuntimeCallId.TOOLBAR_ROUND_CHIPS_RUNTIME_AFTER_SOFT_KEY_BIND,
            ),
            feature.afterRuntimeCalls,
        )

        val delegate = feature.afterDelegate()
        val globeCall = delegate.indexOf("GboardSpacebarGlobeKeyRuntime;->afterSoftKeyBind")
        val chevronCall = delegate.indexOf("GboardSpacebarChevronRuntime;->afterSoftKeyBind")
        val toolbarChipsCall = delegate.indexOf("GboardToolbarRoundChipsRuntime;->afterSoftKeyBind")
        assertTrue("Globe runtime call is missing", globeCall >= 0)
        assertTrue("Chevron runtime call is missing", chevronCall > globeCall)
        assertTrue("Toolbar round chips runtime call is missing", toolbarChipsCall > chevronCall)
        assertEquals(3, delegate.lineSequence().count { it.contains("invoke-static") })
    }
}
