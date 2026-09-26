package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutTest {
    @Test
    fun `explicit page break and table rows create separate pages`() {
        val doc = Document()
        val first = Paragraph("Первая страница")
        val second = Paragraph("Вторая страница").also { it.pageBreakBefore = true }
        val rows = MutableList(30) { index ->
            mutableListOf(TableCell(mutableListOf(Paragraph("Строка $index"))))
        }
        doc.loadBlocks(listOf(first, second, TableBlock(rows)))
        val layout = Layout.build(doc)
        assertTrue(layout.pages.size >= 3)
        assertEquals(0, layout.pages[0].lines.first().paragraphIndex)
        assertEquals(1, layout.pages[1].lines.first().paragraphIndex)
        assertEquals(30, layout.pages.sumOf { it.objects.size })
    }

    @Test
    fun `layout keeps spaces tabs and hard line breaks`() {
        val original = "Первый  второй\tтретий\nНовая строка"
        val document = Document.fromText("")
        document.loadBlocks(listOf(Paragraph(original)))
        val lines = Layout.build(document).pages.flatMap { it.lines }
        assertEquals(original.replace("\n", ""), lines.joinToString("") { it.text })
        assertTrue(lines.any { it.startInParagraph > original.indexOf('\n') })
    }
}
