package org.example.fanfic

import org.example.engine.Document
import org.example.engine.Paragraph
import org.example.engine.TextRun
import org.example.engine.Align
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FanficPreviewSerializerTest {
    @Test
    fun `bold run becomes clean Ficbook HTML`() {
        val paragraph = Paragraph().also {
            it.setRuns(listOf(TextRun("Текст", bold = true),
                TextRun(" и правка", italic = true, underline = true, strikethrough = true)))
        }
        val document = Document().also { it.loadBlocks(listOf(paragraph, Paragraph())) }
        val html = FanficPreviewSerializer.toHtml(document)
        assertContains(html, "<p><b>Текст</b><i><u><s> и правка</s></u></i></p>")
        assertContains(html, "<p></p>")
        assertFalse(html.contains("style="))
        assertFalse(html.contains("font-family"))
        assertFalse(html.contains("class="))
        assertFalse(html.contains("<span"))
    }

    @Test
    fun `only centered paragraph gets alignment`() {
        val document = Document().also {
            it.loadBlocks(listOf(Paragraph("Первый"), Paragraph("Второй", align = Align.CENTER)))
        }
        val html = FanficPreviewSerializer.toHtml(document)
        assertContains(html, "<p>Первый</p><p align=\"center\">Второй</p>")
        assertFalse(html.contains("<p align=\"center\">Первый"))
    }

    @Test
    fun `empty paragraph stays a scene break`() {
        val document = Document().also { it.loadBlocks(listOf(Paragraph("До"), Paragraph(), Paragraph("После"))) }
        assertContains(FanficPreviewSerializer.toHtml(document), "<p>До</p><p></p><p>После</p>")
    }
}
