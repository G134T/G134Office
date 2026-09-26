package org.example.ui

import javafx.scene.control.CheckMenuItem
import javafx.scene.control.Menu
import javafx.scene.control.MenuBar
import javafx.scene.control.MenuItem
import javafx.scene.control.RadioMenuItem
import javafx.scene.control.ToggleGroup
import javafx.scene.control.SeparatorMenuItem
import javafx.scene.input.KeyCombination

class AppMenus {
    val settings = item("Настройки")
    val neu = item("Создать", "Shortcut+N")
    val open = item("Открыть...", "Shortcut+O")
    val close = item("Закрыть", "Shortcut+W")
    val save = item("Сохранить", "Shortcut+S")
    val saveAs = item("Сохранить как...", "Shortcut+Shift+S")
    val saveFanficChapter = item("Сохранить главу для Фикбук…")
    val commitFanficChapter = item("Закоммитить главу")
    val print = item("Печать...", "Shortcut+P")
    val exit = item("Выход", "Shortcut+Q")

    val undo = item("Отменить", "Shortcut+Z")
    val redo = item("Повторить", "Shortcut+Y")
    val cut = item("Вырезать", "Shortcut+X")
    val copy = item("Копировать", "Shortcut+C")
    val paste = item("Вставить", "Shortcut+V")
    val selectAll = item("Выделить всё", "Shortcut+A")
    val find = item("Найти...", "Shortcut+F")
    val replace = item("Заменить...", "Shortcut+H")

    private val themeGroup = ToggleGroup()
    val themeItems: Map<AppTheme, RadioMenuItem> =
        AppTheme.entries.associateWith { theme ->
            RadioMenuItem(theme.title).apply { toggleGroup = themeGroup }
        }

    val viewRuler = check("Линейка", false)
    val viewGrid = check("Сетка", false)
    val viewSpaceScroll = check("Прокрутка пробелом", false)
    val viewRibbon = check("Лента", true)
    val viewStatus = check("Строка состояния", true)
    val viewFanfic = check("ФФ", false)
    val zoomIn = item("Увеличить", "Shortcut+PLUS")
    val zoomOut = item("Уменьшить", "Shortcut+MINUS")
    val zoomReset = item("Масштаб 100%", "Shortcut+0")
    val fullScreen = item("Во весь экран", "F11")

    val insertDate = item("Дата и время")
    val insertSymbol = item("Символ…")
    val wordCount = item("Количество слов")
    val checkText = item("Проверить текст", "F7")
    val langRu = item("Язык: русский")
    val langUk = item("Язык: українська")
    val langBe = item("Язык: беларуская")
    val langEn = item("Язык: English")

    val alignLeft = item("По левому краю", "Shortcut+L")
    val alignCenter = item("По центру", "Shortcut+E")
    val alignRight = item("По правому краю", "Shortcut+R")
    val alignJustify = item("По ширине", "Shortcut+J")
    val indentMore = item("Увеличить отступ", "Shortcut+M")
    val indentLess = item("Уменьшить отступ", "Shortcut+Shift+M")

    val pdfRotateLeft = item("Вращать страницу влево")
    val pdfRotateRight = item("Вращать страницу вправо")
    val pdfInsertBlank = item("Вставить пустую страницу")
    val pdfDeletePage = item("Удалить страницу")
    val pdfMoveUp = item("Страница вверх")
    val pdfMoveDown = item("Страница вниз")
    val pdfInsertFile = item("Вставить PDF...")
    val pdfExtract = item("Извлечь страницы...")
    val pdfProps = item("Свойства PDF...")

    val about = item("О программе")

    private val pdfItems
        get() = listOf(
            pdfRotateLeft, pdfRotateRight, pdfInsertBlank, pdfDeletePage,
            pdfMoveUp, pdfMoveDown, pdfInsertFile, pdfExtract, pdfProps
        )

    lateinit var bar: MenuBar
        private set
    private lateinit var editMenu: Menu
    private lateinit var reviewMenu: Menu
    private lateinit var formatMenu: Menu
    private lateinit var insertMenu: Menu
    private var chrome = UiChrome.STANDARD

    fun build(): MenuBar {
        val file = Menu("Файл", null, neu, open, close, save, saveAs, saveFanficChapter,
            commitFanficChapter, print, SeparatorMenuItem(), exit)
        val home = Menu(
            "Главная", null,
            undo, redo, SeparatorMenuItem(),
            cut, copy, paste, selectAll, SeparatorMenuItem(),
            find, replace
        )
        val view = Menu(
            "Вид", null,
            viewRuler, viewGrid, viewSpaceScroll, SeparatorMenuItem(),
            viewRibbon, viewStatus, viewFanfic,
            Menu("Оформление").apply {
                items.addAll(
                    themeItems.getValue(AppTheme.LIGHT),
                    themeItems.getValue(AppTheme.GRAY),
                    themeItems.getValue(AppTheme.DARK),
                    themeItems.getValue(AppTheme.GLASS),
                    Menu("Microsoft Word").apply {
                        items.addAll(ThemeFamily.WORD.variants.map { theme ->
                            themeItems.getValue(theme).also { item -> item.text = theme.wordYear ?: theme.title }
                        })
                    },
                    themeItems.getValue(AppTheme.NOTEPAD_WIN11),
                    themeItems.getValue(AppTheme.OPEN_OFFICE),
                    themeItems.getValue(AppTheme.MYOFFICE)
                )
            }, SeparatorMenuItem(),
            zoomIn, zoomOut, zoomReset, SeparatorMenuItem(),
            fullScreen
        )
        val insert = Menu("Вставка", null, insertSymbol, insertDate)
        val format = Menu(
            "Формат", null,
            alignLeft, alignCenter, alignRight, alignJustify,
            SeparatorMenuItem(),
            indentMore, indentLess
        )
        val review = Menu(
            "Рецензирование", null,
            wordCount,
            SeparatorMenuItem(),
            checkText,
            langRu, langUk, langBe, langEn
        )
        val pdf = Menu(
            "PDF", null,
            pdfRotateLeft, pdfRotateRight, SeparatorMenuItem(),
            pdfInsertBlank, pdfDeletePage, pdfMoveUp, pdfMoveDown, SeparatorMenuItem(),
            pdfInsertFile, pdfExtract, SeparatorMenuItem(),
            pdfProps
        )
        val help = Menu("Справка", null, about)
        val service = Menu("Сервис", null, settings)
        bar = MenuBar(file, home, view, insert, format, review, pdf, service, help)
        editMenu = home
        reviewMenu = review
        formatMenu = format
        insertMenu = insert
        setPdfEnabled(false)
        return bar
    }

    fun applyLanguage(language: String) {
        if (!::bar.isInitialized) return
        fun translate(menu: Menu) {
            menu.text = UiText.get(menu.text, language)
            menu.items.forEach { item ->
                if (item is Menu) translate(item)
                else item.text?.let { item.text = UiText.get(it, language) }
            }
        }
        bar.menus.forEach(::translate)
    }

    fun setPdfEnabled(on: Boolean) {
        pdfItems.forEach { it.isDisable = !on }
    }

    fun selectTheme(theme: AppTheme) {
        themeItems[theme]?.isSelected = true
    }

    fun setChrome(next: UiChrome) {
        chrome = next
        if (!::bar.isInitialized) return
        when (next) {
            UiChrome.OPEN_OFFICE -> {
                editMenu.text = "Правка"
                insertSymbol.text = "Специальный символ..."
                formatMenu.text = "Формат"
                reviewMenu.text = "Сервис"
            }
            UiChrome.MY_OFFICE -> {
                editMenu.text = "Главная"
                insertSymbol.text = "Символы..."
                formatMenu.text = "Макет"
                reviewMenu.text = "Рецензирование"
            }
            UiChrome.STANDARD -> {
                editMenu.text = "Главная"
                insertSymbol.text = "Символ…"
                formatMenu.text = "Формат"
                reviewMenu.text = "Рецензирование"
            }
        }
    }

    fun applyTheme(pack: Theme.Pack) {
        if (!::bar.isInitialized) return
        bar.style = when (chrome) {
            UiChrome.OPEN_OFFICE ->
                "-fx-background-color: linear-gradient(to bottom, #f7f8fa, #dce2eb); -fx-border-color: #9daab9; -fx-border-width: 0 0 1 0;"
            UiChrome.MY_OFFICE ->
                "-fx-background-color: #0b5cab; -fx-border-color: #084b8a; -fx-border-width: 0 0 1 0;"
            UiChrome.STANDARD ->
                "-fx-background-color: ${pack.menuBg};"
        }
    }

    private fun item(text: String, hotkey: String? = null) = MenuItem(text).apply {
        if (hotkey != null) accelerator = KeyCombination.keyCombination(hotkey)
    }

    private fun check(text: String, on: Boolean) = CheckMenuItem(text).apply { isSelected = on }
}
