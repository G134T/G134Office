package org.example.engine

import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorViewportTest {
    @Test
    fun `long documents use a viewport sized canvas at every scroll position and zoom`() = onFx {
        val editor = EditorCanvas()
        val root = StackPane(editor)
        Scene(root, 800.0, 600.0)
        root.applyCss()
        root.layout()
        editor.loadDocument(longDocument())
        for (zoom in listOf(0.5, 1.0, 2.5)) {
            editor.setZoom(zoom)
            root.layout()
            for (position in listOf(0.0, 0.5, 1.0)) {
                editor.vvalue = position
                editor.hvalue = position
                root.layout()
                val canvas = canvas(editor)
                assertTrue(canvas.height <= editor.viewportBounds.height + 1, "Canvas must not grow with page count")
                assertTrue(canvas.width <= editor.viewportBounds.width + 1, "Canvas must not grow with zoom")
                assertTrue(editor.content.layoutBounds.height > 30_000)
                val snapshot = canvas.snapshot(null, null)
                assertTrue(snapshot.width > 0 && snapshot.height > 0)
            }
        }
        editor.setFanficMode(true)
        root.layout()
        assertTrue(canvas(editor).height <= editor.viewportBounds.height + 1)
    }

    @Test
    fun `keyboard navigation and mouse selection reach text after scrolling`() = onFx {
        val editor = EditorCanvas()
        val root = StackPane(editor)
        Scene(root, 800.0, 600.0)
        root.applyCss()
        root.layout()
        editor.loadDocument(longDocument())
        editor.fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.END, false, true, false, false))
        root.layout()
        assertTrue(editor.vvalue > 0.9, "Ctrl+End must scroll to the last page")
        assertEquals(editor.plainText().length, editor.caretOffset())

        // Click the first character of the last paragraph in viewport coordinates.
        val laid = Layout.build(editor.document)
        val line = laid.pages.last().lines.first()
        val canvas = canvas(editor)
        val ox = ((editor.content.layoutBounds.width - laid.pageWidth) / 2.0).coerceAtLeast(12.0)
        val x = ox + line.x - canvas.layoutX
        val y = 12.0 + laid.pages.lastIndex * (laid.pageHeight + 20.0) + line.y + line.height / 2 - canvas.layoutY
        assertTrue(y in 0.0..canvas.height)
        canvas.onMousePressed // Handlers are registered through addEventHandler.
        canvas.fireEvent(MouseEvent(MouseEvent.MOUSE_PRESSED, x, y, x, y, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, false, false, true, null))
        assertEquals(editor.plainText().lastIndexOf('\n') + 1, editor.caretOffset())
        editor.fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, true, false, false, false))
        assertEquals(1, editor.selectedRange()!!.count())
    }

    private fun longDocument() = Document().also { doc ->
        doc.loadBlocks((1..80).map { number -> Paragraph("Page $number").apply { pageBreakBefore = number > 1 } })
    }

    private fun canvas(editor: EditorCanvas): Canvas = when (val content = editor.content) {
        is Canvas -> content
        is Pane -> content.children.filterIsInstance<Canvas>().single()
        else -> error("Missing editor canvas")
    }

    private fun onFx(block: () -> Unit) {
        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        val action = Runnable {
            try { block() } catch (error: Throwable) { failure = error } finally { finished.countDown() }
        }
        try { Platform.startup(action) } catch (_: IllegalStateException) { Platform.runLater(action) }
        assertTrue(finished.await(30, TimeUnit.SECONDS), "JavaFX test timed out")
        failure?.let { throw it }
    }
}
