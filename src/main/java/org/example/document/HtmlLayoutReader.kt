package org.example.document

import org.example.engine.*
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

internal object HtmlLayoutReader {
    fun read(file: File): Document {
        val bytes = file.readBytes()
        val probe = String(bytes, StandardCharsets.UTF_8)
        val charset = Regex("charset\\s*=\\s*[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(probe)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8
        val raw = String(bytes, charset)
        val blocks = mutableListOf<DocumentBlock>()
        var current: Paragraph? = null
        var row: MutableList<TableCell>? = null
        var cell: TableCell? = null
        var rows: MutableList<MutableList<TableCell>>? = null
        var inBody = false
        val inline = ArrayDeque<Pair<HTML.Tag, TextRun>>()
        fun flush() {
            current?.let { paragraph ->
                if (cell != null) cell!!.paragraphs.add(paragraph) else blocks += paragraph
            }
            current = null
        }
        fun styleMap(rawStyle: String): Map<String, String> = rawStyle.split(';').mapNotNull { piece ->
            val key = piece.substringBefore(':', "").trim().lowercase()
            if (key.isEmpty() || !piece.contains(':')) null else key to piece.substringAfter(':').trim()
        }.toMap()
        fun length(value: String?): Double? {
            val match = Regex("(-?[0-9.]+)(px|pt|mm|cm|in)?").matchEntire(value.orEmpty().trim()) ?: return null
            val number = match.groupValues[1].toDoubleOrNull() ?: return null
            return number * when (match.groupValues[2]) {
                "px" -> 0.75; "mm" -> 72.0 / 25.4; "cm" -> 72.0 / 2.54; "in" -> 72.0; else -> 1.0
            }
        }
        fun start(tag: HTML.Tag, attributes: MutableAttributeSet) {
            if (tag == HTML.Tag.BODY) inBody = true
            if (!inBody) return
            val css = styleMap(attributes.getAttribute(HTML.Attribute.STYLE)?.toString().orEmpty())
            when (tag) {
                HTML.Tag.TABLE -> { flush(); rows = mutableListOf() }
                HTML.Tag.TR -> { flush(); row = mutableListOf() }
                HTML.Tag.TD, HTML.Tag.TH -> { flush(); cell = TableCell(mutableListOf()) }
                HTML.Tag.P, HTML.Tag.DIV, HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3,
                HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.LI -> {
                    flush()
                    current = Paragraph(align = when (css["text-align"] ?: attributes.getAttribute(HTML.Attribute.ALIGN)?.toString()) {
                        "center" -> Align.CENTER; "right" -> Align.RIGHT; "justify" -> Align.JUSTIFY; else -> Align.LEFT
                    }).also { paragraph ->
                        paragraph.fontSize = length(css["font-size"]) ?: when (tag) {
                            HTML.Tag.H1 -> 24.0; HTML.Tag.H2 -> 20.0; HTML.Tag.H3 -> 17.0; else -> 12.0
                        }
                        paragraph.leftIndentPt = length(css["margin-left"]) ?: 0.0
                        paragraph.rightIndentPt = length(css["margin-right"]) ?: 0.0
                        paragraph.firstLineIndentPt = length(css["text-indent"]) ?: 0.0
                        paragraph.spacingBeforePt = length(css["margin-top"]) ?: 0.0
                        paragraph.spacingAfterPt = length(css["margin-bottom"]) ?: 0.0
                        paragraph.bold = tag in setOf(HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3, HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6)
                    }
                }
            }
            if (tag in setOf(HTML.Tag.B, HTML.Tag.STRONG, HTML.Tag.I, HTML.Tag.EM, HTML.Tag.U, HTML.Tag.SPAN, HTML.Tag.FONT)) {
                val previous = inline.lastOrNull()?.second ?: TextRun("")
                inline.addLast(tag to previous.copy(
                    bold = previous.bold || tag in setOf(HTML.Tag.B, HTML.Tag.STRONG) || css["font-weight"] == "bold",
                    italic = previous.italic || tag in setOf(HTML.Tag.I, HTML.Tag.EM) || css["font-style"] == "italic",
                    underline = previous.underline || tag == HTML.Tag.U || css["text-decoration"]?.contains("underline") == true,
                    fontFamily = css["font-family"]?.trim('"', '\'', ' ') ?: previous.fontFamily,
                    fontSize = length(css["font-size"]) ?: previous.fontSize,
                    color = css["color"] ?: attributes.getAttribute(HTML.Attribute.COLOR)?.toString() ?: previous.color
                ))
            }
        }
        val callback = object : HTMLEditorKit.ParserCallback() {
            override fun handleStartTag(tag: HTML.Tag, attributes: MutableAttributeSet, pos: Int) = start(tag, attributes)
            override fun handleSimpleTag(tag: HTML.Tag, attributes: MutableAttributeSet, pos: Int) {
                if (!inBody) return
                when (tag) {
                    HTML.Tag.BR -> current?.let { it.setRuns(it.runs + TextRun("\n")) }
                    HTML.Tag.IMG -> {
                        val src = attributes.getAttribute(HTML.Attribute.SRC)?.toString().orEmpty()
                        val picture = runCatching {
                            val bytes = imageBytes(file, src) ?: return@runCatching null
                            ImageBlock(bytes, imageType(bytes),
                                length(attributes.getAttribute(HTML.Attribute.WIDTH)?.toString()) ?: 300.0,
                                length(attributes.getAttribute(HTML.Attribute.HEIGHT)?.toString()) ?: 200.0,
                                attributes.getAttribute(HTML.Attribute.ALT)?.toString().orEmpty())
                        }.getOrNull()
                        if (picture != null) { flush(); blocks += picture }
                    }
                }
            }
            override fun handleText(data: CharArray, pos: Int) {
                if (!inBody) return
                if (current == null) current = Paragraph()
                current!!.setRuns(current!!.runs + (inline.lastOrNull()?.second ?: TextRun("")).copy(text = String(data)))
            }
            override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                if (!inBody) return
                if (tag == HTML.Tag.BODY) inBody = false
                when (tag) {
                    HTML.Tag.TD, HTML.Tag.TH -> { flush(); cell?.let { row?.add(it) }; cell = null }
                    HTML.Tag.TR -> { row?.let { rows?.add(it) }; row = null }
                    HTML.Tag.TABLE -> { rows?.let { blocks += TableBlock(it) }; rows = null }
                    HTML.Tag.P, HTML.Tag.DIV, HTML.Tag.H1, HTML.Tag.H2, HTML.Tag.H3,
                    HTML.Tag.H4, HTML.Tag.H5, HTML.Tag.H6, HTML.Tag.LI -> flush()
                }
                if (inline.lastOrNull()?.first == tag) inline.removeLast()
            }
        }
        ParserDelegator().parse(StringReader(raw), callback, true)
        flush()
        return Document().also { document ->
            document.loadBlocks(blocks)
            Regex("@page\\s*\\{([^}]*)}", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.let { page ->
                val css = styleMap(page)
                css["size"]?.split(Regex("\\s+"))?.takeIf { it.size == 2 }?.let { size ->
                    length(size[0])?.let { document.pageWidthPt = it }
                    length(size[1])?.let { document.pageHeightPt = it }
                }
                length(css["margin"])?.let { document.marginPt = it }
            }
        }
    }

    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024

    private fun imageBytes(documentFile: File, src: String): ByteArray? {
        if (src.isBlank() || src.indexOf('\u0000') >= 0) return null
        val bytes = when {
            src.startsWith("data:", true) && src.contains(";base64,") ->
                runCatching { Base64.getDecoder().decode(src.substringAfter(";base64,")) }.getOrNull()
            src.contains("://") || src.startsWith("file:", true) -> null
            else -> localImage(documentFile, src)
        } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES || !looksLikeImage(bytes)) return null
        return bytes
    }

    /** Relative images only, and only inside the HTML file's own directory. */
    private fun localImage(documentFile: File, src: String): ByteArray? {
        val base = documentFile.parentFile?.canonicalFile ?: return null
        val target = File(base, src).canonicalFile
        if (!target.toPath().startsWith(base.toPath())) return null
        if (!target.isFile || target.length() <= 0L || target.length() > MAX_IMAGE_BYTES) return null
        return target.readBytes()
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()) return true
        if (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) return true
        return bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
    }

    private fun imageType(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size >= 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
        bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte() -> "image/webp"
        bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "image/bmp"
        else -> "image/png"
    }
}
