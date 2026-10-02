package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTest {
    @Test
    fun `explicit page break and table rows create separate pages`() {
        val doc = Document()
        val first = Paragraph("Первая страница")
        val second = Paragraph("Вторая страница").also { it.pageBreakBefore = true }
        val rows = MutableList(30) { index ->
            mutableListOf(TableCell(mutableListOf(Paragraph("Строка $index"))))
        }
        doc.loadBlocks(listOf(first, second, TableBlock(rows)))
        val layout = Layout.build(doc)
        assertTrue(layout.pages.size >= 3)
        assertEquals(0, layout.pages[0].lines.first().paragraphIndex)
        assertEquals(1, layout.pages[1].lines.first().paragraphIndex)
        assertEquals(30, layout.pages.sumOf { it.objects.size })
    }

    @Test
    fun `layout keeps spaces tabs and hard line breaks`() {
        val original = "Первый  второй\tтретий\nНовая строка"
        val document = Document.fromText("")
        document.loadBlocks(listOf(Paragraph(original)))
        val lines = Layout.build(document).pages.flatMap { it.lines }
        assertEquals(original.replace("\n", ""), lines.joinToString("") { it.text })
        assertTrue(lines.any { it.startInParagraph > original.indexOf('\n') })
    }

    @Test
    fun `first line indent does not narrow the following lines`() {
        val doc = page(320.0)
        val paragraph = Paragraph(List(12) { "слово" }.joinToString(" ")).also { it.firstLineIndentPt = 72.0 }
        doc.loadBlocks(listOf(paragraph))
        val lines = Layout.build(doc).pages.flatMap { it.lines }
        assertTrue(lines.size >= 2, lines.joinToString(" | ") { it.text })
        assertEquals(doc.marginLeftPt + 72.0, lines[0].x, 1.0)
        assertEquals(doc.marginLeftPt, lines[1].x, 1.0)
    }

    @Test
    fun `hanging indent pulls only the first line toward the margin`() {
        val doc = page(340.0)
        val paragraph = Paragraph(List(14) { "слово" }.joinToString(" ")).also {
            it.leftIndentPt = 72.0
            it.firstLineIndentPt = -36.0
        }
        doc.loadBlocks(listOf(paragraph))
        val lines = Layout.build(doc).pages.flatMap { it.lines }
        assertTrue(lines.size >= 2, lines.joinToString(" | ") { it.text })
        assertEquals(doc.marginLeftPt + 36.0, lines[0].x, 1.0)
        assertEquals(doc.marginLeftPt + 72.0, lines[1].x, 1.0)
    }

    @Test
    fun `justification stretches wrapped lines and leaves the last line ragged`() {
        val doc = page(280.0)
        doc.loadBlocks(listOf(Paragraph(List(18) { "слово" }.joinToString(" "), Align.JUSTIFY)))
        val lines = Layout.build(doc).pages.flatMap { it.lines }
        assertTrue(lines.size >= 2, lines.joinToString(" | ") { it.text })
        assertTrue(lines.first().justify)
        assertEquals(false, lines.last().justify)
        val end = Layout.spans(lines.first()).last().let { it.x + it.width }
        assertEquals(lines.first().boxRight, end, 1.5)
    }

    @Test
    fun `center shifts right when the first line is indented`() {
        fun origin(indent: Double): Double {
            val doc = page(595.0)
            doc.loadBlocks(listOf(Paragraph("Коротко", Align.CENTER).also { it.firstLineIndentPt = indent }))
            return Layout.build(doc).pages.first().lines.first().x
        }
        assertTrue(origin(80.0) > origin(0.0) + 20.0)
    }

    @Test
    fun `right aligned line ends on the right margin`() {
        val doc = page(595.0)
        doc.marginRightPt = 40.0
        doc.loadBlocks(listOf(Paragraph("Край", Align.RIGHT)))
        val line = Layout.build(doc).pages.first().lines.first()
        assertEquals(doc.pageWidthPt - doc.marginRightPt, line.x + line.width, 1.5)
    }

    @Test
    fun `tab advances to the next half-inch stop`() {
        val doc = page(595.0)
        doc.marginLeftPt = 56.0
        doc.loadBlocks(listOf(Paragraph("А\tБ")))
        val letter = Layout.spans(Layout.build(doc).pages.first().lines.first()).first { it.text == "Б" }
        assertTrue(letter.x >= 56.0 + Layout.defaultTabPt - 0.2, "x=${letter.x}")
    }

    @Test
    fun `caret place counts wrapped lines from the start of the document`() {
        val doc = page(280.0)
        doc.loadBlocks(listOf(Paragraph(List(18) { "слово" }.joinToString(" "))))
        val layout = Layout.build(doc)
        assertTrue(layout.pages.first().lines.size >= 2)
        assertEquals(CaretPlace(1, layout.pages.size, 1, 1), Layout.caretPlace(layout, 0, 0))
        val second = layout.pages.first().lines[1]
        assertEquals(
            CaretPlace(1, layout.pages.size, 2, 2),
            Layout.caretPlace(layout, second.paragraphIndex, second.startInParagraph + 1)
        )
    }

    private fun page(width: Double) = Document().also {
        it.pageWidthPt = width
        it.pageHeightPt = 900.0
        it.marginLeftPt = 36.0
        it.marginRightPt = 36.0
        it.marginTopPt = 36.0
        it.marginBottomPt = 36.0
    }
}
