package org.example.ui

import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.DialogPane
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.StackPane
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AboutDialogTest {
    @Test
    fun `update and project link use the same action without accepting the dialog`() = onFx {
        var opened = 0
        val dialog = AboutDialog.create(null, Theme.pack(AppTheme.LIGHT)) { opened++ }
        val update = dialog.dialogPane.lookupButton(dialog.dialogPane.buttonTypes.first()) as Button
        val github = dialog.dialogPane.content.lookup("#about-github") as Hyperlink

        assertEquals("Обновить", update.text)
        assertTrue(update.isDefaultButton)
        assertEquals("https://github.com/G134T/G134Office", AboutDialog.PROJECT_URL)
        assertEquals(AboutDialog.PROJECT_URL, github.tooltip.text)
        update.fire()
        assertEquals(1, opened)
        assertNull(dialog.result)
        github.fire()
        assertEquals(2, opened)
        assertEquals(ButtonBar.ButtonData.CANCEL_CLOSE, dialog.dialogPane.buttonTypes.last().buttonData)
    }

    @Test
    fun `about preserves supported formats limitations and manual update instructions`() = onFx {
        val dialog = AboutDialog.create(null, Theme.pack(AppTheme.DARK)) {}
        val text = labels(dialog.dialogPane.content as Parent).joinToString("\n") { it.text }

        for (required in listOf("G134Office", "DOCX", "DOCM", "DOTX", "ODT", "OTT", "RTF", "HTML",
            "PDF", "FB2", "EPUB", "TXT", "MD", "LanguageTool", "Фикбука", "колонтитулы", "только чтение")) {
            assertTrue(text.contains(required), "Missing about information: $required")
        }
        assertTrue(text.contains("MIT License"))
        assertTrue(text.contains("вдохновлён Apache OpenOffice"))
        assertTrue(text.contains("Установка выполняется вручную"))
        val version = dialog.dialogPane.content.lookup("#about-version") as Label
        assertTrue(version.text == "Сборка для разработки" || version.text.startsWith("Версия "))
    }

    @Test
    fun `details scroll while update stays visible in light dark and small layouts`() = onFx {
        for (theme in listOf(AppTheme.LIGHT, AppTheme.DARK)) {
            for ((width, height) in listOf(620.0 to 650.0, 400.0 to 460.0)) {
                val dialog = AboutDialog.create(null, Theme.pack(theme)) {}
                val pane = dialog.dialogPane
                dialog.dialogPane = DialogPane()
                pane.minHeight = height
                pane.prefHeight = height
                val root = StackPane(pane)
                Scene(root, width, height)
                root.applyCss()
                root.layout()
                val details = pane.content.lookup("#about-details") as ScrollPane
                val update = pane.lookupButton(pane.buttonTypes.first()) as Button
                val buttonBounds = pane.sceneToLocal(update.localToScene(update.boundsInLocal))
                assertTrue(buttonBounds.minY >= 0.0 && buttonBounds.maxY <= height,
                    "$theme ${width}x$height button vertical bounds: $buttonBounds")
                assertTrue(buttonBounds.minX >= 0.0 && buttonBounds.maxX <= width,
                    "$theme ${width}x$height button horizontal bounds: $buttonBounds")
                assertTrue(details.viewportBounds.height > 0.0, "Missing viewport for $theme")
                assertTrue(details.content.layoutBounds.width <= details.viewportBounds.width + 1.0,
                    "$theme content=${details.content.layoutBounds}, viewport=${details.viewportBounds}")
                labels(pane.content as Parent).filter { it.isWrapText }.forEach { label ->
                    assertTrue(label.height + 1.0 >= label.prefHeight(label.width),
                        "Clipped label at ${width}x$height: ${label.text}")
                }
            }
        }
    }

    private fun labels(parent: Parent): List<Label> = parent.childrenUnmodifiable.flatMap { child ->
        when (child) {
            is Label -> listOf(child)
            is ScrollPane -> labels(child.content as Parent)
            is Parent -> labels(child)
            else -> emptyList()
        }
    }

    private fun onFx(block: () -> Unit) {
        val finished = CountDownLatch(1)
        var failure: Throwable? = null
        val action = Runnable {
            try { block() } catch (error: Throwable) { failure = error } finally { finished.countDown() }
        }
        try { Platform.startup(action) } catch (_: IllegalStateException) { Platform.runLater(action) }
        assertTrue(finished.await(30, TimeUnit.SECONDS), "JavaFX test timed out")
        failure?.let { throw it }
    }
}
