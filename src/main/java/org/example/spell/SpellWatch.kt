package org.example.spell

import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import javafx.stage.Popup
import javafx.util.Duration
import org.example.engine.EditorCanvas
import java.util.concurrent.atomic.AtomicInteger

class SpellWatch(
    private val canvas: EditorCanvas,
    private val lang: () -> DocLanguage
) {
    private val popup = Popup()
    private val box = VBox(5.0)
    private var delay: PauseTransition? = null
    private val generation = AtomicInteger(0)
    private var lastShownKey: String? = null
    private var currentFixes: List<Pair<IntRange, String>> = emptyList()

    init {
        box.padding = Insets(8.0, 12.0, 10.0, 12.0)
        box.maxWidth = 360.0
        box.style = """
            -fx-background-color: rgba(12,12,14,0.96);
            -fx-border-color: #4da3ff;
            -fx-border-width: 1;
            -fx-background-radius: 10;
            -fx-border-radius: 10;
        """.trimIndent()
        popup.content.add(box)
        popup.isAutoHide = true
        popup.isHideOnEscape = true
    }

    fun attach() {
        canvas.addEventHandler(KeyEvent.KEY_RELEASED) { ev ->
            when (ev.code) {
                KeyCode.ESCAPE -> popup.hide()
                KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3,
                KeyCode.DIGIT4, KeyCode.DIGIT5 -> {
                    if (popup.isShowing) {
                        applyIndex(ev.code.ordinal - KeyCode.DIGIT1.ordinal)
                        ev.consume()
                    }
                }
                KeyCode.SPACE, KeyCode.ENTER, KeyCode.PERIOD,
                KeyCode.COMMA, KeyCode.SEMICOLON -> schedule(140.0)
                else -> if (ev.code.isLetterKey || ev.code == KeyCode.BACK_SPACE) schedule(560.0)
            }
        }
        canvas.focusedProperty().addListener { _, _, on -> if (!on) popup.hide() }
    }

    private fun schedule(ms: Double) {
        delay?.stop()
        delay = PauseTransition(Duration.millis(ms)).also {
            it.setOnFinished { scan() }
            it.play()
        }
    }

    private fun scan() {
        val text = canvas.plainText()
        val caret = canvas.caretOffset().coerceIn(0, text.length)
        if (text.isBlank()) {
            popup.hide()
            return
        }
        val chunkStart = (caret - 320).coerceAtLeast(0)
        val chunk = text.substring(chunkStart, caret)
        val token = lastToken(chunk)
        if (token.length < 2) {
            popup.hide()
            return
        }
        val key = "${lang().code}|$token|${chunk.takeLast(40)}"
        if (key == lastShownKey && popup.isShowing) return
        val gen = generation.incrementAndGet()
        Thread({
            val issues = try {
                SpellEngine.check(chunk, lang())
            } catch (_: Exception) {
                emptyList()
            }
            Platform.runLater {
                if (gen != generation.get()) return@runLater
                showIssues(text, chunkStart, caret, issues, key)
            }
        }, "g134-spell").apply { isDaemon = true }.start()
    }

    private fun showIssues(
        fullText: String,
        chunkStart: Int,
        caret: Int,
        issues: List<SpellIssue>,
        key: String
    ) {
        val near = issues.filter { chunkStart + it.to >= caret - 40 }
        if (near.isEmpty()) {
            popup.hide()
            lastShownKey = null
            return
        }
        val last = near.maxBy { it.to }
        val absFrom = (chunkStart + last.from).coerceIn(0, fullText.length)
        val absTo = (chunkStart + last.to).coerceIn(absFrom, fullText.length)
        if (absFrom == absTo) {
            popup.hide()
            return
        }
        lastShownKey = key
        currentFixes = last.replacements.take(5).map { absFrom until absTo to it }
        box.children.clear()
        box.children += Label("Возможно ошибка: «${last.word}»").apply {
            style = "-fx-text-fill: #ff6b6b; -fx-font-size: 12px; -fx-font-weight: bold;"
            isWrapText = true
            maxWidth = 330.0
        }
        box.children += Label(last.message).apply {
            style = "-fx-text-fill: #c8c8c8; -fx-font-size: 11px;"
            isWrapText = true
            maxWidth = 330.0
        }
        if (currentFixes.isEmpty()) {
            box.children += Label("Вариантов нет").apply {
                style = "-fx-text-fill: #888; -fx-font-size: 11px;"
            }
        } else {
            currentFixes.forEachIndexed { index, (_, fix) ->
                box.children += Label("${index + 1}.  $fix").apply {
                    style = "-fx-text-fill: #7ec8ff; -fx-font-size: 13px;"
                    setOnMouseClicked { applyIndex(index) }
                }
            }
        }
        val p = canvas.localToScreen(24.0, 48.0)
        if (p != null) popup.show(canvas, p.x, p.y)
        canvas.requestFocus()
    }

    private fun applyIndex(index: Int) {
        val fix = currentFixes.getOrNull(index) ?: return
        canvas.replaceRange(fix.first.first, fix.first.endExclusive, fix.second)
        popup.hide()
        lastShownKey = null
        currentFixes = emptyList()
    }

    private fun lastToken(chunk: String): String {
        val trimmed = chunk.trimEnd()
        val idx = trimmed.indexOfLast { ch ->
            ch.isWhitespace() || ch in ".,;:!?«»\"'()[]{}"
        }
        return trimmed.substring(if (idx < 0) 0 else idx + 1)
    }
}