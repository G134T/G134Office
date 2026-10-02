package org.example.fanfic

import org.example.engine.Document
import org.example.engine.TextRun

/** Типографика кнопки «Отформатировать текст» на Книге фанфиков. */
object FanficFormatter {

    fun format(raw: String): String = formatMapped(raw).text

    private fun formatMapped(raw: String): MappedText =
        MappedText(convertQuotes(raw), IntArray(raw.length) { it })
            .replace(Regex("""(^|[\n\r])-\s""")) { it.groupValues[1] + "— " }
            .replace(Regex("""(\s)-(\s)""")) { it.groupValues[1] + "—" + it.groupValues[2] }
            .replace(Regex("–")) { "—" }
            .replace(Regex("--")) { "—" }
            .replace(Regex("\\.\\.\\.")) { "…" }
            .replace(Regex("\\.\\.")) { "…" }
            .replace(Regex("\r\n|\r")) { "\n" }
            .replace(Regex(" {2,}")) { " " }
            .replace(Regex("[^\n]+")) { it.value.trimEnd() }

    fun applyTo(document: Document) {
        document.paragraphs.forEach { paragraph ->
            val source = paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }
                .filter { it.text.isNotEmpty() }
            if (source.isEmpty()) return@forEach
            val formatted = formatMapped(paragraph.text)
            val runs = mutableListOf<TextRun>()
            var sourceEnd = 0
            var outputAt = 0
            source.forEach { run ->
                sourceEnd += run.text.length
                val start = outputAt
                while (outputAt < formatted.text.length && formatted.origins[outputAt] < sourceEnd) outputAt++
                if (outputAt > start) runs += run.copy(text = formatted.text.substring(start, outputAt))
            }
            paragraph.setRuns(runs)
        }
    }

    /** Every output character remembers its source offset, including punctuation collapsed across runs. */
    private data class MappedText(val text: String, val origins: IntArray) {
        fun replace(pattern: Regex, replacement: (MatchResult) -> String): MappedText {
            val out = StringBuilder(text.length)
            val positions = ArrayList<Int>(text.length)
            var copied = 0
            for (match in pattern.findAll(text)) {
                out.append(text, copied, match.range.first)
                for (index in copied until match.range.first) positions += origins[index]
                val next = replacement(match)
                out.append(next)
                next.indices.forEach { index ->
                    positions += origins[match.range.first + index.coerceAtMost(match.value.lastIndex)]
                }
                copied = match.range.last + 1
            }
            out.append(text, copied, text.length)
            for (index in copied until text.length) positions += origins[index]
            return MappedText(out.toString(), positions.toIntArray())
        }
    }

    fun sizePages(chars: Int): Int = ((chars.coerceAtLeast(1) + 1799) / 1800).coerceAtLeast(1)

    private fun convertQuotes(raw: String): String {
        val out = StringBuilder(raw.length)
        var open = true
        for (ch in raw) {
            when (ch) {
                '"', '“', '”', '„', '‟' -> {
                    out.append(if (open) '«' else '»')
                    open = !open
                }
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
