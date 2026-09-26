package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

class StatusBarPane : HBox() {
    private var chrome = UiChrome.STANDARD
    private val path = chip("", "Файл")
    private val position = chip("Стр 1  Кол 1", "Позиция курсора")
    private val words = chip("Слов: 0", "Статистика")
    private val language = chip("Русский", "Язык проверки")
    private val theme = chip("AMOLED", "Тема")
    private val align = chip("Слева", "Выравнивание")
    private val font = chip("Segoe UI 16", "Шрифт")
    private val message = chip("", "Состояние")
    private val labels = listOf(path, position, words, language, theme, align, font, message)
    private val lines = mutableListOf<Separator>()

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
            pad(position), vline(),
            pad(words), pad(message),
            spacer,
            vline(), pad(language),
            vline(), pad(theme),
            vline(), pad(align),
            vline(), pad(font)
        )
    }

    fun setMessage(text: String) { message.text = text }

    fun setChrome(next: UiChrome) { chrome = next }

    fun update(
        filePath: String,
        line: Int,
        col: Int,
        linesCount: Int,
        chars: Int,
        wordsCount: Int,
        languageTitle: String = language.text,
        themeTitle: String = theme.text,
        alignTitle: String = align.text,
        fontTitle: String = font.text
    ) {
        path.text = filePath.ifBlank { "Новый документ" }
        position.text = "Стр $line  Кол $col"
        words.text = "Слов $wordsCount   строк $linesCount   знаков $chars"
        language.text = languageTitle
        theme.text = themeTitle
        align.text = alignTitle
        font.text = fontTitle
    }

    fun applyTheme(pack: Theme.Pack) {
        style = when (chrome) {
            UiChrome.OPEN_OFFICE ->
                "-fx-background-color: linear-gradient(to bottom, #f5f7fa, #dce2e9); -fx-border-color: #a6b0bc; -fx-border-width: 1 0 0 0;"
            UiChrome.MY_OFFICE ->
                "-fx-background-color: #0b5cab; -fx-border-color: #084b8a; -fx-border-width: 1 0 0 0;"
            UiChrome.STANDARD ->
                "-fx-background-color: ${pack.statusBg}; -fx-border-color: ${pack.border}; -fx-border-width: 1 0 0 0;"
        }
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
