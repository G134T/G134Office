package org.example.ui

import javafx.application.Platform
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class WordRibbonTest {
    @Test
    fun `standard themes split home review and view the way word does`() {
        onFx {
            val ribbon = RibbonBar()
            ribbon.setChrome(UiChrome.WORD)
            ribbon.applyTheme(Theme.pack(AppTheme.LIGHT))

            assertNotNull(ribbon.paste.parent)
            assertNotNull(ribbon.formatPainter.parent)
            assertNotNull(ribbon.styleHeading1.parent)
            assertNotNull(ribbon.lineSpacingBox.parent)
            assertNotNull(ribbon.undo.parent)
            assertNull(ribbon.spell.parent)
            assertNull(ribbon.ruler.parent)
            assertEquals("Главная", ribbon.wordTabTitle(0))

            ribbon.selectWordTab(1)
            assertEquals("Вставка", ribbon.wordTabTitle(1))
            assertNotNull(ribbon.insertSymbol.parent)
            assertNotNull(ribbon.insertDate.parent)
            assertNotNull(ribbon.pageBreak.parent)
            assertNull(ribbon.paste.parent)
            assertNull(ribbon.landscape.parent)

            ribbon.selectWordTab(2)
            assertEquals("Формат", ribbon.wordTabTitle(2))
            assertEquals(1, ribbon.children.size)
            assertNotNull(ribbon.landscape.parent)
            assertNotNull(ribbon.marginsLess.parent)
            assertNotNull(ribbon.marginsMore.parent)
            assertNull(ribbon.insertSymbol.parent)
            assertNull(ribbon.paste.parent)

            ribbon.selectWordTab(3)
            assertEquals("Рецензирование", ribbon.wordTabTitle(3))
            assertNotNull(ribbon.spell.parent)
            assertNotNull(ribbon.wordCount.parent)
            assertNotNull(ribbon.langBox.parent)
            assertNull(ribbon.paste.parent)
            assertNull(ribbon.styleNormal.parent)

            ribbon.selectWordTab(4)
            assertEquals("Вид", ribbon.wordTabTitle(4))
            assertNotNull(ribbon.ruler.parent)
            assertNotNull(ribbon.navigation.parent)
            assertNotNull(ribbon.zoomIn.parent)
            assertNotNull(ribbon.fitWidth.parent)
            assertNotNull(ribbon.readMode.parent)
            assertNotNull(ribbon.themeFamilyBox.parent)
            assertNotNull(ribbon.layoutBox.parent)
            assertNull(ribbon.spell.parent)
            assertNull(ribbon.paste.parent)

            ribbon.setChrome(UiChrome.STANDARD)
            ribbon.applyTheme(Theme.pack(AppTheme.WORD_2023))
            assertNull(ribbon.styleHeading1.parent)
            assertNull(ribbon.readMode.parent)
            assertNotNull(ribbon.paste.parent)
            assertNotNull(ribbon.spell.parent)
            assertNotNull(ribbon.ruler.parent)
        }
    }

    @Test
    fun `myoffice keeps one toolbar and a contextual inspector`() {
        onFx {
            val ribbon = RibbonBar()
            ribbon.setChrome(UiChrome.MY_OFFICE)
            ribbon.applyTheme(Theme.pack(AppTheme.MYOFFICE))
            assertEquals("Абзац", ribbon.inspectorHeading())
            assertTrue(ribbon.onFixedBar(ribbon.paste))
            assertTrue(ribbon.onFixedBar(ribbon.bold))
            assertFalse(ribbon.onFixedBar(ribbon.styleHeading1))
            assertTrue(ribbon.inspectorShows(ribbon.styleHeading1))
            assertTrue(ribbon.inspectorShows(ribbon.insertSymbol))
            assertFalse(ribbon.inspectorShows(ribbon.fontFamily))

            ribbon.showContext(selected = true, heading = false)
            assertEquals("Текст", ribbon.inspectorHeading())
            assertTrue(ribbon.inspectorShows(ribbon.fontFamily))
            assertTrue(ribbon.inspectorShows(ribbon.fontColor))
            assertFalse(ribbon.inspectorShows(ribbon.styleHeading1))
            assertTrue(ribbon.inspectorShows(ribbon.insertSymbol))

            ribbon.showContext(selected = false, heading = true)
            assertEquals("Заголовок", ribbon.inspectorHeading())
            assertTrue(ribbon.inspectorShows(ribbon.styleHeading1))
            assertTrue(ribbon.inspectorShows(ribbon.fontSizeBox))
            assertFalse(ribbon.inspectorShows(ribbon.indentMore))
        }
    }

    @Test
    fun `classic toolbar follows the dark palette instead of a fixed blue`() {
        onFx {
            val ribbon = RibbonBar()
            ribbon.setChrome(UiChrome.OPEN_OFFICE)
            ribbon.applyTheme(Theme.pack(AppTheme.DARK))
            assertTrue(ribbon.bold.style.contains(Theme.pack(AppTheme.DARK).buttonFg))
            assertTrue(ribbon.style.contains(Theme.pack(AppTheme.DARK).ribbonBg))
            assertNotNull(ribbon.undo.parent)
            assertNotNull(ribbon.insertSymbol.parent)
            assertNotNull(ribbon.layoutBox.parent)
        }
    }
}

private fun onFx(block: () -> Unit) {
    val latch = CountDownLatch(1)
    var error: Throwable? = null
    val job = Runnable {
        try {
            block()
        } catch (thrown: Throwable) {
            error = thrown
        } finally {
            latch.countDown()
        }
    }
    try {
        Platform.startup(job)
    } catch (_: IllegalStateException) {
        Platform.runLater(job)
    }
    if (!latch.await(30, TimeUnit.SECONDS)) fail("JavaFX не успел собрать ленту")
    error?.let { throw it }
}
