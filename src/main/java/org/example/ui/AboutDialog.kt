package org.example.ui

import javafx.event.ActionEvent
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.Screen
import javafx.stage.Window

internal object AboutDialog {
    const val PROJECT_URL = "https://github.com/G134T/G134Office"

    fun create(owner: Window?, pack: Theme.Pack, openProject: () -> Unit): Dialog<ButtonType> {
        val dialog = Dialog<ButtonType>().apply {
            title = "О программе"
            if (owner != null) initOwner(owner)
            isResizable = true
        }
        val screen = owner?.let {
            Screen.getScreensForRectangle(it.x, it.y, it.width, it.height).firstOrNull()
        } ?: Screen.getPrimary()
        val bounds = screen.visualBounds
        val updateType = ButtonType("Обновить", ButtonBar.ButtonData.OK_DONE)
        val closeType = ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE)
        dialog.dialogPane.apply {
            stylesheets.setAll(Theme.dialogStylesheet(pack))
            stylesheets.add("data:text/css," + """
                #about-details .scroll-bar { -fx-background-color: transparent; }
                #about-details .scroll-bar .track { -fx-background-color: transparent; -fx-border-color: transparent; }
                #about-details .scroll-bar .thumb { -fx-background-color: ${Theme.ink(pack).hintFg}; -fx-background-radius: 8; }
                #about-details .scroll-bar .increment-button, #about-details .scroll-bar .decrement-button { -fx-background-color: transparent; }
                #about-details .scroll-bar .increment-arrow, #about-details .scroll-bar .decrement-arrow { -fx-background-color: ${pack.popupFg}; }
            """.trimIndent().replace("\n", " ").replace("#", "%23"))
            content = content(pack, openProject)
            prefWidth = minOf(620.0, bounds.width - 48.0).coerceAtLeast(300.0)
            prefHeight = minOf(650.0, bounds.height - 80.0).coerceAtLeast(300.0)
            minWidth = minOf(400.0, prefWidth)
            minHeight = minOf(400.0, prefHeight)
            buttonTypes.setAll(updateType, closeType)
        }
        (dialog.dialogPane.lookupButton(updateType) as Button).apply {
            id = "about-update"
            isDefaultButton = true
            tooltip = Tooltip("Открыть GitHub для скачивания новой версии")
            addEventFilter(ActionEvent.ACTION) { event ->
                event.consume()
                openProject()
            }
        }
        return dialog
    }

    private fun content(pack: Theme.Pack, openProject: () -> Unit): VBox {
        val mark = Label("G").apply {
            alignment = Pos.CENTER
            setMinSize(60.0, 60.0)
            setPrefSize(60.0, 60.0)
            style = "-fx-background-color: ${pack.accent}; -fx-text-fill: ${pack.onAccent}; " +
                "-fx-background-radius: 16; -fx-font-size: 34px; -fx-font-weight: bold;"
        }
        val version = AboutDialog::class.java.`package`.implementationVersion
        val identity = VBox(4.0,
            Label("G134Office").apply { style = "-fx-font-size: 27px; -fx-font-weight: bold;" },
            wrap("Документы, PDF и творчество — в одном приложении."),
            wrap(version?.let { "Версия $it" } ?: "Сборка для разработки").apply {
                id = "about-version"
                style = "-fx-font-size: 12px; -fx-text-fill: ${Theme.ink(pack).hintFg};"
            }
        ).apply { minWidth = 0.0 }
        val hero = HBox(16.0, mark, identity).apply {
            alignment = Pos.CENTER_LEFT
            minHeight = Region.USE_PREF_SIZE
            HBox.setHgrow(identity, Priority.ALWAYS)
        }
        val features = card(pack, "Возможности",
            wrap("Редактор на Kotlin и JavaFX с собственным движком вёрстки страниц."),
            wrap("Проверка орфографии LanguageTool: русский, українська, беларуская, English."),
            wrap("Режим Фикбука со входом через браузер. Аннотации PDF и извлечение страниц."),
            wrap("Линейка, сетка, режим чтения, настраиваемый курсор и автомасштаб по DPI."),
            wrap("Больше 13 тем: Microsoft Word и OpenOffice по годам, Светлая, Серая, " +
                "Тёмная AMOLED, Стеклянная, Блокнот Windows 11 и МойОфис.")
        )
        val formats = GridPane().apply {
            hgap = 20.0
            add(column("Открытие", "DOCX, DOCM, DOTX\nDOC, DOT\nODT, OTT\nRTF, HTML, PDF\nFB2, EPUB\nTXT, MD"), 0, 0)
            add(column("Сохранение", "DOCX, ODT\nRTF, HTML, PDF\nFB2\nTXT, MD"), 1, 0)
            columnConstraints.addAll(
                ColumnConstraints().apply { percentWidth = 50.0; hgrow = Priority.ALWAYS },
                ColumnConstraints().apply { percentWidth = 50.0; hgrow = Priority.ALWAYS }
            )
        }
        val formatCard = card(pack, "Форматы документов", formats,
            wrap("DOC, DOT и EPUB — только чтение. DOCM, DOTX и OTT нельзя перезаписать: " +
                "сохраните копию как DOCX или ODT. Плавающие фигуры и колонтитулы Word пока не поддерживаются.")
        )
        val authenticityCard = card(pack, "Подлинность и загрузка",
            wrap("G134Office — независимый проект, вдохновлённый офисными редакторами, включая OpenOffice. " +
                "Проект не связан с Apache Software Foundation."),
            wrap("В версии 1.0.1 нет цифровой подписи Windows (Authenticode). Поэтому Windows может показать «Неизвестный издатель» или заблокировать запуск."),
            wrap("Скачивайте программу со страницы проекта на GitHub. Сверяйте SHA-256, только если контрольная сумма опубликована вместе с выпуском."),
            wrap("Подпись установщика сама по себе не подтверждает подписи EXE и библиотек внутри приложения."),
            wrap("Исходный код G134Office распространяется по MIT License. Проект вдохновлён Apache OpenOffice, но не является его форком и не связан с Apache Software Foundation.")
        )
        val details = ScrollPane(VBox(12.0, features, formatCard, authenticityCard).apply {
            padding = Insets(0.0, 10.0, 0.0, 0.0)
        }).apply {
            id = "about-details"
            isFitToWidth = true
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            minHeight = 80.0
            prefViewportHeight = 340.0
            style = "-fx-background: ${pack.popupBg}; -fx-background-color: transparent; -fx-padding: 0;"
            VBox.setVgrow(this, Priority.ALWAYS)
        }
        val github = Hyperlink("GitHub · исходный код и загрузки").apply {
            id = "about-github"
            style = "-fx-text-fill: ${pack.popupFg}; -fx-underline: true; -fx-padding: 0;"
            isWrapText = true
            maxWidth = Double.MAX_VALUE
            tooltip = Tooltip(PROJECT_URL)
            setOnAction { openProject() }
        }
        return VBox(18.0, hero, details, VBox(6.0,
            heading("Обновления"),
            wrap("Нажмите «Обновить», чтобы открыть GitHub в браузере и скачать новую версию. " +
                "Установка выполняется вручную."),
            github
        )).apply {
            padding = Insets(12.0, 12.0, 4.0, 12.0)
        }
    }

    private fun card(pack: Theme.Pack, title: String, vararg children: javafx.scene.Node) =
        VBox(8.0, heading(title), *children).apply {
            padding = Insets(14.0)
            style = "-fx-border-color: ${pack.border}; -fx-border-radius: 10; -fx-background-radius: 10;"
        }

    private fun heading(text: String) = Label(text).apply {
        style = "-fx-font-weight: bold; -fx-font-size: 14px;"
    }

    private fun wrap(text: String) = Label(text).apply {
        isWrapText = true
        minWidth = 0.0
        minHeight = Region.USE_PREF_SIZE
        maxWidth = Double.MAX_VALUE
    }

    private fun column(title: String, formats: String) = VBox(6.0, heading(title), wrap(formats)).apply {
        minWidth = 0.0
    }
}
