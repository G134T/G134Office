package org.example.ui

import javafx.application.Platform
import javafx.scene.control.TextField
import org.example.engine.Align
import org.example.engine.Document
import org.example.engine.EditorCanvas
import org.example.engine.Paragraph
import org.example.engine.TextRun
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindReplaceTest {
    @Test
    fun `replace all preserves formatting and is undone and redone in one step`() = onFx {
        val editor = EditorCanvas()
        val heading = Paragraph().also {
            it.outlineLevel = 1
            it.setRuns(listOf(TextRun("Find this Find", bold = true)))
        }
        editor.loadDocument(Document().also { it.loadBlocks(listOf(heading, Paragraph("Keep this", Align.RIGHT))) })
        var changes = 0
        editor.onChange = { changes++ }
        val dialog = FindReplaceDialog(editor)
        replaceAll(dialog, "Find", "Found")

        assertEquals("Found this Found\nKeep this", editor.plainText())
        assertEquals(1, changes)
        assertTrue(editor.document.paragraphs[0].runs.all { it.bold })
        assertEquals(0, editor.document.paragraphs[1].outlineLevel)
        assertEquals(Align.RIGHT, editor.document.paragraphs[1].align)
        val replacementRuns = editor.document.paragraphs[0].runs.toList()

        // A search without matches must not add another undo entry or mark the document changed.
        replaceAll(dialog, "absent", "unused")
        assertEquals(1, changes)
        editor.undo()
        assertEquals("Find this Find\nKeep this", editor.plainText())
        assertEquals(listOf(TextRun("Find this Find", bold = true)), editor.document.paragraphs[0].runs)
        editor.redo()
        assertEquals("Found this Found\nKeep this", editor.plainText())
        assertEquals(replacementRuns, editor.document.paragraphs[0].runs)
        assertEquals(Align.RIGHT, editor.document.paragraphs[1].align)
    }

    private fun replaceAll(dialog: FindReplaceDialog, query: String, replacement: String) {
        for ((name, value) in listOf("findField" to query, "replaceField" to replacement)) {
            val field = FindReplaceDialog::class.java.getDeclaredField(name).apply { isAccessible = true }
            (field.get(dialog) as TextField).text = value
        }
        FindReplaceDialog::class.java.getDeclaredMethod("replaceAll").apply { isAccessible = true }.invoke(dialog)
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
