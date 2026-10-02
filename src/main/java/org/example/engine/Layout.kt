package org.example.engine

import javafx.scene.text.Font
import javafx.scene.text.FontPosture
import javafx.scene.text.FontWeight
import javafx.scene.text.Text
import kotlin.math.floor

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
    val runs: List<TextRun> = emptyList(),
    /** Left page margin. Default tab stops are measured from here, as in Word and OpenOffice. */
    val tabOrigin: Double = 0.0,
    /** Right edge of the line box. Justification stretches spaces up to this x. */
    val boxRight: Double = 0.0,
    /** True only for a wrapped line that is not the last line of its paragraph. */
    val justify: Boolean = false
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

data class PlacedSpan(
    val text: String,
    val x: Double,
    val width: Double,
    val fontFamily: String,
    val fontSize: Double,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val color: String?,
    val startInLine: Int
)

data class CaretPlace(val page: Int, val pages: Int, val line: Int, val column: Int)

object Layout {
    /** Word's default tab interval is half an inch. OpenOffice uses 1.25 cm, which is the same step. */
    const val defaultTabPt = 36.0

    private val probe = Text()

    fun nextTab(xFromOrigin: Double, interval: Double = defaultTabPt): Double {
        val step = interval.coerceAtLeast(1.0)
        if (xFromOrigin <= 0.0) return step
        return (floor(xFromOrigin / step + 1e-6) + 1.0) * step
    }

    /** Page, wrapped line and column under the caret. All numbers are 1-based. */
    fun caretPlace(result: LayoutResult, paragraph: Int, char: Int): CaretPlace {
        val pages = result.pages.size.coerceAtLeast(1)
        var lineNo = 0
        var page = 1
        var line = 1
        var column = (char + 1).coerceAtLeast(1)
        result.pages.forEach { laid ->
            laid.lines.forEach { row ->
                lineNo++
                val start = row.startInParagraph
                val end = start + row.text.length
                if (row.paragraphIndex == paragraph && char >= start && char <= end) {
                    page = laid.index + 1
                    line = lineNo
                    column = (char - start + 1).coerceAtLeast(1)
                }
            }
        }
        return CaretPlace(page, pages, line.coerceAtLeast(1), column)
    }

    fun build(doc: Document): LayoutResult {
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
        doc.blocks.forEachIndexed { blockIndex, block ->
            if (block is TableBlock) {
                val columns = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                val cellWidth = contentW / columns
                block.rows.forEach { row ->
                    val rowHeight = row.maxOfOrNull { cell ->
                        val lines = cell.paragraphs.sumOf { paragraph ->
                            val font = fontOf(paragraph)
                            wrapPlain(paragraph.text, font, (cellWidth - 10).coerceAtLeast(20.0)).size
                        }
                        (lines.coerceAtLeast(1) * 18.0 + 12.0)
                    } ?: 34.0
                    if (y + rowHeight > doc.pageHeightPt - doc.marginBottomPt &&
                        (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) flushPage()
                    pageObjects += LaidObject(TableBlock(mutableListOf(row)), doc.marginLeftPt, y, contentW, rowHeight)
                    y += rowHeight
                }
                y += 10.0
                return@forEachIndexed
            }
            if (block is ImageBlock) {
                val scale = minOf(1.0, contentW / block.widthPt.coerceAtLeast(1.0),
                    (doc.pageHeightPt - doc.marginTopPt - doc.marginBottomPt) / block.heightPt.coerceAtLeast(1.0))
                val width = block.widthPt * scale
                val height = block.heightPt * scale
                if (y + height > doc.pageHeightPt - doc.marginBottomPt) flushPage()
                pageObjects += LaidObject(block, doc.marginLeftPt, y, width, height)
                y += height + 10.0
                return@forEachIndexed
            }
            val para = block as Paragraph
            if (para.pageBreakBefore && (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) flushPage()
            y += para.spacingBeforePt.coerceAtLeast(0.0)
            val built = paragraphLines(para, pIndex, doc.marginLeftPt, contentW)
            val paragraphHeight = built.sumOf { it.height }
            val pageBottom = doc.pageHeightPt - doc.marginBottomPt
            val nextParagraph = doc.blocks.getOrNull(blockIndex + 1) as? Paragraph
            val nextHeight = if (para.keepWithNext && nextParagraph != null) {
                nextParagraph.spacingBeforePt.coerceAtLeast(0.0) +
                    paragraphLines(nextParagraph, pIndex + 1, doc.marginLeftPt, contentW).sumOf { it.height }
            } else 0.0
            val keepGroupHeight = paragraphHeight + nextHeight + para.spacingAfterPt.coerceAtLeast(0.0)
            if (para.keepWithNext && nextParagraph != null && keepGroupHeight <= pageBottom - doc.marginTopPt &&
                y + keepGroupHeight > pageBottom && (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) {
                flushPage()
            }
            if (para.keepTogether && paragraphHeight <= pageBottom - doc.marginTopPt &&
                y + paragraphHeight > pageBottom && (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) {
                flushPage()
            }
            built.forEach { line ->
                if (y + line.height > doc.pageHeightPt - doc.marginBottomPt &&
                    (pageLines.isNotEmpty() || pageObjects.isNotEmpty())) flushPage()
                pageLines += line.copy(y = y)
                y += line.height
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

    fun spans(line: LaidLine): List<PlacedSpan> {
        if (line.runs.isEmpty() && line.text.isEmpty()) return emptyList()
        val source = line.runs.ifEmpty { listOf(TextRun(line.text, bold = line.bold)) }
        if (!line.justify) return place(source, line, 0.0)
        val natural = place(source, line, 0.0)
        val end = natural.lastOrNull()?.let { it.x + it.width } ?: line.x
        val gaps = expandableSpaces(source.joinToString("") { it.text })
        val extra = if (gaps > 0) (line.boxRight - end).coerceAtLeast(0.0) / gaps else 0.0
        return if (extra <= 0.01) natural else place(source, line, extra)
    }

    /** Page x of a caret inside the line. `localChars` is an offset from the line start. */
    fun prefixX(line: LaidLine, localChars: Int): Double {
        if (localChars <= 0) return line.x
        val placed = spans(line)
        var covered = 0
        for (span in placed) {
            val count = span.text.length
            if (count == 0) continue
            if (localChars < covered + count) {
                val inside = localChars - covered
                if (span.text == "\t" || span.text.all { it == ' ' }) {
                    return span.x + span.width * inside / count.toDouble()
                }
                return span.x + measureWidth(span.text.take(inside), fontOf(span))
            }
            covered += count
            if (localChars == covered) return span.x + span.width
        }
        return placed.lastOrNull()?.let { it.x + it.width } ?: line.x
    }

    private data class Atom(val text: String, val style: TextRun, val start: Int, val kind: Kind)
    private enum class Kind { WORD, SPACE, TAB }

    private fun paragraphLines(para: Paragraph, paragraphIndex: Int, marginLeft: Double, contentW: Double): List<LaidLine> {
        val left = marginLeft + para.leftIndentPt
        val body = (contentW - para.leftIndentPt - para.rightIndentPt).coerceAtLeast(40.0)
        val rightEdge = left + body
        val segments = segmentsOf(para)
        val lines = mutableListOf<LaidLine>()
        segments.forEachIndexed { segmentIndex, segment ->
            val wrapped = wrapSegment(
                segment.atoms, segment.start, para, segmentIndex == 0, left, rightEdge, marginLeft
            )
            wrapped.forEachIndexed { index, draft ->
                val lastOfSegment = index == wrapped.lastIndex
                val origin = draft.origin
                val natural = draft.natural
                val hasTab = draft.text.contains('\t')
                val gaps = expandableSpaces(draft.text)
                val justify = para.align == Align.JUSTIFY && !lastOfSegment && !hasTab && gaps > 0 &&
                    natural < draft.box - 0.75
                val x = when {
                    hasTab || para.align == Align.LEFT || para.align == Align.JUSTIFY -> origin
                    para.align == Align.CENTER -> origin + (draft.box - natural) / 2.0
                    para.align == Align.RIGHT -> origin + draft.box - natural
                    else -> origin
                }
                lines += LaidLine(
                    text = draft.text,
                    x = x,
                    y = 0.0,
                    width = if (justify) draft.box else natural,
                    height = draft.height,
                    paragraphIndex = paragraphIndex,
                    startInParagraph = draft.start,
                    align = para.align,
                    fontFamily = para.fontFamily,
                    fontSize = para.fontSize,
                    bold = para.bold,
                    runs = draft.runs,
                    tabOrigin = marginLeft,
                    boxRight = origin + draft.box,
                    justify = justify
                )
            }
        }
        return lines
    }

    private data class Draft(
        val text: String,
        val start: Int,
        val runs: List<TextRun>,
        val origin: Double,
        val box: Double,
        val natural: Double,
        val height: Double
    )

    private data class Segment(val atoms: List<Atom>, val start: Int)

    private fun wrapSegment(
        segment: List<Atom>,
        segmentStart: Int,
        para: Paragraph,
        firstSegment: Boolean,
        left: Double,
        rightEdge: Double,
        marginLeft: Double
    ): List<Draft> {
        if (segment.isEmpty()) {
            val origin = (if (firstSegment) left + para.firstLineIndentPt else left).coerceAtLeast(4.0)
            val box = (rightEdge - origin).coerceAtLeast(20.0)
            return listOf(Draft("", segmentStart, emptyList(), origin, box, 0.0, lineHeight(emptyList(), para)))
        }
        val lines = mutableListOf<MutableList<Atom>>()
        var current = mutableListOf<Atom>()
        var lineIndex = 0
        fun metrics(index: Int): Pair<Double, Double> {
            val first = firstSegment && index == 0
            val origin = (if (first) left + para.firstLineIndentPt else left).coerceAtLeast(4.0)
            return origin to (rightEdge - origin).coerceAtLeast(20.0)
        }
        fun flush() {
            if (current.isEmpty()) return
            lines += current
            current = mutableListOf()
            lineIndex++
        }
        fun fits(atom: Atom): Boolean {
            val (origin, box) = metrics(lineIndex)
            val startX = if (current.isEmpty()) origin else origin + widthOf(current, origin, marginLeft, para)
            if (atom.kind == Kind.SPACE && current.isNotEmpty()) return true
            val width = atomWidth(atom, startX, marginLeft, para)
            if (current.isEmpty()) return atom.text.length <= 1 || width <= box + 0.4
            return startX + width <= origin + box + 0.4
        }
        segment.forEach { atom ->
            if (!fits(atom) && current.isNotEmpty() && atom.kind != Kind.SPACE) flush()
            if (atom.kind == Kind.WORD && atom.text.length > 1 && !fits(atom)) {
                atom.text.forEachIndexed { offset, ch ->
                    val piece = atom.copy(text = ch.toString(), start = atom.start + offset)
                    if (!fits(piece) && current.isNotEmpty()) flush()
                    current += piece
                }
            } else current += atom
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines += current
        return lines.mapIndexed { index, atoms ->
            val (origin, box) = metrics(index)
            val text = atoms.joinToString("") { it.text }
            val start = atoms.firstOrNull()?.start ?: segmentStart
            val runs = runsOf(atoms)
            Draft(text, start, runs, origin, box, widthOf(atoms, origin, marginLeft, para), lineHeight(runs, para))
        }
    }

    private fun segmentsOf(para: Paragraph): List<Segment> {
        val source = para.runs.ifEmpty {
            if (para.text.isEmpty()) emptyList() else listOf(TextRun(para.text, bold = para.bold))
        }
        if (source.isEmpty()) return listOf(Segment(emptyList(), 0))
        val segments = mutableListOf<Segment>()
        var current = mutableListOf<Atom>()
        var absolute = 0
        var segmentStart = 0
        fun push() {
            segments += Segment(current, current.firstOrNull()?.start ?: segmentStart)
            current = mutableListOf()
        }
        source.forEach { run ->
            var i = 0
            while (i < run.text.length) {
                val ch = run.text[i]
                if (ch == '\n') {
                    push()
                    i++
                    absolute++
                    segmentStart = absolute
                    continue
                }
                val start = absolute
                val kind = when {
                    ch == '\t' -> Kind.TAB
                    ch == ' ' -> Kind.SPACE
                    else -> Kind.WORD
                }
                val buf = StringBuilder()
                if (kind == Kind.TAB) {
                    buf.append(ch)
                    i++
                    absolute++
                } else {
                    while (i < run.text.length) {
                        val next = run.text[i]
                        if (next == '\n' || next == '\t') break
                        val nextKind = if (next == ' ') Kind.SPACE else Kind.WORD
                        if (nextKind != kind) break
                        buf.append(next)
                        i++
                        absolute++
                    }
                }
                current += Atom(buf.toString(), run, start, kind)
            }
        }
        push()
        return segments
    }

    private fun runsOf(atoms: List<Atom>): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        atoms.forEach { atom ->
            val run = atom.style.copy(text = atom.text)
            val prev = runs.lastOrNull()
            if (prev != null && ParagraphFormatting.sameStyle(prev, run)) runs[runs.lastIndex] = prev.copy(text = prev.text + run.text)
            else runs += run
        }
        return runs
    }

    private fun widthOf(atoms: List<Atom>, origin: Double, tabOrigin: Double, para: Paragraph): Double {
        var x = origin
        atoms.forEach { atom -> x += atomWidth(atom, x, tabOrigin, para) }
        return x - origin
    }

    private fun atomWidth(atom: Atom, x: Double, tabOrigin: Double, para: Paragraph): Double {
        if (atom.kind == Kind.TAB) {
            return (tabOrigin + nextTab(x - tabOrigin) - x).coerceAtLeast(0.0)
        }
        return measureWidth(atom.text, fontFor(atom.style, para))
    }

    private fun place(runs: List<TextRun>, line: LaidLine, extraPerSpace: Double): List<PlacedSpan> {
        val text = runs.joinToString("") { it.text }
        val lastContent = text.indexOfLast { it != ' ' }
        val out = mutableListOf<PlacedSpan>()
        var x = line.x
        var index = 0
        runs.forEach { run ->
            var i = 0
            while (i < run.text.length) {
                val ch = run.text[i]
                val start = index
                if (ch == '\t') {
                    val width = (line.tabOrigin + nextTab(x - line.tabOrigin) - x).coerceAtLeast(0.0)
                    out += span(run, "\t", x, width, line, start)
                    x += width
                    i++
                    index++
                    continue
                }
                val space = ch == ' '
                val buf = StringBuilder()
                while (i < run.text.length) {
                    val next = run.text[i]
                    if (next == '\t' || (next == ' ') != space) break
                    buf.append(next)
                    i++
                    index++
                }
                val chunk = buf.toString()
                val font = fontFor(run, line)
                var width = measureWidth(chunk, font)
                if (space && extraPerSpace > 0.0) {
                    val counted = chunk.indices.count { start + it <= lastContent }
                    width += extraPerSpace * counted
                }
                out += span(run, chunk, x, width, line, start)
                x += width
            }
        }
        return out
    }

    private fun span(run: TextRun, text: String, x: Double, width: Double, line: LaidLine, start: Int) = PlacedSpan(
        text = text,
        x = x,
        width = width,
        fontFamily = run.fontFamily ?: line.fontFamily,
        fontSize = run.fontSize ?: line.fontSize,
        bold = run.bold || line.bold,
        italic = run.italic,
        underline = run.underline,
        strikethrough = run.strikethrough,
        color = run.color,
        startInLine = start
    )

    private fun expandableSpaces(text: String): Int {
        val last = text.indexOfLast { it != ' ' }
        if (last < 0) return 0
        return text.take(last + 1).count { it == ' ' }
    }

    private fun lineHeight(runs: List<TextRun>, para: Paragraph): Double {
        val font = runs.maxByOrNull { it.fontSize ?: para.fontSize }?.let { fontFor(it, para) } ?: fontOf(para)
        val size = runs.maxOfOrNull { it.fontSize ?: para.fontSize } ?: para.fontSize
        return measureHeight(font).coerceAtLeast(size * 1.15) * para.lineSpacing.coerceIn(0.8, 3.0)
    }

    private data class WrappedText(val text: String, val start: Int)

    private fun wrapPlain(text: String, font: Font, maxW: Double): List<WrappedText> {
        val lines = mutableListOf<WrappedText>()
        var segmentStart = 0
        text.split('\n').forEach { segment ->
            var current = StringBuilder()
            var lineStart = segmentStart
            Regex("\\S+|\\s+").findAll(segment).forEach { match ->
                val token = match.value
                val absoluteStart = segmentStart + match.range.first
                if (current.isNotEmpty() && measureWidth(current.toString() + token, font) > maxW) {
                    lines += WrappedText(current.toString(), lineStart)
                    current = StringBuilder()
                    lineStart = absoluteStart
                }
                if (measureWidth(token, font) > maxW) {
                    token.forEachIndexed { index, char ->
                        if (current.isNotEmpty() && measureWidth(current.toString() + char, font) > maxW) {
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

    private fun fontOf(para: Paragraph): Font = fontFor(TextRun("", bold = para.bold), para)

    private fun fontFor(run: TextRun, para: Paragraph): Font = Font.font(
        run.fontFamily ?: para.fontFamily,
        if (run.bold || para.bold) FontWeight.BOLD else FontWeight.NORMAL,
        if (run.italic) FontPosture.ITALIC else FontPosture.REGULAR,
        run.fontSize ?: para.fontSize
    )

    private fun fontFor(run: TextRun, line: LaidLine): Font = Font.font(
        run.fontFamily ?: line.fontFamily,
        if (run.bold || line.bold) FontWeight.BOLD else FontWeight.NORMAL,
        if (run.italic) FontPosture.ITALIC else FontPosture.REGULAR,
        run.fontSize ?: line.fontSize
    )

    private fun fontOf(span: PlacedSpan): Font = Font.font(
        span.fontFamily,
        if (span.bold) FontWeight.BOLD else FontWeight.NORMAL,
        if (span.italic) FontPosture.ITALIC else FontPosture.REGULAR,
        span.fontSize
    )

    private fun measureWidth(s: String, font: Font): Double {
        if (s.isEmpty()) return 0.0
        probe.font = font
        probe.wrappingWidth = 0.0
        probe.text = s
        return probe.layoutBounds.width
    }

    private fun measureHeight(font: Font): Double {
        probe.font = font
        probe.wrappingWidth = 0.0
        probe.text = "Hg"
        return probe.layoutBounds.height
    }
}
