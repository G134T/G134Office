package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.Window
import javafx.util.StringConverter

/** Диалог вставки символов, похожий на «Символ» в Word. */
object SymbolDialog {
    private data class Set(val title: String, val codePoints: IntRange)

    private val sets = listOf(
        Set("Основная латиница", 0x0021..0x007E),
        Set("Кириллица", 0x0400..0x04FF),
        Set("Греческий алфавит", 0x0370..0x03FF),
        Set("Знаки пунктуации", 0x2000..0x206F),
        Set("Стрелки", 0x2190..0x21FF),
        Set("Математика", 0x2200..0x22FF),
        Set("Валюта", 0x20A0..0x20CF),
        Set("Декоративные знаки", 0x2600..0x27BF),
        Set("Эмодзи", 0x1F300..0x1F64F)
    )

    fun show(
        owner: Window?,
        pack: Theme.Pack,
        chrome: UiChrome = UiChrome.STANDARD,
        insert: (String) -> Unit
    ) {
        val stage = Stage()
        stage.title = when (chrome) {
            UiChrome.OPEN_OFFICE -> "Специальный символ"
            UiChrome.MY_OFFICE -> "Вставка символа"
            UiChrome.STANDARD -> "Символ"
        }
        stage.initModality(Modality.WINDOW_MODAL)
        if (owner != null) stage.initOwner(owner)

        val setBox = ComboBox<Set>().apply {
            items.addAll(sets)
            value = sets.first()
            prefWidth = 220.0
            converter = object : StringConverter<Set>() {
                override fun toString(value: Set?) = value?.title ?: ""
                override fun fromString(value: String?) = sets.firstOrNull { it.title == value } ?: sets.first()
            }
        }
        val fontBox = ComboBox<String>().apply {
            items.addAll("Segoe UI Symbol", "Segoe UI Emoji", "Arial", "Times New Roman", "PT Astra Sans", "PT Astra Serif")
            value = if (chrome == UiChrome.MY_OFFICE && items.contains("PT Astra Sans")) "PT Astra Sans" else "Segoe UI Symbol"
            prefWidth = 170.0
        }
        val selected = Label(" ").apply {
            minWidth = 66.0
            minHeight = 66.0
            alignment = Pos.CENTER
            style = "-fx-font-size: 38px; -fx-border-color: ${pack.border}; -fx-background-color: ${pack.editorBg};"
        }
        val code = TextField().apply {
            isEditable = false
            prefWidth = 130.0
        }
        var selectedSymbol = ""

        fun select(symbol: String) {
            selectedSymbol = symbol
            selected.text = symbol
            val cp = symbol.codePointAt(0)
            code.text = "U+" + cp.toString(16).uppercase().padStart(4, '0')
            selected.style = "-fx-font-family: '${fontBox.value}'; -fx-font-size: 38px; " +
                "-fx-border-color: ${pack.border}; -fx-background-color: ${pack.editorBg};"
        }

        val grid = GridPane().apply {
            hgap = 3.0
            vgap = 3.0
            padding = Insets(8.0)
        }
        val scroll = ScrollPane(grid).apply {
            isFitToWidth = true
            prefViewportHeight = 330.0
            style = "-fx-background-color: ${pack.popupBg};"
        }

        fun rebuild() {
            grid.children.clear()
            val symbols = setBox.value.codePoints.mapNotNull { cp ->
                runCatching { String(Character.toChars(cp)) }.getOrNull()
                    ?.takeIf { value -> value[0].isDefined() && !value[0].isISOControl() }
            }
            symbols.forEachIndexed { index, symbol ->
                val button = Button(symbol).apply {
                    prefWidth = 42.0
                    prefHeight = 38.0
                    style = "-fx-font-family: '${fontBox.value}'; -fx-font-size: 19px; " +
                        "-fx-background-color: ${pack.popupBg}; -fx-text-fill: ${pack.popupFg}; " +
                        "-fx-border-color: ${pack.border};"
                    setOnAction { select(symbol) }
                }
                grid.add(button, index % 12, index / 12)
            }
        }
        setBox.valueProperty().addListener { _, _, _ -> rebuild() }
        fontBox.valueProperty().addListener { _, _, _ -> if (selectedSymbol.isNotEmpty()) select(selectedSymbol); rebuild() }
        rebuild()

        val insertButton = Button("Вставить").apply {
            isDefaultButton = true
            setOnAction {
                if (selectedSymbol.isNotEmpty()) {
                    insert(selectedSymbol)
                    stage.close()
                }
            }
        }
        val closeButton = Button("Отмена").apply { setOnAction { stage.close() } }
        val topBar = HBox(
            10.0,
            Label("Шрифт:"), fontBox,
            Label(when (chrome) {
                UiChrome.OPEN_OFFICE -> "Подмножество:"
                UiChrome.MY_OFFICE -> "Набор символов:"
                UiChrome.STANDARD -> "Набор:"
            }), setBox
        ).apply {
            alignment = Pos.CENTER_LEFT
        }
        val preview = HBox(12.0, selected, VBox(6.0, Label("Код символа:"), code)).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(8.0, 8.0, 4.0, 8.0)
        }
        val bottomBar = HBox(8.0, insertButton, closeButton).apply {
            alignment = Pos.CENTER_RIGHT
            padding = Insets(4.0, 8.0, 8.0, 8.0)
        }
        val root = BorderPane().apply {
            top = topBar
            center = scroll
            bottom = VBox(preview, bottomBar)
            padding = Insets(10.0)
            style = "-fx-background-color: ${pack.popupBg};"
        }
        val ink = Theme.ink(pack)
        val css = """
            .label { -fx-text-fill: ${pack.popupFg}; }
            .combo-box, .text-field { -fx-background-color: ${pack.popupBg}; -fx-text-fill: ${pack.popupFg}; -fx-border-color: ${pack.border}; }
            .combo-box .list-cell { -fx-background-color: transparent; -fx-text-fill: ${pack.popupFg}; }
            .button {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-border-color: ${pack.border};
            }
            .button:hover {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-text-fill: ${ink.itemHoverFg};
            }
        """.trimIndent()
        root.stylesheets.add("data:text/css," + css.replace("\n", " ").replace("#", "%23"))
        VBox.setVgrow(scroll, Priority.ALWAYS)
        stage.scene = Scene(root, 650.0, 520.0)
        stage.show()
    }
}
