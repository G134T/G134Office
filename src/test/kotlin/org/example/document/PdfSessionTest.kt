package org.example.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfSessionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `failed PDF opens keep the document renderer unsaved edits and source file`() {
        val source = createPdf("source.pdf")
        val broken = directory.resolve("broken.pdf").toFile().also { it.writeText("not a PDF") }
        val protected = createPdf("protected.pdf", password = "secret")
        val session = PdfSession()
        try {
            session.open(source)
            session.rotatePage(0, 90)
            session.insertBlank(1)
            val previous = session.withDocument { doc, renderer -> doc to renderer }
            for ((file, password) in listOf(broken to null, protected to null, protected to "wrong")) {
                if (file == protected) assertFailsWith<InvalidPasswordException> { session.open(file, password) }
                else assertFailsWith<IOException> { session.open(file) }

                assertTrue(session.isOpen)
                assertTrue(session.dirty)
                assertEquals(source, session.file)
                assertEquals(2, session.pageCount)
                session.withDocument { doc, renderer ->
                    assertSame(previous.first, doc)
                    assertSame(previous.second, renderer)
                    assertEquals(90, doc.getPage(0).rotation)
                    assertTrue(renderer.renderImage(0, 0.1f).width > 0)
                }
            }
            val saved = directory.resolve("still-editable.pdf").toFile()
            session.save(saved)
            Loader.loadPDF(saved).use {
                assertEquals(2, it.numberOfPages)
                assertEquals(90, it.getPage(0).rotation)
            }
        } finally {
            session.close()
        }
    }

    @Test
    fun `successful password retry replaces the old session and closes its document`() {
        val source = createPdf("source.pdf")
        val target = createPdf("new.pdf", pages = 2, password = "secret")
        val session = PdfSession()
        try {
            session.open(source)
            session.rotatePage(0, 90)
            val previous = session.withDocument { doc, _ -> doc }
            assertFailsWith<InvalidPasswordException> { session.open(target) }

            session.open(target, "secret")

            assertEquals(target, session.file)
            assertEquals(2, session.pageCount)
            assertFalse(session.dirty)
            assertTrue(previous.document.isClosed)
            session.withDocument { doc, _ -> assertEquals(0, doc.getPage(0).rotation) }
        } finally {
            session.close()
        }
    }

    @Test
    fun `saving replaces the file that is currently open`() {
        val source = createPdf("locked.pdf")
        val session = PdfSession()
        try {
            session.open(source)
            session.rotatePage(0, 90)
            session.save(source)
            Loader.loadPDF(source).use { assertEquals(90, it.getPage(0).rotation) }
            session.rotatePage(0, 90)
            session.save(source)
            assertTrue(session.isOpen)
            assertFalse(session.dirty)
            Loader.loadPDF(source).use { assertEquals(180, it.getPage(0).rotation) }
        } finally {
            session.close()
        }
    }

    @Test
    fun `text index keeps the visible page text`() {
        val file = directory.resolve("visible.pdf").toFile()
        DocumentFormats.write(file, "AUDIT_VISIBLE_TEXT")
        val index = PdfTextIndex()
        Loader.loadPDF(file).use { index.rebuild(it) }
        assertTrue(index.text().contains("AUDIT_VISIBLE_TEXT"))
        assertTrue(index.runs.any { it.unicode.isNotEmpty() })
        assertEquals(index.pageText(0).trim(), index.text())
    }

    @Test
    fun `pages move to the requested final position in both directions`() {
        val source = createPdf("reorder.pdf", pages = 3)
        val session = PdfSession()
        try {
            session.open(source)
            session.rotatePage(1, 90)
            session.rotatePage(2, 180)
            fun order() = session.withDocument { doc, _ -> doc.pages.map { it.rotation } }
            session.movePage(0, 1)
            assertEquals(listOf(90, 0, 180), order())
            session.movePage(1, 2)
            assertEquals(listOf(90, 180, 0), order())
            session.movePage(2, 0)
            assertEquals(listOf(0, 90, 180), order())
            session.movePage(0, 2)
            assertEquals(listOf(90, 180, 0), order())
            assertEquals(3, session.pageCount)
            assertTrue(session.dirty)
            val saved = directory.resolve("reordered.pdf").toFile()
            session.save(saved)
            Loader.loadPDF(saved).use { doc -> assertEquals(listOf(90, 180, 0), doc.pages.map { it.rotation }) }
        } finally {
            session.close()
        }
    }

    private fun createPdf(name: String, pages: Int = 1, password: String? = null): File {
        val file = directory.resolve(name).toFile()
        PDDocument().use { doc ->
            repeat(pages) { doc.addPage(PDPage()) }
            if (password != null) doc.protect(StandardProtectionPolicy("owner", password, AccessPermission()))
            doc.save(file)
        }
        return file
    }
}
