package org.example.engine

/** Formatting operations targeted by the editor caret and selection. */
object ParagraphFormatting {
    fun affectedIndices(document: Document, caretParagraph: Int, selection: IntRange?): List<Int> {
        if (selection == null || selection.isEmpty()) {
            return listOf(caretParagraph.coerceIn(0, document.paragraphs.lastIndex))
        }
        val result = mutableListOf<Int>()
        var offset = 0
        document.paragraphs.forEachIndexed { index, paragraph ->
            val end = offset + paragraph.text.length
            if (selection.first <= end && selection.last >= offset) result += index
            offset = end + 1
        }
        return result
    }

    fun splitAtCaret(document: Document, paragraphIndex: Int, charIndex: Int): Int {
        val current = document.paragraphs[paragraphIndex]
        val at = charIndex.coerceIn(0, current.text.length)
        val rest = current.text.substring(at)
        current.text = current.text.take(at)
        document.insertParagraph(paragraphIndex + 1,
            Paragraph(rest, current.align, current.fontFamily, current.fontSize, current.bold))
        return paragraphIndex + 1
    }
}
