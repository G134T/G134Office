package org.example.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CaretPrefsTest {
    @Test
    fun `caret color falls back to the default blue`() {
        assertEquals(DEFAULT_CARET_COLOR, normalizeCaretColor(null))
        assertEquals(DEFAULT_CARET_COLOR, normalizeCaretColor("blue"))
        assertEquals("#185abd", normalizeCaretColor("#185ABD"))
    }

    @Test
    fun `blink speed keeps system, steady and a bounded custom interval`() {
        assertEquals(0, normalizeCaretBlink(0))
        assertEquals(-1, normalizeCaretBlink(-5))
        assertEquals(150, normalizeCaretBlink(40))
        assertEquals(530, normalizeCaretBlink(530))
        assertEquals(1200, normalizeCaretBlink(4000))
    }
}
