package org.example.document

import org.example.engine.*
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object HtmlLayoutWriter {
    fun write(file: File, document: Document) {
        val body = buildString {
            fun paragraph(paragraph: Paragraph) {
                append("<p style=\"text-align:${when (paragraph.align) {
                    Align.CENTER -> "center"; Align.RIGHT -> "right"; Align.JUSTIFY -> "justify"; else -> "left"
                }};margin-left:${paragraph.leftIndentPt}pt;margin-right:${paragraph.rightIndentPt}pt;")
                append("text-indent:${paragraph.firstLineIndentPt}pt;margin-top:${paragraph.spacingBeforePt}pt;")
                append("margin-bottom:${paragraph.spacingAfterPt}pt;font-size:${paragraph.fontSize}pt\">")
                (paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }).forEach { run ->
                    append("<span style=\"")
                    if (run.bold || paragraph.bold) append("font-weight:bold;")
                    if (run.italic) append("font-style:italic;")
                    if (run.underline) append("text-decoration:underline;")
                    run.fontFamily?.let { append("font-family:${escape(it)};") }
                    run.fontSize?.let { append("font-size:${it}pt;") }
                    run.color?.let { append("color:${escape(it)};") }
                    append("\">${escape(run.text).replace("\n", "<br>")}</span>")
                }
                append("</p>")
            }
            document.blocks.forEach { block ->
                when (block) {
                    is Paragraph -> paragraph(block)
                    is TableBlock -> {
                        append("<table border=\"1\" style=\"border-collapse:collapse\">")
                        block.rows.forEach { row ->
                            append("<tr>")
                            row.forEach { cell ->
                                append("<td>")
                                cell.paragraphs.forEach(::paragraph)
                                append("</td>")
                            }
                            append("</tr>")
                        }
                        append("</table>")
                    }
                    is ImageBlock -> {
                        append("<img src=\"data:${block.contentType};base64,${Base64.getEncoder().encodeToString(block.bytes)}\"")
                        append(" width=\"${block.widthPt / 0.75}\" height=\"${block.heightPt / 0.75}\"")
                        append(" alt=\"${escape(block.description)}\">")
                    }
                }
            }
        }
        val html = """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>@page{size:${document.pageWidthPt}pt ${document.pageHeightPt}pt;margin:${document.marginTopPt}pt}</style></head><body>$body</body></html>"""
        file.writeText(html, StandardCharsets.UTF_8)
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
