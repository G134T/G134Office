package org.example.engine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DocumentTest {
    @Test
    fun `loading text preserves paragraphs including trailing blank line`() {
        val source = "Первая\n\nПоследняя\n"
        val document = Document.fromText(source)
        assertEquals(listOf("Первая", "", "Последняя", ""), document.paragraphs.map { it.text })
        assertEquals(source, document.toPlainText())
    }
}
