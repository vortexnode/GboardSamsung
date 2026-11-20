package dev.jason.gboardpatches.patches.gboard.shared

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
}
