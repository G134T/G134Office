package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidesTest {
    @Test
    fun `snap uses visible half-centimeter grid steps`() {
        val step = Guides.snapStepCm * Guides.ptPerCm
        assertEquals(1.0, Guides.snap(1.0, false), 0.001)
        assertEquals(step, Guides.snap(step * 0.6, true), 0.2)
        assertEquals(0.0, Guides.snap(step * 0.2, true), 0.2)
    }

    @Test
    fun `left indent drag moves paragraph and first line together without changing other paragraphs`() {
        val document = Document()
        val first = Paragraph("Первый").also { it.leftIndentPt = 10.0; it.firstLineIndentPt = 20.0 }
        val second = Paragraph("Второй").also { it.leftIndentPt = 0.0 }
        document.loadBlocks(listOf(first, second))
        val pageX = document.marginLeftPt + 28.0
        Guides.apply(Guides.Handle.LEFT_INDENT, pageX, 0.0, document, listOf(first), snapToGrid = false)
        assertEquals(28.0, first.leftIndentPt, 0.2)
        assertEquals(20.0, first.firstLineIndentPt, 0.2)
        assertEquals(0.0, second.leftIndentPt, 0.001)
    }

    @Test
    fun `margin handles stay inside the page`() {
        val document = Document()
        Guides.apply(Guides.Handle.LEFT_MARGIN, 400.0, 0.0, document, emptyList(), false)
        assertTrue(document.marginLeftPt >= 18.0)
        assertTrue(document.marginLeftPt + document.marginRightPt < document.pageWidthPt)
    }

    @Test
    fun `hit detects first-line marker on the top half of the ruler`() {
        val document = Document()
        val paragraph = Paragraph("Текст").also { it.leftIndentPt = 12.0; it.firstLineIndentPt = 18.0 }
        val marks = Guides.marks(document, paragraph)
        val ox = 40.0
        val oy = 40.0
        val handle = Guides.hit(
            ox + marks.firstLine, oy - Guides.rulerSize + 10.0,
            ox, oy, document.pageWidthPt, document.pageHeightPt, marks
        )
        assertEquals(Guides.Handle.FIRST_LINE, handle)
    }

    @Test
    fun `overlapping margin and indent markers use separate ruler lanes`() {
        val document = Document()
        val marks = Guides.marks(document, document.paragraphs.first())
        val ox = 40.0
        val oy = 40.0
        assertEquals(Guides.Handle.LEFT_MARGIN, Guides.hit(
            ox + marks.leftMargin, oy - Guides.rulerSize + 3.0,
            ox, oy, document.pageWidthPt, document.pageHeightPt, marks
        ))
        assertEquals(Guides.Handle.LEFT_INDENT, Guides.hit(
            ox + marks.leftIndent, oy - 3.0,
            ox, oy, document.pageWidthPt, document.pageHeightPt, marks
        ))
    }

    @Test
    fun `indent command changes paragraph geometry and keeps text intact`() {
        val document = Document()
        val paragraph = document.paragraphs.first()
        paragraph.text = "Текст"
        Guides.stepIndent(document, listOf(paragraph), true, true)
        assertEquals(Guides.gridStepCm * Guides.ptPerCm, paragraph.leftIndentPt, 0.01)
        assertEquals("Текст", paragraph.text)
        Guides.stepIndent(document, listOf(paragraph), false, true)
        assertEquals(0.0, paragraph.leftIndentPt, 0.01)
    }

    @Test
    fun `right margin snaps to visible grid measured from page left`() {
        val document = Document()
        val step = Guides.gridStepCm * Guides.ptPerCm
        Guides.apply(Guides.Handle.RIGHT_MARGIN, step * 30.6, 0.0, document, emptyList(), true)
        assertEquals(step * 31, document.pageWidthPt - document.marginRightPt, 0.01)
    }
}
