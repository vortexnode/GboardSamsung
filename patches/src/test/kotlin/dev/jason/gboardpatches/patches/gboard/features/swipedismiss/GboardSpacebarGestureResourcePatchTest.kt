package dev.jason.gboardpatches.patches.gboard.features.swipedismiss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class GboardSpacebarGestureResourcePatchTest {
    @Test
    fun removesSpacebarFreeCursorAndImePickerWhilePreservingPressAndGlobePicker() {
        val doc = parseXml(
            """
            <framework>
                <softkeys>
                    <softkey id="@id/softkey_space">
                        <action type="PRESS" keycode="SPACE" data=" " />
                        <if enable_free_cursor_by_long_press_space="true" enable_scrub_move="true" free_cursor="true">
                            <action type="LONG_PRESS" keycode="SWITCH_KEYBOARD" data="free_cursor" />
                        </if>
                        <else>
                            <action type="LONG_PRESS" keycode="SHOW_SYSTEM_IME_PICKER" data="space" />
                        </else>
                    </softkey>
                    <softkey id="@id/softkey_globe">
                        <action type="PRESS" keycode="SWITCH_TO_NEXT_LANGUAGE" data="globe" />
                        <action type="LONG_PRESS" keycode="SHOW_SYSTEM_IME_PICKER" data="globe" />
                    </softkey>
                </softkeys>
            </framework>
            """.trimIndent(),
        )

        assertTrue(hasConflictingSpacebarNodes(doc))
        val result = applySpacebarGestureResourceCleanup(doc)

        assertEquals(1, result.removedFreeCursorLongPressActions)
        assertEquals(1, result.removedSpaceImePickerLongPressActions)
        assertEquals(0, result.removedScrubMoveHandlers)
        assertEquals(0, result.removedTriggerFreeCursorHandlers)
        assertFalse(hasConflictingSpacebarNodes(doc))

        val remainingActions = (0 until doc.getElementsByTagName("action").length)
            .map { doc.getElementsByTagName("action").item(it).attributes }
        assertEquals(3, remainingActions.size)
        assertTrue(remainingActions.any { it.getNamedItem("keycode")?.nodeValue == "SPACE" })
        assertTrue(
            remainingActions.any {
                it.getNamedItem("keycode")?.nodeValue == "SHOW_SYSTEM_IME_PICKER" &&
                    it.getNamedItem("data")?.nodeValue == "globe"
            },
        )
    }

    @Test
    fun removesScrubMoveAndTriggerFreeCursorWhilePreservingScrubDeleteAndInlineSuggestionScrub() {
        val doc = parseXml(
            """
            <framework>
                <motion_event_handler
                    class="com.google.android.libraries.inputmethod.motioneventhandler.scrubdelete.ScrubDeleteMotionEventHandler"
                    preference_key="@string/pref_key_enable_gesture_delete" />
                <if free_cursor="false">
                    <motion_event_handler
                        class="com.google.android.libraries.inputmethod.motioneventhandler.scrubmove.ScrubMoveMotionEventHandler"
                        preference_key="@string/pref_key_enable_gesture_cursor_control" />
                </if>
                <else>
                    <motion_event_handler
                        class="com.google.android.libraries.inputmethod.freecursor.TriggerFreeCursorMotionEventHandler"
                        preference_key="@string/pref_key_enable_gesture_cursor_control" />
                </else>
                <motion_event_handler
                    class="com.google.android.apps.inputmethod.libs.inlinesuggestion.InlineSuggestionScrubSpaceMotionEventHandler" />
            </framework>
            """.trimIndent(),
        )

        assertTrue(hasConflictingSpacebarNodes(doc))
        val result = applySpacebarGestureResourceCleanup(doc)

        assertEquals(0, result.removedFreeCursorLongPressActions)
        assertEquals(0, result.removedSpaceImePickerLongPressActions)
        assertEquals(1, result.removedScrubMoveHandlers)
        assertEquals(1, result.removedTriggerFreeCursorHandlers)
        assertFalse(hasConflictingSpacebarNodes(doc))

        val remainingClasses = (0 until doc.getElementsByTagName("motion_event_handler").length)
            .map {
                doc.getElementsByTagName("motion_event_handler")
                    .item(it)
                    .attributes
                    .getNamedItem("class")
                    .nodeValue
            }
        assertEquals(
            listOf(
                "com.google.android.libraries.inputmethod.motioneventhandler.scrubdelete.ScrubDeleteMotionEventHandler",
                "com.google.android.apps.inputmethod.libs.inlinesuggestion.InlineSuggestionScrubSpaceMotionEventHandler",
            ),
            remainingClasses,
        )
    }

    @Test
    fun doesNotFlagGlobeOnlyImePickerXmlAsConflicting() {
        val doc = parseXml(
            """
            <framework>
                <softkey id="@id/softkey_globe">
                    <action type="LONG_PRESS" keycode="SHOW_SYSTEM_IME_PICKER" data="globe" />
                </softkey>
            </framework>
            """.trimIndent(),
        )

        assertFalse(hasConflictingSpacebarNodes(doc))
        val result = applySpacebarGestureResourceCleanup(doc)
        assertEquals(0, result.totalRemoved)
    }

    private fun parseXml(xml: String): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
}
