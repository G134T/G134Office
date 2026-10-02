package org.example.engine

/** Formatting operations targeted by the editor caret and selection. */
object ParagraphFormatting {
    fun affectedIndices(document: Document, caretParagraph: Int, selection: IntRange?): List<Int> {
        if (selection == null || selection.isEmpty()) {
            return listOf(caretParagraph.coerceIn(0, document.paragraphs.lastIndex.coerceAtLeast(0)))
        }
        val result = mutableListOf<Int>()
        var offset = 0
        document.paragraphs.forEachIndexed { index, paragraph ->
            val end = offset + paragraph.text.length
            if (selection.first <= end && selection.last >= offset) result += index
            offset = end + 1
        }
        return result.ifEmpty { listOf(caretParagraph.coerceIn(0, document.paragraphs.lastIndex.coerceAtLeast(0))) }
    }

    fun splitAtCaret(document: Document, paragraphIndex: Int, charIndex: Int): Int {
        val current = document.paragraphs[paragraphIndex]
        val at = charIndex.coerceIn(0, current.text.length)
        val tail = sliceRuns(current, at, current.text.length)
        current.setRuns(sliceRuns(current, 0, at))
        val next = cloneProps(current)
        next.setRuns(tail)
        document.insertParagraphAfter(paragraphIndex, next)
        return paragraphIndex + 1
    }

    /** Style used for the next typed character: the run to the left, or the first run at the start. */
    fun styleAt(paragraph: Paragraph, index: Int): TextRun {
        if (paragraph.text.isEmpty()) {
            return TextRun("", fontFamily = paragraph.fontFamily, fontSize = paragraph.fontSize, bold = paragraph.bold)
        }
        val source = paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text, bold = paragraph.bold)) }
        val at = if (index <= 0) 0 else (index - 1).coerceAtMost(paragraph.text.length - 1)
        var pos = 0
        for (run in source) {
            val end = pos + run.text.length
            if (at < end) return run.copy(text = "")
            pos = end
        }
        return source.last().copy(text = "")
    }

    fun insertText(paragraph: Paragraph, at: Int, text: String, style: TextRun? = null) {
        if (text.isEmpty()) return
        val index = at.coerceIn(0, paragraph.text.length)
        val base = style ?: styleAt(paragraph, index)
        val inserted = base.copy(text = text)
        paragraph.setRuns(mergeAdjacent(sliceRuns(paragraph, 0, index) + inserted + sliceRuns(paragraph, index, paragraph.text.length)))
    }

    fun deleteSpan(paragraph: Paragraph, from: Int, to: Int) {
        val length = paragraph.text.length
        val a = from.coerceIn(0, length)
        val b = to.coerceIn(a, length)
        paragraph.setRuns(mergeAdjacent(sliceRuns(paragraph, 0, a) + sliceRuns(paragraph, b, length)))
    }

    fun mergeNext(document: Document, index: Int) {
        if (index < 0 || index >= document.paragraphs.lastIndex) return
        val current = document.paragraphs[index]
        val next = document.paragraphs[index + 1]
        current.setRuns(mergeAdjacent(current.runs.filter { it.text.isNotEmpty() } + next.runs.filter { it.text.isNotEmpty() }))
        document.paragraphs.removeAt(index + 1)
    }

    fun sliceRuns(paragraph: Paragraph, from: Int, to: Int): List<TextRun> {
        if (from >= to) return emptyList()
        val source = paragraph.runs.ifEmpty {
            if (paragraph.text.isEmpty()) emptyList() else listOf(TextRun(paragraph.text, bold = paragraph.bold))
        }
        val result = mutableListOf<TextRun>()
        var pos = 0
        for (run in source) {
            val begin = (from - pos).coerceIn(0, run.text.length)
            val end = (to - pos).coerceIn(begin, run.text.length)
            pos += run.text.length
            if (begin < end) result += run.copy(text = run.text.substring(begin, end))
        }
        return result
    }

    fun mapRange(document: Document, from: Int, to: Int, map: (TextRun) -> TextRun) {
        if (from >= to) return
        var offset = 0
        document.paragraphs.forEach { paragraph ->
            val start = offset
            val end = offset + paragraph.text.length
            val localA = (from.coerceAtLeast(start) - start)
            val localB = (to.coerceAtMost(end) - start)
            if (localA < localB) {
                val source = paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text, bold = paragraph.bold)) }
                val next = mutableListOf<TextRun>()
                var pos = 0
                source.forEach { run ->
                    val r0 = pos
                    val r1 = pos + run.text.length
                    pos = r1
                    if (r1 <= localA || r0 >= localB) {
                        if (run.text.isNotEmpty()) next += run
                    } else {
                        if (r0 < localA) next += run.copy(text = run.text.substring(0, localA - r0))
                        val midFrom = localA.coerceIn(r0, r1) - r0
                        val midTo = localB.coerceIn(r0, r1) - r0
                        if (midTo > midFrom) next += map(run.copy(text = run.text.substring(midFrom, midTo)))
                        if (r1 > localB) next += run.copy(text = run.text.substring(localB - r0))
                    }
                }
                paragraph.setRuns(mergeAdjacent(next))
            }
            offset = end + 1
        }
    }

    /** Paragraphs touched by a plain-text selection, one run list per line of that selection. */
    fun clipParts(document: Document, from: Int, to: Int): List<List<TextRun>> {
        val text = document.toPlainText()
        val a = from.coerceIn(0, text.length)
        val b = to.coerceIn(a, text.length)
        if (a == b) return emptyList()
        val lines = text.substring(a, b).split('\n')
        val result = mutableListOf<List<TextRun>>()
        var offset = 0
        document.paragraphs.forEach { paragraph ->
            val start = offset
            val end = offset + paragraph.text.length
            if (a <= end && b > start) {
                val localA = (a - start).coerceIn(0, paragraph.text.length)
                val localB = (b - start).coerceIn(localA, paragraph.text.length)
                result += sliceRuns(paragraph, localA, localB)
            }
            offset = end + 1
        }
        while (result.size < lines.size) result.add(emptyList())
        while (result.size > lines.size && result.isNotEmpty()) result.removeAt(result.lastIndex)
        return result
    }

    fun cloneProps(source: Paragraph): Paragraph = Paragraph("", source.align, source.fontFamily, source.fontSize, source.bold).also {
        it.leftIndentPt = source.leftIndentPt
        it.rightIndentPt = source.rightIndentPt
        it.firstLineIndentPt = source.firstLineIndentPt
        it.spacingBeforePt = source.spacingBeforePt
        it.spacingAfterPt = source.spacingAfterPt
        it.lineSpacing = source.lineSpacing
        it.outlineLevel = source.outlineLevel
    }

    fun clearRun(run: TextRun): TextRun = run.copy(
        fontFamily = null,
        fontSize = null,
        bold = false,
        italic = false,
        underline = false,
        strikethrough = false,
        color = null
    )

    /** Стили абзаца как на вкладке «Главная» в Word: обычный, заголовок 1, заголовок 2. */
    fun applyWordStyle(paragraph: Paragraph, level: Int) {
        val size: Double
        val bold: Boolean
        val before: Double
        val after: Double
        val spacing: Double
        val outline: Int
        when (level) {
            1 -> {
                size = 28.0; bold = true; before = 12.0; after = 6.0; spacing = 1.15; outline = 1
            }
            2 -> {
                size = 20.0; bold = true; before = 10.0; after = 4.0; spacing = 1.15; outline = 2
            }
            else -> {
                size = 16.0; bold = false; before = 0.0; after = 8.0; spacing = 1.15; outline = 0
            }
        }
        paragraph.outlineLevel = outline
        paragraph.fontSize = size
        paragraph.bold = bold
        paragraph.align = Align.LEFT
        paragraph.leftIndentPt = 0.0
        paragraph.rightIndentPt = 0.0
        paragraph.firstLineIndentPt = 0.0
        paragraph.spacingBeforePt = before
        paragraph.spacingAfterPt = after
        paragraph.lineSpacing = spacing
        if (paragraph.runs.isNotEmpty()) {
            paragraph.setRuns(paragraph.runs.map { run ->
                clearRun(run).copy(fontFamily = run.fontFamily, fontSize = size, bold = bold)
            })
        }
    }

    fun mergeAdjacent(runs: List<TextRun>): List<TextRun> {
        val out = mutableListOf<TextRun>()
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val prev = out.lastOrNull()
            if (prev != null && sameStyle(prev, run)) out[out.lastIndex] = prev.copy(text = prev.text + run.text)
            else out += run
        }
        return out
    }

    fun sameStyle(a: TextRun, b: TextRun) = a.fontFamily == b.fontFamily && a.fontSize == b.fontSize &&
        a.bold == b.bold && a.italic == b.italic && a.underline == b.underline &&
        a.strikethrough == b.strikethrough && a.color == b.color

    fun isWordChar(ch: Char) = ch.isLetterOrDigit() || ch == '_' || ch == '-'

    /** Ctrl+стрелка: граница следующего или предыдущего слова. */
    fun moveWord(text: String, index: Int, forward: Boolean): Int {
        val i0 = index.coerceIn(0, text.length)
        if (forward) {
            var i = i0
            while (i < text.length && isWordChar(text[i])) i++
            while (i < text.length && !isWordChar(text[i])) i++
            return i
        }
        var i = i0
        if (i == 0) return 0
        i--
        while (i > 0 && !isWordChar(text[i])) i--
        while (i > 0 && isWordChar(text[i - 1])) i--
        return i
    }

    fun wordBounds(text: String, index: Int): IntRange {
        if (text.isEmpty()) return 0 until 0
        val i = index.coerceIn(0, text.lastIndex)
        var a = i
        var b = i + 1
        if (isWordChar(text[i])) {
            while (a > 0 && isWordChar(text[a - 1])) a--
            while (b < text.length && isWordChar(text[b])) b++
        } else {
            val space = text[i].isWhitespace()
            while (a > 0 && text[a - 1].isWhitespace() == space && isWordChar(text[a - 1]) == isWordChar(text[i])) a--
            while (b < text.length && text[b].isWhitespace() == space && isWordChar(text[b]) == isWordChar(text[i])) b++
        }
        return a until b
    }

    fun toClipboardHtml(parts: List<List<TextRun>>): String {
        val body = parts.joinToString("") { runs ->
            "<p>" + runs.joinToString("") { run ->
                var chunk = escapeHtml(run.text)
                if (run.bold) chunk = "<b>$chunk</b>"
                if (run.italic) chunk = "<i>$chunk</i>"
                if (run.underline) chunk = "<u>$chunk</u>"
                if (run.strikethrough) chunk = "<s>$chunk</s>"
                chunk
            } + "</p>"
        }
        return "<html><body>$body</body></html>"
    }

    fun encodeClip(parts: List<List<TextRun>>): String {
        val out = StringBuilder()
        out.append(parts.size).append('\u0000')
        for (part in parts) {
            out.append(part.size).append('\u0000')
            for (run in part) {
                field(out, run.text)
                field(out, run.fontFamily.orEmpty())
                field(out, run.fontSize?.toString().orEmpty())
                field(out, buildString {
                    append(if (run.bold) 'b' else '-')
                    append(if (run.italic) 'i' else '-')
                    append(if (run.underline) 'u' else '-')
                    append(if (run.strikethrough) 's' else '-')
                })
                field(out, run.color.orEmpty())
            }
        }
        return out.toString()
    }

    fun decodeClip(raw: String): List<List<TextRun>>? {
        if (raw.isEmpty()) return null
        return runCatching {
            var at = 0
            fun readInt(): Int {
                val end = raw.indexOf('\u0000', at)
                if (end < 0) error("clip")
                val value = raw.substring(at, end).toInt()
                at = end + 1
                return value
            }
            fun readField(): String {
                val mark = raw.indexOf(':', at)
                if (mark < 0) error("clip")
                val length = raw.substring(at, mark).toInt()
                val start = mark + 1
                val end = start + length
                if (end > raw.length) error("clip")
                at = end
                return raw.substring(start, end)
            }
            val paragraphs = readInt()
            List(paragraphs) {
                val runs = readInt()
                List(runs) {
                    val text = readField()
                    val family = readField().ifEmpty { null }
                    val size = readField().toDoubleOrNull()
                    val flags = readField()
                    val color = readField().ifEmpty { null }
                    TextRun(
                        text,
                        fontFamily = family,
                        fontSize = size,
                        bold = flags.getOrNull(0) == 'b',
                        italic = flags.getOrNull(1) == 'i',
                        underline = flags.getOrNull(2) == 'u',
                        strikethrough = flags.getOrNull(3) == 's',
                        color = color
                    )
                }
            }
        }.getOrNull()
    }

    private fun field(out: StringBuilder, value: String) {
        out.append(value.length).append(':').append(value)
    }

    private fun escapeHtml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
