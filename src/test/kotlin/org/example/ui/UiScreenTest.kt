package org.example.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiScreenTest {
    @Test
    fun `full hd at windows 100 percent stays at normal scale`() {
        assertEquals(1.0, UiScreen.glassScale(1920, 1080, 96, null))
    }

    @Test
    fun `laptop scaling is kept and not applied twice`() {
        assertEquals(1.25, UiScreen.glassScale(1920, 1080, 120, null))
        assertEquals(1.5, UiScreen.glassScale(1920, 1080, 144, null))
    }

    @Test
    fun `large monitors without windows scaling get a readable interface`() {
        assertEquals(1.5, UiScreen.glassScale(2560, 1440, 96, null))
        assertEquals(1.5, UiScreen.glassScale(3440, 1440, 96, null))
        assertEquals(2.0, UiScreen.glassScale(3840, 2160, 96, null))
    }

    @Test
    fun `four k at windows 150 percent follows windows`() {
        assertEquals(1.5, UiScreen.glassScale(3840, 2160, 144, null))
    }

    @Test
    fun `old monitor stays at 100 percent`() {
        assertEquals(1.0, UiScreen.glassScale(1024, 768, 96, null))
        assertEquals(1.0, UiScreen.glassScale(1280, 1024, 96, null))
    }

    @Test
    fun `manual percent multiplies the windows scale`() {
        assertEquals(1.25, UiScreen.glassScale(1920, 1080, 96, 125))
        assertEquals(0.75, UiScreen.glassScale(1024, 768, 96, 75))
    }

    @Test
    fun `window fits an old screen and uses a widescreen`() {
        val old = UiScreen.windowBox(1024.0, 768.0)
        assertTrue(old.width <= 1024.0)
        assertTrue(old.height <= 768.0)
        assertTrue(old.minWidth <= old.width)
        assertTrue(old.minHeight <= old.height)

        val laptop = UiScreen.windowBox(1366.0, 768.0)
        assertTrue(laptop.width <= 1366.0)
        assertTrue(laptop.height <= 768.0)

        val wide = UiScreen.windowBox(3440.0, 1400.0)
        assertTrue(wide.width in 1500.0..1720.0)
        assertTrue(wide.height <= 1400.0)

        val fullHd = UiScreen.windowBox(1920.0, 1080.0)
        assertTrue(fullHd.width in 1400.0..1500.0)
        assertTrue(fullHd.height <= 1080.0)
    }
}
