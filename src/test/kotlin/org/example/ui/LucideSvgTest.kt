package org.example.ui

import javafx.application.Platform
import javafx.scene.paint.Color
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LucideSvgTest {
    @Test
    fun `icon set falls back to the factory standard`() {
        assertEquals(IconSet.STANDARD, IconSet.fromPref(null))
        assertEquals(IconSet.STANDARD, IconSet.fromPref(" "))
        assertEquals(IconSet.STANDARD, IconSet.fromPref("painted"))
        assertEquals(IconSet.CUSTOM, IconSet.fromPref("custom"))
    }

    @Test
    fun `every installed icon is a stroked svg`() {
        assertTrue(OfficeIconCatalog.files.size >= 24)
        OfficeIconCatalog.files.forEach { name ->
            val svg = lucideSvg(name)
            assertTrue(svg.contains("stroke="), name)
            assertTrue(lucideShapes(svg).isNotEmpty(), name)
        }
    }

    @Test
    fun `alignment icons keep three text lines`() {
        assertEquals(3, lucideShapes(lucideSvg(OfficeIconCatalog.ALIGN_LEFT)).size)
        assertEquals(3, lucideShapes(lucideSvg(OfficeIconCatalog.ALIGN_CENTER)).size)
        assertEquals(3, lucideShapes(lucideSvg(OfficeIconCatalog.ALIGN_RIGHT)).size)
        assertEquals(3, lucideShapes(lucideSvg(OfficeIconCatalog.ALIGN_JUSTIFY)).size)
    }

    @Test
    fun `circles lines and rounded rects stay separate shapes`() {
        val circle = lucideShapes("""<circle cx="6" cy="6" r="3" />""").single()
        assertEquals(LucideShape.Oval(6.0, 6.0, 3.0), circle)
        val line = lucideShapes("""<line x1="4" x2="20" y1="9" y2="9" />""").single()
        assertEquals(LucideShape.Segment(4.0, 9.0, 20.0, 9.0), line)
        val rect = lucideShapes("""<rect x="8" y="2" width="8" height="4" rx="1" />""").single()
        assertEquals(LucideShape.Box(8.0, 2.0, 8.0, 4.0, 1.0, 1.0), rect)
    }

    @Test
    fun `every icon can be drawn`() {
        onFx {
            OfficeIconCatalog.files.forEach { name ->
                val glyph = LucideIcons.glyph(name, 16.0, Color.web("#1b1b1b"))
                assertTrue(glyph.layoutBounds.width > 0.0, name)
                glyph.color(Color.web("#f5f5f5"))
            }
        }
    }
}

private fun onFx(block: () -> Unit) {
    val latch = CountDownLatch(1)
    var error: Throwable? = null
    val job = Runnable {
        try {
            block()
        } catch (thrown: Throwable) {
            error = thrown
        } finally {
            latch.countDown()
        }
    }
    try {
        Platform.startup(job)
    } catch (_: IllegalStateException) {
        Platform.runLater(job)
    }
    if (!latch.await(30, TimeUnit.SECONDS)) fail("JavaFX не успел нарисовать значки")
    error?.let { throw it }
}
