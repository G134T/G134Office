package org.example.fanfic

import org.example.engine.Document
import org.example.engine.ImageBlock
import org.example.engine.Paragraph
import org.example.engine.TableBlock
import org.example.engine.TextRun
import java.io.File
import java.nio.charset.StandardCharsets

data class FanficChapterFiles(val html: File, val markdown: File)

object FanficChapterExporter {
    fun targets(html: File): FanficChapterFiles {
        require(html.extension.equals("html", ignoreCase = true))
        return FanficChapterFiles(html, File(html.parentFile, "${html.nameWithoutExtension}.md"))
    }

    fun save(document: Document, html: File, title: String): FanficChapterFiles {
        val files = targets(html)
        files.html.writeText(FanficPreviewSerializer.toHtml(document), StandardCharsets.UTF_8)
        files.markdown.writeText(toMarkdown(document, title), StandardCharsets.UTF_8)
        return files
    }

    fun toMarkdown(document: Document, title: String): String = buildString {
        append("# ").append(escape(title)).append("\n\n")
        document.blocks.forEachIndexed { index, block ->
            if (index > 0) append("\n\n")
            when (block) {
                is Paragraph -> {
                    if (block.text.isEmpty()) append("***")
                    else (block.runs.ifEmpty { listOf(TextRun(block.text)) }).forEach { run ->
                        val value = escape(run.text)
                        val bold = block.bold || run.bold
                        if (bold) append("**")
                        if (run.italic) append('*')
                        append(value)
                        if (run.italic) append('*')
                        if (bold) append("**")
                    }
                }
                is TableBlock -> append(block.rows.joinToString("\n") { row ->
                    row.joinToString(" · ") { cell -> cell.paragraphs.joinToString(" / ") { it.text } }
                })
                is ImageBlock -> append(escape(block.description.ifBlank { "[Изображение]" }))
            }
        }
        append('\n')
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\")
        .replace("*", "\\*").replace("_", "\\_").replace("`", "\\`")
        .replace("[", "\\[").replace("]", "\\]")
}
