package org.example.fanfic

import org.example.engine.Document
import org.example.engine.TextRun

/** Типографика кнопки «Отформатировать текст» на Книге фанфиков. */
object FanficFormatter {

    fun format(raw: String): String {
        val withQuotes = convertQuotes(raw)
        val dashed = withQuotes
            .replace(Regex("""(^|[\n\r])-\s"""), "$1— ")
            .replace(Regex("""(\s)-(\s)"""), "$1—$2")
            .replace("–", "—")
            .replace("--", "—")
            .replace("...", "…")
            .replace("..", "…")
        return dashed.lines().joinToString("\n") { line ->
            line.replace(Regex(" {2,}"), " ").trimEnd()
        }
    }

    fun applyTo(document: Document) {
        document.paragraphs.forEach { paragraph ->
            val source = paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }
            if (source.isEmpty()) return@forEach
            val formatted = source.map { it.copy(text = format(it.text)) }
            val joined = format(formatted.joinToString("") { it.text })
            if (formatted.size == 1) paragraph.setRuns(listOf(formatted[0].copy(text = joined)))
            else paragraph.setRuns(formatted)
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
