package org.example.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/** Линейка и сетка в духе OpenOffice Writer: см, поля, отступы, привязка. */
object Guides {
    const val ptPerMm = 72.0 / 25.4
    const val ptPerCm = ptPerMm * 10.0
    const val rulerSize = 24.0
    const val gridStepCm = 0.5
    const val snapStepCm = gridStepCm
    const val hitSlop = 7.0

    enum class Handle {
        NONE, LEFT_MARGIN, RIGHT_MARGIN, TOP_MARGIN, BOTTOM_MARGIN,
        LEFT_INDENT, FIRST_LINE, RIGHT_INDENT
    }

    data class Marks(
        val leftMargin: Double,
        val rightMargin: Double,
        val topMargin: Double,
        val bottomMargin: Double,
        val leftIndent: Double,
        val firstLine: Double,
        val rightIndent: Double
    )

    fun marks(document: Document, paragraph: Paragraph?): Marks {
        val left = document.marginLeftPt
        val right = document.pageWidthPt - document.marginRightPt
        val top = document.marginTopPt
        val bottom = document.pageHeightPt - document.marginBottomPt
        val leftIndent = left + (paragraph?.leftIndentPt ?: 0.0)
        val first = leftIndent + (paragraph?.firstLineIndentPt ?: 0.0)
        val rightIndent = right - (paragraph?.rightIndentPt ?: 0.0)
        return Marks(left, right, top, bottom, leftIndent, first, rightIndent)
    }

    fun snap(pt: Double, enabled: Boolean): Double {
        if (!enabled) return pt
        val step = snapStepCm * ptPerCm
        return (pt / step).roundToInt() * step
    }

    fun hit(
        x: Double,
        y: Double,
        ox: Double,
        oy: Double,
        pageWidth: Double,
        pageHeight: Double,
        marks: Marks
    ): Handle {
        val onH = y in (oy - rulerSize)..oy + 1.0 && x in (ox - 2)..(ox + pageWidth + 2)
        val onV = x in (ox - rulerSize)..ox + 1.0 && y in (oy - 2)..(oy + pageHeight + 2)
        if (!onH && !onV) return Handle.NONE
        if (onH) {
            val px = x - ox
            val lane = y - (oy - rulerSize)
            if (lane < 7.0) {
                nearest(px, marks.leftMargin, Handle.LEFT_MARGIN, true)?.let { return it }
                nearest(px, marks.rightMargin, Handle.RIGHT_MARGIN, true)?.let { return it }
            } else if (lane < 15.0) {
                nearest(px, marks.firstLine, Handle.FIRST_LINE, true)?.let { return it }
            } else {
                nearest(px, marks.leftIndent, Handle.LEFT_INDENT, true)?.let { return it }
                nearest(px, marks.rightIndent, Handle.RIGHT_INDENT, true)?.let { return it }
            }
            return Handle.NONE
        }
        val py = y - oy
        nearest(py, marks.topMargin, Handle.TOP_MARGIN, true)?.let { return it }
        nearest(py, marks.bottomMargin, Handle.BOTTOM_MARGIN, true)?.let { return it }
        return Handle.NONE
    }

    fun apply(
        handle: Handle,
        pageX: Double,
        pageY: Double,
        document: Document,
        paragraphs: List<Paragraph>,
        snapToGrid: Boolean
    ) {
        val minContent = 72.0
        when (handle) {
            Handle.LEFT_MARGIN -> {
                val next = snap(pageX, snapToGrid).coerceIn(18.0, document.pageWidthPt - document.marginRightPt - minContent)
                document.marginLeftPt = next
            }
            Handle.RIGHT_MARGIN -> {
                val next = (document.pageWidthPt - snap(pageX, snapToGrid))
                    .coerceIn(18.0, document.pageWidthPt - document.marginLeftPt - minContent)
                document.marginRightPt = next
            }
            Handle.TOP_MARGIN -> {
                val next = snap(pageY, snapToGrid).coerceIn(18.0, document.pageHeightPt - document.marginBottomPt - minContent)
                document.marginTopPt = next
            }
            Handle.BOTTOM_MARGIN -> {
                val next = (document.pageHeightPt - snap(pageY, snapToGrid))
                    .coerceIn(18.0, document.pageHeightPt - document.marginTopPt - minContent)
                document.marginBottomPt = next
            }
            Handle.LEFT_INDENT -> paragraphs.forEach { paragraph ->
                val maxIndent = (document.pageWidthPt - document.marginLeftPt - document.marginRightPt - paragraph.rightIndentPt - 40.0)
                    .coerceAtLeast(0.0)
                val next = snap(pageX - document.marginLeftPt, snapToGrid).coerceIn(0.0, maxIndent)
                paragraph.leftIndentPt = next
            }
            Handle.FIRST_LINE -> paragraphs.forEach { paragraph ->
                val maxIndent = (document.pageWidthPt - document.marginLeftPt - document.marginRightPt -
                    paragraph.leftIndentPt - paragraph.rightIndentPt - 40.0).coerceAtLeast(0.0)
                val next = snap(pageX - document.marginLeftPt - paragraph.leftIndentPt, snapToGrid)
                    .coerceIn(-paragraph.leftIndentPt, maxIndent)
                paragraph.firstLineIndentPt = next
            }
            Handle.RIGHT_INDENT -> paragraphs.forEach { paragraph ->
                val maxIndent = (document.pageWidthPt - document.marginLeftPt - document.marginRightPt - paragraph.leftIndentPt - 40.0)
                    .coerceAtLeast(0.0)
                val next = (document.pageWidthPt - document.marginRightPt - snap(pageX, snapToGrid))
                    .coerceIn(0.0, maxIndent)
                paragraph.rightIndentPt = next
            }
            Handle.NONE -> Unit
        }
    }

    fun stepIndent(document: Document, paragraphs: List<Paragraph>, increase: Boolean, snapToGrid: Boolean) {
        val step = gridStepCm * ptPerCm
        paragraphs.forEach { paragraph ->
            val maxIndent = (document.pageWidthPt - document.marginLeftPt - document.marginRightPt -
                paragraph.rightIndentPt - 40.0).coerceAtLeast(0.0)
            val next = paragraph.leftIndentPt + if (increase) step else -step
            paragraph.leftIndentPt = snap(next, snapToGrid).coerceIn(0.0, maxIndent)
        }
    }

    fun isHorizontal(handle: Handle) = handle == Handle.LEFT_MARGIN || handle == Handle.RIGHT_MARGIN ||
        handle == Handle.LEFT_INDENT || handle == Handle.FIRST_LINE || handle == Handle.RIGHT_INDENT

    fun isVertical(handle: Handle) = handle == Handle.TOP_MARGIN || handle == Handle.BOTTOM_MARGIN

    private fun nearest(value: Double, target: Double, handle: Handle, allowed: Boolean): Handle? {
        if (!allowed) return null
        return handle.takeIf { abs(value - target) <= hitSlop }
    }
}
