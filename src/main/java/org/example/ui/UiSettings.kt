package org.example.ui

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Font
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import javafx.util.Duration
import javafx.util.StringConverter
import org.example.engine.EditorCanvas
import org.example.engine.Paper
import java.io.File
import java.util.prefs.Preferences
import kotlin.math.roundToInt

internal object UiText {
    private val words = mapOf(
        "Начало" to "Start", "Создать документ" to "Create document", "Открыть…" to "Open…",
        "Недавние файлы" to "Recent files", "Здесь появятся открытые документы." to "Opened documents will appear here.",
        "Настройки" to "Settings", "Интерфейс" to "Interface", "Документ по умолчанию" to "Default document",
        "Фикбук" to "Ficbook", "Файлы" to "Files", "Размер интерфейса" to "Interface scale",
        "Авто" to "Automatic",
        "Масштаб читается из разрешения и масштаба Windows. Свой процент включается после перезапуска." to
            "Scale follows the Windows resolution and display scale. A custom percent applies after restart.",
        "Шрифт интерфейса" to "Interface font", "Размер шрифта" to "Font size",
        "Язык интерфейса" to "Interface language", "Тема" to "Theme", "Формат нового файла" to "New file format",
        "Ориентация" to "Orientation", "Размер страницы" to "Page size", "Режим при открытии" to "Mode on open",
        "Лист" to "Page", "Снизу" to "Below", "Справа" to "Right", "Книжная" to "Portrait",
        "Альбомная" to "Landscape", "Ширина колонки превью" to "Preview column width",
        "Раскладка ФФ" to "FF layout", "Папка для «Сохранить как»" to "Save As folder",
        "Выбрать…" to "Browse…", "Недавних файлов" to "Recent files count",
        "Очистить недавние" to "Clear recent files", "Системный по умолчанию" to "System default",
        "Применить" to "Apply", "Отмена" to "Cancel", "Файл" to "File", "Сервис" to "Tools",
        "Создать" to "New", "Открыть..." to "Open...", "Закрыть" to "Close",
        "Сохранить" to "Save", "Сохранить как..." to "Save As...", "Выход" to "Exit",
        "Главная" to "Home", "Вид" to "View", "Вставка" to "Insert", "Формат" to "Format",
        "Рецензирование" to "Review", "Справка" to "Help", "Оформление" to "Appearance",
        "Правка" to "Edit", "Раскладка" to "Layout",
        "Лента — Word" to "Ribbon — Word",
        "Классический — OpenOffice" to "Classic — OpenOffice",
        "Компактный — МойОфис" to "Compact — MyOffice",
        "Панели инструментов" to "Toolbars", "Панель инструментов" to "Toolbar",
        "Светлая, Серая, Тёмная AMOLED и Стеклянная меняют только цвета выбранной раскладки. Годы Word оставляют свою ленту, годы OpenOffice — классические панели, МойОфис — свой вид." to
            "Light, Gray, Dark AMOLED and Glass change only the colors of the chosen layout. Word years keep their ribbon, OpenOffice years keep the classic bars, and MyOffice keeps its own look.",
        "Печать..." to "Print...", "Отменить" to "Undo", "Повторить" to "Redo",
        "Вырезать" to "Cut", "Копировать" to "Copy", "Вставить" to "Paste",
        "Выделить всё" to "Select all", "Найти..." to "Find...", "Заменить..." to "Replace...",
        "Линейка" to "Ruler", "Сетка" to "Grid", "Прокрутка пробелом" to "Scroll with space",
        "Лента" to "Ribbon", "Строка состояния" to "Status bar", "Увеличить" to "Zoom in",
        "Уменьшить" to "Zoom out", "Масштаб 100%" to "Zoom 100%", "Во весь экран" to "Full screen",
        "Дата и время" to "Date and time", "Символ…" to "Symbol…", "Количество слов" to "Word count",
        "Проверить текст" to "Check spelling", "По левому краю" to "Align left",
        "По центру" to "Center", "По правому краю" to "Align right", "По ширине" to "Justify",
        "Увеличить отступ" to "Increase indent", "Уменьшить отступ" to "Decrease indent",
        "О программе" to "About",
        "Темы" to "Themes",
        "Значки" to "Icons",
        "Стандартные" to "Standard",
        "Кастомные" to "Custom",
        "Значки сразу появляются на ленте и в меню и берут цвет из темы." to
            "Icons appear at once on the ribbon and menus and take their color from the theme.",
        "Курсор" to "Caret",
        "Цвет курсора" to "Caret color",
        "Скорость мигания" to "Blink speed",
        "Как в Windows" to "Match Windows",
        "Не мигать" to "Do not blink",
        "Своя скорость" to "Custom speed",
        "Медленнее" to "Slower",
        "Быстрее" to "Faster",
        "Так мигает курсор в тексте" to "The caret blinks like this"
    )
    fun get(key: String, language: String): String {
        val russian = words.entries.firstOrNull { it.value == key }?.key ?: key
        return if (language == "en") words[russian] ?: russian else russian
    }
}

internal class UiSettings {
    private val prefs = Preferences.userNodeForPackage(RecentDocuments::class.java)
    var scaleAuto: Boolean = prefs.getBoolean("ui.scale.auto", true)
    var scale: Int = snapScale(prefs.getInt("ui.scale", 100))
    var font: String = prefs.get("ui.font", "")
    var fontSize: Int = prefs.getInt("ui.fontSize", 13).coerceIn(12, 16)
    var language: String = prefs.get("ui.language", "ru")
    var theme: AppTheme? = AppTheme.entries.firstOrNull { it.name == prefs.get("ui.theme", "") }
    var layout: UiChrome = UiChrome.fromPref(prefs.get("ui.layout", "")) ?: UiChrome.defaultLayout(theme)
    var format: String = prefs.get("document.format", "DOCX")
    var paper: Paper = Paper.entries.firstOrNull { it.name == prefs.get("document.paper", "A4") } ?: Paper.A4
    var landscape: Boolean = prefs.getBoolean("document.landscape", false)
    var openMode: String = prefs.get("document.openMode", "Лист")
    var previewWidth: Int = prefs.getInt("ficbook.previewWidth", 720).coerceIn(320, 1600)
    var placement: String = prefs.get("ficbook.placement", "Снизу")
    var saveFolder: String = prefs.get("files.saveFolder", "")
    var recentLimit: Int = prefs.getInt("files.recentLimit", 12).coerceIn(5, 30)
    var caretColor: String = normalizeCaretColor(prefs.get("caret.color", DEFAULT_CARET_COLOR))
    /** 0 — скорость Windows, отрицательное — без мигания, иначе миллисекунды. */
    var caretBlinkMs: Int = normalizeCaretBlink(prefs.getInt("caret.blinkMs", 0))
    var iconSet: IconSet = IconSet.fromPref(prefs.get("ui.icons", IconSet.STANDARD.name))

    fun save() {
        prefs.putBoolean("ui.scale.auto", scaleAuto)
        prefs.putInt("ui.scale", scale); prefs.put("ui.font", font); prefs.putInt("ui.fontSize", fontSize)
        prefs.put("ui.language", language); theme?.let { prefs.put("ui.theme", it.name) }
        prefs.put("ui.layout", layout.name)
        prefs.put("document.format", format); prefs.put("document.paper", paper.name)
        prefs.putBoolean("document.landscape", landscape); prefs.put("document.openMode", openMode)
        prefs.putInt("ficbook.previewWidth", previewWidth); prefs.put("ficbook.placement", placement)
        prefs.put("files.saveFolder", saveFolder); prefs.putInt("files.recentLimit", recentLimit)
        prefs.put("caret.color", normalizeCaretColor(caretColor))
        prefs.putInt("caret.blinkMs", normalizeCaretBlink(caretBlinkMs))
        prefs.put("ui.icons", iconSet.name)
    }

    fun writeIconSet() {
        prefs.put("ui.icons", iconSet.name)
    }
}

internal val interfaceScaleSteps = listOf(75, 100, 125, 150, 175, 200)

internal fun snapScale(percent: Int): Int = interfaceScaleSteps.minBy { kotlin.math.abs(it - percent) }

internal const val DEFAULT_CARET_COLOR = "#00B4D8"

internal fun normalizeCaretColor(raw: String?): String {
    val value = raw?.trim().orEmpty()
    return if (value.matches(Regex("#(?i)[0-9a-f]{6}"))) value.lowercase() else DEFAULT_CARET_COLOR
}

internal fun normalizeCaretBlink(ms: Int): Int = when {
    ms < 0 -> -1
    ms == 0 -> 0
    else -> ms.coerceIn(150, 1200)
}

internal object SettingsDialog {
    fun show(owner: Window, settings: UiSettings, onIcons: (IconSet) -> Unit, clearRecent: () -> Unit): Boolean {
        val t = { key: String -> UiText.get(key, settings.language) }
        val dialog = Dialog<Boolean>().apply { title = t("Настройки"); initOwner(owner) }
        val tabs = TabPane()
        fun <T> box(values: List<T>, value: T) = ComboBox<T>().apply {
            items.addAll(values); this.value = value; prefWidth = 240.0
        }
        fun tab(title: String, vararg rows: Pair<String, javafx.scene.Node>): Tab {
            val grid = GridPane().apply { hgap = 14.0; vgap = 12.0; padding = Insets(20.0) }
            rows.forEachIndexed { index, row -> grid.add(Label(t(row.first)), 0, index); grid.add(row.second, 1, index) }
            return Tab(t(title), grid).apply { isClosable = false }
        }
        val scale = box(listOf(0, 75, 100, 125, 150, 175, 200), if (settings.scaleAuto) 0 else settings.scale)
        scale.converter = object : StringConverter<Int>() {
            override fun toString(value: Int?) = if (value == null || value == 0) t("Авто") else "$value%"
            override fun fromString(value: String?) = value?.removeSuffix("%")?.toIntOrNull() ?: 0
        }
        scale.tooltip = Tooltip(t("Масштаб читается из разрешения и масштаба Windows. Свой процент включается после перезапуска."))
        val font = box(listOf("") + (Font.getFamilies() + "Segoe UI").distinct().sorted(), settings.font)
        font.converter = object : javafx.util.StringConverter<String>() {
            override fun toString(value: String?) = if (value.isNullOrEmpty()) t("Системный по умолчанию") else value
            override fun fromString(value: String?) = value.orEmpty()
        }
        val fontSize = box((12..16).toList(), settings.fontSize)
        val language = box(listOf("Русский", "English"), if (settings.language == "en") "English" else "Русский")
        val theme = box(AppTheme.entries, settings.theme ?: AppTheme.DARK)
        theme.converter = object : StringConverter<AppTheme>() {
            override fun toString(value: AppTheme?) = value?.title ?: ""
            override fun fromString(value: String?) =
                AppTheme.entries.firstOrNull { it.title == value } ?: AppTheme.DARK
        }
        val layout = box(UiChrome.layouts, settings.layout)
        layout.converter = object : StringConverter<UiChrome>() {
            override fun toString(value: UiChrome?) = value?.title ?: ""
            override fun fromString(value: String?) =
                UiChrome.layouts.firstOrNull { it.title == value } ?: UiChrome.WORD
        }
        val appearance = appearanceTab(settings, layout, theme, t, onIcons)
        val format = box(listOf("DOCX", "ODT"), settings.format)
        val paper = box(Paper.entries, settings.paper)
        paper.converter = object : StringConverter<Paper>() {
            override fun toString(value: Paper?) = value?.title ?: ""
            override fun fromString(value: String?) =
                Paper.entries.firstOrNull { it.title == value } ?: Paper.A4
        }
        val orientation = box(listOf("Книжная", "Альбомная"), if (settings.landscape) "Альбомная" else "Книжная")
        val openMode = box(listOf("Лист", "ФФ"), settings.openMode)
        val previewWidth = Spinner<Int>(320, 1600, settings.previewWidth, 20).apply { isEditable = true }
        val placement = box(listOf("Снизу", "Справа"), settings.placement)
        listOf(orientation, openMode, placement).forEach { combo ->
            combo.converter = object : javafx.util.StringConverter<String>() {
                override fun toString(value: String?) = value?.let(t) ?: ""
                override fun fromString(value: String?) = value.orEmpty()
            }
        }
        val folder = TextField(settings.saveFolder).apply { prefWidth = 300.0 }
        val browse = Button(t("Выбрать…")).apply { setOnAction {
            DirectoryChooser().showDialog(owner)?.let { folder.text = it.absolutePath }
        } }
        val folderRow = VBox(6.0, folder, browse)
        val recentLimit = Spinner<Int>(5, 30, settings.recentLimit).apply { isEditable = true }
        val clear = Button(t("Очистить недавние")).apply { setOnAction { clearRecent() } }
        val caret = caretTab(settings, t)
        tabs.tabs.addAll(
            tab("Интерфейс", "Размер интерфейса" to scale, "Шрифт интерфейса" to font,
                "Размер шрифта" to fontSize, "Язык интерфейса" to language),
            appearance,
            tab("Документ по умолчанию", "Формат нового файла" to format, "Размер страницы" to paper,
                "Ориентация" to orientation, "Режим при открытии" to openMode),
            tab("Фикбук", "Ширина колонки превью" to previewWidth, "Раскладка ФФ" to placement),
            tab("Файлы", "Папка для «Сохранить как»" to folderRow, "Недавних файлов" to recentLimit,
                "Очистить недавние" to clear),
            caret.first
        )
        dialog.dialogPane.content = tabs
        dialog.dialogPane.prefWidth = 650.0
        val pack = Theme.pack(settings.theme ?: AppTheme.DARK)
        dialog.dialogPane.sceneProperty().addListener { _, _, scene ->
            if (scene != null) Theme.applyCss(scene, pack)
        }
        val apply = ButtonType(t("Применить"), ButtonBar.ButtonData.OK_DONE)
        dialog.dialogPane.buttonTypes.addAll(apply, ButtonType.CANCEL)
        dialog.setOnHidden { caret.second.stop() }
        dialog.setResultConverter { it == apply }
        if (dialog.showAndWait().orElse(false) != true) return false
        settings.scaleAuto = scale.value == 0
        settings.scale = if (scale.value == 0) settings.scale else scale.value
        settings.font = font.value.orEmpty(); settings.fontSize = fontSize.value
        settings.language = if (language.value == "English") "en" else "ru"
        settings.theme = theme.value
        settings.layout = when {
            settings.theme?.officeYear != null -> UiChrome.OPEN_OFFICE
            settings.theme == AppTheme.MYOFFICE -> UiChrome.MY_OFFICE
            else -> layout.value ?: settings.layout
        }
        settings.format = format.value; settings.paper = paper.value
        settings.landscape = orientation.value == "Альбомная"; settings.openMode = openMode.value
        settings.previewWidth = previewWidth.value; settings.placement = placement.value
        settings.saveFolder = folder.text.trim(); settings.recentLimit = recentLimit.value
        caret.second.applyTo(settings)
        settings.save()
        return true
    }

    private fun appearanceTab(
        settings: UiSettings,
        layout: ComboBox<UiChrome>,
        theme: ComboBox<AppTheme>,
        t: (String) -> String,
        onIcons: (IconSet) -> Unit
    ): Tab {
        val icons = ToggleGroup()
        val standard = RadioButton(t("Стандартные")).apply {
            toggleGroup = icons
            isSelected = settings.iconSet == IconSet.STANDARD
        }
        val custom = RadioButton(t("Кастомные")).apply {
            toggleGroup = icons
            isSelected = settings.iconSet == IconSet.CUSTOM
        }
        val preview = HBox(8.0).apply { alignment = Pos.CENTER_LEFT }
        val samples = listOf(
            OfficeIconCatalog.ALIGN_LEFT to "⬅",
            OfficeIconCatalog.ALIGN_CENTER to "⬌",
            OfficeIconCatalog.ALIGN_RIGHT to "➡",
            OfficeIconCatalog.ALIGN_JUSTIFY to "☰",
            OfficeIconCatalog.BOLD to "Ж",
            OfficeIconCatalog.ITALIC to "К",
            OfficeIconCatalog.UNDERLINE to "Ч",
            OfficeIconCatalog.SAVE to "▣"
        )
        fun paintPreview() {
            val pack = Theme.pack(theme.value ?: AppTheme.DARK)
            val card = HBox(8.0).apply {
                alignment = Pos.CENTER_LEFT
                padding = Insets(10.0, 12.0, 10.0, 12.0)
                style = "-fx-background-color: ${pack.ribbonBg}; -fx-background-radius: 8; " +
                    "-fx-border-color: ${pack.border}; -fx-border-radius: 8;"
            }
            val ink = runCatching { Color.web(pack.buttonFg) }.getOrDefault(Color.BLACK)
            if (custom.isSelected) {
                samples.forEach { (name, _) -> card.children += LucideIcons.glyph(name, 18.0, ink) }
            } else {
                samples.forEach { (_, mark) ->
                    card.children += Label(mark).apply {
                        style = "-fx-text-fill: ${pack.buttonFg}; -fx-font-size: 16px; -fx-font-family: 'Segoe UI Symbol';"
                        minWidth = 18.0
                        alignment = Pos.CENTER
                    }
                }
            }
            preview.children.setAll(card)
        }
        fun choose(set: IconSet) {
            if (settings.iconSet == set) {
                paintPreview()
                return
            }
            settings.iconSet = set
            settings.writeIconSet()
            onIcons(set)
            paintPreview()
        }
        icons.selectedToggleProperty().addListener { _, _, selected ->
            if (selected == null) return@addListener
            choose(if (selected == custom) IconSet.CUSTOM else IconSet.STANDARD)
        }
        theme.valueProperty().addListener { _, _, _ -> paintPreview() }
        paintPreview()
        val rows = VBox(
            10.0,
            Label(t("Раскладка")),
            layout,
            Label(t("Темы")),
            theme,
            Label(t("Светлая, Серая, Тёмная AMOLED и Стеклянная меняют только цвета выбранной раскладки. Годы Word оставляют свою ленту, годы OpenOffice — классические панели, МойОфис — свой вид.")).apply {
                isWrapText = true
            },
            Label(t("Значки")),
            standard,
            custom,
            preview,
            Label(t("Значки сразу появляются на ленте и в меню и берут цвет из темы.")).apply { isWrapText = true }
        ).apply { padding = Insets(20.0); prefWidth = 560.0 }
        return Tab(t("Оформление"), rows).apply { isClosable = false }
    }

    private fun caretTab(settings: UiSettings, t: (String) -> String): Pair<Tab, CaretDraft> {
        val picker = ColorPicker(Color.web(settings.caretColor)).apply { prefWidth = 240.0 }
        val presets = HBox(6.0).apply { alignment = Pos.CENTER_LEFT }
        listOf("#00B4D8", "#1a1a1a", "#185abd", "#2f7d32", "#c0392b").forEach { hex ->
            presets.children += Button().apply {
                prefWidth = 28.0
                prefHeight = 28.0
                minWidth = 28.0
                style = "-fx-background-color: $hex; -fx-border-color: #94a3b8; -fx-background-radius: 6; -fx-border-radius: 6;"
                tooltip = Tooltip(hex)
                setOnAction { picker.value = Color.web(hex) }
            }
        }
        val speed = ToggleGroup()
        val system = RadioButton(t("Как в Windows")).apply { toggleGroup = speed }
        val steady = RadioButton(t("Не мигать")).apply { toggleGroup = speed }
        val custom = RadioButton(t("Своя скорость")).apply { toggleGroup = speed }
        val slider = Slider(150.0, 1200.0, 530.0).apply {
            blockIncrement = 10.0
            majorTickUnit = 150.0
            isShowTickMarks = true
            prefWidth = 240.0
        }
        val speedLabel = Label()
        when {
            settings.caretBlinkMs < 0 -> steady.isSelected = true
            settings.caretBlinkMs == 0 -> system.isSelected = true
            else -> {
                custom.isSelected = true
                slider.value = settings.caretBlinkMs.toDouble()
            }
        }
        val sample = Label(t("Так мигает курсор в тексте")).apply {
            style = "-fx-text-fill: #1a1a1a; -fx-font-size: 16px;"
        }
        val bar = Rectangle(2.0, 22.0)
        val card = HBox(3.0, bar, sample).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(10.0, 12.0, 10.0, 12.0)
            style = "-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #c5d2e2; -fx-border-radius: 8;"
        }
        var lit = true
        val blink = Timeline().apply { cycleCount = Timeline.INDEFINITE }
        fun restart() {
            blink.stop()
            bar.fill = picker.value ?: Color.web(DEFAULT_CARET_COLOR)
            bar.isVisible = true
            lit = true
            slider.isDisable = !custom.isSelected
            val period = when {
                steady.isSelected -> -1.0
                custom.isSelected -> slider.value
                else -> EditorCanvas.systemCaretBlinkMs
            }
            speedLabel.text = when {
                steady.isSelected -> t("Не мигать")
                custom.isSelected -> "${slider.value.roundToInt()} мс"
                EditorCanvas.systemCaretBlinkMs < 0 -> t("Не мигать")
                else -> "${EditorCanvas.systemCaretBlinkMs.roundToInt()} мс"
            }
            if (period <= 0) return
            blink.keyFrames.setAll(KeyFrame(Duration.millis(period), EventHandler {
                lit = !lit
                bar.isVisible = lit
            }))
            blink.play()
        }
        picker.valueProperty().addListener { _, _, _ -> restart() }
        slider.valueProperty().addListener { _, _, _ -> if (custom.isSelected) restart() }
        speed.selectedToggleProperty().addListener { _, _, _ -> restart() }
        restart()
        val rows = VBox(
            10.0,
            Label(t("Цвет курсора")),
            picker,
            presets,
            Label(t("Скорость мигания")),
            system,
            steady,
            custom,
            HBox(8.0, Label(t("Медленнее")), slider, Label(t("Быстрее"))).apply { alignment = Pos.CENTER_LEFT },
            speedLabel,
            card
        ).apply { padding = Insets(20.0) }
        val draft = CaretDraft(blink) { target ->
            val chosen = picker.value ?: Color.web(DEFAULT_CARET_COLOR)
            target.caretColor = normalizeCaretColor(
                "#%02x%02x%02x".format(
                    (chosen.red * 255).roundToInt(),
                    (chosen.green * 255).roundToInt(),
                    (chosen.blue * 255).roundToInt()
                )
            )
            target.caretBlinkMs = when {
                steady.isSelected -> -1
                custom.isSelected -> slider.value.roundToInt()
                else -> 0
            }
        }
        return Tab(t("Курсор"), rows).apply { isClosable = false } to draft
    }

    private class CaretDraft(private val blink: Timeline, private val write: (UiSettings) -> Unit) {
        fun stop() = blink.stop()
        fun applyTo(settings: UiSettings) = write(settings)
    }
}
