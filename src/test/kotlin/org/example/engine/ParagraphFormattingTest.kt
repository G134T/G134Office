package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ParagraphFormattingTest {
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
}
