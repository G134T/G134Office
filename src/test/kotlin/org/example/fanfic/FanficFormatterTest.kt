package org.example.fanfic

import org.example.engine.Document
import org.example.engine.Paragraph
import org.example.engine.TextRun
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FanficFormatterTest {
    @Test
    fun `quotes become chevrons and hyphen speech becomes dash`() {
        val raw = "- Привет, - сказала Катя. \"Никогда\" не слышала..."
        val formatted = FanficFormatter.format(raw)
        assertEquals("— Привет, — сказала Катя. «Никогда» не слышала…", formatted)
    }

    @Test
    fun `word hyphens stay hyphens`() {
        assertEquals("кое-что всё-таки", FanficFormatter.format("кое-что всё-таки"))
    }

    @Test
    fun `double spaces collapse`() {
        assertEquals("раз два", FanficFormatter.format("раз  два"))
    }

    @Test
    fun `applyTo keeps run markup`() {
        val paragraph = Paragraph().also {
            it.setRuns(listOf(TextRun("\"Жирный\"", bold = true)))
        }
        val document = Document().also { it.loadBlocks(listOf(paragraph)) }
        FanficFormatter.applyTo(document)
        val run = document.paragraphs[0].runs.single()
        assertEquals("«Жирный»", run.text)
        assertEquals(true, run.bold)
    }

    @Test
    fun `page size uses ficbook 1800 characters`() {
        assertEquals(1, FanficFormatter.sizePages(1))
        assertEquals(1, FanficFormatter.sizePages(1800))
        assertEquals(2, FanficFormatter.sizePages(1801))
    }
}
