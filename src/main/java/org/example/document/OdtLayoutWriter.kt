package org.example.document

import org.example.engine.*
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object OdtLayoutWriter {
    fun write(file: File, document: Document) {
        val automaticStyles = StringBuilder()
        val body = StringBuilder()
        val pictures = mutableListOf<Pair<String, ImageBlock>>()
        var paragraphNumber = 0
        var runNumber = 0
        fun paragraphXml(paragraph: Paragraph): String {
            val styleId = "P${++paragraphNumber}"
            automaticStyles.append("<style:style style:name=\"$styleId\" style:family=\"paragraph\">")
            automaticStyles.append("<style:paragraph-properties fo:text-align=\"${when (paragraph.align) {
                Align.CENTER -> "center"; Align.RIGHT -> "right"; Align.JUSTIFY -> "justify"; else -> "left"
            }}\" fo:margin-left=\"${paragraph.leftIndentPt}pt\" fo:margin-right=\"${paragraph.rightIndentPt}pt\"")
            automaticStyles.append(" fo:text-indent=\"${paragraph.firstLineIndentPt}pt\" fo:margin-top=\"${paragraph.spacingBeforePt}pt\"")
            automaticStyles.append(" fo:margin-bottom=\"${paragraph.spacingAfterPt}pt\" fo:line-height=\"${paragraph.lineSpacing * 100}%\"")
            if (paragraph.pageBreakBefore) automaticStyles.append(" fo:break-before=\"page\"")
            automaticStyles.append("/>")
            automaticStyles.append("<style:text-properties fo:font-size=\"${paragraph.fontSize}pt\" style:font-name=\"${xml(paragraph.fontFamily)}\"/>")
            automaticStyles.append("</style:style>")
            val text = StringBuilder()
            (paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }).forEach { run ->
                val runId = "R${++runNumber}"
                automaticStyles.append("<style:style style:name=\"$runId\" style:family=\"text\"><style:text-properties")
                run.fontFamily?.let { automaticStyles.append(" style:font-name=\"${xml(it)}\"") }
                run.fontSize?.let { automaticStyles.append(" fo:font-size=\"${it}pt\"") }
                if (run.bold || paragraph.bold) automaticStyles.append(" fo:font-weight=\"bold\"")
                if (run.italic) automaticStyles.append(" fo:font-style=\"italic\"")
                if (run.underline) automaticStyles.append(" style:text-underline-style=\"solid\"")
                run.color?.let { automaticStyles.append(" fo:color=\"${xml(it)}\"") }
                automaticStyles.append("/></style:style>")
                text.append("<text:span text:style-name=\"$runId\">${xmlText(run.text)}</text:span>")
            }
            return "<text:p text:style-name=\"$styleId\">$text</text:p>"
        }
        document.blocks.forEach { block ->
            when (block) {
                is Paragraph -> body.append(paragraphXml(block))
                is TableBlock -> {
                    body.append("<table:table table:name=\"Table${paragraphNumber + 1}\">")
                    block.rows.forEach { row ->
                        body.append("<table:table-row>")
                        row.forEach { cell ->
                            body.append("<table:table-cell office:value-type=\"string\">")
                            cell.paragraphs.forEach { body.append(paragraphXml(it)) }
                            body.append("</table:table-cell>")
                        }
                        body.append("</table:table-row>")
                    }
                    body.append("</table:table>")
                }
                is ImageBlock -> {
                    val extension = when (block.contentType) { "image/jpeg" -> "jpg"; "image/gif" -> "gif"; else -> "png" }
                    val path = "Pictures/image${pictures.size + 1}.$extension"
                    pictures += path to block
                    body.append("<text:p><draw:frame draw:name=\"Image${pictures.size}\" svg:width=\"${block.widthPt}pt\" svg:height=\"${block.heightPt}pt\">")
                    body.append("<draw:image xlink:href=\"$path\" xlink:type=\"simple\" xlink:show=\"embed\" xlink:actuate=\"onLoad\"/>")
                    body.append("</draw:frame></text:p>")
                }
            }
        }
        val namespaces = """xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0" xmlns:xlink="http://www.w3.org/1999/xlink""""
        val content = """<?xml version="1.0" encoding="UTF-8"?><office:document-content $namespaces office:version="1.2"><office:automatic-styles>$automaticStyles</office:automatic-styles><office:body><office:text>$body</office:text></office:body></office:document-content>"""
        val styles = """<?xml version="1.0" encoding="UTF-8"?><office:document-styles $namespaces office:version="1.2"><office:automatic-styles><style:page-layout style:name="Page"><style:page-layout-properties fo:page-width="${document.pageWidthPt}pt" fo:page-height="${document.pageHeightPt}pt" fo:margin-left="${document.marginLeftPt}pt" fo:margin-right="${document.marginRightPt}pt" fo:margin-top="${document.marginTopPt}pt" fo:margin-bottom="${document.marginBottomPt}pt"/></style:page-layout></office:automatic-styles><office:master-styles><style:master-page style:name="Standard" style:page-layout-name="Page"/></office:master-styles></office:document-styles>"""
        val manifest = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2"><manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>""")
            append("""<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>""")
            pictures.forEach { (path, image) -> append("<manifest:file-entry manifest:full-path=\"$path\" manifest:media-type=\"${image.contentType}\"/>") }
            append("</manifest:manifest>")
        }
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            val mime = "application/vnd.oasis.opendocument.text".toByteArray(StandardCharsets.UTF_8)
            val entry = ZipEntry("mimetype")
            entry.method = ZipEntry.STORED
            entry.size = mime.size.toLong()
            entry.compressedSize = entry.size
            entry.crc = CRC32().also { it.update(mime) }.value
            zip.putNextEntry(entry); zip.write(mime); zip.closeEntry()
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
            put("content.xml", content.toByteArray(StandardCharsets.UTF_8))
            put("styles.xml", styles.toByteArray(StandardCharsets.UTF_8))
            put("META-INF/manifest.xml", manifest.toByteArray(StandardCharsets.UTF_8))
            pictures.forEach { (path, image) -> put(path, image.bytes) }
        }
    }

    private fun xml(raw: String): String = raw.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun xmlText(raw: String): String = xml(raw).replace("\t", "<text:tab/>")
        .replace("\n", "<text:line-break/>")
}
