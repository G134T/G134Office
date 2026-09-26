package org.example.ui

import javafx.geometry.Insets
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.GridPane
import javafx.stage.Window
import javafx.util.StringConverter
import org.example.engine.Paper

data class NewDocChoice(val paper: Paper, val landscape: Boolean)

object NewDocumentDialog {
    fun show(owner: Window?, pack: Theme.Pack, defaultPaper: Paper = Paper.A4, defaultLandscape: Boolean = false): NewDocChoice? {
        val dialog = Dialog<NewDocChoice>()
        dialog.title = "Создать документ"
        dialog.headerText = "Формат листа"
        if (owner != null) dialog.initOwner(owner)

        val paperBox = ComboBox<Paper>().apply {
            items.addAll(Paper.entries)
            value = defaultPaper
            prefWidth = 280.0
            converter = object : StringConverter<Paper>() {
                override fun toString(p: Paper?) = p?.title ?: ""
                override fun fromString(s: String?) =
                    Paper.entries.firstOrNull { it.title == s } ?: Paper.A4
            }
        }
        val group = ToggleGroup()
        val portrait = RadioButton("Книжная").apply { toggleGroup = group; isSelected = !defaultLandscape }
        val landscape = RadioButton("Альбомная").apply { toggleGroup = group; isSelected = defaultLandscape }

        val grid = GridPane().apply {
            hgap = 12.0
            vgap = 12.0
            padding = Insets(12.0, 8.0, 4.0, 8.0)
            add(Label("Размер:"), 0, 0)
            add(paperBox, 1, 0)
            add(Label("Ориентация:"), 0, 1)
            add(portrait, 1, 1)
            add(landscape, 1, 2)
        }
        dialog.dialogPane.content = grid
        val ok = ButtonType("Создать", ButtonBar.ButtonData.OK_DONE)
        val cancel = ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.buttonTypes.setAll(ok, cancel)

        val ink = Theme.ink(pack)
        val css = """
            .dialog-pane { -fx-background-color: ${pack.popupBg}; }
            .dialog-pane > .header-panel { -fx-background-color: ${pack.menuBg}; }
            .dialog-pane > .header-panel .label { -fx-text-fill: ${pack.menuFg}; }
            .dialog-pane .label { -fx-text-fill: ${pack.popupFg}; }
            .dialog-pane .radio-button { -fx-text-fill: ${pack.popupFg}; }
            .dialog-pane .combo-box { -fx-background-color: ${pack.popupBg}; }
            .dialog-pane .combo-box .list-cell { -fx-text-fill: ${pack.popupFg}; -fx-background-color: transparent; }
            .dialog-pane .button {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-border-color: ${pack.border};
            }
            .dialog-pane .button:hover {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-text-fill: ${ink.itemHoverFg};
            }
        """.trimIndent()
        dialog.dialogPane.style = "-fx-background-color: ${pack.popupBg};"
        dialog.dialogPane.stylesheets.add(
            "data:text/css," + css.replace("\n", " ").replace("#", "%23")
        )

        dialog.setResultConverter { btn ->
            if (btn == ok) NewDocChoice(paperBox.value ?: Paper.A4, landscape.isSelected)
            else null
        }
        return dialog.showAndWait().orElse(null)
    }
}
