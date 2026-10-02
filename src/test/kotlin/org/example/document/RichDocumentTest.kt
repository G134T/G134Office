package org.example.document

import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.example.engine.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichDocumentTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `docx saves CSS and imported RGB colors and reads them back`() {
        val paragraph = Paragraph().also {
            it.setRuns(listOf(
                TextRun("Красный", color = "#ff0000"),
                TextRun("Зелёный", color = "00aa00"),
                TextRun("Авто", color = "auto"),
                TextRun("Обычный")
            ))
        }
        val document = Document().also { it.loadBlocks(listOf(paragraph)) }
        val file = directory.resolve("colors.docx").toFile()
        DocumentFormats.write(file, document)
        val opened = DocumentFormats.readDocument(file)
        assertEquals(paragraph.text, opened.paragraphs.first().text)
        assertEquals(listOf("ff0000", "00aa00", "auto", null),
            opened.paragraphs.first().runs.map { it.color?.lowercase() })
        DocumentFormats.write(file, opened)
        assertEquals(opened.paragraphs.first().runs, DocumentFormats.readDocument(file).paragraphs.first().runs)
    }

    @Test
    fun `docx retains runs table and image across open and save`() {
        val png = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val paragraph = Paragraph().also {
            it.setRuns(listOf(TextRun("Обычный "), TextRun("жирный", bold = true, italic = true, strikethrough = true)))
        }
        val table = TableBlock(mutableListOf(mutableListOf(TableCell(mutableListOf(Paragraph("Ячейка"))))))
        val document = Document().also {
            it.loadBlocks(listOf(paragraph, table, ImageBlock(png, "image/png", 72.0, 72.0)))
            it.pageWidthPt = 612.0
            it.pageHeightPt = 792.0
            it.marginLeftPt = 72.0
            it.paragraphs.first().leftIndentPt = 18.0
            it.paragraphs.first().spacingAfterPt = 9.0
        }
        val first = directory.resolve("first.docx").toFile()
        DocumentFormats.write(first, document)
        val opened = DocumentFormats.readDocument(first)
        assertEquals("Обычный жирный", opened.paragraphs.first().text)
        assertTrue(opened.paragraphs.first().runs[1].bold)
        assertTrue(opened.paragraphs.first().runs[1].italic)
        assertTrue(opened.paragraphs.first().runs[1].strikethrough)
        assertEquals("Ячейка", (opened.blocks.filterIsInstance<TableBlock>().first()).rows[0][0].paragraphs[0].text)
        assertTrue(opened.blocks.filterIsInstance<ImageBlock>().first().bytes.isNotEmpty())
        assertEquals(612.0, opened.pageWidthPt, 0.1)
        assertEquals(72.0, opened.marginLeftPt, 0.1)
        assertEquals(18.0, opened.paragraphs.first().leftIndentPt, 0.1)
        assertEquals(9.0, opened.paragraphs.first().spacingAfterPt, 0.1)

        val second = directory.resolve("second.docx").toFile()
        DocumentFormats.write(second, opened)
        val reopened = DocumentFormats.readDocument(second)
        assertTrue(reopened.blocks.any { it is TableBlock })
        assertTrue(reopened.blocks.any { it is ImageBlock })
    }

    @Test
    fun `docx keeps a heading outline level`() {
        val document = Document().also {
            it.loadBlocks(listOf(Paragraph("Глава").also { paragraph ->
                ParagraphFormatting.applyWordStyle(paragraph, 1)
            }))
        }
        val file = directory.resolve("heading.docx").toFile()
        DocumentFormats.write(file, document)
        val opened = DocumentFormats.readDocument(file)
        assertEquals("Глава", opened.paragraphs.first().text)
        assertEquals(1, opened.paragraphs.first().outlineLevel)
    }

    @Test
    fun `text edit keeps unaffected rich blocks`() {
        val paragraph = Paragraph().also { it.setRuns(listOf(TextRun("До "), TextRun("редактирования", bold = true))) }
        val table = TableBlock(mutableListOf(mutableListOf(TableCell(mutableListOf(Paragraph("Данные"))))))
        val document = Document().also { it.loadBlocks(listOf(paragraph, table, Paragraph("После"))) }
        document.replaceRange(3, paragraph.text.length, "правки")
        assertEquals("До правки", document.paragraphs[0].text)
        assertEquals("До ", document.paragraphs[0].runs.first().text)
        assertTrue(document.blocks[1] === table)
        assertEquals("После", document.paragraphs[1].text)
    }

    @Test
    fun `editing one span retains neighboring formatting`() {
        val paragraph = Paragraph().also {
            it.setRuns(listOf(TextRun("A "), TextRun("BC", bold = true), TextRun(" D", italic = true)))
        }
        val document = Document().also { it.loadBlocks(listOf(paragraph)) }
        document.replaceRange(2, 3, "X")
        assertEquals("A XC D", paragraph.text)
        assertTrue(paragraph.runs.any { it.text == "C" && it.bold })
        assertTrue(paragraph.runs.any { it.text == " D" && it.italic })
    }

    @Test
    fun `odt preserves page layout paragraph style table and image`() {
        val png = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val paragraph = Paragraph("Текст", Align.CENTER).also {
            it.leftIndentPt = 24.0
            it.spacingAfterPt = 12.0
            it.setRuns(listOf(TextRun("Текст", bold = true)))
        }
        val document = Document().also {
            it.loadBlocks(listOf(paragraph,
                TableBlock(mutableListOf(mutableListOf(TableCell(mutableListOf(Paragraph("Клетка")))))),
                ImageBlock(png, "image/png", 36.0, 36.0)))
            it.pageWidthPt = 612.0
            it.pageHeightPt = 792.0
            it.marginLeftPt = 72.0
        }
        val file = directory.resolve("layout.odt").toFile()
        DocumentFormats.write(file, document)
        val opened = DocumentFormats.readDocument(file)
        assertEquals(612.0, opened.pageWidthPt, 0.1)
        assertEquals(72.0, opened.marginLeftPt, 0.1)
        assertEquals(Align.CENTER, opened.paragraphs.first().align)
        assertEquals(24.0, opened.paragraphs.first().leftIndentPt, 0.1)
        assertTrue(opened.paragraphs.first().runs.first().bold)
        assertTrue(opened.blocks.any { it is TableBlock })
        assertTrue(opened.blocks.any { it is ImageBlock })
    }

    @Test
    fun `rtf opens as structured paragraphs`() {
        val file = directory.resolve("note.rtf").toFile()
        DocumentFormats.write(file, "Первый\nВторой")
        val document = DocumentFormats.readDocument(file)
        assertTrue(document.paragraphs.size >= 2)
        assertEquals("Первый", document.paragraphs.first().text)
        assertTrue(document.paragraphs.first().runs.isNotEmpty())
    }

    @Test
    fun `rtf round trip keeps alignment and character style`() {
        val paragraph = Paragraph("", Align.RIGHT).also {
            it.leftIndentPt = 20.0
            it.setRuns(listOf(TextRun("Курсив", italic = true)))
        }
        val file = directory.resolve("styled.rtf").toFile()
        DocumentFormats.write(file, Document().also { it.loadBlocks(listOf(paragraph)) })
        val result = DocumentFormats.readDocument(file).paragraphs.first()
        assertEquals("Курсив", result.text)
        assertEquals(Align.RIGHT, result.align)
        assertTrue(result.runs.first().italic)
    }

    @Test
    fun `html round trip keeps paragraphs table and image`() {
        val png = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val paragraph = Paragraph("", Align.CENTER).also {
            it.setRuns(listOf(TextRun("Текст", bold = true)))
        }
        val doc = Document().also {
            it.loadBlocks(listOf(paragraph,
                TableBlock(mutableListOf(mutableListOf(TableCell(mutableListOf(Paragraph("Ячейка")))))),
                ImageBlock(png, "image/png", 30.0, 30.0)))
        }
        val file = directory.resolve("styled.html").toFile()
        DocumentFormats.write(file, doc)
        val result = DocumentFormats.readDocument(file)
        assertEquals("Текст", result.paragraphs.first().text)
        assertEquals(Align.CENTER, result.paragraphs.first().align)
        assertTrue(result.paragraphs.first().runs.first().bold)
        assertTrue(result.blocks.any { it is TableBlock })
        assertTrue(result.blocks.any { it is ImageBlock })
    }

    @Test
    fun `docx save keeps a header the editor does not show`() {
        val file = directory.resolve("header.docx").toFile()
        XWPFDocument().use { word ->
            word.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("КОЛОНТИТУЛ")
            word.createParagraph().createRun().setText("Основной")
            FileOutputStream(file).use { word.write(it) }
        }
        val opened = DocumentFormats.readDocument(file)
        opened.paragraphs.first().setRuns(listOf(TextRun("Изменённый")))
        DocumentFormats.write(file, opened)
        FileInputStream(file).use { input ->
            XWPFDocument(input).use { word ->
                assertEquals("КОЛОНТИТУЛ", word.headerList.joinToString("") { it.text }.trim())
                assertEquals("Изменённый", word.paragraphs.joinToString("") { it.text }.trim())
            }
        }
    }

    @Test
    fun `html images cannot escape the document folder`() {
        directory.resolve("private-note.txt").toFile().writeText("SECRET-TOKEN")
        val folder = directory.resolve("inbox").toFile().also { it.mkdirs() }
        val png = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        File(folder, "pic.png").writeBytes(png)
        val html = File(folder, "document.html")
        html.writeText("""<html><body><p>Видно</p><img src="../private-note.txt"><img src="pic.png"></body></html>""")
        val opened = DocumentFormats.readDocument(html)
        assertEquals("Видно", opened.toPlainText().trim())
        val images = opened.blocks.filterIsInstance<ImageBlock>()
        assertEquals(1, images.size)
        assertEquals("image/png", images.single().contentType)
        assertFalse(images.single().bytes.toString(Charsets.ISO_8859_1).contains("SECRET-TOKEN"))
        val exported = directory.resolve("exported.html").toFile()
        DocumentFormats.write(exported, opened)
        assertFalse(exported.readText().contains("SECRET-TOKEN"))
    }
}
