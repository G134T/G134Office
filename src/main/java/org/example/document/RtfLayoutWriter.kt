package org.example.document

import org.example.engine.*
import java.awt.Color
import java.io.File
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.rtf.RTFEditorKit

internal object RtfLayoutWriter {
    fun write(file: File, document: Document) {
        val styled = DefaultStyledDocument()
        document.blocks.forEach { block ->
            val paragraphs = when (block) {
                is Paragraph -> listOf(block)
                is TableBlock -> block.rows.map { row -> Paragraph(row.joinToString("\t") { cell ->
                    cell.paragraphs.joinToString(" / ") { it.text }
                }) }
                is ImageBlock -> listOf(Paragraph(block.description.ifBlank { "[Изображение]" }))
            }
            paragraphs.forEach { paragraph ->
                val start = styled.length
                (paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }).forEach { run ->
                    val attributes = SimpleAttributeSet()
                    StyleConstants.setFontFamily(attributes, run.fontFamily ?: paragraph.fontFamily)
                    StyleConstants.setFontSize(attributes, (run.fontSize ?: paragraph.fontSize).toInt())
                    StyleConstants.setBold(attributes, run.bold || paragraph.bold)
                    StyleConstants.setItalic(attributes, run.italic)
                    StyleConstants.setUnderline(attributes, run.underline)
                    run.color?.let { color ->
                        runCatching { Color.decode(color) }.getOrNull()?.let { StyleConstants.setForeground(attributes, it) }
                    }
                    styled.insertString(styled.length, run.text, attributes)
                }
                styled.insertString(styled.length, "\n", null)
                val attributes = SimpleAttributeSet()
                StyleConstants.setAlignment(attributes, when (paragraph.align) {
                    Align.CENTER -> StyleConstants.ALIGN_CENTER
                    Align.RIGHT -> StyleConstants.ALIGN_RIGHT
                    Align.JUSTIFY -> StyleConstants.ALIGN_JUSTIFIED
                    else -> StyleConstants.ALIGN_LEFT
                })
                StyleConstants.setLeftIndent(attributes, paragraph.leftIndentPt.toFloat())
                StyleConstants.setRightIndent(attributes, paragraph.rightIndentPt.toFloat())
                StyleConstants.setFirstLineIndent(attributes, paragraph.firstLineIndentPt.toFloat())
                StyleConstants.setSpaceAbove(attributes, paragraph.spacingBeforePt.toFloat())
                StyleConstants.setSpaceBelow(attributes, paragraph.spacingAfterPt.toFloat())
                StyleConstants.setLineSpacing(attributes, (paragraph.lineSpacing - 1.0).toFloat())
                styled.setParagraphAttributes(start, styled.length - start, attributes, false)
            }
        }
        file.outputStream().use { RTFEditorKit().write(it, styled, 0, styled.length) }
    }
}
