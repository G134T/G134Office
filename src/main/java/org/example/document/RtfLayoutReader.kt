package org.example.document

import org.example.engine.*
import java.io.File
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument
import javax.swing.text.rtf.RTFEditorKit

internal object RtfLayoutReader {
    fun read(file: File): Document {
        val kit = RTFEditorKit()
        val styled = kit.createDefaultDocument() as StyledDocument
        file.inputStream().use { kit.read(it, styled, 0) }
        val blocks = mutableListOf<DocumentBlock>()
        val root = styled.defaultRootElement
        for (index in 0 until root.elementCount) {
            val source = root.getElement(index)
            val attributes = source.attributes
            val paragraph = Paragraph(align = when (StyleConstants.getAlignment(attributes)) {
                StyleConstants.ALIGN_CENTER -> Align.CENTER
                StyleConstants.ALIGN_RIGHT -> Align.RIGHT
                StyleConstants.ALIGN_JUSTIFIED -> Align.JUSTIFY
                else -> Align.LEFT
            })
            paragraph.leftIndentPt = StyleConstants.getLeftIndent(attributes).toDouble()
            paragraph.rightIndentPt = StyleConstants.getRightIndent(attributes).toDouble()
            paragraph.firstLineIndentPt = StyleConstants.getFirstLineIndent(attributes).toDouble()
            paragraph.spacingBeforePt = StyleConstants.getSpaceAbove(attributes).toDouble()
            paragraph.spacingAfterPt = StyleConstants.getSpaceBelow(attributes).toDouble()
            paragraph.lineSpacing = (1.0 + StyleConstants.getLineSpacing(attributes)).coerceIn(0.8, 3.0)
            val runs = mutableListOf<TextRun>()
            for (part in 0 until source.elementCount) {
                val segment = source.getElement(part)
                val start = segment.startOffset.coerceAtMost(styled.length)
                val end = segment.endOffset.coerceAtMost(styled.length)
                if (end <= start) continue
                val value = styled.getText(start, end - start).trimEnd('\n')
                if (value.isEmpty()) continue
                val style = segment.attributes
                runs += TextRun(value,
                    StyleConstants.getFontFamily(style), StyleConstants.getFontSize(style).toDouble(),
                    StyleConstants.isBold(style), StyleConstants.isItalic(style), StyleConstants.isUnderline(style),
                    StyleConstants.getForeground(style)?.let { color ->
                        "#%02X%02X%02X".format(color.red, color.green, color.blue)
                    })
            }
            paragraph.setRuns(runs)
            blocks += paragraph
        }
        return Document().also { it.loadBlocks(blocks) }
    }
}
