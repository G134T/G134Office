package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Separator
import javafx.scene.control.Tooltip
import javafx.scene.paint.Color
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.util.Duration
import javafx.util.StringConverter
import org.example.spell.DocLanguage

class RibbonBar : VBox() {
    val neu = action("✦", "Создать (Ctrl+N)")
    val open = action("▱", "Открыть (Ctrl+O)")
    val save = action("▣", "Сохранить (Ctrl+S)")
    val commitFanficChapter = action("Git", "Закоммитить главу")
    val cut = action("✂", "Вырезать (Ctrl+X)")
    val copy = action("⧉", "Копировать (Ctrl+C)")
    val paste = action("▤", "Вставить (Ctrl+V)")
    val bold = action("Ж", "Полужирный")
    val italic = action("К", "Курсив")
    val underline = action("Ч", "Подчёркнутый")
    val grow = action("A+", "Крупнее")
    val shrink = action("A−", "Мельче")
    val alignLeft = action("⬅", "По левому краю")
    val alignCenter = action("⬌", "По центру")
    val alignRight = action("➡", "По правому краю")
    val alignJustify = action("☰", "По ширине")
    val indentMore = action("⇥", "Увеличить отступ")
    val indentLess = action("⇤", "Уменьшить отступ")
    val find = action("⌕", "Найти (Ctrl+F)")
    val replace = action("↻", "Заменить (Ctrl+H)")
    val spell = action("✓А", "Проверить текст (F7)")
    val ruler = action("═", "Линейка: перетаскивайте маркеры полей и отступов")
    val grid = action("#", "Сетка 0,5 см: показывает шаг и привязывает маркеры линейки")
    val fanficMode = action("ФФ", "Режим Фикбук")

    val fontFamily = ComboBox<String>().apply {
        items.addAll(Font.getFamilies().sorted())
        value = if (items.contains("Segoe UI")) "Segoe UI" else items.firstOrNull() ?: "System"
        prefWidth = 148.0
        maxWidth = 148.0
        visibleRowCount = 16
        tooltip = Tooltip("Шрифт всего документа")
    }

    val fontSizeBox = ComboBox<String>().apply {
        items.addAll("10", "11", "12", "14", "16", "18", "20", "24", "28", "36")
        value = "16"
        prefWidth = 72.0
        minWidth = 72.0
        maxWidth = 80.0
        visibleRowCount = 10
        isEditable = false
        tooltip = Tooltip("Размер шрифта")
    }

    val themeFamilyBox = ComboBox<ThemeFamily>().apply {
        items.addAll(ThemeFamily.entries)
        value = ThemeFamily.DARK
        prefWidth = 168.0
        visibleRowCount = 8
        tooltip = Tooltip("Тема оформления")
        converter = object : StringConverter<ThemeFamily>() {
            override fun toString(t: ThemeFamily?) = t?.title ?: ""
            override fun fromString(s: String?) =
                ThemeFamily.entries.firstOrNull { it.title == s } ?: ThemeFamily.DARK
        }
    }

    val wordYearBox = ComboBox<AppTheme>().apply {
        items.addAll(ThemeFamily.WORD.variants)
        value = AppTheme.WORD_2023
        prefWidth = 88.0
        minWidth = 80.0
        visibleRowCount = 6
        tooltip = Tooltip("Год оформления Word")
        isVisible = false
        isManaged = false
        converter = object : StringConverter<AppTheme>() {
            override fun toString(t: AppTheme?) = t?.wordYear ?: t?.title ?: ""
            override fun fromString(s: String?) =
                ThemeFamily.WORD.variants.firstOrNull { it.wordYear == s } ?: AppTheme.WORD_2023
        }
    }

    val langBox = ComboBox<DocLanguage>().apply {
        items.addAll(DocLanguage.entries)
        value = DocLanguage.RU
        prefWidth = 140.0
        tooltip = Tooltip("Язык проверки")
        converter = object : StringConverter<DocLanguage>() {
            override fun toString(t: DocLanguage?) = t?.title ?: ""
            override fun fromString(s: String?) =
                DocLanguage.entries.firstOrNull { it.title == s } ?: DocLanguage.RU
        }
    }

    private val buttons = listOf(
        neu, open, save, commitFanficChapter, cut, copy, paste, bold, italic, underline, grow, shrink,
        alignLeft, alignCenter, alignRight, alignJustify, indentMore, indentLess,
        find, replace, spell, ruler, grid, fanficMode
    )
    private val groupLabels = mutableListOf<Label>()
    private val separators = mutableListOf<Separator>()
    private val row = HBox(10.0)
    private val standardRow = HBox(6.0)
    private val formattingRow = HBox(6.0)
    private var originalGroups: List<Node> = emptyList()
    private var chrome = UiChrome.STANDARD
    private val originalButtonText = mutableMapOf<Button, String>()
    private val myOfficeTabs = HBox(0.0).apply {
        alignment = Pos.BOTTOM_LEFT
        padding = Insets(6.0, 10.0, 0.0, 10.0)
        minHeight = 32.0
        children.addAll(
            myTab("Главная", true),
            myTab("Вставка", false),
            myTab("Макет", false),
            myTab("Рецензирование", false),
            myTab("Вид", false)
        )
    }

    init {
        row.isFillHeight = true
        row.alignment = Pos.CENTER_LEFT
        row.padding = Insets(6.0, 10.0, 8.0, 10.0)
        val spacer = Region()
        HBox.setHgrow(spacer, Priority.ALWAYS)
        row.children.addAll(
            group("Файл", neu, open, save, commitFanficChapter),
            sep(),
            group("Буфер", cut, copy, paste),
            sep(),
            group("Шрифт", fontFamily, fontSizeBox, bold, italic, underline, grow, shrink),
            sep(),
            group("Абзац", alignLeft, alignCenter, alignRight, alignJustify, indentMore, indentLess),
            sep(),
            group("Правка", find, replace, spell),
            sep(),
            group("Вид", ruler, grid, fanficMode),
            spacer,
            group("Оформление", themeFamilyBox, wordYearBox),
            sep(),
            group("Язык проверки", langBox)
        )
        originalGroups = row.children.toList()
        buttons.forEach { originalButtonText[it] = it.text }
        children.add(row)
    }

    fun setChrome(next: UiChrome) {
        if (chrome == next) return
        resetLayout()
        chrome = next
        when (next) {
            UiChrome.STANDARD -> Unit
            UiChrome.OPEN_OFFICE -> applyOpenOfficeLayout()
            UiChrome.MY_OFFICE -> applyMyOfficeLayout()
        }
    }

    private fun resetLayout() {
        standardRow.children.clear()
        formattingRow.children.clear()
        row.children.setAll(originalGroups)
        groupLabels.forEach { it.isVisible = true; it.isManaged = true }
        buttons.forEach { it.text = originalButtonText[it] ?: it.text }
        children.setAll(row)
        row.padding = Insets(6.0, 10.0, 8.0, 10.0)
    }

    private fun applyOpenOfficeLayout() {
        row.children.clear()
        standardRow.children.addAll(listOf(0, 1, 2, 3, 8, 9, 10).map(originalGroups::get))
        formattingRow.children.addAll(listOf(4, 5, 6, 7, 12, 13, 14).map(originalGroups::get))
        listOf(standardRow, formattingRow).forEach {
            it.alignment = Pos.CENTER_LEFT
            it.padding = Insets(3.0, 8.0, 3.0, 8.0)
        }
        groupLabels.forEach { it.isVisible = false; it.isManaged = false }
        buttons.forEach { it.text = when (it) {
            neu -> "▤"; open -> "📂"; save -> "▣"; cut -> "✂"; copy -> "▣"
            paste -> "▤"; bold -> "B"; italic -> "I"; underline -> "U"
            alignLeft -> "≡"; alignCenter -> "☰"; alignRight -> "≡"
            else -> originalButtonText[it] ?: it.text
        } }
        children.setAll(standardRow, formattingRow)
    }

    private fun applyMyOfficeLayout() {
        groupLabels.forEach { it.isVisible = true; it.isManaged = true }
        buttons.forEach { it.text = when (it) {
            neu -> "＋"; open -> "📂"; save -> "💾"; cut -> "✂"; copy -> "⧉"
            paste -> "▣"; bold -> "Ж"; italic -> "К"; underline -> "Ч"
            grow -> "A+"; shrink -> "A−"
            alignLeft -> "≡"; alignCenter -> "☰"; alignRight -> "≡"; alignJustify -> "☰"
            else -> originalButtonText[it] ?: it.text
        } }
        row.padding = Insets(8.0, 12.0, 10.0, 12.0)
        children.setAll(myOfficeTabs, row)
    }

    fun applyTheme(pack: Theme.Pack) {
        val background = when (chrome) {
            UiChrome.OPEN_OFFICE ->
                "linear-gradient(to bottom, #f6f8fb 0%, #e3e8f0 55%, #d6dce6 100%)"
            UiChrome.MY_OFFICE -> "#ffffff"
            UiChrome.STANDARD -> pack.ribbonBg
        }
        val border = if (chrome == UiChrome.MY_OFFICE) "#0b5cab" else pack.border
        style = when (chrome) {
            UiChrome.MY_OFFICE ->
                "-fx-background-color: #0b5cab; -fx-border-color: #d5e2ee; -fx-border-width: 0 0 1 0;"
            else ->
                "-fx-background-color: $background; -fx-border-color: $border; -fx-border-width: 0 0 1 0;"
        }
        myOfficeTabs.style = "-fx-background-color: #0b5cab;"
        row.style = "-fx-background-color: $background;"
        standardRow.style = "-fx-background-color: $background; -fx-border-color: ${pack.border}; -fx-border-width: 0 0 1 0;"
        formattingRow.style = "-fx-background-color: $background;"
        groupLabels.forEach { it.style = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 10px;" }
        separators.forEach { it.style = "-fx-background-color: ${pack.border}; -fx-pref-height: 40;" }
        buttons.forEach { styleButton(it, pack) }
        styleCombo(fontFamily, pack) { it ?: "" }
        styleCombo(fontSizeBox, pack) { it ?: "" }
        styleCombo(themeFamilyBox, pack) { it?.title ?: "" }
        styleCombo(wordYearBox, pack) { it?.wordYear ?: "" }
        styleCombo(langBox, pack) { it?.title ?: "" }
    }

    fun resolvedTheme(): AppTheme {
        val family = themeFamilyBox.value ?: ThemeFamily.DARK
        return if (family == ThemeFamily.WORD) wordYearBox.value ?: AppTheme.WORD_2023
        else family.variants.first()
    }

    fun showTheme(theme: AppTheme) {
        val family = theme.family
        val word = family == ThemeFamily.WORD
        wordYearBox.isVisible = word
        wordYearBox.isManaged = word
        if (word && wordYearBox.value != theme) wordYearBox.value = theme
        if (themeFamilyBox.value != family) themeFamilyBox.value = family
    }

    private fun <T> styleCombo(box: ComboBox<T>, pack: Theme.Pack, label: (T?) -> String) {
        box.style = "-fx-background-color: ${pack.popupBg}; -fx-border-color: ${pack.border}; " +
            "-fx-background-radius: 4; -fx-border-radius: 4;"
        val color = runCatching { Color.web(pack.popupFg) }.getOrDefault(Color.WHITE)
        val cell = object : ListCell<T>() {
            override fun updateItem(item: T?, empty: Boolean) {
                super.updateItem(item, empty)
                text = if (empty) null else label(item)
                textFill = color
                style = "-fx-background-color: transparent; -fx-text-fill: ${pack.popupFg}; -fx-padding: 3 8 3 8;"
            }
        }
        box.buttonCell = cell
        val selected = box.value
        cell.text = if (selected == null) null else label(selected)
        cell.textFill = color
    }

    private fun group(title: String, vararg nodes: Node): VBox {
        val items = HBox(4.0, *nodes).apply { alignment = Pos.CENTER_LEFT }
        val label = Label(title)
        groupLabels += label
        return VBox(2.0, items, label).apply { alignment = Pos.CENTER }
    }

    private fun sep(): Separator {
        val s = Separator(Orientation.VERTICAL)
        s.prefHeight = 40.0
        separators += s
        return s
    }

    private fun action(text: String, tip: String) = Button(text).apply {
        minWidth = 40.0
        minHeight = 32.0
        tooltip = Tooltip(tip).apply {
            showDelay = Duration.millis(160.0)
            hideDelay = Duration.millis(80.0)
            showDuration = Duration.seconds(8.0)
        }
    }

    private fun myTab(title: String, selected: Boolean) = Label(title).apply {
        padding = Insets(6.0, 16.0, 8.0, 16.0)
        style = if (selected)
            "-fx-background-color: #ffffff; -fx-text-fill: #0b5cab; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 4 4 0 0;"
        else
            "-fx-text-fill: #e8f2fb; -fx-font-size: 13px;"
    }

    private fun styleButton(button: Button, pack: Theme.Pack) {
        if (chrome == UiChrome.OPEN_OFFICE) {
            button.minWidth = 27.0
            button.minHeight = 26.0
            button.prefHeight = 26.0
            button.style = "-fx-background-color: transparent; -fx-text-fill: #26374c; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 2 5 2 5;"
            button.setOnMouseEntered { button.style = "-fx-background-color: #dceaf9; -fx-border-color: #8aaed4; -fx-text-fill: #26374c; -fx-padding: 2 5 2 5;" }
            button.setOnMouseExited { button.style = "-fx-background-color: transparent; -fx-text-fill: #26374c; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 2 5 2 5;" }
            return
        }
        if (chrome == UiChrome.MY_OFFICE) {
            button.minWidth = 32.0
            button.minHeight = 28.0
            button.prefHeight = 28.0
            val base =
                "-fx-background-color: transparent; -fx-text-fill: #0b5cab; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
            val hover =
                "-fx-background-color: #d7e8f8; -fx-text-fill: #084b8a; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
            button.style = base
            button.setOnMouseEntered { button.style = hover }
            button.setOnMouseExited { button.style = base }
            return
        }
        button.minWidth = 40.0
        button.minHeight = 32.0
        button.prefHeight = -1.0
        val base =
            "-fx-background-color: transparent; -fx-font-family: 'Segoe UI Symbol'; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-text-fill: ${pack.buttonFg}; -fx-background-radius: 8; -fx-border-radius: 8; " +
                "-fx-padding: 4 8 4 8;"
        val hover =
            "-fx-background-color: ${pack.buttonHover}; -fx-font-family: 'Segoe UI Symbol'; -fx-font-size: 14px; " +
                "-fx-font-weight: bold; -fx-text-fill: ${pack.buttonFg}; -fx-background-radius: 8; -fx-border-radius: 8; " +
                "-fx-padding: 4 8 4 8;"
        button.style = base
        button.setOnMouseEntered { button.style = hover }
        button.setOnMouseExited { button.style = base }
    }
}
