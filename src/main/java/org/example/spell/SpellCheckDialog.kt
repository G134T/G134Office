package org.example.spell

import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import org.example.engine.EditorCanvas
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage

class SpellCheckDialog(
    private val owner: Stage,
    private val editor: EditorCanvas
) {
    fun show(lang: DocLanguage) {
        val issues = try {
            SpellEngine.check(editor.plainText(), lang)
        } catch (ex: Exception) {
            javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR,
                ex.message ?: "LanguageTool не поднялся"
            ).apply {
                title = "Проверка"
                headerText = "Словари не загрузились"
            }.showAndWait()
            return
        }

        val list = ListView<String>()
        if (issues.isEmpty()) {
            list.items.add("Ошибок не нашёл.")
        } else {
            issues.forEach { issue ->
                val hint = issue.replacements.firstOrNull() ?: "—"
                list.items.add("«${issue.word}» → $hint   (${issue.message})")
            }
        }

        list.selectionModel.selectedIndexProperty().addListener { _, _, idx ->
            val i = idx.toInt()
            if (i in issues.indices) {
                editor.requestFocus()
                editor.selectRange(issues[i].from, issues[i].to)
            }
        }

        val replace = Button("Заменить")
        replace.setOnAction {
            val i = list.selectionModel.selectedIndex
            if (i !in issues.indices) return@setOnAction
            val issue = issues[i]
            val replacement = issue.replacements.firstOrNull() ?: return@setOnAction
            editor.replaceRange(issue.from, issue.to, replacement)
            (replace.scene.window as? Stage)?.close()
            show(lang)
        }

        val close = Button("Закрыть")
        val root = BorderPane().apply {
            padding = Insets(12.0)
            top = Label("Язык: ${lang.title}. Кликни строку — прыгнет в текст.")
            center = list
            bottom = VBox(8.0, HBox(8.0, replace, close)).apply { padding = Insets(8.0, 0.0, 0.0, 0.0) }
        }

        val stage = Stage()
        stage.initOwner(owner)
        stage.initModality(Modality.NONE)
        stage.title = "Проверка текста"
        stage.scene = Scene(root, 640.0, 360.0)
        close.setOnAction { stage.close() }
        stage.show()
    }
}
