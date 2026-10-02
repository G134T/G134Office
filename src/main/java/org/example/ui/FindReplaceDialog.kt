package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import java.util.regex.Pattern
import org.example.engine.EditorCanvas

class FindReplaceDialog(private val editor: EditorCanvas) {
    private var stage: Stage? = null
    private val findField = TextField()
    private val replaceField = TextField()
    private val replaceButton = Button("Заменить")
    private val replaceAllButton = Button("Заменить всё")
    private val matchCase = CheckBox("Регистр")
    private val wholeWord = CheckBox("Слово целиком")
    private val status = Label(" ")

    fun show(owner: Stage, replaceMode: Boolean, pack: Theme.Pack) {
        stage?.takeIf { it.isShowing }?.let {
            it.title = if (replaceMode) "Найти и заменить" else "Найти"
            replaceField.isDisable = !replaceMode
            replaceButton.isDisable = !replaceMode
            replaceAllButton.isDisable = !replaceMode
            it.toFront()
            findField.requestFocus()
            findField.selectAll()
            return
        }

        val window = Stage()
        window.initOwner(owner)
        window.initModality(Modality.NONE)
        window.title = if (replaceMode) "Найти и заменить" else "Найти"

        findField.promptText = "Что искать"
        replaceField.promptText = "На что заменить"
        replaceField.isDisable = !replaceMode

        val findNext = Button("Найти далее")
        val count = Button("Считать")
        val close = Button("Закрыть")
        findNext.isDefaultButton = true
        close.isCancelButton = true

        replaceButton.isDisable = !replaceMode
        replaceAllButton.isDisable = !replaceMode

        findNext.setOnAction { findNext() }
        replaceButton.setOnAction { replaceOne() }
        replaceAllButton.setOnAction { replaceAll() }
        count.setOnAction { showCount() }
        close.setOnAction { window.close() }

        findField.setOnKeyPressed { if (it.code == KeyCode.ENTER) findNext() }
        replaceField.setOnKeyPressed { if (it.code == KeyCode.ENTER) replaceOne() }

        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            add(Label("Найти:"), 0, 0)
            add(findField, 1, 0)
            add(Label("Заменить:"), 0, 1)
            add(replaceField, 1, 1)
            columnConstraints.add(ColumnConstraints(80.0))
            columnConstraints.add(ColumnConstraints().apply { hgrow = Priority.ALWAYS })
        }
        GridPane.setHgrow(findField, Priority.ALWAYS)
        GridPane.setHgrow(replaceField, Priority.ALWAYS)

        val flags = HBox(12.0, matchCase, wholeWord).apply { alignment = Pos.CENTER_LEFT }
        val actions = HBox(8.0, findNext, replaceButton, replaceAllButton, count, close)
        status.style = "-fx-text-fill: ${Theme.ink(pack).hintFg}; -fx-font-size: 12px;"

        val root = VBox(12.0, grid, flags, actions, status).apply {
            padding = Insets(16.0)
            style = "-fx-background-color: ${pack.popupBg};"
        }

        window.scene = Scene(root, 560.0, 220.0).apply {
            stylesheets.add(Theme.dialogStylesheet(pack))
        }
        window.setOnCloseRequest { stage = null }
        stage = window
        window.show()
        findField.requestFocus()
        val selected = editor.selectedRange()?.let { editor.plainText().substring(it.first, it.last + 1) }.orEmpty()
        val oneLine = selected.substringBefore('\n')
        if (oneLine.isNotEmpty() && !selected.contains('\n') && oneLine.length <= 80) {
            findField.text = oneLine
            findField.selectAll()
        }
    }

    private fun findNext() {
        val query = findField.text
        if (query.isEmpty()) {
            status.text = "Пустой запрос."
            return
        }
        val text = editor.plainText()
        val regex = buildRegex(query)
        val from = editor.caretOffset().coerceAtMost(text.length)
        val matcher = regex.matcher(text)
        val found = when {
            matcher.find(from) -> matcher.start() to matcher.end()
            matcher.reset().find() -> matcher.start() to matcher.end()
            else -> null
        }
        if (found == null) {
            status.text = "Совпадений нет."
            return
        }
        editor.requestFocus()
        editor.selectRange(found.first, found.second)
        status.text = "Найдено на позиции ${found.first + 1}."
    }

    private fun replaceOne() {
        val selected = editor.selectedRange()
        if (selected != null && buildRegex(findField.text).matcher(editor.plainText().substring(selected.first, selected.last + 1)).matches()) {
            editor.replaceRange(selected.first, selected.last + 1, replaceField.text)
        }
        findNext()
    }

    private fun replaceAll() {
        val query = findField.text
        if (query.isEmpty()) return
        val regex = buildRegex(query)
        val original = editor.plainText()
        val matcher = regex.matcher(original)
        val ranges = mutableListOf<IntRange>()
        while (matcher.find()) {
            ranges += matcher.start() until matcher.end()
        }
        editor.replaceRanges(ranges, replaceField.text)
        val n = ranges.size
        status.text = if (n == 0) "Нечего заменять." else "Заменено: $n"
    }

    private fun showCount() {
        val query = findField.text
        if (query.isEmpty()) {
            status.text = "Пустой запрос."
            return
        }
        val matcher = buildRegex(query).matcher(editor.plainText())
        var n = 0
        while (matcher.find()) n++
        status.text = "Вхождений: $n"
    }

    private fun buildRegex(query: String): Pattern {
        val body = Pattern.quote(query)
        val wrapped = if (wholeWord.isSelected) "\\b$body\\b" else body
        val flags = if (matchCase.isSelected) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(wrapped, flags)
    }
}
