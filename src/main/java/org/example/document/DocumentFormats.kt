package org.example.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.util.Units
import org.example.engine.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import java.math.BigInteger

object DocumentFormats {

    fun readDocument(file: File): Document = when (kind(file)) {
        "docx" -> readRichDocx(file)
        "doc" -> readRichDoc(file)
        "odt" -> OdtLayoutReader.read(file)
        "rtf" -> RtfLayoutReader.read(file)
        "html" -> HtmlLayoutReader.read(file)
        else -> Document.fromText(read(file))
    }

    private fun readRichDoc(file: File): Document {
        FileInputStream(file).use { input ->
            HWPFDocument(input).use { word ->
                val range = word.range
                val blocks = mutableListOf<DocumentBlock>()
                var index = 0
                while (index < range.numParagraphs()) {
                    val source = range.getParagraph(index)
                    if (source.isInTable) {
                        val table = range.getTable(source)
                        val rows = (0 until table.numRows()).map { rowIndex ->
                            val row = table.getRow(rowIndex)
                            (0 until row.numCells()).map { cellIndex ->
                                val cell = row.getCell(cellIndex)
                                TableCell((0 until cell.numParagraphs()).map { richLegacyParagraph(cell.getParagraph(it)) }.toMutableList())
                            }.toMutableList()
                        }.toMutableList()
                        blocks += TableBlock(rows)
                        while (index < range.numParagraphs() && range.getParagraph(index).startOffset < table.endOffset) index++
                    } else {
                        blocks += richLegacyParagraph(source)
                        index++
                    }
                }
                return Document().also { document ->
                    document.loadBlocks(blocks)
                    if (range.numSections() > 0) range.getSection(0).let { section ->
                        document.pageWidthPt = section.pageWidth / 20.0
                        document.pageHeightPt = section.pageHeight / 20.0
                        document.marginLeftPt = section.marginLeft / 20.0
                        document.marginRightPt = section.marginRight / 20.0
                        document.marginTopPt = section.marginTop / 20.0
                        document.marginBottomPt = section.marginBottom / 20.0
                        document.landscape = document.pageWidthPt > document.pageHeightPt
                    }
                }
            }
        }
    }

    private fun richLegacyParagraph(source: org.apache.poi.hwpf.usermodel.Paragraph): Paragraph {
        val paragraph = Paragraph(align = when (source.justification) {
            1 -> Align.CENTER
            2 -> Align.RIGHT
            3 -> Align.JUSTIFY
            else -> Align.LEFT
        })
        paragraph.setRuns((0 until source.numCharacterRuns()).mapNotNull { index ->
            val run = source.getCharacterRun(index)
            val text = run.text().replace('\r', '\n').trimEnd('\n', '\u0007')
            if (text.isEmpty()) null else TextRun(text, run.fontName, run.fontSizeAsDouble,
                run.isBold, run.isItalic, run.underlineCode != 0)
        })
        paragraph.leftIndentPt = source.indentFromLeft / 20.0
        paragraph.rightIndentPt = source.indentFromRight / 20.0
        paragraph.firstLineIndentPt = source.firstLineIndent / 20.0
        paragraph.spacingBeforePt = source.spacingBefore.coerceAtLeast(0) / 20.0
        paragraph.spacingAfterPt = source.spacingAfter.coerceAtLeast(0) / 20.0
        paragraph.pageBreakBefore = source.pageBreakBefore()
        return paragraph
    }

    fun write(file: File, document: Document) {
        if (kind(file) == "odt") {
            if (file.extension.lowercase() != "odt") throw IllegalArgumentException("Сохрани документ как .odt")
            AtomicFileSave.write(file) { temporary -> OdtLayoutWriter.write(temporary, document) }
            return
        }
        if (kind(file) == "rtf") {
            AtomicFileSave.write(file) { temporary -> RtfLayoutWriter.write(temporary, document) }
            return
        }
        if (kind(file) == "html") {
            AtomicFileSave.write(file) { temporary -> HtmlLayoutWriter.write(temporary, document) }
            return
        }
        if (kind(file) != "docx") {
            write(file, document.toExportText())
            return
        }
        if (file.extension.lowercase() != "docx") {
            throw IllegalArgumentException("Сохрани документ как .docx")
        }
        AtomicFileSave.write(file) { temporary -> writeRichDocx(temporary, document) }
    }

    private fun readRichDocx(file: File): Document {
        FileInputStream(file).use { input ->
            XWPFDocument(input).use { word ->
                val blocks = mutableListOf<DocumentBlock>()
                word.bodyElements.forEach { element ->
                    when (element) {
                        is XWPFParagraph -> {
                            blocks += richParagraph(element)
                            element.runs.flatMap { it.embeddedPictures }.forEach { picture ->
                                val data = picture.pictureData
                                val pixels = runCatching { ImageIO.read(ByteArrayInputStream(data.data)) }.getOrNull()
                                val extent = picture.ctPicture.spPr?.xfrm?.ext
                                val width = extent?.cx?.toString()?.toDoubleOrNull()?.div(12700.0)
                                    ?: pixels?.width?.toDouble()?.times(0.75) ?: 300.0
                                val height = extent?.cy?.toString()?.toDoubleOrNull()?.div(12700.0)
                                    ?: pixels?.height?.toDouble()?.times(0.75) ?: 200.0
                                blocks += ImageBlock(data.data, data.packagePart.contentType, width, height, picture.description.orEmpty())
                            }
                        }
                        is XWPFTable -> blocks += TableBlock(element.rows.map { row ->
                            row.tableCells.map { cell ->
                                TableCell(cell.paragraphs.map(::richParagraph).toMutableList())
                            }.toMutableList()
                        }.toMutableList())
                    }
                }
                return Document().also { document ->
                    document.loadBlocks(blocks)
                    word.document.body.sectPr?.let { section ->
                        section.pgSz?.let { size ->
                            document.pageWidthPt = (size.w.toString().toDoubleOrNull() ?: 11900.0) / 20.0
                            document.pageHeightPt = (size.h.toString().toDoubleOrNull() ?: 16840.0) / 20.0
                            document.landscape = document.pageWidthPt > document.pageHeightPt
                        }
                        section.pgMar?.let { margins ->
                            document.marginLeftPt = (margins.left.toString().toDoubleOrNull() ?: 1120.0) / 20.0
                            document.marginRightPt = (margins.right.toString().toDoubleOrNull() ?: 1120.0) / 20.0
                            document.marginTopPt = (margins.top.toString().toDoubleOrNull() ?: 1120.0) / 20.0
                            document.marginBottomPt = (margins.bottom.toString().toDoubleOrNull() ?: 1120.0) / 20.0
                        }
                    }
                }
            }
        }
    }

    private fun richParagraph(source: XWPFParagraph): Paragraph {
        val paragraph = Paragraph(align = when (source.alignment?.name) {
            "CENTER" -> Align.CENTER
            "RIGHT" -> Align.RIGHT
            "BOTH", "DISTRIBUTE" -> Align.JUSTIFY
            else -> Align.LEFT
        })
        val headingLevel = Regex("(?i)heading\\s*([1-6])").find(source.style.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        paragraph.fontSize = source.runs.firstNotNullOfOrNull { it.fontSizeAsDouble?.takeIf { size -> size > 0 } }
            ?: when (headingLevel) { 1 -> 22.0; 2 -> 18.0; 3 -> 16.0; else -> 12.0 }
        paragraph.fontFamily = source.runs.firstNotNullOfOrNull { it.fontFamily } ?: "Calibri"
        paragraph.bold = headingLevel != null
        paragraph.setRuns(source.runs.map { run ->
            TextRun(
                text = run.text().orEmpty(),
                fontFamily = run.fontFamily,
                fontSize = run.fontSizeAsDouble?.takeIf { it > 0 },
                bold = run.isBold,
                italic = run.isItalic,
                underline = run.underline.name != "NONE",
                color = run.color,
                strikethrough = run.isStrikeThrough
            )
        })
        paragraph.leftIndentPt = source.indentationLeft.coerceAtLeast(0) / 20.0
        paragraph.rightIndentPt = source.indentationRight.coerceAtLeast(0) / 20.0
        paragraph.firstLineIndentPt = source.indentationFirstLine / 20.0
        paragraph.spacingBeforePt = source.spacingBefore.coerceAtLeast(0) / 20.0
        paragraph.spacingAfterPt = source.spacingAfter.coerceAtLeast(0) / 20.0
        paragraph.lineSpacing = (source.spacingBetween / 1.0).takeIf { it in 0.8..3.0 } ?: 1.0
        paragraph.pageBreakBefore = source.isPageBreak
        return paragraph
    }

    private fun writeRichDocx(file: File, document: Document) {
        XWPFDocument().use { word ->
            val section = word.document.body.addNewSectPr()
            section.addNewPgSz().also {
                it.w = BigInteger.valueOf((document.pageWidthPt * 20).toLong())
                it.h = BigInteger.valueOf((document.pageHeightPt * 20).toLong())
            }
            section.addNewPgMar().also {
                it.left = BigInteger.valueOf((document.marginLeftPt * 20).toLong())
                it.right = BigInteger.valueOf((document.marginRightPt * 20).toLong())
                it.top = BigInteger.valueOf((document.marginTopPt * 20).toLong())
                it.bottom = BigInteger.valueOf((document.marginBottomPt * 20).toLong())
            }
            document.blocks.forEach { block ->
                when (block) {
                    is Paragraph -> writeParagraph(word.createParagraph(), block)
                    is TableBlock -> {
                        val cols = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        val table = word.createTable(block.rows.size.coerceAtLeast(1), cols)
                        block.rows.forEachIndexed { rowIndex, row ->
                            row.forEachIndexed { colIndex, cell ->
                                val target = table.getRow(rowIndex).getCell(colIndex)
                                target.removeParagraph(0)
                                cell.paragraphs.forEach { writeParagraph(target.addParagraph(), it) }
                            }
                        }
                    }
                    is ImageBlock -> {
                        val type = when (block.contentType.lowercase()) {
                            "image/jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG
                            "image/gif" -> XWPFDocument.PICTURE_TYPE_GIF
                            else -> XWPFDocument.PICTURE_TYPE_PNG
                        }
                        val run = word.createParagraph().createRun()
                        ByteArrayInputStream(block.bytes).use {
                            run.addPicture(it, type, "image", Units.toEMU(block.widthPt), Units.toEMU(block.heightPt))
                        }
                    }
                }
            }
            FileOutputStream(file).use { word.write(it) }
        }
    }

    private fun writeParagraph(target: XWPFParagraph, source: Paragraph) {
        target.indentationLeft = (source.leftIndentPt * 20).toInt()
        target.indentationRight = (source.rightIndentPt * 20).toInt()
        target.indentationFirstLine = (source.firstLineIndentPt * 20).toInt()
        target.spacingBefore = (source.spacingBeforePt * 20).toInt()
        target.spacingAfter = (source.spacingAfterPt * 20).toInt()
        target.setSpacingBetween(source.lineSpacing)
        target.isPageBreak = source.pageBreakBefore
        target.alignment = when (source.align) {
            Align.LEFT -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.LEFT
            Align.CENTER -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER
            Align.RIGHT -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT
            Align.JUSTIFY -> org.apache.poi.xwpf.usermodel.ParagraphAlignment.BOTH
        }
        (source.runs.ifEmpty { listOf(TextRun(source.text)) }).forEach { item ->
            val run = target.createRun()
            run.setText(item.text)
            run.fontFamily = item.fontFamily ?: source.fontFamily
            run.fontSize = (item.fontSize ?: source.fontSize).toInt()
            run.isBold = item.bold || source.bold
            run.isItalic = item.italic
            run.isStrikeThrough = item.strikethrough
            if (item.underline) run.underline = org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
            item.color?.let { run.color = it }
        }
    }

    fun kind(file: File?): String {
        val name = file?.name?.lowercase().orEmpty()
        return when {
            name.endsWith(".docx") || name.endsWith(".docm") || name.endsWith(".dotx") -> "docx"
            name.endsWith(".doc") || name.endsWith(".dot") -> "doc"
            name.endsWith(".odt") || name.endsWith(".ott") -> "odt"
            name.endsWith(".html") || name.endsWith(".htm") -> "html"
            name.endsWith(".rtf") -> "rtf"
            name.endsWith(".pdf") -> "pdf"
            name.endsWith(".fb2") -> "fb2"
            name.endsWith(".epub") -> "epub"
            else -> "txt"
        }
    }

    fun isDocx(file: File?): Boolean = kind(file) == "docx"

    fun canOpen(file: File): Boolean = kind(file) != "unknown"

    fun read(file: File): String {
        if (!file.exists()) throw IllegalArgumentException("Файл не найден: ${file.name}")
        if (file.length() == 0L) return ""
        return when (kind(file)) {
            "docx" -> readDocx(file)
            "doc" -> readDoc(file)
            "odt" -> readOdt(file)
            "html" -> readHtml(file)
            "rtf" -> readRtf(file)
            "pdf" -> readPdf(file)
            "fb2" -> readFb2(file)
            "epub" -> readEpub(file)
            else -> readText(file)
        }
    }

    fun write(file: File, text: String) {
        if (file.extension.lowercase() in setOf("docm", "dotx", "ott")) {
            throw IllegalArgumentException("Шаблоны и документы с макросами нельзя безопасно перезаписать. Сохрани копию как .docx или .odt")
        }
        val format = kind(file)
        AtomicFileSave.write(file) { temporary -> writeContent(temporary, text, format) }
    }

    private fun writeContent(file: File, text: String, format: String) {
        when (format) {
            "docx" -> writeDocx(file, text)
            "doc" -> throw IllegalArgumentException("Старый .doc только читаем. Сохрани как .docx")
            "odt" -> writeOdt(file, text)
            "html" -> writeHtml(file, text)
            "rtf" -> writeRtf(file, text)
            "pdf" -> writePdf(file, text)
            "fb2" -> writeFb2(file, text)
            "epub" -> throw IllegalArgumentException("EPUB пока только открываем. Сохрани как .txt или .html")
            else -> Files.writeString(file.toPath(), text, StandardCharsets.UTF_8)
        }
    }

    private fun readDoc(file: File): String {
        FileInputStream(file).use { input ->
            HWPFDocument(input).use { doc ->
                WordExtractor(doc).use { ext ->
                    return buildString {
                        ext.headerText?.replace("\r", "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            appendLine(it)
                            appendLine()
                        }
                        val body = (ext.text ?: "").replace("\r\n", "\n").replace('\r', '\n')
                        append(body)
                        ext.footnoteText?.joinToString("\n") { it.replace("\r", "") }?.trim()
                            ?.takeIf { it.isNotEmpty() }?.let {
                                appendLine()
                                appendLine()
                                appendLine(it)
                            }
                        ext.footerText?.replace("\r", "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            appendLine()
                            appendLine(it)
                        }
                    }.trim()
                }
            }
        }
    }

    private fun readDocx(file: File): String {
        FileInputStream(file).use { input ->
            XWPFDocument(input).use { doc ->
                val parts = mutableListOf<String>()
                doc.headerList.map { it.text.trim() }.filter { it.isNotEmpty() }.forEach { parts += it }
                if (parts.isNotEmpty()) parts += ""
                doc.bodyElements.forEach { el ->
                    when (el) {
                        is XWPFParagraph -> parts += paragraphText(el)
                        is XWPFTable -> appendTable(el, parts)
                    }
                }
                doc.footerList.map { it.text.trim() }.filter { it.isNotEmpty() }.forEach {
                    parts += ""
                    parts += it
                }
                try {
                    doc.footnotes.orEmpty().map { note ->
                        note.paragraphs.joinToString(" ") { it.text }.trim()
                    }.filter { it.isNotEmpty() }.forEach { parts += it }
                } catch (_: Exception) {
                }
                return if (parts.isEmpty()) doc.paragraphs.joinToString("\n") { it.text }
                else parts.joinToString("\n")
            }
        }
    }

    private fun paragraphText(p: XWPFParagraph): String {
        val raw = p.text.orEmpty()
        val style = p.style.orEmpty()
        val heading = Regex("(?i)heading\\s*(\\d)").find(style)?.groupValues?.get(1)
            ?: Regex("Заголовок\\s*(\\d)").find(style)?.groupValues?.get(1)
        val indent = try {
            p.numIlvl?.intValueExact() ?: 0
        } catch (_: Exception) {
            0
        }
        val prefix = when {
            heading != null -> "#".repeat(heading.toInt().coerceIn(1, 6)) + " "
            p.numID != null && raw.isNotEmpty() -> "    ".repeat(indent.coerceAtLeast(0)) + "• "
            else -> ""
        }
        return prefix + raw
    }

    private fun appendTable(table: XWPFTable, parts: MutableList<String>) {
        table.rows.forEach { row ->
            parts += row.tableCells.joinToString("\t") { cell ->
                cell.paragraphs.joinToString(" / ") { paragraphText(it) }.trim()
            }
            row.tableCells.forEach { cell ->
                cell.tables.forEach { appendTable(it, parts) }
            }
        }
    }

    private fun writeDocx(file: File, text: String) {
        XWPFDocument().use { doc ->
            FileOutputStream(file).use { out ->
                text.split("\n").forEach { line ->
                    val p = doc.createParagraph()
                    val run = p.createRun()
                    val (body, size, bold) = when {
                        line.startsWith("###### ") -> Triple(line.removePrefix("###### "), 12, true)
                        line.startsWith("##### ") -> Triple(line.removePrefix("##### "), 13, true)
                        line.startsWith("#### ") -> Triple(line.removePrefix("#### "), 14, true)
                        line.startsWith("### ") -> Triple(line.removePrefix("### "), 16, true)
                        line.startsWith("## ") -> Triple(line.removePrefix("## "), 18, true)
                        line.startsWith("# ") -> Triple(line.removePrefix("# "), 22, true)
                        line.startsWith("• ") -> Triple(line, 12, false)
                        line.startsWith("- ") -> Triple("• " + line.removePrefix("- "), 12, false)
                        else -> Triple(line, 12, false)
                    }
                    when {
                        line.startsWith("# ") -> p.style = "Heading1"
                        line.startsWith("## ") -> p.style = "Heading2"
                        line.startsWith("### ") -> p.style = "Heading3"
                        line.startsWith("#### ") -> p.style = "Heading4"
                        line.startsWith("##### ") -> p.style = "Heading5"
                        line.startsWith("###### ") -> p.style = "Heading6"
                    }
                    run.setText(body)
                    run.fontFamily = "Calibri"
                    run.fontSize = size
                    run.isBold = bold
                }
                doc.write(out)
            }
        }
    }

    private fun readPdf(file: File): String {
        Loader.loadPDF(file).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc).trim()
        }
    }

    private fun readOdt(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml") ?: return ""
            zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                var xml = reader.readText()
                xml = xml.replace(Regex("""<text:h[^>]*text:outline-level="(\d)"[^>]*>""")) { match ->
                    "\n" + "#".repeat(match.groupValues[1].toInt().coerceIn(1, 6)) + " "
                }
                xml = xml.replace(Regex("<text:h[^>]*>"), "\n# ")
                xml = xml.replace(Regex("<text:p[^>]*>"), "\n")
                xml = xml.replace(Regex("<text:line-break[^/]*/>"), "\n")
                xml = xml.replace(Regex("<text:tab[^/]*/>"), "\t")
                xml = xml.replace(Regex("<text:list-item[^>]*>"), "\n• ")
                xml = xml.replace(Regex("<table:table-row[^>]*>"), "\n")
                xml = xml.replace(Regex("<table:table-cell[^>]*>"), "\t")
                return stripTags(xml).trim()
            }
        }
    }

    private fun writeOdt(file: File, text: String) {
        val escaped = text.split("\n").joinToString("") { line ->
            when {
                line.startsWith("# ") ->
                    "<text:h text:style-name=\"Heading_20_1\" text:outline-level=\"1\">${escapeXml(line.removePrefix("# "))}</text:h>"
                line.startsWith("## ") ->
                    "<text:h text:style-name=\"Heading_20_2\" text:outline-level=\"2\">${escapeXml(line.removePrefix("## "))}</text:h>"
                line.startsWith("### ") ->
                    "<text:h text:style-name=\"Heading_20_3\" text:outline-level=\"3\">${escapeXml(line.removePrefix("### "))}</text:h>"
                else ->
                    "<text:p text:style-name=\"Standard\">${escapeXml(line)}</text:p>"
            }
        }
        val content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
              <office:body><office:text>$escaped</office:text></office:body>
            </office:document-content>
        """.trimIndent()
        val styles = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
              xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
              xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
              office:version="1.2">
              <office:font-face-decls>
                <style:font-face style:name="Arial" svg:font-family="Arial"/>
              </office:font-face-decls>
              <office:styles>
                <style:style style:name="Standard" style:family="paragraph">
                  <style:text-properties style:font-name="Arial" fo:font-size="12pt"/>
                </style:style>
                <style:style style:name="Heading_20_1" style:display-name="Heading 1" style:family="paragraph">
                  <style:text-properties style:font-name="Arial" fo:font-size="22pt" fo:font-weight="bold"/>
                </style:style>
                <style:style style:name="Heading_20_2" style:display-name="Heading 2" style:family="paragraph">
                  <style:text-properties style:font-name="Arial" fo:font-size="18pt" fo:font-weight="bold"/>
                </style:style>
                <style:style style:name="Heading_20_3" style:display-name="Heading 3" style:family="paragraph">
                  <style:text-properties style:font-name="Arial" fo:font-size="16pt" fo:font-weight="bold"/>
                </style:style>
              </office:styles>
            </office:document-styles>
        """.trimIndent()
        val meta = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
              xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
              xmlns:dc="http://purl.org/dc/elements/1.1/" office:version="1.2">
              <office:meta>
                <meta:generator>G134Office</meta:generator>
                <dc:title>G134Office</dc:title>
              </office:meta>
            </office:document-meta>
        """.trimIndent()
        val manifest = """
            <?xml version="1.0" encoding="UTF-8"?>
            <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
              <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text"/>
              <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
              <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
              <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
            </manifest:manifest>
        """.trimIndent()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.setMethod(ZipOutputStream.STORED)
            putStored(zip, "mimetype", "application/vnd.oasis.opendocument.text".toByteArray())
            zip.setMethod(ZipOutputStream.DEFLATED)
            put(zip, "content.xml", content.toByteArray(StandardCharsets.UTF_8))
            put(zip, "styles.xml", styles.toByteArray(StandardCharsets.UTF_8))
            put(zip, "meta.xml", meta.toByteArray(StandardCharsets.UTF_8))
            put(zip, "META-INF/manifest.xml", manifest.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun readEpub(file: File): String {
        ZipFile(file).use { zip ->
            val ordered = epubSpine(zip) ?: zip.entries().toList()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { name ->
                    val lower = name.lowercase()
                    lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                }
                .sorted()
            val chunks = ordered.mapNotNull { path ->
                val entry = zip.getEntry(path) ?: zip.getEntry(path.replace('\\', '/')) ?: return@mapNotNull null
                zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    .let { htmlToText(it) }
            }
            return chunks.filter { it.isNotBlank() }.joinToString("\n\n").trim()
        }
    }

    private fun epubSpine(zip: ZipFile): List<String>? {
        val container = zip.getEntry("META-INF/container.xml") ?: return null
        val xml = zip.getInputStream(container).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)?.replace('\\', '/') ?: return null
        val opfEntry = zip.getEntry(opfPath) ?: return null
        val opf = zip.getInputStream(opfEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val hrefById = mutableMapOf<String, String>()
        Regex("<item\\s+([^>]+)/?>", RegexOption.IGNORE_CASE).findAll(opf).forEach { match ->
            val attrs = match.groupValues[1]
            val id = Regex("\\bid\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
            val href = Regex("\\bhref\\s*=\\s*\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)
            if (id != null && href != null) hrefById[id] = href
        }
        val spine = Regex("<itemref[^>]*idref\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(opf).map { it.groupValues[1] }.toList()
        if (spine.isEmpty()) return null
        return spine.mapNotNull { id ->
            val href = hrefById[id] ?: return@mapNotNull null
            resolveHref(base, href.substringBefore('#'))
        }
    }

    private fun resolveHref(base: String, href: String): String {
        val combined = (base + href).replace('\\', '/')
        val parts = mutableListOf<String>()
        combined.split('/').forEach { part ->
            when (part) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private fun readFb2(file: File): String {
        val bytes = Files.readAllBytes(file.toPath())
        val head = String(bytes.copyOf(minOf(bytes.size, 120)), StandardCharsets.UTF_8)
        val encName = Regex("encoding\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1) ?: "UTF-8"
        val charset = try {
            Charset.forName(encName)
        } catch (_: Exception) {
            StandardCharsets.UTF_8
        }
        var xml = String(bytes, charset).replace(Regex("(?is)<binary\\b.*?</binary>"), "")
        val title = Regex("(?is)<book-title>(.*?)</book-title>").find(xml)?.groupValues?.get(1)
            ?.let { stripTags(it).trim() }
        xml = xml.substringAfter("<body", xml)
        xml = xml.replace(Regex("(?i)<title[^>]*>"), "\n")
            .replace(Regex("(?i)</title>"), "\n")
            .replace(Regex("(?i)<empty-line\\s*/?>"), "\n")
            .replace(Regex("(?i)<subtitle[^>]*>"), "\n")
            .replace(Regex("(?i)<p[^>]*>"), "\n")
            .replace(Regex("(?i)<v[^>]*>"), "\n")
            .replace(Regex("(?i)<text-author[^>]*>"), "\n")
        val body = stripTags(xml).trim()
        return listOfNotNull(title?.takeIf { it.isNotEmpty() && !body.startsWith(it) }, body)
            .joinToString("\n\n")
    }

    private fun writeFb2(file: File, text: String) {
        val paras = text.split("\n").joinToString("") { "<p>${escapeXml(it)}</p>" }
        val fb2 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>G134Office</book-title></title-info></description>
              <body><section>$paras</section></body>
            </FictionBook>
        """.trimIndent()
        Files.writeString(file.toPath(), fb2, StandardCharsets.UTF_8)
    }

    private fun readHtml(file: File): String {
        val bytes = Files.readAllBytes(file.toPath())
        val probe = String(bytes, StandardCharsets.UTF_8)
        val csName = Regex("charset\\s*=\\s*[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(probe)?.groupValues?.get(1)
            ?: Regex("encoding\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
                .find(probe)?.groupValues?.get(1)
            ?: "UTF-8"
        val raw = try {
            String(bytes, Charset.forName(csName))
        } catch (_: Exception) {
            probe
        }
        return htmlToText(raw)
    }

    private fun htmlToText(raw: String): String {
        var html = raw
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</div>"), "\n")
            .replace(Regex("(?i)</h1>"), "\n")
            .replace(Regex("(?i)</h2>"), "\n")
            .replace(Regex("(?i)</h3>"), "\n")
            .replace(Regex("(?i)</h4>"), "\n")
            .replace(Regex("(?i)</h5>"), "\n")
            .replace(Regex("(?i)</h6>"), "\n")
            .replace(Regex("(?i)</li>"), "\n")
            .replace(Regex("(?i)</tr>"), "\n")
            .replace(Regex("(?i)</td>"), "\t")
            .replace(Regex("(?i)</th>"), "\t")
            .replace(Regex("(?i)<li[^>]*>"), "• ")
            .replace(Regex("(?i)<h1[^>]*>"), "# ")
            .replace(Regex("(?i)<h2[^>]*>"), "## ")
            .replace(Regex("(?i)<h3[^>]*>"), "### ")
            .replace(Regex("(?i)<h4[^>]*>"), "#### ")
            .replace(Regex("(?i)<h5[^>]*>"), "##### ")
            .replace(Regex("(?i)<h6[^>]*>"), "###### ")
        html = stripTags(html)
        html = html.replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&hellip;", "…")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toInt().toChar().toString()
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toInt(16).toChar().toString()
            }
        return html.trim()
    }

    private fun writeHtml(file: File, text: String) {
        val body = text.split("\n").joinToString("\n") { line ->
            when {
                line.startsWith("###### ") -> "<h6>${escapeXml(line.removePrefix("###### "))}</h6>"
                line.startsWith("##### ") -> "<h5>${escapeXml(line.removePrefix("##### "))}</h5>"
                line.startsWith("#### ") -> "<h4>${escapeXml(line.removePrefix("#### "))}</h4>"
                line.startsWith("### ") -> "<h3>${escapeXml(line.removePrefix("### "))}</h3>"
                line.startsWith("## ") -> "<h2>${escapeXml(line.removePrefix("## "))}</h2>"
                line.startsWith("# ") -> "<h1>${escapeXml(line.removePrefix("# "))}</h1>"
                line.startsWith("• ") -> "<li>${escapeXml(line.removePrefix("• "))}</li>"
                else -> "<p>${escapeXml(line)}</p>"
            }
        }
        val html =
            "<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"UTF-8\"><title>G134Office</title></head><body>$body</body></html>"
        Files.writeString(file.toPath(), html, StandardCharsets.UTF_8)
    }

    private fun readRtf(file: File): String {
        val bytes = Files.readAllBytes(file.toPath())
        val raw = try {
            String(bytes, Charset.forName("windows-1251"))
        } catch (_: Exception) {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
        return decodeRtf(raw).trim()
    }

    internal fun decodeRtf(src: String): String {
        val out = StringBuilder()
        var i = 0
        var skip = 0
        var unicodeSkip = 1
        fun letter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
        while (i < src.length) {
            when (val c = src[i]) {
                '\\' -> {
                    if (i + 1 >= src.length) break
                    when (val n = src[i + 1]) {
                        '\\', '{', '}' -> {
                            if (skip == 0) out.append(n)
                            i += 2
                        }
                        '\'' -> {
                            if (i + 3 < src.length && skip == 0) {
                                val hex = src.substring(i + 2, i + 4)
                                val value = hex.toIntOrNull(16)
                                if (value != null) {
                                    out.append(String(byteArrayOf(value.toByte()), Charset.forName("windows-1251")))
                                }
                            }
                            i += 4
                        }
                        '\n', '\r' -> i += 2
                        '*' -> {
                            skip++
                            i += 2
                        }
                        else -> {
                            var j = i + 1
                            while (j < src.length && letter(src[j])) j++
                            val word = src.substring(i + 1, j)
                            var arg = ""
                            if (j < src.length && (src[j] == '-' || src[j].isDigit())) {
                                val start = j
                                if (src[j] == '-') j++
                                while (j < src.length && src[j].isDigit()) j++
                                arg = src.substring(start, j)
                            }
                            if (j < src.length && src[j] == ' ') j++
                            i = j
                            when (word) {
                                "par", "line" -> if (skip == 0) out.append('\n')
                                "tab" -> if (skip == 0) out.append('\t')
                                "u" -> {
                                    val cp = arg.toIntOrNull() ?: 0
                                    val code = if (cp < 0) cp + 65536 else cp
                                    if (skip == 0 && code in 0..0x10FFFF) out.append(Character.toChars(code))
                                    var skipped = 0
                                    while (skipped < unicodeSkip && i < src.length) {
                                        when (src[i]) {
                                            '\\' -> {
                                                if (i + 1 < src.length && src[i + 1] == '\'' && i + 3 < src.length) i += 4
                                                else break
                                            }
                                            '{', '}' -> break
                                            else -> i++
                                        }
                                        skipped++
                                    }
                                }
                                "uc" -> unicodeSkip = arg.toIntOrNull()?.coerceAtLeast(0) ?: 1
                                "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer" -> skip++
                            }
                        }
                    }
                }
                '{' -> {
                    i++
                    if (skip > 0) skip++
                }
                '}' -> {
                    i++
                    if (skip > 0) skip--
                }
                '\n', '\r' -> i++
                else -> {
                    if (skip == 0) out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun writeRtf(file: File, text: String) {
        val body = buildString {
            for (ch in text) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '{' -> append("\\{")
                    '}' -> append("\\}")
                    '\n' -> append("\\par\r\n")
                    in '\u0000'..'\u007f' -> append(ch)
                    else -> append("\\u${ch.code}?")
                }
            }
        }
        val rtf = "{\\rtf1\\ansi\\ansicpg1251\\deff0{\\fonttbl{\\f0 Arial;}}\\uc1\\f0\\fs24 $body}"
        Files.write(file.toPath(), rtf.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writePdf(file: File, text: String) {
        val fontFile = OfficeFonts.cyrillicTtf()
        PDDocument().use { doc ->
            val font = PDType0Font.load(doc, fontFile)
            var page = PDPage()
            doc.addPage(page)
            var stream = PDPageContentStream(doc, page)
            stream.beginText()
            stream.setFont(font, 12f)
            stream.newLineAtOffset(50f, 750f)
            var y = 750f
            val lines = if (text.isEmpty()) listOf("") else text.split("\n").flatMap { wrapLine(sanitizePdf(it), 95) }
            for (line in lines) {
                if (y < 60f) {
                    stream.endText()
                    stream.close()
                    page = PDPage()
                    doc.addPage(page)
                    stream = PDPageContentStream(doc, page)
                    stream.beginText()
                    stream.setFont(font, 12f)
                    stream.newLineAtOffset(50f, 750f)
                    y = 750f
                }
                stream.showText(line)
                stream.newLineAtOffset(0f, -16f)
                y -= 16f
            }
            stream.endText()
            stream.close()
            doc.save(file)
        }
    }

    private fun sanitizePdf(line: String): String =
        buildString(line.length) {
            for (ch in line) {
                when {
                    ch == '\t' -> append("    ")
                    ch.code < 32 -> append(' ')
                    else -> append(ch)
                }
            }
        }

    private fun wrapLine(line: String, width: Int): List<String> {
        if (line.length <= width) return listOf(line)
        val out = mutableListOf<String>()
        var rest = line
        while (rest.length > width) {
            val cut = rest.lastIndexOf(' ', width).let { if (it < 1) width else it }
            out += rest.substring(0, cut)
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotEmpty()) out += rest
        return out
    }

    private fun readText(file: File): String {
        val bytes = Files.readAllBytes(file.toPath())
        return String(bytes, detectCharset(bytes)).removePrefix("\uFEFF")
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }
        if (isValidUtf8(bytes)) return StandardCharsets.UTF_8
        return Charset.forName("windows-1251")
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val extra = when {
                b and 0x80 == 0 -> 0
                b and 0xE0 == 0xC0 -> 1
                b and 0xF0 == 0xE0 -> 2
                b and 0xF8 == 0xF0 -> 3
                else -> return false
            }
            if (i + extra >= bytes.size) return false
            for (k in 1..extra) {
                if (bytes[i + k].toInt() and 0xC0 != 0x80) return false
            }
            i += extra + 1
        }
        return true
    }

    private fun stripTags(s: String) = s
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")

    private fun escapeXml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun put(zip: ZipOutputStream, name: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
    }

    private fun putStored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val e = ZipEntry(name)
        e.method = ZipEntry.STORED
        e.size = data.size.toLong()
        e.compressedSize = data.size.toLong()
        val crc = java.util.zip.CRC32()
        crc.update(data)
        e.crc = crc.value
        zip.putNextEntry(e)
        zip.write(data)
        zip.closeEntry()
    }
}
