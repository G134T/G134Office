package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ParagraphFormattingTest {
    @Test
    fun `enter inserts both halves before tables and images including trailing blocks`() {
        for (withFollowingParagraph in listOf(false, true)) {
            for (at in listOf(0, 2, 4)) {
                val source = Paragraph("abcd", Align.RIGHT).also { it.setRuns(listOf(TextRun("abcd", bold = true))) }
                val table = TableBlock(mutableListOf())
                val image = ImageBlock(byteArrayOf(1), "image/png", 10.0, 10.0)
                val blocks = mutableListOf<DocumentBlock>(source, table, image)
                if (withFollowingParagraph) blocks += Paragraph("after")
                val document = Document().also { it.loadBlocks(blocks) }

                assertEquals(1, ParagraphFormatting.splitAtCaret(document, 0, at))

                assertEquals("abcd".substring(0, at), (document.blocks[0] as Paragraph).text)
                val tail = document.blocks[1] as Paragraph
                assertEquals("abcd".substring(at), tail.text)
                assertEquals(Align.RIGHT, tail.align)
                assertEquals(true, tail.runs.all { it.bold })
                assertSame(table, document.blocks[2])
                assertSame(image, document.blocks[3])
                if (withFollowingParagraph) assertEquals("after", (document.blocks[4] as Paragraph).text)
            }
        }
    }

    @Test
    fun `centering paragraph under caret leaves first paragraph left aligned`() {
        val document = Document().also {
            it.loadBlocks(listOf(Paragraph("Первый"), Paragraph("Второй")))
        }
        ParagraphFormatting.affectedIndices(document, caretParagraph = 1, selection = null)
            .forEach { document.paragraphs[it].align = Align.CENTER }
        assertEquals(Align.LEFT, document.paragraphs[0].align)
        assertEquals(Align.CENTER, document.paragraphs[1].align)
    }

    @Test
    fun `selection ending at next paragraph start does not align that paragraph`() {
        val document = Document.fromText("Первый\nВторой\nТретий")
        val end = "Первый\nВторой\n".length
        assertEquals(listOf(0, 1),
            ParagraphFormatting.affectedIndices(document, 2, 0 until end))
    }

    @Test
    fun `enter copies alignment from previous paragraph`() {
        val document = Document().also { it.loadBlocks(listOf(Paragraph("Текст", Align.RIGHT))) }
        ParagraphFormatting.splitAtCaret(document, 0, 2)
        assertEquals(Align.RIGHT, document.paragraphs[1].align)
        assertEquals("Те", document.paragraphs[0].text)
        assertEquals("кст", document.paragraphs[1].text)
    }

    @Test
    fun `enter keeps bold italic and indent on both halves`() {
        val source = Paragraph("Жирный", Align.JUSTIFY).also {
            it.leftIndentPt = 18.0
            it.firstLineIndentPt = 12.0
            it.setRuns(listOf(TextRun("Жирный", bold = true, italic = true)))
        }
        val document = Document().also { it.loadBlocks(listOf(source)) }
        ParagraphFormatting.splitAtCaret(document, 0, 3)
        assertEquals("Жир", document.paragraphs[0].text)
        assertEquals("ный", document.paragraphs[1].text)
        assertEquals(true, document.paragraphs[0].runs.single().bold)
        assertEquals(true, document.paragraphs[1].runs.single().italic)
        assertEquals(Align.JUSTIFY, document.paragraphs[1].align)
        assertEquals(18.0, document.paragraphs[1].leftIndentPt, 0.01)
        assertEquals(12.0, document.paragraphs[1].firstLineIndentPt, 0.01)
    }

    @Test
    fun `typing inside a bold word keeps the bold run`() {
        val paragraph = Paragraph().also { it.setRuns(listOf(TextRun("мир", bold = true))) }
        ParagraphFormatting.insertText(paragraph, 3, "!")
        assertEquals("мир!", paragraph.text)
        assertEquals(true, paragraph.runs.single().bold)
    }

    @Test
    fun `deleting one character keeps the neighbouring styles`() {
        val paragraph = Paragraph().also {
            it.setRuns(listOf(TextRun("A "), TextRun("BC", bold = true), TextRun(" D", italic = true)))
        }
        ParagraphFormatting.deleteSpan(paragraph, 2, 3)
        assertEquals("A C D", paragraph.text)
        assertEquals(true, paragraph.runs.first { it.text == "C" }.bold)
        assertEquals(true, paragraph.runs.first { it.text == " D" }.italic)
    }

    @Test
    fun `word styles set outline spacing and drop extra character formatting`() {
        val paragraph = Paragraph("Глава", Align.CENTER).also {
            it.leftIndentPt = 36.0
            it.setRuns(listOf(TextRun("Глава", italic = true, underline = true, color = "#ff0000", fontFamily = "Calibri")))
        }
        ParagraphFormatting.applyWordStyle(paragraph, 1)
        assertEquals(1, paragraph.outlineLevel)
        assertEquals(28.0, paragraph.fontSize, 0.01)
        assertEquals(true, paragraph.bold)
        assertEquals(Align.LEFT, paragraph.align)
        assertEquals(0.0, paragraph.leftIndentPt, 0.01)
        assertEquals(1.15, paragraph.lineSpacing, 0.01)
        val run = paragraph.runs.single()
        assertEquals(false, run.italic)
        assertEquals(false, run.underline)
        assertEquals(null, run.color)
        assertEquals("Calibri", run.fontFamily)
        assertEquals(true, run.bold)

        ParagraphFormatting.applyWordStyle(paragraph, 0)
        assertEquals(0, paragraph.outlineLevel)
        assertEquals(16.0, paragraph.fontSize, 0.01)
        assertEquals(false, paragraph.runs.single().bold)
    }

    @Test
    fun `clearing a run keeps the letters and drops the paint`() {
        val clean = ParagraphFormatting.clearRun(TextRun("текст", bold = true, strikethrough = true, color = "#00aa00"))
        assertEquals("текст", clean.text)
        assertEquals(false, clean.bold)
        assertEquals(false, clean.strikethrough)
        assertEquals(null, clean.color)
    }

    @Test
    fun `copied paragraph keeps its outline level`() {
        val source = Paragraph("Глава").also { it.outlineLevel = 2 }
        val copy = Document().also { it.loadBlocks(listOf(source)) }.copy()
        assertEquals(2, copy.paragraphs.single().outlineLevel)
        assertEquals(2, ParagraphFormatting.cloneProps(source).outlineLevel)
    }

    @Test
    fun `word movement stops at the next word`() {
        val text = "привет мир"
        assertEquals(7, ParagraphFormatting.moveWord(text, 0, true))
        assertEquals(0, ParagraphFormatting.moveWord(text, 7, false))
        assertEquals(0 until 6, ParagraphFormatting.wordBounds(text, 2))
    }

    @Test
    fun `clipboard round trip keeps run style`() {
        val parts = listOf(listOf(TextRun("Жирный", bold = true, fontSize = 18.0), TextRun(" обычный")))
        val decoded = ParagraphFormatting.decodeClip(ParagraphFormatting.encodeClip(parts))
        assertEquals("Жирный", decoded?.single()?.first()?.text)
        assertEquals(true, decoded?.single()?.first()?.bold)
        assertEquals(18.0, decoded?.single()?.first()?.fontSize)
        assertEquals(" обычный", decoded?.single()?.get(1)?.text)
    }
}
