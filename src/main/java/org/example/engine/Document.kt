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
    /** Keep this paragraph with the following paragraph when a page break is needed. */
    var keepWithNext: Boolean = false
    /** Do not split this paragraph across pages when it fits on one page. */
    var keepTogether: Boolean = false
    /** 0 — обычный текст, 1 и 2 — заголовки Word. */
    var outlineLevel: Int = 0
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
    private val blockItems = BlockList().apply { add(Paragraph()) }
    val blocks: MutableList<DocumentBlock> = blockItems
    val paragraphs: MutableList<Paragraph> = object : AbstractMutableList<Paragraph>() {
        override val size: Int get() = blockItems.paragraphCount
        override fun get(index: Int): Paragraph = blockItems[blockItems.paragraphBlock(index)] as Paragraph
        override fun add(index: Int, element: Paragraph) {
            require(index in 0..size)
            blockItems.add(if (index == size) blockItems.size else blockItems.paragraphBlock(index), element)
        }
        override fun removeAt(index: Int): Paragraph =
            blockItems.removeAt(blockItems.paragraphBlock(index)) as Paragraph
        override fun set(index: Int, element: Paragraph): Paragraph {
            val at = blockItems.paragraphBlock(index)
            val old = blockItems[at] as Paragraph
            blockItems[at] = element
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

    fun toPlainText(): String = buildString {
        var first = true
        for (block in blockItems) {
            if (block !is Paragraph) continue
            if (!first) append('\n')
            first = false
            append(block.text)
        }
    }

    /** Line, word and character totals without building the joined document string. */
    fun textStats(): TextStats {
        var lines = 1
        var words = 0
        var chars = 0
        var insideWord = false
        var seenParagraph = false
        for (block in blockItems) {
            if (block !is Paragraph) continue
            if (seenParagraph) {
                chars++
                lines++
                insideWord = false
            }
            seenParagraph = true
            for (ch in block.text) {
                chars++
                if (ch == '\n') lines++
                if (ch.isWhitespace()) insideWord = false
                else if (!insideWord) {
                    words++
                    insideWord = true
                }
            }
        }
        return TextStats(lines, words, chars)
    }

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
        it.keepWithNext = keepWithNext; it.keepTogether = keepTogether
        it.outlineLevel = outlineLevel
    }

    fun replaceRange(from: Int, to: Int, replacement: String) {
        val parts = replacement.split('\n').map { line ->
            if (line.isEmpty()) emptyList() else listOf(TextRun(line))
        }
        replaceParts(from, to, parts)
    }

    /** Replaces non-overlapping matches in the original text, retaining the first matched character's style. */
    fun replaceRanges(ranges: List<IntRange>, replacement: String) {
        val ordered = ranges.sortedBy { it.first }
        val length = toPlainText().length
        ordered.forEachIndexed { index, range ->
            require(!range.isEmpty() && range.first >= 0 && range.last < length)
            require(index == 0 || ordered[index - 1].last < range.first)
        }
        ordered.asReversed().forEach { range ->
            val style = ParagraphFormatting.clipParts(this, range.first, range.last + 1)
                .flatten().firstOrNull() ?: TextRun("")
            replaceParts(range.first, range.last + 1, replacement.split('\n').map { line ->
                if (line.isEmpty()) emptyList() else listOf(style.copy(text = line))
            })
        }
    }

    /** Replaces a plain-text span with styled paragraph fragments. One fragment is inline; further fragments are new paragraphs. */
    fun replaceParts(from: Int, to: Int, parts: List<List<TextRun>>) {
        val text = toPlainText()
        val a = from.coerceIn(0, text.length)
        val b = to.coerceIn(a, text.length)
        fun locate(offset: Int): Pair<Int, Int> {
            var remaining = offset
            var index = 0
            for (block in blockItems) {
                if (block !is Paragraph) continue
                if (remaining <= block.text.length) return index to remaining
                remaining -= block.text.length + 1
                index++
            }
            val last = paragraphs.lastOrNull()
            return paragraphs.lastIndex.coerceAtLeast(0) to (last?.text?.length ?: 0)
        }
        val (startIndex, startChar) = locate(a)
        val (endIndex, endChar) = locate(b)
        val start = paragraphs[startIndex]
        val before = ParagraphFormatting.sliceRuns(start, 0, startChar)
        val after = ParagraphFormatting.sliceRuns(paragraphs[endIndex], endChar, paragraphs[endIndex].text.length)
        val chunks = parts.ifEmpty { listOf(emptyList()) }
        repeat(endIndex - startIndex) { paragraphs.removeAt(startIndex + 1) }
        start.setRuns(before + chunks.first() + if (chunks.size == 1) after else emptyList())
        chunks.drop(1).forEachIndexed { index, runs ->
            val paragraph = ParagraphFormatting.cloneProps(start)
            paragraph.setRuns(runs + if (index == chunks.size - 2) after else emptyList())
            insertParagraphAfter(startIndex + index, paragraph)
        }
    }

    fun insertParagraph(index: Int, paragraph: Paragraph = Paragraph()) {
        paragraphs.add(index.coerceIn(0, paragraphs.size), paragraph)
    }

    /** Keeps new text beside its source paragraph, ahead of any following table or image. */
    fun insertParagraphAfter(index: Int, paragraph: Paragraph) {
        blockItems.add(blockItems.paragraphBlock(index) + 1, paragraph)
    }

    companion object { fun fromText(raw: String) = Document().also { it.fromPlainText(raw) } }
}

data class TextStats(val lines: Int, val words: Int, val chars: Int)

/** Block storage with an O(1) paragraph index. Structural edits rebuild the index once. */
private class BlockList : AbstractMutableList<DocumentBlock>() {
    private val items = ArrayList<DocumentBlock>()
    private val paragraphBlocks = ArrayList<Int>()

    val paragraphCount: Int get() = paragraphBlocks.size

    fun paragraphBlock(index: Int): Int = paragraphBlocks[index]

    private fun reindex() {
        paragraphBlocks.clear()
        items.forEachIndexed { index, block ->
            if (block is Paragraph) paragraphBlocks += index
        }
    }

    override val size: Int get() = items.size
    override fun get(index: Int): DocumentBlock = items[index]

    override fun add(index: Int, element: DocumentBlock) {
        items.add(index, element)
        reindex()
    }

    override fun addAll(index: Int, elements: Collection<DocumentBlock>): Boolean {
        if (elements.isEmpty()) return false
        items.addAll(index, elements)
        reindex()
        return true
    }

    override fun removeAt(index: Int): DocumentBlock {
        val removed = items.removeAt(index)
        reindex()
        return removed
    }

    override fun set(index: Int, element: DocumentBlock): DocumentBlock {
        val old = items.set(index, element)
        reindex()
        return old
    }

    override fun clear() {
        items.clear()
        paragraphBlocks.clear()
    }
}
