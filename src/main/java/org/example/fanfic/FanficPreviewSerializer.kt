package org.example.fanfic

import org.example.engine.Align
import org.example.engine.Document
import org.example.engine.ImageBlock
import org.example.engine.Paragraph
import org.example.engine.TableBlock
import org.example.engine.TextRun

/** A read-only HTML representation of the current document for preview/export checks. */
object FanficPreviewSerializer {
    fun toHtml(document: Document): String = buildString {
        document.blocks.forEach { block ->
            when (block) {
                is Paragraph -> {
                    if (block.text.isEmpty()) {
                        append("<p></p>")
                    } else {
                        append(if (block.align == Align.CENTER) "<p align=\"center\">" else "<p>")
                        (block.runs.ifEmpty { listOf(TextRun(block.text)) }).forEach { run ->
                            val bold = block.bold || run.bold
                            if (bold) append("<b>")
                            if (run.italic) append("<i>")
                            if (run.underline) append("<u>")
                            if (run.strikethrough) append("<s>")
                            append(escape(run.text).replace("\n", "<br>"))
                            if (run.strikethrough) append("</s>")
                            if (run.underline) append("</u>")
                            if (run.italic) append("</i>")
                            if (bold) append("</b>")
                        }
                        append("</p>")
                    }
                }
                is TableBlock -> block.rows.forEach { row ->
                    append("<p>")
                    append(escape(row.joinToString(" · ") { cell -> cell.paragraphs.joinToString(" / ") { it.text } }))
                    append("</p>")
                }
                is ImageBlock -> append("<p>${escape(block.description.ifBlank { "[Изображение]" })}</p>")
            }
        }
    }

    private fun escape(value: String): String = value.replace("&", "&amp;")
        .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
