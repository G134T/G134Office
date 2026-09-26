package org.example.engine

import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.Text

data class LaidLine(
    val text: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val paragraphIndex: Int,
    val startInParagraph: Int = 0,
    val align: Align,
    val fontFamily: String,
    val fontSize: Double,
    val bold: Boolean,
    val runs: List<TextRun> = emptyList()
)

data class LaidPage(
    val index: Int,
    val lines: List<LaidLine>,
    val objects: List<LaidObject> = emptyList()
)

data class LaidObject(val block: DocumentBlock, val x: Double, val y: Double, val width: Double, val height: Double)

data class LayoutResult(
    val pages: List<LaidPage>,
    val pageWidth: Double,
    val pageHeight: Double
)

object Layout {

    fun build(doc: Document): LayoutResult {
        val probe = Text()
        val contentW = (doc.pageWidthPt - doc.marginLeftPt - doc.marginRightPt).coerceAtLeast(40.0)
        val pages = mutableListOf<MutableList<LaidLine>>()
        val objectPages = mutableListOf<MutableList<LaidObject>>()
        var pageLines = mutableListOf<LaidLine>()
        var pageObjects = mutableListOf<LaidObject>()
        var y = doc.marginTopPt

        fun flushPage() {
            pages += pageLines
            objectPages += pageObjects
            pageLines = mutableListOf()
            pageObjects = mutableListOf()
            y = doc.marginTopPt
        }

        var pIndex = 0
        doc.blocks.forEach { block ->
            if (block is TableBlock) {
                val columns = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                val cellWidth = contentW / columns
                block.rows.forEach { row ->
                    val rowHeight = row.maxOfOrNull { cell ->
                        val lines = cell.paragraphs.sumOf { paragraph ->
                            val font = fontOf(paragraph)
                            wrap(paragraph.text, font, (cellWidth - 10).coerceAtLeast(20.0), probe).size
                        }
                        (lines.coerceAtLeast(1) * 18.0 + 12.0)
                    } ?: 34.0
                    if (y + rowHeight > doc.pageHeightPt - doc.marginBottomPt &&
                        (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) flushPage()
                    pageObjects += LaidObject(TableBlock(mutableListOf(row)), doc.marginLeftPt, y, contentW, rowHeight)
                    y += rowHeight
                }
                y += 10.0
                return@forEach
            }
            if (block is ImageBlock) {
                val scale = minOf(1.0, contentW / block.widthPt.coerceAtLeast(1.0),
                    (doc.pageHeightPt - doc.marginTopPt - doc.marginBottomPt) / block.heightPt.coerceAtLeast(1.0))
                val width = block.widthPt * scale
                val height = block.heightPt * scale
                if (y + height > doc.pageHeightPt - doc.marginBottomPt) flushPage()
                pageObjects += LaidObject(block, doc.marginLeftPt, y, width, height)
                y += height + 10.0
                return@forEach
            }
            val para = block as Paragraph
            if (para.pageBreakBefore && (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) flushPage()
            y += para.spacingBeforePt.coerceAtLeast(0.0)
            val font = fontOf(para)
            val lineH = measureHeight(font, probe).coerceAtLeast(para.fontSize * 1.25) * para.lineSpacing.coerceIn(0.8, 3.0)
            val available = (contentW - para.leftIndentPt - para.rightIndentPt).coerceAtLeast(40.0)
            val chunks = wrap(para.text, font, (available - para.firstLineIndentPt).coerceAtLeast(40.0), probe)
            chunks.forEachIndexed { lineIndex, wrapped ->
                if (y + lineH > doc.pageHeightPt - doc.marginBottomPt) flushPage()
                val chunk = wrapped.text
                val w = measureWidth(chunk, font, probe)
                val start = wrapped.start
                var runOffset = 0
                val visibleRuns = para.runs.mapNotNull { run ->
                    val a = (start - runOffset).coerceIn(0, run.text.length)
                    val b = (start + chunk.length - runOffset).coerceIn(a, run.text.length)
                    runOffset += run.text.length
                    run.copy(text = run.text.substring(a, b)).takeIf { it.text.isNotEmpty() }
                }
                val x = when (para.align) {
                    Align.LEFT, Align.JUSTIFY -> doc.marginLeftPt + para.leftIndentPt + if (lineIndex == 0) para.firstLineIndentPt else 0.0
                    Align.CENTER -> doc.marginLeftPt + para.leftIndentPt + (available - w) / 2.0
                    Align.RIGHT -> doc.marginLeftPt + para.leftIndentPt + available - w
                }
                pageLines += LaidLine(
                    text = chunk,
                    x = x,
                    y = y,
                    width = w,
                    height = lineH,
                    paragraphIndex = pIndex,
                    startInParagraph = start,
                    align = para.align,
                    fontFamily = para.fontFamily,
                    fontSize = para.fontSize,
                    bold = para.bold,
                    runs = visibleRuns
                )
                y += lineH
            }
            y += para.spacingAfterPt.coerceAtLeast(0.0)
            pIndex++
        }
        if (pageLines.isNotEmpty() || pageObjects.isNotEmpty() || pages.isEmpty()) {
            pages += pageLines
            objectPages += pageObjects
        }
        return LayoutResult(
            pages = pages.mapIndexed { i, lines -> LaidPage(i, lines, objectPages[i]) },
            pageWidth = doc.pageWidthPt,
            pageHeight = doc.pageHeightPt
        )
    }

    private data class WrappedText(val text: String, val start: Int)

    private fun wrap(text: String, font: Font, maxW: Double, probe: Text): List<WrappedText> {
        val lines = mutableListOf<WrappedText>()
        var segmentStart = 0
        text.split('\n').forEach { segment ->
            var current = StringBuilder()
            var lineStart = segmentStart
            Regex("\\S+|\\s+").findAll(segment).forEach { match ->
                val token = match.value
                val absoluteStart = segmentStart + match.range.first
                if (current.isNotEmpty() && measureWidth(current.toString() + token, font, probe) > maxW) {
                    lines += WrappedText(current.toString(), lineStart)
                    current = StringBuilder()
                    lineStart = absoluteStart
                }
                if (measureWidth(token, font, probe) > maxW) {
                    token.forEachIndexed { index, char ->
                        if (current.isNotEmpty() && measureWidth(current.toString() + char, font, probe) > maxW) {
                            lines += WrappedText(current.toString(), lineStart)
                            current = StringBuilder()
                            lineStart = absoluteStart + index
                        }
                        current.append(char)
                    }
                } else current.append(token)
            }
            lines += WrappedText(current.toString(), lineStart)
            segmentStart += segment.length + 1
        }
        return lines
    }

    private fun fontOf(para: Paragraph): Font {
        val weight = if (para.bold) FontWeight.BOLD else FontWeight.NORMAL
        return Font.font(para.fontFamily, weight, para.fontSize)
    }

    private fun measureWidth(s: String, font: Font, probe: Text): Double {
        probe.font = font
        probe.text = s.ifEmpty { " " }
        return probe.layoutBounds.width
    }

    private fun measureHeight(font: Font, probe: Text): Double {
        probe.font = font
        probe.text = "Hg"
        return probe.layoutBounds.height
    }
}
