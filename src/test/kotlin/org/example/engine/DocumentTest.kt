package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DocumentTest {
    @Test
    fun `batch replacement retains runs paragraph properties and intervening blocks`() {
        val first = Paragraph().also {
            it.outlineLevel = 1
            it.setRuns(listOf(TextRun("Keep ", italic = true), TextRun("Find Find", bold = true, color = "#ff0000")))
        }
        val second = Paragraph("Find tail", Align.RIGHT).also {
            it.leftIndentPt = 18.0
            it.spacingAfterPt = 9.0
            it.pageBreakBefore = true
        }
        val table = TableBlock(mutableListOf(mutableListOf(TableCell(mutableListOf(Paragraph("Cell"))))))
        val image = ImageBlock(byteArrayOf(1), "image/png", 10.0, 10.0)
        val document = Document().also { it.loadBlocks(listOf(first, table, image, second)) }
        val ranges = Regex("Find").findAll(document.toPlainText()).map { it.range }.toList()

        document.replaceRanges(ranges, "Found")

        assertEquals("Keep Found Found\nFound tail", document.toPlainText())
        assertEquals(TextRun("Keep ", italic = true), first.runs.first())
        assertTrue(first.runs.filter { it.text.contains("Found") }.all { it.bold && it.color == "#ff0000" })
        assertEquals(1, first.outlineLevel)
        assertEquals(0, second.outlineLevel)
        assertEquals(Align.RIGHT, second.align)
        assertEquals(18.0, second.leftIndentPt)
        assertEquals(9.0, second.spacingAfterPt)
        assertTrue(second.pageBreakBefore)
        assertSame(table, document.blocks[1])
        assertSame(image, document.blocks[2])
    }

    @Test
    fun `batch replacement supports deletion and literal replacement characters`() {
        for (replacement in listOf("", "\$1\\", "foofoo")) {
            val document = Document.fromText("foo foo\nfoo")
            val ranges = Regex("foo").findAll(document.toPlainText()).map { it.range }.toList()
            document.replaceRanges(ranges, replacement)
            assertEquals("$replacement $replacement\n$replacement", document.toPlainText())
        }
    }

    @Test
    fun `multiline insertion stays before the following table and image`() {
        val table = TableBlock(mutableListOf())
        val image = ImageBlock(byteArrayOf(1), "image/png", 10.0, 10.0)
        val document = Document().also { it.loadBlocks(listOf(Paragraph("abcd"), table, image, Paragraph("after"))) }
        document.replaceRange(2, 2, "\nnew\n")
        assertEquals(listOf("ab", "new", "cd", "after"), document.paragraphs.map { it.text })
        assertSame(table, document.blocks[3])
        assertSame(image, document.blocks[4])
    }

    @Test
    fun `loading text preserves paragraphs including trailing blank line`() {
        val source = "Первая\n\nПоследняя\n"
        val document = Document.fromText(source)
        assertEquals(listOf("Первая", "", "Последняя", ""), document.paragraphs.map { it.text })
        assertEquals(source, document.toPlainText())
        assertEquals(TextStats(lines = 4, words = 2, chars = source.length), document.textStats())
    }
}
