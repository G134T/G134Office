package org.example.document

import org.example.engine.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.Document as XmlDocument
import java.io.File
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Reads the layout-bearing subset of OpenDocument without flattening its blocks. */
internal object OdtLayoutReader {
    private const val OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private const val TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private const val STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private const val FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
    private const val TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private const val DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    private const val XLINK = "http://www.w3.org/1999/xlink"
    private const val SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"

    fun read(file: File): Document = ZipFile(file).use { zip ->
        val content = parse(zip.getInputStream(zip.getEntry("content.xml")
            ?: throw IllegalArgumentException("В ODT нет content.xml")).readBytes())
        val styles = zip.getEntry("styles.xml")?.let { parse(zip.getInputStream(it).readBytes()) }
        val stylesByName = mutableMapOf<String, Element>()
        listOfNotNull(styles, content).forEach { xml ->
            val nodes = xml.getElementsByTagNameNS(STYLE, "style")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                stylesByName[element.getAttributeNS(STYLE, "name")] = element
            }
        }
        val document = Document()
        val page = listOfNotNull(styles, content).asSequence().mapNotNull {
            it.getElementsByTagNameNS(STYLE, "page-layout-properties").item(0) as? Element
        }.firstOrNull()
        page?.let {
            length(it.getAttributeNS(FO, "page-width"))?.let { value -> document.pageWidthPt = value }
            length(it.getAttributeNS(FO, "page-height"))?.let { value -> document.pageHeightPt = value }
            length(it.getAttributeNS(FO, "margin-left"))?.let { value -> document.marginLeftPt = value }
            length(it.getAttributeNS(FO, "margin-right"))?.let { value -> document.marginRightPt = value }
            length(it.getAttributeNS(FO, "margin-top"))?.let { value -> document.marginTopPt = value }
            length(it.getAttributeNS(FO, "margin-bottom"))?.let { value -> document.marginBottomPt = value }
        }
        document.landscape = document.pageWidthPt > document.pageHeightPt
        val body = content.getElementsByTagNameNS(OFFICE, "text").item(0) as? Element
        val blocks = mutableListOf<DocumentBlock>()
        if (body != null) children(body).forEach { element ->
            when {
                element.namespaceURI == TEXT && element.localName in setOf("p", "h") -> {
                    blocks += paragraph(element, stylesByName)
                    images(element, zip).forEach { blocks += it }
                }
                element.namespaceURI == TABLE && element.localName == "table" -> {
                    val rows = children(element).filter { it.namespaceURI == TABLE && it.localName == "table-row" }
                        .map { row ->
                            children(row).filter { it.namespaceURI == TABLE && it.localName == "table-cell" }
                                .map { cell ->
                                    TableCell(children(cell).filter { it.namespaceURI == TEXT && it.localName in setOf("p", "h") }
                                        .map { paragraph(it, stylesByName) }.toMutableList())
                                }.toMutableList()
                        }.toMutableList()
                    blocks += TableBlock(rows)
                }
            }
        }
        document.loadBlocks(blocks)
        document
    }

    private fun paragraph(element: Element, styles: Map<String, Element>): Paragraph {
        val style = styles[element.getAttributeNS(TEXT, "style-name")]
        val properties = style?.getElementsByTagNameNS(STYLE, "paragraph-properties")?.item(0) as? Element
        val textProperties = style?.getElementsByTagNameNS(STYLE, "text-properties")?.item(0) as? Element
        val paragraph = Paragraph(align = when (properties?.getAttributeNS(FO, "text-align")) {
            "center" -> Align.CENTER
            "end", "right" -> Align.RIGHT
            "justify" -> Align.JUSTIFY
            else -> Align.LEFT
        })
        properties?.let {
            length(it.getAttributeNS(FO, "margin-left"))?.let { value -> paragraph.leftIndentPt = value }
            length(it.getAttributeNS(FO, "margin-right"))?.let { value -> paragraph.rightIndentPt = value }
            length(it.getAttributeNS(FO, "text-indent"))?.let { value -> paragraph.firstLineIndentPt = value }
            length(it.getAttributeNS(FO, "margin-top"))?.let { value -> paragraph.spacingBeforePt = value }
            length(it.getAttributeNS(FO, "margin-bottom"))?.let { value -> paragraph.spacingAfterPt = value }
            val spacing = it.getAttributeNS(FO, "line-height")
            if (spacing.endsWith('%')) paragraph.lineSpacing = (spacing.dropLast(1).toDoubleOrNull() ?: 100.0) / 100.0
            paragraph.pageBreakBefore = it.getAttributeNS(FO, "break-before") == "page"
        }
        textProperties?.let {
            length(it.getAttributeNS(FO, "font-size"))?.let { value -> paragraph.fontSize = value }
            it.getAttributeNS(STYLE, "font-name").takeIf { name -> name.isNotBlank() }?.let { name -> paragraph.fontFamily = name }
        }
        val runs = mutableListOf<TextRun>()
        fun append(node: Node, inherited: Element?) {
            when (node.nodeType) {
                Node.TEXT_NODE -> node.nodeValue?.takeIf { it.isNotEmpty() }?.let { value ->
                    runs += TextRun(value,
                        fontFamily = inherited?.getAttributeNS(STYLE, "font-name")?.takeIf { it.isNotBlank() },
                        fontSize = inherited?.getAttributeNS(FO, "font-size")?.let(::length),
                        bold = inherited?.getAttributeNS(FO, "font-weight") == "bold",
                        italic = inherited?.getAttributeNS(FO, "font-style") == "italic",
                        underline = inherited?.getAttributeNS(STYLE, "text-underline-style")?.let { it.isNotBlank() && it != "none" } ?: false,
                        color = inherited?.getAttributeNS(FO, "color")?.takeIf { it.isNotBlank() })
                }
                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    when {
                        child.namespaceURI == TEXT && child.localName == "s" ->
                            runs += TextRun(" ".repeat(child.getAttributeNS(TEXT, "c").toIntOrNull()?.coerceIn(1, 1000) ?: 1))
                        child.namespaceURI == TEXT && child.localName == "tab" -> runs += TextRun("\t")
                        child.namespaceURI == TEXT && child.localName == "line-break" -> runs += TextRun("\n")
                        child.namespaceURI == TEXT && child.localName == "span" -> {
                            val spanStyle = styles[child.getAttributeNS(TEXT, "style-name")]
                            val props = spanStyle?.getElementsByTagNameNS(STYLE, "text-properties")?.item(0) as? Element
                            val nodes = child.childNodes
                            for (i in 0 until nodes.length) append(nodes.item(i), props ?: inherited)
                        }
                        else -> {
                            val nodes = child.childNodes
                            for (i in 0 until nodes.length) append(nodes.item(i), inherited)
                        }
                    }
                }
            }
        }
        val nodes = element.childNodes
        for (i in 0 until nodes.length) append(nodes.item(i), textProperties)
        paragraph.setRuns(runs)
        return paragraph
    }

    private fun images(paragraph: Element, zip: ZipFile): List<ImageBlock> {
        val frames = paragraph.getElementsByTagNameNS(DRAW, "frame")
        return (0 until frames.length).mapNotNull { index ->
            val frame = frames.item(index) as Element
            val image = frame.getElementsByTagNameNS(DRAW, "image").item(0) as? Element ?: return@mapNotNull null
            val path = image.getAttributeNS(XLINK, "href").removePrefix("./")
            val entry = zip.getEntry(path) ?: return@mapNotNull null
            val type = when (path.substringAfterLast('.').lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                else -> "image/png"
            }
            ImageBlock(zip.getInputStream(entry).readBytes(), type,
                length(frame.getAttributeNS(SVG, "width")) ?: 300.0,
                length(frame.getAttributeNS(SVG, "height")) ?: 200.0)
        }
    }

    private fun parse(bytes: ByteArray): XmlDocument {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        return factory.newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun children(element: Element): List<Element> = (0 until element.childNodes.length)
        .mapNotNull { element.childNodes.item(it) as? Element }

    private fun length(raw: String): Double? {
        val match = Regex("^(-?[0-9.]+)(cm|mm|in|pt|px)?$").matchEntire(raw.trim()) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        return number * when (match.groupValues[2]) {
            "cm" -> 72.0 / 2.54
            "mm" -> 72.0 / 25.4
            "in" -> 72.0
            "px" -> 0.75
            else -> 1.0
        }
    }
}
