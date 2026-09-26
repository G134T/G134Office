package org.example.document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentSaveTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `partial write failure preserves original and removes temporary file`() {
        val target = directory.resolve("original.docx").toFile()
        val original = byteArrayOf(0, 1, 2, 127, -1)
        target.writeBytes(original)

        assertFailsWith<IOException> {
            AtomicFileSave.write(target) { temporary ->
                temporary.writeText("partial content")
                throw IOException("Simulated disk write failure")
            }
        }

        assertContentEquals(original, target.readBytes())
        assertEquals(listOf("original.docx"), directory.toFile().list()!!.toList())
    }

    @Test
    fun `failed new save leaves no document`() {
        assertFailsWith<IOException> {
            AtomicFileSave.write(directory.resolve("new.txt").toFile()) {
                it.writeText("partial")
                throw IOException("Simulated failure")
            }
        }
        assertEquals(0, directory.toFile().list()!!.size)
    }

    @Test
    fun `failed replacement preserves destination and cleans temporary file`() {
        val target = Files.createDirectory(directory.resolve("occupied.txt"))
        Files.writeString(target.resolve("keep.txt"), "original")
        assertFailsWith<IOException> {
            AtomicFileSave.write(target.toFile()) { it.writeText("replacement") }
        }
        assertEquals("original", Files.readString(target.resolve("keep.txt")))
        assertEquals(listOf("occupied.txt"), directory.toFile().list()!!.toList())
    }

    @Test
    fun `docx saves empty text and preserves blank and trailing paragraphs`() {
        val target = directory.resolve("document.docx").toFile()
        for (text in listOf("", "Текст", "Первая\n\nПоследняя\n")) {
            DocumentFormats.write(target, text)
            assertEquals(text, DocumentFormats.read(target))
        }
        assertEquals(listOf("document.docx"), directory.toFile().list()!!.toList())
    }

    @Test
    fun `text save creates directories and replaces existing content`() {
        val target = directory.resolve("nested/document.txt").toFile()
        DocumentFormats.write(target, "old")
        DocumentFormats.write(target, "Новый текст\n")
        assertEquals("Новый текст\n", DocumentFormats.read(target))
    }

    @Test
    fun `odt html and fb2 save nonempty text and trailing empty paragraph`() {
        val text = "Текст\n"
        for (extension in listOf("odt", "html", "fb2")) {
            val target = directory.resolve("document.$extension").toFile()
            DocumentFormats.write(target, text)
            val content = if (extension == "odt") {
                ZipFile(target).use { zip ->
                    zip.getInputStream(zip.getEntry("content.xml")).bufferedReader().use { it.readText() }
                }
            } else target.readText()
            assertTrue(content.contains("Текст"))
            val emptyParagraph = if (extension == "odt") "<text:p text:style-name=\"Standard\"></text:p>" else "<p></p>"
            assertTrue(content.contains(emptyParagraph))
        }
    }

    @Test
    fun `unsupported format preserves existing file`() {
        val target = directory.resolve("original.doc").toFile()
        target.writeText("original")
        assertFailsWith<IllegalArgumentException> { DocumentFormats.write(target, "new") }
        assertEquals("original", target.readText())
        assertEquals(listOf("original.doc"), directory.toFile().list()!!.toList())
    }

    @Test
    fun `rtf html and pdf keep cyrillic and extra pages`() {
        val text = "Заголовок\nПривет, мир\n"
        val rtf = directory.resolve("note.rtf").toFile()
        DocumentFormats.write(rtf, text)
        assertTrue(DocumentFormats.read(rtf).contains("Привет"))

        val html = directory.resolve("note.html").toFile()
        DocumentFormats.write(html, "# Заголовок\nТекст")
        val htmlText = DocumentFormats.read(html)
        assertTrue(htmlText.contains("Заголовок"))
        assertTrue(htmlText.contains("Текст"))

        val pdf = directory.resolve("note.pdf").toFile()
        val many = (1..48).joinToString("\n") { "Строка $it кириллица" }
        DocumentFormats.write(pdf, many)
        val pdfText = DocumentFormats.read(pdf)
        assertTrue(pdfText.contains("Строка 1"))
        assertTrue(pdfText.contains("Строка 48"))
    }

    @Test
    fun `docx headings round trip as markdown prefixes`() {
        val target = directory.resolve("head.docx").toFile()
        DocumentFormats.write(target, "# Заголовок\nОбычный")
        val read = DocumentFormats.read(target)
        assertTrue(read.contains("Заголовок"))
        assertTrue(read.contains("Обычный"))
    }

    @Test
    fun `docx import keeps text from multiple runs and table cells`() {
        val target = directory.resolve("complex.docx").toFile()
        XWPFDocument().use { doc ->
            val paragraph = doc.createParagraph()
            paragraph.createRun().setText("Первая ")
            paragraph.createRun().setText("часть")
            val table = doc.createTable(2, 2)
            table.getRow(0).getCell(0).text = "А1"
            table.getRow(0).getCell(1).text = "Б1"
            table.getRow(1).getCell(0).text = "А2"
            table.getRow(1).getCell(1).text = "Б2"
            target.outputStream().use { doc.write(it) }
        }
        val result = DocumentFormats.read(target)
        assertTrue(result.contains("Первая часть"))
        assertTrue(result.contains("А1\tБ1"))
        assertTrue(result.contains("А2\tБ2"))
    }

    @Test
    fun `windows-1251 text is detected without bom`() {
        val target = directory.resolve("cp1251.txt").toFile()
        val encoded = "Привет".toByteArray(java.nio.charset.Charset.forName("windows-1251"))
        target.writeBytes(encoded)
        assertEquals("Привет", DocumentFormats.read(target))
    }

    @Test
    fun `pdf session rotates and keeps page after save`() {
        val source = directory.resolve("book.pdf").toFile()
        DocumentFormats.write(source, (1..5).joinToString("\n") { "Стр$it" })
        val session = PdfSession()
        session.open(source)
        assertEquals(true, session.pageCount >= 1)
        session.rotatePage(0, 90)
        session.insertBlank(1)
        val copy = directory.resolve("book-out.pdf").toFile()
        session.save(copy)
        assertTrue(copy.exists() && copy.length() > 0)
        assertTrue(session.pageCount >= 2)
        session.close()
    }
}
