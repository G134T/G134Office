package org.example.engine

enum class Align { LEFT, CENTER, RIGHT, JUSTIFY }

enum class Paper(val title: String, val widthPt: Double, val heightPt: Double) {
    A5("A5 (148×210 мм)", 420.0, 595.0), A4("A4 (210×297 мм)", 595.0, 842.0),
    A3("A3 (297×420 мм)", 842.0, 1191.0), LETTER("Letter (216×279 мм)", 612.0, 792.0),
    LEGAL("Legal (216×356 мм)", 612.0, 1008.0)
}

sealed interface DocumentBlock

data class TextRun(
    val text: String,
    val fontFamily: String? = null,
    val fontSize: Double? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val color: String? = null,
    val strikethrough: Boolean = false
)

class Paragraph(
    text: String = "",
    var align: Align = Align.LEFT,
    var fontFamily: String = "Segoe UI",
    var fontSize: Double = 16.0,
    var bold: Boolean = false
) : DocumentBlock {
    var leftIndentPt: Double = 0.0
    var rightIndentPt: Double = 0.0
    var firstLineIndentPt: Double = 0.0
    var spacingBeforePt: Double = 0.0
    var spacingAfterPt: Double = 0.0
    var lineSpacing: Double = 1.0
    var pageBreakBefore: Boolean = false
    var text: String = text
        set(value) {
            field = value
            runs.clear()
            if (value.isNotEmpty()) runs += TextRun(value)
        }
    val runs: MutableList<TextRun> = mutableListOf<TextRun>().also {
        if (text.isNotEmpty()) it += TextRun(text)
    }

    fun setRuns(items: List<TextRun>) {
        text = items.joinToString("") { it.text }
        runs.clear()
        runs.addAll(items)
    }
}

data class TableCell(val paragraphs: MutableList<Paragraph> = mutableListOf(Paragraph()))
data class TableBlock(val rows: MutableList<MutableList<TableCell>>) : DocumentBlock
data class ImageBlock(
    val bytes: ByteArray,
    val contentType: String,
    val widthPt: Double,
    val heightPt: Double,
    val description: String = ""
) : DocumentBlock

class Document {
    val blocks: MutableList<DocumentBlock> = mutableListOf(Paragraph())
    val paragraphs: MutableList<Paragraph> = object : AbstractMutableList<Paragraph>() {
        override val size: Int get() = blocks.count { it is Paragraph }
        private fun blockIndex(index: Int): Int {
            require(index in 0 until size)
            var seen = 0
            blocks.forEachIndexed { at, block ->
                if (block is Paragraph && seen++ == index) return at
            }
            error("Paragraph index out of range")
        }
        override fun get(index: Int): Paragraph = blocks[blockIndex(index)] as Paragraph
        override fun add(index: Int, element: Paragraph) {
            require(index in 0..size)
            blocks.add(if (index == size) blocks.size else blockIndex(index), element)
        }
        override fun removeAt(index: Int): Paragraph = blocks.removeAt(blockIndex(index)) as Paragraph
        override fun set(index: Int, element: Paragraph): Paragraph {
            val at = blockIndex(index)
            val old = blocks[at] as Paragraph
            blocks[at] = element
            return old
        }
    }

    var pageWidthPt: Double = Paper.A4.widthPt
    var pageHeightPt: Double = Paper.A4.heightPt
    var marginPt: Double = 56.0
        set(value) {
            field = value
            marginLeftPt = value; marginRightPt = value
            marginTopPt = value; marginBottomPt = value
        }
    var marginLeftPt: Double = marginPt
    var marginRightPt: Double = marginPt
    var marginTopPt: Double = marginPt
    var marginBottomPt: Double = marginPt
    var paper: Paper = Paper.A4
    var landscape: Boolean = false

    fun applyPaper(next: Paper, land: Boolean) {
        paper = next
        landscape = land
        pageWidthPt = if (land) next.heightPt else next.widthPt
        pageHeightPt = if (land) next.widthPt else next.heightPt
    }

    fun isEmpty(): Boolean = blocks.all {
        when (it) {
            is Paragraph -> it.text.isEmpty()
            is TableBlock -> it.rows.all { row -> row.all { cell -> cell.paragraphs.all { p -> p.text.isEmpty() } } }
            is ImageBlock -> false
        }
    }

    fun toPlainText(): String = paragraphs.joinToString("\n") { it.text }

    fun toExportText(): String = blocks.joinToString("\n") { block ->
        when (block) {
            is Paragraph -> block.text
            is TableBlock -> block.rows.joinToString("\n") { row ->
                row.joinToString("\t") { cell -> cell.paragraphs.joinToString(" / ") { it.text } }
            }
            is ImageBlock -> block.description.ifBlank { "[Изображение]" }
        }
    }

    fun fromPlainText(raw: String) {
        blocks.clear()
        raw.split('\n').forEach { blocks += Paragraph(it) }
        if (blocks.isEmpty()) blocks += Paragraph()
    }

    fun loadBlocks(items: List<DocumentBlock>) {
        blocks.clear()
        blocks.addAll(items)
        if (paragraphs.isEmpty()) blocks += Paragraph()
    }

    fun copy(): Document = Document().also { result ->
        result.loadBlocks(blocks.map { block ->
            when (block) {
                is Paragraph -> block.copyParagraph()
                is TableBlock -> TableBlock(block.rows.map { row ->
                    row.map { cell -> TableCell(cell.paragraphs.map { it.copyParagraph() }.toMutableList()) }.toMutableList()
                }.toMutableList())
                is ImageBlock -> block
            }
        })
        result.applyPaper(paper, landscape)
        result.pageWidthPt = pageWidthPt
        result.pageHeightPt = pageHeightPt
        result.marginPt = marginPt
        result.marginLeftPt = marginLeftPt
        result.marginRightPt = marginRightPt
        result.marginTopPt = marginTopPt
        result.marginBottomPt = marginBottomPt
    }

    private fun Paragraph.copyParagraph(): Paragraph = Paragraph(text, align, fontFamily, fontSize, bold).also {
        it.setRuns(runs.toList())
        it.leftIndentPt = leftIndentPt; it.rightIndentPt = rightIndentPt
        it.firstLineIndentPt = firstLineIndentPt
        it.spacingBeforePt = spacingBeforePt; it.spacingAfterPt = spacingAfterPt
        it.lineSpacing = lineSpacing; it.pageBreakBefore = pageBreakBefore
    }

    fun replaceRange(from: Int, to: Int, replacement: String) {
        val text = toPlainText()
        val a = from.coerceIn(0, text.length)
        val b = to.coerceIn(a, text.length)
        fun locate(offset: Int): Pair<Int, Int> {
            var remaining = offset
            paragraphs.forEachIndexed { index, paragraph ->
                if (remaining <= paragraph.text.length) return index to remaining
                remaining -= paragraph.text.length + 1
            }
            return paragraphs.lastIndex to paragraphs.last().text.length
        }
        val (startIndex, startChar) = locate(a)
        val (endIndex, endChar) = locate(b)
        val start = paragraphs[startIndex]
        fun sliceRuns(paragraph: Paragraph, from: Int, to: Int): List<TextRun> {
            var offset = 0
            return paragraph.runs.mapNotNull { run ->
                val begin = (from - offset).coerceIn(0, run.text.length)
                val end = (to - offset).coerceIn(begin, run.text.length)
                offset += run.text.length
                run.copy(text = run.text.substring(begin, end)).takeIf { it.text.isNotEmpty() }
            }
        }
        val before = sliceRuns(start, 0, startChar)
        val after = sliceRuns(paragraphs[endIndex], endChar, paragraphs[endIndex].text.length)
        repeat(endIndex - startIndex) { paragraphs.removeAt(startIndex + 1) }
        val lines = replacement.split('\n')
        start.setRuns(before + TextRun(lines.first()).takeIf { it.text.isNotEmpty() }.let { listOfNotNull(it) } +
            if (lines.size == 1) after else emptyList())
        lines.drop(1).forEachIndexed { index, line ->
            val paragraph = Paragraph("", start.align, start.fontFamily, start.fontSize, start.bold)
            paragraph.setRuns(listOfNotNull(TextRun(line).takeIf { it.text.isNotEmpty() }) +
                if (index == lines.size - 2) after else emptyList())
            paragraphs.add(startIndex + 1 + index, paragraph)
        }
    }

    fun insertParagraph(index: Int, paragraph: Paragraph = Paragraph()) {
        paragraphs.add(index.coerceIn(0, paragraphs.size), paragraph)
    }

    companion object { fun fromText(raw: String) = Document().also { it.fromPlainText(raw) } }
}
