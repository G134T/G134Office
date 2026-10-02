package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Separator
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.paint.Color
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.util.Duration
import javafx.util.StringConverter
import org.example.engine.Align
import org.example.spell.DocLanguage

class RibbonBar : VBox() {
    val neu = action("✦", "Создать (Ctrl+N)")
    val open = action("▱", "Открыть (Ctrl+O)")
    val save = action("▣", "Сохранить (Ctrl+S)")
    val commitFanficChapter = action("Git", "Закоммитить главу")
    val cut = action("✂", "Вырезать (Ctrl+X)")
    val copy = action("⧉", "Копировать (Ctrl+C)")
    val paste = action("▤", "Вставить (Ctrl+V)")
    val bold = action("Ж", "Полужирный (Ctrl+B)")
    val italic = action("К", "Курсив (Ctrl+I)")
    val underline = action("Ч", "Подчёркнутый (Ctrl+U)")
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
    val insertSymbol = action("Ω", "Символ")
    val insertDate = action("Дата", "Дата и время")
    val pageBreak = action("Разрыв", "Разрыв страницы")
    val landscape = action("Лист", "Книжная или альбомная ориентация")
    val marginsLess = action("Уже", "Уменьшить поля")
    val marginsMore = action("Шире", "Увеличить поля")
    val wordCount = action("Слова", "Количество слов")
    val zoomOut = action("−", "Уменьшить")
    val zoomReset = action("100%", "Масштаб 100%")
    val zoomIn = action("+", "Увеличить")
    val undo = action("↶", "Отменить (Ctrl+Z)")
    val redo = action("↷", "Повторить (Ctrl+Y)")
    val strike = action("S", "Зачёркнутый")
    val clearFormat = action("A⌫", "Очистить формат")
    val formatPainter = action("Кисть", "Формат по образцу")
    val selectAll = action("Всё", "Выделить всё (Ctrl+A)")
    val styleNormal = action("Обычный", "Стиль «Обычный»")
    val styleHeading1 = action("Заголовок 1", "Стиль «Заголовок 1»")
    val styleHeading2 = action("Заголовок 2", "Стиль «Заголовок 2»")
    val printLayout = action("Страница", "Разметка страницы")
    val readMode = action("Чтение", "Режим чтения")
    val navigation = action("Навигация", "Область навигации")
    val fitWidth = action("Ширина", "По ширине страницы")
    val fullScreen = action("Экран", "Во весь экран (F11)")

    val fontColor = ColorPicker(Color.web("#141414")).apply {
        prefWidth = 56.0
        maxWidth = 64.0
        minHeight = 32.0
        tooltip = Tooltip("Цвет шрифта")
    }

    val lineSpacingBox = ComboBox<String>().apply {
        items.addAll("1,0", "1,15", "1,5", "2,0")
        value = "1,15"
        prefWidth = 76.0
        minWidth = 76.0
        visibleRowCount = 4
        tooltip = Tooltip("Междустрочный интервал")
    }

    private val zoomLabel = Label("100%").apply {
        minWidth = 48.0
        alignment = Pos.CENTER
    }

    val fontFamily = ComboBox<String>().apply {
        items.addAll(Font.getFamilies().sorted())
        value = if (items.contains("Segoe UI")) "Segoe UI" else items.firstOrNull() ?: "System"
        prefWidth = 148.0
        maxWidth = 148.0
        visibleRowCount = 16
        tooltip = Tooltip("Шрифт выделенного фрагмента или следующего ввода")
    }

    val fontSizeBox = ComboBox<String>().apply {
        items.addAll("10", "11", "12", "14", "16", "18", "20", "24", "28", "36")
        value = "16"
        prefWidth = 72.0
        minWidth = 72.0
        maxWidth = 80.0
        visibleRowCount = 10
        isEditable = false
        tooltip = Tooltip("Размер шрифта выделенного фрагмента или следующего ввода")
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

    val layoutBox = ComboBox<UiChrome>().apply {
        items.addAll(UiChrome.layouts)
        value = UiChrome.WORD
        prefWidth = 220.0
        minWidth = 180.0
        visibleRowCount = 3
        tooltip = Tooltip("Раскладка: лента Word, классическое меню OpenOffice или компактная панель МойОфис")
        converter = object : StringConverter<UiChrome>() {
            override fun toString(t: UiChrome?) = t?.title ?: ""
            override fun fromString(s: String?) =
                UiChrome.layouts.firstOrNull { it.title == s } ?: UiChrome.WORD
        }
    }

    val officeYearBox = ComboBox<AppTheme>().apply {
        items.addAll(ThemeFamily.OPEN_OFFICE.variants)
        value = AppTheme.OPEN_OFFICE
        prefWidth = 88.0
        minWidth = 80.0
        visibleRowCount = 6
        tooltip = Tooltip("Год оформления OpenOffice")
        isVisible = false
        isManaged = false
        converter = object : StringConverter<AppTheme>() {
            override fun toString(t: AppTheme?) = t?.officeYear ?: t?.title ?: ""
            override fun fromString(s: String?) =
                ThemeFamily.OPEN_OFFICE.variants.firstOrNull { it.officeYear == s } ?: AppTheme.OPEN_OFFICE
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

    private val armed = mutableSetOf<Button>()
    private val faces = mutableMapOf<Button, Face>()
    private val glyphs = mutableMapOf<Button, OfficeGlyph>()
    private var iconSet = IconSet.STANDARD

    internal fun setIconSet(next: IconSet) {
        iconSet = next
        val pack = themePack ?: return
        buttons.forEach { styleButton(it, pack) }
    }

    fun showFormatting(align: Align, boldOn: Boolean, italicOn: Boolean, underlineOn: Boolean) {
        setPressed(alignLeft, align == Align.LEFT)
        setPressed(alignCenter, align == Align.CENTER)
        setPressed(alignRight, align == Align.RIGHT)
        setPressed(alignJustify, align == Align.JUSTIFY)
        setPressed(bold, boldOn)
        setPressed(italic, italicOn)
        setPressed(underline, underlineOn)
    }

    fun setPressed(button: Button, on: Boolean) {
        if (on) armed += button else armed -= button
        paintButton(button, button.isHover)
    }

    private val buttons = listOf(
        neu, open, save, commitFanficChapter, cut, copy, paste, bold, italic, underline, grow, shrink,
        alignLeft, alignCenter, alignRight, alignJustify, indentMore, indentLess,
        find, replace, spell, ruler, grid, fanficMode,
        insertSymbol, insertDate, pageBreak, landscape, marginsLess, marginsMore, wordCount,
        zoomOut, zoomReset, zoomIn, undo, redo, strike, clearFormat, formatPainter, selectAll,
        styleNormal, styleHeading1, styleHeading2, printLayout, readMode, navigation, fitWidth, fullScreen
    )
    private val groupLabels = mutableListOf<Label>()
    private val groupContents = linkedMapOf<VBox, List<Node>>()
    private val separators = mutableListOf<Separator>()
    private val row = HBox(10.0)
    private val ribbonScroll = ScrollPane().apply {
        isFitToHeight = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        style = "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
        styleClass.add("office-ribbon-scroll")
    }
    private val standardRow = HBox(6.0)
    private val formattingRow = HBox(6.0)
    private var originalGroups: List<Node> = emptyList()
    private var standardPlan: Map<VBox, List<Node>> = emptyMap()
    private var wordHomePlan: Map<VBox, List<Node>> = emptyMap()
    private var wordInsertPlan: Map<VBox, List<Node>> = emptyMap()
    private var wordLayoutPlan: Map<VBox, List<Node>> = emptyMap()
    private var wordReviewPlan: Map<VBox, List<Node>> = emptyMap()
    private var wordViewPlan: Map<VBox, List<Node>> = emptyMap()
    private var wordHomeRow: List<Node> = emptyList()
    private var wordInsertRow: List<Node> = emptyList()
    private var wordLayoutRow: List<Node> = emptyList()
    private var wordReviewRow: List<Node> = emptyList()
    private var wordViewRow: List<Node> = emptyList()
    private val managedGroups = mutableListOf<VBox>()
    private val fixedBar = HBox(4.0)
    private val barGaps = mutableListOf<Separator>()
    private var chrome = UiChrome.STANDARD
    private var wordTabIndex = 0
    private val wordTabTitles = listOf("Главная", "Вставка", "Формат", "Рецензирование", "Вид")
    private var themePack: Theme.Pack? = null
    private var quietLayout = false
    private val originalButtonText = mutableMapOf<Button, String>()
    private val inspectorTitle = Label("Абзац")
    private val inspectorHint = Label("Абзац в позиции курсора").apply { isWrapText = true }
    private val textCard = VBox(6.0)
    private val paragraphCard = VBox(6.0)
    private val indentBlock = VBox(6.0)
    private val spacingBlock = VBox(6.0)
    private val styleBlock = VBox(6.0)
    private val toolsCard = VBox(8.0)
    private val inspectorLabels = mutableListOf<Label>()
    private val fontCaption = inspectorCaption("Шрифт")
    private val indentCaption = inspectorCaption("Отступы")
    private val spacingCaption = inspectorCaption("Интервал")
    private val styleCaption = inspectorCaption("Стили")
    private val insertCaption = inspectorCaption("Вставка")
    private val pageCaption = inspectorCaption("Страница")
    private val viewCaption = inspectorCaption("Вид")
    private val inspectorBody = VBox(10.0).apply { padding = Insets(12.0) }
    val inspectorPane = ScrollPane(inspectorBody).apply {
        isFitToWidth = true
        hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        minWidth = 250.0
        prefWidth = 280.0
        maxWidth = 320.0
        maxHeight = Double.MAX_VALUE
        style = "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
    }
    private var contextSelected = false
    private var contextHeading = false
    private var contextMode = ""
    private var insertGroup: VBox
    private var pageGroup: VBox
    private var zoomGroup: VBox

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
            group("Правка", find, replace),
            sep(),
            group("Проверка", spell),
            sep(),
            group("Вид", ruler, grid, fanficMode),
            spacer,
            group("Оформление", layoutBox, themeFamilyBox, officeYearBox, wordYearBox),
            sep(),
            group("Язык проверки", langBox)
        )
        insertGroup = group("Вставка", insertSymbol, insertDate, pageBreak)
        pageGroup = group("Страница", landscape, marginsLess, marginsMore)
        zoomGroup = group("Масштаб", zoomOut, zoomLabel, zoomIn, zoomReset, fitWidth)
        originalGroups = row.children.toList()
        standardPlan = groupContents.mapValues { (_, nodes) -> nodes.toList() }
        buildWordPlans()
        mount(standardPlan)
        buttons.forEach { originalButtonText[it] = it.text }
        children.add(row)
    }

    fun showZoom(percent: Int) {
        zoomLabel.text = "$percent%"
    }

    fun showLineSpacing(label: String) {
        if (lineSpacingBox.items.contains(label) && lineSpacingBox.value != label) {
            lineSpacingBox.value = label
        }
    }

    fun setChrome(next: UiChrome) {
        if (chrome == next) return
        resetLayout()
        chrome = next
        when (next) {
            UiChrome.STANDARD -> Unit
            UiChrome.OPEN_OFFICE -> applyOpenOfficeLayout()
            UiChrome.MY_OFFICE -> applyMyOfficeLayout()
            UiChrome.WORD -> applyWordLayout()
        }
    }

    private fun resetLayout() {
        releaseInspector()
        ribbonScroll.content = null
        standardRow.children.clear()
        formattingRow.children.clear()
        mount(standardPlan)
        row.children.setAll(originalGroups)
        groupLabels.forEach { it.isVisible = true; it.isManaged = true }
        children.setAll(row)
        row.padding = Insets(6.0, 10.0, 8.0, 10.0)
    }

    private fun releaseInspector() {
        textCard.children.clear()
        indentBlock.children.clear()
        spacingBlock.children.clear()
        styleBlock.children.clear()
        paragraphCard.children.clear()
        toolsCard.children.clear()
        fixedBar.children.clear()
        contextMode = ""
    }

    private fun applyOpenOfficeLayout() {
        row.children.clear()
        barGaps.clear()
        boxOf(insertGroup).children.setAll(insertSymbol, insertDate, pageBreak)
        boxOf(pageGroup).children.setAll(landscape, marginsLess, marginsMore)
        boxOf(zoomGroup).children.setAll(zoomOut, zoomLabel, zoomIn, zoomReset, fitWidth)
        standardRow.children.setAll(
            originalGroups[0], barGap(), undo, redo, barGap(),
            originalGroups[2], barGap(), originalGroups[8], barGap(),
            originalGroups[10], wordCount, barGap(), zoomGroup, barGap(),
            originalGroups[12], barGap(), originalGroups[14], barGap(), originalGroups[16]
        )
        formattingRow.children.setAll(
            originalGroups[4], barGap(), fontColor, strike, clearFormat, formatPainter, barGap(),
            originalGroups[6], barGap(), lineSpacingBox, barGap(),
            styleNormal, styleHeading1, styleHeading2, barGap(),
            insertGroup, barGap(), pageGroup
        )
        listOf(standardRow, formattingRow).forEach {
            it.alignment = Pos.CENTER_LEFT
            it.padding = Insets(3.0, 8.0, 3.0, 8.0)
        }
        groupLabels.forEach { it.isVisible = false; it.isManaged = false }
        children.setAll(standardRow, formattingRow)
    }

    private fun barGap(): Separator {
        val gap = Separator(Orientation.VERTICAL)
        gap.prefHeight = 22.0
        gap.maxHeight = 22.0
        barGaps += gap
        return gap
    }

    private fun applyWordLayout() {
        groupLabels.forEach { it.isVisible = true; it.isManaged = true }
        ribbonScroll.isFitToWidth = false
        ribbonScroll.minHeight = 78.0
        ribbonScroll.prefHeight = 82.0
        ribbonScroll.content = row
        children.setAll(ribbonScroll)
        showWordTab(wordTabIndex)
    }

    internal fun selectWordTab(index: Int) = showWordTab(index)

    internal fun wordTabTitle(index: Int): String = wordTabTitles[index]

    private fun showWordTab(index: Int) {
        wordTabIndex = index.coerceIn(0, wordTabTitles.lastIndex)
        val plan = when (wordTabIndex) {
            0 -> wordHomePlan
            1 -> wordInsertPlan
            2 -> wordLayoutPlan
            3 -> wordReviewPlan
            else -> wordViewPlan
        }
        val nodes = when (wordTabIndex) {
            0 -> wordHomeRow
            1 -> wordInsertRow
            2 -> wordLayoutRow
            3 -> wordReviewRow
            else -> wordViewRow
        }
        mount(plan)
        row.children.setAll(nodes)
    }

    private fun buildWordPlans() {
        val document = group("Документ", neu, open, save)
        val history = group("История", undo, redo)
        val clip = group("Буфер обмена", paste, cut, copy, formatPainter)
        val font = group(
            "Шрифт", fontFamily, fontSizeBox, grow, shrink, clearFormat,
            bold, italic, underline, strike, fontColor
        )
        val paragraph = group(
            "Абзац", alignLeft, alignCenter, alignRight, alignJustify,
            indentLess, indentMore, lineSpacingBox
        )
        val styles = group("Стили", styleNormal, styleHeading1, styleHeading2)
        val editing = group("Редактирование", find, replace, selectAll)
        wordHomePlan = linkedMapOf(
            document to listOf(neu, open, save),
            history to listOf(undo, redo),
            clip to listOf(paste, cut, copy, formatPainter),
            font to listOf(fontFamily, fontSizeBox, grow, shrink, clearFormat, bold, italic, underline, strike, fontColor),
            paragraph to listOf(alignLeft, alignCenter, alignRight, alignJustify, indentLess, indentMore, lineSpacingBox),
            styles to listOf(styleNormal, styleHeading1, styleHeading2),
            editing to listOf(find, replace, selectAll)
        )
        wordHomeRow = listOf(document, sep(), history, sep(), clip, sep(), font, sep(), paragraph, sep(), styles, sep(), editing)

        wordInsertPlan = linkedMapOf(insertGroup to listOf(insertSymbol, insertDate, pageBreak))
        wordInsertRow = listOf(insertGroup)
        wordLayoutPlan = linkedMapOf(pageGroup to listOf(landscape, marginsLess, marginsMore))
        wordLayoutRow = listOf(pageGroup)

        val proof = group("Правописание", spell, wordCount)
        val language = group("Язык", langBox)
        wordReviewPlan = linkedMapOf(
            proof to listOf(spell, wordCount),
            language to listOf(langBox)
        )
        wordReviewRow = listOf(proof, sep(), language)

        val modes = group("Режимы", printLayout, readMode)
        val show = group("Показать", ruler, grid, navigation)
        val zoom = group("Масштаб", zoomOut, zoomLabel, zoomIn, zoomReset, fitWidth)
        val window = group("Окно", fullScreen)
        val look = group("Оформление", layoutBox, themeFamilyBox, officeYearBox, wordYearBox)
        val book = group("Фикбук", fanficMode)
        wordViewPlan = linkedMapOf(
            modes to listOf(printLayout, readMode),
            show to listOf(ruler, grid, navigation),
            zoom to listOf(zoomOut, zoomLabel, zoomIn, zoomReset, fitWidth),
            window to listOf(fullScreen),
            look to listOf(layoutBox, themeFamilyBox, officeYearBox, wordYearBox),
            book to listOf(fanficMode)
        )
        wordViewRow = listOf(modes, sep(), show, sep(), zoom, sep(), window, sep(), look, sep(), book)
    }

    private fun mount(plan: Map<VBox, List<Node>>) {
        managedGroups.forEach { boxOf(it).children.clear() }
        groupContents.clear()
        plan.forEach { (group, nodes) ->
            groupContents[group] = nodes
            boxOf(group).children.setAll(nodes)
        }
    }

    private fun boxOf(group: VBox): HBox {
        val first = group.children.firstOrNull()
        if (first is HBox) return first
        val created = HBox(4.0).apply { alignment = Pos.CENTER_LEFT }
        if (group.children.isEmpty()) group.children.add(0, created) else group.children[0] = created
        return created
    }

    private fun applyMyOfficeLayout() {
        groupLabels.forEach { it.isVisible = false; it.isManaged = false }
        fixedBar.alignment = Pos.CENTER_LEFT
        fixedBar.padding = Insets(4.0, 8.0, 4.0, 8.0)
        barGaps.clear()
        fixedBar.children.setAll(
            neu, open, save, barGap(), undo, redo, barGap(),
            cut, copy, paste, barGap(),
            bold, italic, underline, barGap(),
            alignLeft, alignCenter, alignRight, alignJustify, barGap(),
            find, replace, spell
        )
        textCard.children.setAll(fontCaption, fontFamily, fontSizeBox, rowOf(grow, shrink, strike, clearFormat), fontColor)
        indentBlock.children.setAll(indentCaption, rowOf(indentLess, indentMore))
        spacingBlock.children.setAll(spacingCaption, lineSpacingBox)
        styleBlock.children.setAll(styleCaption, styleNormal, styleHeading1, styleHeading2)
        paragraphCard.children.setAll(indentBlock, spacingBlock, styleBlock)
        toolsCard.children.setAll(
            insertCaption, rowOf(insertSymbol, insertDate, pageBreak),
            pageCaption, rowOf(landscape, marginsLess, marginsMore),
            viewCaption,
            rowOf(zoomOut, zoomLabel, zoomIn, zoomReset, fitWidth),
            rowOf(ruler, grid, navigation, printLayout, readMode, fullScreen, wordCount, fanficMode),
            layoutBox, themeFamilyBox, officeYearBox, wordYearBox, langBox
        )
        inspectorBody.children.setAll(inspectorTitle, inspectorHint, textCard, paragraphCard, toolsCard)
        children.setAll(fixedBar)
        val selected = contextSelected
        val heading = contextHeading
        contextMode = ""
        showContext(selected, heading)
        themePack?.let { styleInspector(it) }
    }

    fun showContext(selected: Boolean, heading: Boolean) {
        contextSelected = selected
        contextHeading = heading
        if (chrome != UiChrome.MY_OFFICE) return
        val mode = when {
            selected -> "text"
            heading -> "heading"
            else -> "paragraph"
        }
        if (mode == contextMode && textCard.parent != null) return
        contextMode = mode
        inspectorTitle.text = when (mode) {
            "text" -> "Текст"
            "heading" -> "Заголовок"
            else -> "Абзац"
        }
        inspectorHint.text = when (mode) {
            "text" -> "Формат выделенного фрагмента"
            "heading" -> "Стиль заголовка в позиции курсора"
            else -> "Абзац в позиции курсора"
        }
        val font = mode != "paragraph"
        val styles = mode != "text"
        val body = mode == "paragraph"
        textCard.isVisible = font
        textCard.isManaged = font
        paragraphCard.isVisible = styles
        paragraphCard.isManaged = styles
        indentBlock.isVisible = body
        indentBlock.isManaged = body
        spacingBlock.isVisible = body
        spacingBlock.isManaged = body
        styleBlock.isVisible = styles
        styleBlock.isManaged = styles
    }

    internal fun layoutBoxQuiet(): Boolean = quietLayout

    internal fun onFixedBar(node: Node): Boolean =
        generateSequence(node.parent) { it.parent }.any { it == fixedBar }

    internal fun inspectorShows(node: Node): Boolean {
        var current: Node? = node
        while (current != null) {
            if (!current.isVisible || !current.isManaged) return false
            if (current == inspectorBody) return true
            current = current.parent
        }
        return false
    }

    internal fun inspectorHeading(): String = inspectorTitle.text

    private fun styleInspector(pack: Theme.Pack) {
        inspectorPane.styleClass.remove("office-inspector")
        inspectorPane.styleClass.add("office-inspector")
        inspectorBody.style = "-fx-background-color: ${pack.menuBg};"
        inspectorTitle.style = "-fx-text-fill: ${pack.buttonFg}; -fx-font-size: 16px; -fx-font-weight: bold;"
        inspectorHint.style = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 12px;"
        val caption = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 11px; -fx-font-weight: bold;"
        inspectorLabels.forEach { it.style = caption }
        val card = "-fx-background-color: ${pack.popupBg}; -fx-background-radius: 8; " +
            "-fx-border-color: ${pack.border}; -fx-border-radius: 8; -fx-padding: 8;"
        listOf(textCard, paragraphCard, toolsCard).forEach { it.style = card }
        fixedBar.style = "-fx-background-color: ${pack.ribbonBg}; -fx-border-color: ${pack.border}; -fx-border-width: 0 0 1 0;"
        barGaps.forEach { it.style = "-fx-background-color: ${pack.border};" }
    }

    private fun inspectorCaption(text: String) = Label(text).apply { inspectorLabels += this }

    private fun rowOf(vararg nodes: Node) = HBox(4.0, *nodes).apply { alignment = Pos.CENTER_LEFT }

    fun applyTheme(pack: Theme.Pack) {
        themePack = pack
        row.spacing = 10.0
        themeFamilyBox.prefWidth = 168.0
        langBox.prefWidth = 140.0
        groupContents.forEach { (group, controls) ->
            val items = HBox(4.0, *controls.toTypedArray()).apply { alignment = Pos.CENTER_LEFT }
            if (group.children.isEmpty()) group.children.add(0, items) else group.children[0] = items
        }
        val background = pack.ribbonBg
        val border = pack.border
        style = when (chrome) {
            UiChrome.MY_OFFICE ->
                "-fx-background-color: ${pack.menuBg}; -fx-border-color: ${pack.border}; -fx-border-width: 0 0 1 0;"
            else ->
                "-fx-background-color: $background; -fx-border-color: $border; -fx-border-width: 0 0 1 0;"
        }
        row.style = "-fx-background-color: $background;"
        standardRow.style = "-fx-background-color: $background; -fx-border-color: ${pack.border}; -fx-border-width: 0 0 1 0;"
        formattingRow.style = "-fx-background-color: $background;"
        groupLabels.forEach { it.style = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 10px;" }
        separators.forEach { it.style = "-fx-background-color: ${pack.border}; -fx-pref-height: 40;" }
        barGaps.forEach { it.style = "-fx-background-color: ${pack.border};" }
        buttons.forEach { styleButton(it, pack) }
        styleCombo(fontFamily, pack) { it ?: "" }
        styleCombo(fontSizeBox, pack) { it ?: "" }
        styleCombo(layoutBox, pack) { it?.title ?: "" }
        styleCombo(themeFamilyBox, pack) { it?.title ?: "" }
        styleCombo(officeYearBox, pack) { it?.officeYear ?: "" }
        styleCombo(wordYearBox, pack) { it?.wordYear ?: "" }
        styleCombo(langBox, pack) { it?.title ?: "" }
        styleCombo(lineSpacingBox, pack) { it ?: "" }
        fontColor.style = "-fx-background-color: ${pack.popupBg}; -fx-color-label-visible: false;"
        zoomLabel.style = "-fx-text-fill: ${pack.buttonFg}; -fx-font-size: 12px; -fx-alignment: center;"
        if (chrome == UiChrome.MY_OFFICE) applyMyOfficeLayout()
        if (chrome == UiChrome.OPEN_OFFICE || chrome == UiChrome.MY_OFFICE) {
            themePack?.let { styleInspector(it) }
        }
    }

    fun resolvedTheme(): AppTheme {
        val family = themeFamilyBox.value ?: ThemeFamily.DARK
        return when (family) {
            ThemeFamily.WORD -> wordYearBox.value ?: AppTheme.WORD_2023
            ThemeFamily.OPEN_OFFICE -> officeYearBox.value ?: AppTheme.OPEN_OFFICE
            else -> family.variants.first()
        }
    }

    fun showTheme(theme: AppTheme, layout: UiChrome = UiChrome.WORD) {
        val family = theme.family
        val word = family == ThemeFamily.WORD
        val office = family == ThemeFamily.OPEN_OFFICE
        wordYearBox.isVisible = word
        wordYearBox.isManaged = word
        officeYearBox.isVisible = office
        officeYearBox.isManaged = office
        val showLayout = theme.isPalette || theme == AppTheme.MYOFFICE
        layoutBox.isVisible = showLayout
        layoutBox.isManaged = showLayout
        quietLayout = true
        if (layoutBox.value != layout) layoutBox.value = layout
        if (word && wordYearBox.value != theme) wordYearBox.value = theme
        if (office && officeYearBox.value != theme) officeYearBox.value = theme
        quietLayout = false
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
        return VBox(4.0, items, label).apply {
            alignment = Pos.CENTER
            groupContents[this] = nodes.toList()
            managedGroups += this
        }
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

    private fun caption(button: Button): String {
        val base = originalButtonText[button] ?: button.text
        val labeled = when (chrome) {
            UiChrome.OPEN_OFFICE -> when (button) {
                neu -> "▤"; open -> "📂"; save -> "▣"; cut -> "✂"; copy -> "▣"
                paste -> "▤"; bold -> "B"; italic -> "I"; underline -> "U"
                alignLeft -> "≡"; alignCenter -> "☰"; alignRight -> "≡"
                else -> base
            }
            UiChrome.MY_OFFICE -> when (button) {
                neu -> "＋"; open -> "📂"; save -> "💾"; cut -> "✂"; copy -> "⧉"
                paste -> "▣"; bold -> "Ж"; italic -> "К"; underline -> "Ч"
                grow -> "A+"; shrink -> "A−"
                alignLeft -> "≡"; alignCenter -> "☰"; alignRight -> "≡"; alignJustify -> "☰"
                else -> base
            }
            else -> base
        }
        return if (iconSet == IconSet.CUSTOM && ribbonIcon(button) != null) "" else labeled
    }

    private fun ribbonIcon(button: Button): String? = when (button) {
        neu -> OfficeIconCatalog.NEW
        open -> OfficeIconCatalog.OPEN
        save -> OfficeIconCatalog.SAVE
        commitFanficChapter -> OfficeIconCatalog.COMMIT
        cut -> OfficeIconCatalog.CUT
        copy -> OfficeIconCatalog.COPY
        paste -> OfficeIconCatalog.PASTE
        bold -> OfficeIconCatalog.BOLD
        italic -> OfficeIconCatalog.ITALIC
        underline -> OfficeIconCatalog.UNDERLINE
        grow -> OfficeIconCatalog.GROW
        shrink -> OfficeIconCatalog.SHRINK
        alignLeft -> OfficeIconCatalog.ALIGN_LEFT
        alignCenter -> OfficeIconCatalog.ALIGN_CENTER
        alignRight -> OfficeIconCatalog.ALIGN_RIGHT
        alignJustify -> OfficeIconCatalog.ALIGN_JUSTIFY
        indentMore -> OfficeIconCatalog.INDENT_MORE
        indentLess -> OfficeIconCatalog.INDENT_LESS
        find -> OfficeIconCatalog.FIND
        replace -> OfficeIconCatalog.REPLACE
        spell -> OfficeIconCatalog.SPELL
        ruler -> OfficeIconCatalog.RULER
        grid -> OfficeIconCatalog.GRID
        fanficMode -> OfficeIconCatalog.FANFIC
        insertSymbol -> OfficeIconCatalog.SYMBOL
        insertDate -> OfficeIconCatalog.CALENDAR
        pageBreak -> OfficeIconCatalog.PAGE_BREAK
        landscape -> OfficeIconCatalog.LANDSCAPE
        wordCount -> OfficeIconCatalog.WORD_COUNT
        zoomOut -> OfficeIconCatalog.ZOOM_OUT
        zoomIn -> OfficeIconCatalog.ZOOM_IN
        undo -> OfficeIconCatalog.UNDO
        redo -> OfficeIconCatalog.REDO
        else -> null
    }

    private fun iconInk(pack: Theme.Pack, on: Boolean): Color =
        runCatching { Color.web(if (on) pack.onAccent else pack.buttonFg) }.getOrDefault(Color.BLACK)

    private fun installIcon(button: Button, pack: Theme.Pack) {
        button.text = caption(button)
        val name = ribbonIcon(button)
        if (iconSet != IconSet.CUSTOM || name == null) {
            button.graphic = null
            button.contentDisplay = ContentDisplay.TEXT_ONLY
            glyphs.remove(button)
            return
        }
        val size = if (chrome == UiChrome.OPEN_OFFICE) 15.0 else 16.0
        val glyph = LucideIcons.glyph(name, size, iconInk(pack, button in armed))
        glyphs[button] = glyph
        button.graphic = glyph
        button.contentDisplay = ContentDisplay.GRAPHIC_ONLY
    }

    private fun styleButton(button: Button, pack: Theme.Pack) {
        installIcon(button, pack)
        val accent = "-fx-background-color: ${pack.accent}; -fx-text-fill: ${pack.onAccent};"
        if (chrome == UiChrome.OPEN_OFFICE) {
            button.minWidth = 27.0
            button.minHeight = 26.0
            button.prefHeight = 26.0
            val idle = "-fx-background-color: transparent; -fx-text-fill: ${pack.buttonFg}; -fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 2 5 2 5;"
            val hover = "-fx-background-color: ${pack.buttonHover}; -fx-border-color: ${pack.border}; -fx-text-fill: ${pack.buttonFg}; -fx-padding: 2 5 2 5;"
            val on = "$accent -fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 2 5 2 5; -fx-background-radius: 3;"
            faces[button] = Face(idle, hover, on, on)
        } else if (chrome == UiChrome.MY_OFFICE) {
            button.minWidth = 32.0
            button.minHeight = 28.0
            button.prefHeight = 28.0
            val idle =
                "-fx-background-color: transparent; -fx-text-fill: ${pack.buttonFg}; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
            val hover =
                "-fx-background-color: ${pack.buttonHover}; -fx-text-fill: ${pack.buttonFg}; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
            val on = "$accent -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4;"
            faces[button] = Face(idle, hover, on, on)
        } else {
            button.minWidth = 40.0
            button.minHeight = 32.0
            button.prefHeight = -1.0
            val idle =
                "-fx-background-color: transparent; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; " +
                    "-fx-font-weight: bold; -fx-text-fill: ${pack.buttonFg}; -fx-background-radius: 8; -fx-border-radius: 8; " +
                    "-fx-padding: 4 8 4 8;"
            val hover =
                "-fx-background-color: ${pack.buttonHover}; -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; " +
                    "-fx-font-weight: bold; -fx-text-fill: ${pack.buttonFg}; -fx-background-radius: 8; -fx-border-radius: 8; " +
                    "-fx-padding: 4 8 4 8;"
            val on =
                "$accent -fx-font-family: 'Segoe UI'; -fx-font-size: 13px; -fx-font-weight: bold; " +
                    "-fx-background-radius: 8; -fx-padding: 4 8 4 8;"
            faces[button] = Face(idle, hover, on, on)
        }
        button.setOnMouseEntered { paintButton(button, true) }
        button.setOnMouseExited { paintButton(button, false) }
        paintButton(button, button.isHover)
    }

    private fun paintButton(button: Button, hover: Boolean) {
        val face = faces[button] ?: return
        val on = button in armed
        button.style = when {
            on && hover -> face.armedHover
            on -> face.armed
            hover -> face.hover
            else -> face.idle
        }
        val pack = themePack ?: return
        glyphs[button]?.color(iconInk(pack, on))
    }

    private data class Face(val idle: String, val hover: String, val armed: String, val armedHover: String)
}
