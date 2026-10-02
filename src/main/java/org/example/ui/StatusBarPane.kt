package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class StatusBarPane : HBox() {
    private val path = chip("", "Файл").apply {
        maxWidth = 320.0
        textOverrun = OverrunStyle.CENTER_ELLIPSIS
    }
    private val position = chip("Стр. 1 из 1", "Страница")
    private val caret = chip("1:1", "Строка и столбец")
    private val words = chip("Слов 0", "Статистика")
    private val zoom = chip("100%", "Масштаб")
    private val language = chip("Русский", "Язык проверки")
    private val theme = chip("AMOLED", "Тема")
    private val align = chip("Слева", "Выравнивание")
    private val font = chip("Segoe UI 16", "Шрифт")
    private val message = chip("", "Состояние")
    private val labels = listOf(path, position, caret, words, zoom, language, theme, align, font, message)
    private val lines = mutableListOf<Separator>()
    private val caretPad = pad(caret)
    private val caretLine = vline()
    private val wordsPad = pad(words)
    private val zoomPad = pad(zoom)
    private val zoomLine = vline()
    private val fontPad = pad(font)
    private val messagePad = pad(message).apply {
        isVisible = false
        isManaged = false
    }

    init {
        alignment = Pos.CENTER_LEFT
        spacing = 0.0
        padding = Insets(0.0)
        minHeight = 28.0
        prefHeight = 28.0
        val spacer = Region()
        setHgrow(spacer, Priority.ALWAYS)
        children.addAll(
            pad(path), vline(),
            pad(position), caretLine, caretPad, vline(),
            wordsPad, messagePad,
            spacer,
            zoomLine, zoomPad,
            vline(), pad(language),
            vline(), pad(theme),
            vline(), pad(align),
            vline(), fontPad
        )
    }

    fun setMessage(text: String) {
        message.text = text
        val show = text.isNotBlank()
        messagePad.isVisible = show
        messagePad.isManaged = show
    }

    fun setChrome(@Suppress("UNUSED_PARAMETER") next: UiChrome) = Unit

    fun update(
        filePath: String,
        page: Int,
        pageCount: Int,
        line: Int,
        column: Int,
        textLines: Int,
        wordsCount: Int,
        chars: Int,
        zoomPercent: Int,
        languageTitle: String = language.text,
        themeTitle: String = theme.text,
        alignTitle: String = align.text,
        fontTitle: String = font.text,
        summary: String? = null,
        showCaret: Boolean = true
    ) {
        val shownPath = filePath.ifBlank { "Новый документ" }
        path.text = shownPath
        path.tooltip?.text = filePath.ifBlank { "Новый документ" }
        position.text = "Стр. $page из $pageCount"
        caret.text = "$line:$column"
        caret.tooltip?.text = "Строка $line, столбец $column"
        caretPad.isVisible = showCaret
        caretPad.isManaged = showCaret
        caretLine.isVisible = showCaret
        caretLine.isManaged = showCaret
        if (summary == null) words.text = "Слов $wordsCount · строк $textLines · знаков $chars"
        else words.text = summary
        wordsPad.isVisible = words.text.isNotBlank()
        wordsPad.isManaged = wordsPad.isVisible
        zoom.text = "$zoomPercent%"
        val showZoom = summary == null
        zoomPad.isVisible = showZoom
        zoomPad.isManaged = showZoom
        zoomLine.isVisible = showZoom
        zoomLine.isManaged = showZoom
        language.text = languageTitle
        theme.text = themeTitle
        align.text = alignTitle
        font.text = fontTitle
        val showFont = fontTitle.isNotBlank()
        fontPad.isVisible = showFont
        fontPad.isManaged = showFont
    }

    fun applyTheme(pack: Theme.Pack) {
        style = "-fx-background-color: ${pack.statusBg}; -fx-border-color: ${pack.border}; -fx-border-width: 1 0 0 0;"
        val text = "-fx-text-fill: ${pack.statusFg}; -fx-font-size: 11px;"
        labels.forEach { it.style = text }
        lines.forEach {
            it.style = "-fx-background-color: ${pack.border}; -fx-pref-width: 1;"
        }
    }

    private fun pad(node: Label) = VBox(node).apply {
        alignment = Pos.CENTER_LEFT
        padding = Insets(0.0, 10.0, 0.0, 10.0)
        prefHeight = 28.0
    }

    private fun chip(text: String, tip: String) = Label(text).apply { tooltip = Tooltip(tip) }

    private fun vline(): Separator {
        val s = Separator(Orientation.VERTICAL)
        s.maxHeight = Double.MAX_VALUE
        s.prefHeight = 28.0
        lines += s
        return s
    }
}
