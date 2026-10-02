package org.example.ui

import javafx.scene.Scene
import java.io.File
import kotlin.math.min

enum class AppTheme(
    val title: String,
    val compactRibbon: Boolean = false,
    val wordYear: String? = null,
    val officeYear: String? = null
) {
    LIGHT("Светлая"),
    GRAY("Серая"),
    DARK("Тёмная AMOLED"),
    GLASS("Стеклянная"),
    NOTEPAD_WIN11("Блокнот Windows 11", compactRibbon = true),
    WORD_2003("Microsoft Word 2003", wordYear = "2003"),
    WORD_2010("Microsoft Word 2010", wordYear = "2010"),
    WORD_2016("Microsoft Word 2016", wordYear = "2016"),
    WORD_2019("Microsoft Word 2019", wordYear = "2019"),
    WORD_2023("Microsoft Word 2023", wordYear = "2023"),
    WORD_2026("Microsoft Word 2026", wordYear = "2026"),
    OO_2002("OpenOffice 2002", officeYear = "2002"),
    OPEN_OFFICE("OpenOffice 2005", officeYear = "2005"),
    OO_2008("OpenOffice 2008", officeYear = "2008"),
    OO_2013("OpenOffice 2013", officeYear = "2013"),
    OO_2014("OpenOffice 2014", officeYear = "2014"),
    OO_2023("OpenOffice 2023", officeYear = "2023"),
    MYOFFICE("МойОфис");

    val family: ThemeFamily get() = ThemeFamily.of(this)

    /** Светлая, Серая, Тёмная AMOLED и Стеклянная — палитры, их можно надеть на любую раскладку. */
    val isPalette: Boolean
        get() = this == LIGHT || this == GRAY || this == DARK || this == GLASS
}

enum class ThemeFamily(val title: String) {
    LIGHT("Светлая"),
    GRAY("Серая"),
    DARK("Тёмная AMOLED"),
    GLASS("Стеклянная"),
    WORD("Microsoft Word"),
    NOTEPAD("Блокнот Windows 11"),
    OPEN_OFFICE("OpenOffice"),
    MYOFFICE("МойОфис");

    val variants: List<AppTheme> get() = when (this) {
        LIGHT -> listOf(AppTheme.LIGHT)
        GRAY -> listOf(AppTheme.GRAY)
        DARK -> listOf(AppTheme.DARK)
        GLASS -> listOf(AppTheme.GLASS)
        WORD -> listOf(
            AppTheme.WORD_2003, AppTheme.WORD_2010, AppTheme.WORD_2016,
            AppTheme.WORD_2019, AppTheme.WORD_2023, AppTheme.WORD_2026
        )
        NOTEPAD -> listOf(AppTheme.NOTEPAD_WIN11)
        OPEN_OFFICE -> listOf(
            AppTheme.OO_2002, AppTheme.OPEN_OFFICE, AppTheme.OO_2008,
            AppTheme.OO_2013, AppTheme.OO_2014, AppTheme.OO_2023
        )
        MYOFFICE -> listOf(AppTheme.MYOFFICE)
    }

    companion object {
        fun of(theme: AppTheme) = entries.first { theme in it.variants }
    }
}

enum class UiChrome {
    /** Одна полная лента годовых оформлений Word. */
    STANDARD,
    /** Классика OpenOffice: меню и две компактные панели. */
    OPEN_OFFICE,
    /** Компактный МойОфис: одна панель и инспектор справа. */
    MY_OFFICE,
    /** Лента Word: вкладки Главная, Вставка, Разметка, Рецензирование, Вид. */
    WORD;

    val title: String
        get() = when (this) {
            WORD, STANDARD -> "Лента — Word"
            OPEN_OFFICE -> "Классический — OpenOffice"
            MY_OFFICE -> "Компактный — МойОфис"
        }

    companion object {
        val layouts = listOf(WORD, OPEN_OFFICE, MY_OFFICE)

        fun fromPref(name: String?) = layouts.firstOrNull { it.name == name }

        fun defaultLayout(theme: AppTheme?) = when {
            theme?.officeYear != null -> OPEN_OFFICE
            theme == AppTheme.MYOFFICE -> MY_OFFICE
            else -> WORD
        }

        fun of(theme: AppTheme) = when {
            theme.officeYear != null -> OPEN_OFFICE
            theme == AppTheme.MYOFFICE -> MY_OFFICE
            theme == AppTheme.LIGHT || theme == AppTheme.GRAY ||
                theme == AppTheme.DARK || theme == AppTheme.GLASS -> WORD
            else -> STANDARD
        }

        /** Палитры слушаются выбранной раскладки. Фирменные темы оставляют свой вид. */
        fun resolve(theme: AppTheme, layout: UiChrome) = when {
            theme.officeYear != null -> OPEN_OFFICE
            theme == AppTheme.MYOFFICE -> MY_OFFICE
            theme.isPalette -> if (layout == OPEN_OFFICE || layout == MY_OFFICE) layout else WORD
            else -> of(theme)
        }
    }
}

object Theme {
    data class Pack(
        val windowBg: String,
        val ribbonBg: String,
        val menuBg: String,
        val menuFg: String,
        val popupBg: String,
        val popupFg: String,
        val editorBg: String,
        val editorFg: String,
        val statusBg: String,
        val statusFg: String,
        val border: String,
        val buttonHover: String,
        val buttonFg: String,
        val labelFg: String,
        val accent: String,
        val menuHoverBg: String = "",
        val menuHoverFg: String = "",
        val itemHoverBg: String = "",
        val itemHoverFg: String = "",
        val hintFg: String = "",
        val onAccent: String = "#ffffff",
        val menuRound: Int = 8
    )

    data class Ink(
        val menuHoverBg: String,
        val menuHoverFg: String,
        val itemHoverBg: String,
        val itemHoverFg: String,
        val hintFg: String,
        val onAccent: String
    )

    fun dialogStylesheet(pack: Pack): String {
        val ink = ink(pack)
        val css = """
            .root, .dialog-pane { -fx-background-color: ${pack.popupBg}; }
            .label, .check-box, .radio-button { -fx-text-fill: ${pack.popupFg}; -fx-font-size: 13px; }
            .text-field {
                -fx-background-color: ${pack.editorBg};
                -fx-text-fill: ${pack.editorFg};
                -fx-prompt-text-fill: ${ink.hintFg};
                -fx-border-color: ${pack.border};
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-padding: 6 8 6 8;
            }
            .button {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-border-color: ${pack.border};
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-padding: 6 12 6 12;
            }
            .button:hover { -fx-background-color: ${ink.itemHoverBg}; -fx-text-fill: ${ink.itemHoverFg}; }
            .button:default {
                -fx-background-color: ${pack.accent};
                -fx-text-fill: ${pack.onAccent};
                -fx-border-color: transparent;
            }
            .dialog-pane > .header-panel { -fx-background-color: ${pack.menuBg}; }
            .dialog-pane > .header-panel .label { -fx-text-fill: ${pack.menuFg}; }
            .combo-box, .combo-box .list-cell { -fx-background-color: ${pack.editorBg}; -fx-text-fill: ${pack.popupFg}; }
        """.trimIndent()
        return "data:text/css," + css.replace("\n", " ").replace("#", "%23")
    }

    fun ink(pack: Pack) = Ink(
        menuHoverBg = pack.menuHoverBg.ifBlank { pack.buttonHover },
        menuHoverFg = pack.menuHoverFg.ifBlank { pack.menuFg },
        itemHoverBg = pack.itemHoverBg.ifBlank { pack.accent },
        itemHoverFg = pack.itemHoverFg.ifBlank { pack.onAccent },
        hintFg = pack.hintFg.ifBlank { pack.labelFg },
        onAccent = pack.onAccent
    )

    fun pack(theme: AppTheme) = if (theme.officeYear != null) OpenOfficeTheme.pack(theme) else when (theme) {
        AppTheme.LIGHT -> skin(
            windowBg = "#d4d4d4", ribbonBg = "#ffffff", menuBg = "#ffffff", menuFg = "#1b1b1b",
            popupBg = "#ffffff", popupFg = "#1b1b1b", editorBg = "#ffffff", editorFg = "#1b1b1b",
            statusBg = "#f3f3f3", statusFg = "#333333", border = "#d0d0d0",
            buttonHover = "#e6e6e6", buttonFg = "#1b1b1b", labelFg = "#4a4a4a", accent = "#0f6cbd",
            menuHoverBg = "#f2f2f2", menuHoverFg = "#1b1b1b",
            itemHoverBg = "#deecf9", itemHoverFg = "#1b1b1b", hintFg = "#5e5e5e",
            menuRound = 2
        )
        AppTheme.GRAY -> skin(
            windowBg = "#3a3a3a", ribbonBg = "#4a4a4a", menuBg = "#3f3f3f", menuFg = "#f2f2f2",
            popupBg = "#454545", popupFg = "#f4f4f4", editorBg = "#ffffff", editorFg = "#1c1c1c",
            statusBg = "#333333", statusFg = "#e8e8e8", border = "#2a2a2a",
            buttonHover = "#5c5c5c", buttonFg = "#f5f5f5", labelFg = "#d2d2d2", accent = "#245a86",
            menuHoverBg = "#555555", menuHoverFg = "#ffffff",
            itemHoverBg = "#1f4d73", itemHoverFg = "#ffffff", hintFg = "#c8c8c8",
            menuRound = 2
        )
        AppTheme.DARK -> skin(
            windowBg = "#000000", ribbonBg = "#0a0a0a", menuBg = "#000000", menuFg = "#f5f5f5",
            popupBg = "#161616", popupFg = "#f5f5f5", editorBg = "#000000", editorFg = "#f5f5f5",
            statusBg = "#000000", statusFg = "#dcdcdc", border = "#2a2a2a",
            buttonHover = "#1c1c1c", buttonFg = "#f5f5f5", labelFg = "#c8c8c8", accent = "#2f6fde",
            menuHoverBg = "#1a1a1a", menuHoverFg = "#ffffff",
            itemHoverBg = "#1d4f91", itemHoverFg = "#ffffff", hintFg = "#a6a6a6",
            menuRound = 2
        )
        AppTheme.GLASS -> skin(
            windowBg = "#d5e4ef", ribbonBg = "#f7fbfe", menuBg = "#e7f2f8", menuFg = "#1a3348",
            popupBg = "#f8fbfe", popupFg = "#1a3348", editorBg = "#ffffff", editorFg = "#182638",
            statusBg = "#e7f2f8", statusFg = "#1a3348", border = "#b7c9d8",
            buttonHover = "#d5e7f3", buttonFg = "#1a3348", labelFg = "#3d5670", accent = "#1d5f96",
            menuHoverBg = "#d5e7f3", menuHoverFg = "#1a3348",
            itemHoverBg = "#d5e7f3", itemHoverFg = "#1a3348", hintFg = "#4e6578",
            menuRound = 6
        )
        AppTheme.NOTEPAD_WIN11 -> skin(
            windowBg = "#e9e9e9", ribbonBg = "#f9f9f9", menuBg = "#f9f9f9", menuFg = "#1a1a1a",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#f3f3f3", statusFg = "#1a1a1a", border = "#d0d0d0",
            buttonHover = "#e5e5e5", buttonFg = "#1a1a1a", labelFg = "#3a3a3a", accent = "#0067c0",
            menuHoverBg = "#e5e5e5", menuHoverFg = "#1a1a1a",
            itemHoverBg = "#e5f1fb", itemHoverFg = "#1a1a1a", hintFg = "#5c5c5c"
        )
        AppTheme.WORD_2003 -> skin(
            windowBg = "#8d8d8d", ribbonBg = "#ece9d8", menuBg = "#ece9d8", menuFg = "#000000",
            popupBg = "#ffffff", popupFg = "#000000", editorBg = "#ffffff", editorFg = "#000000",
            statusBg = "#ece9d8", statusFg = "#000000", border = "#aca899",
            buttonHover = "#ffe8a2", buttonFg = "#000000", labelFg = "#333333", accent = "#3a6ea5",
            menuHoverBg = "#fff3c4", menuHoverFg = "#000000",
            itemHoverBg = "#316ac5", itemHoverFg = "#ffffff", hintFg = "#555555",
            menuRound = 2
        )
        AppTheme.WORD_2010 -> skin(
            windowBg = "#c5d0dc", ribbonBg = "#2b579a", menuBg = "#2b579a", menuFg = "#ffffff",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#2b579a", statusFg = "#ffffff", border = "#1f4e8c",
            buttonHover = "#1f4e8c", buttonFg = "#ffffff", labelFg = "#e4eefb", accent = "#2b579a",
            menuHoverBg = "#1f4e8c", menuHoverFg = "#ffffff",
            itemHoverBg = "#c5daf5", itemHoverFg = "#1a1a1a", hintFg = "#5c6b7a",
            menuRound = 2
        )
        AppTheme.WORD_2016 -> skin(
            windowBg = "#d9d9d9", ribbonBg = "#f3f3f3", menuBg = "#ffffff", menuFg = "#1a1a1a",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#2b579a", statusFg = "#ffffff", border = "#c8c8c8",
            buttonHover = "#e1e1e1", buttonFg = "#1a1a1a", labelFg = "#444444", accent = "#2b579a",
            menuHoverBg = "#e6e6e6", menuHoverFg = "#1a1a1a",
            itemHoverBg = "#cde6f7", itemHoverFg = "#1a1a1a", hintFg = "#5c5c5c"
        )
        AppTheme.WORD_2019 -> skin(
            windowBg = "#e6e6e6", ribbonBg = "#f3f3f3", menuBg = "#ffffff", menuFg = "#1b1b1b",
            popupBg = "#ffffff", popupFg = "#1b1b1b", editorBg = "#ffffff", editorFg = "#1b1b1b",
            statusBg = "#f3f3f3", statusFg = "#333333", border = "#d2d2d2",
            buttonHover = "#e5e5e5", buttonFg = "#1b1b1b", labelFg = "#444444", accent = "#185abd",
            menuHoverBg = "#f0f0f0", menuHoverFg = "#1b1b1b",
            itemHoverBg = "#deecf9", itemHoverFg = "#1b1b1b", hintFg = "#666666"
        )
        AppTheme.WORD_2023 -> skin(
            windowBg = "#f0f0f0", ribbonBg = "#ffffff", menuBg = "#ffffff", menuFg = "#1b1b1b",
            popupBg = "#ffffff", popupFg = "#1b1b1b", editorBg = "#ffffff", editorFg = "#1b1b1b",
            statusBg = "#f5f5f5", statusFg = "#333333", border = "#e1e1e1",
            buttonHover = "#f0f0f0", buttonFg = "#1b1b1b", labelFg = "#424242", accent = "#0f6cbd",
            menuHoverBg = "#f5f5f5", menuHoverFg = "#1b1b1b",
            itemHoverBg = "#ebf3fc", itemHoverFg = "#1b1b1b", hintFg = "#616161"
        )
        AppTheme.WORD_2026 -> skin(
            windowBg = "#0b0d11", ribbonBg = "#171a21", menuBg = "#171a21", menuFg = "#f3f6fb",
            popupBg = "#1d222b", popupFg = "#f3f6fb", editorBg = "#12151c", editorFg = "#f3f6fb",
            statusBg = "#171a21", statusFg = "#d7deea", border = "#2a3140",
            buttonHover = "#2a3344", buttonFg = "#ffffff", labelFg = "#c5d0e0", accent = "#2f6fde",
            menuHoverBg = "#2a3344", menuHoverFg = "#ffffff",
            itemHoverBg = "#2f4f86", itemHoverFg = "#ffffff", hintFg = "#a9b4c7"
        )
        AppTheme.MYOFFICE -> skin(
            windowBg = "#d5e2ee", ribbonBg = "#ffffff", menuBg = "#0b5cab", menuFg = "#ffffff",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#0b5cab", statusFg = "#ffffff", border = "#9bb8d4",
            buttonHover = "#d7e8f8", buttonFg = "#0b5cab", labelFg = "#3d5470", accent = "#0b5cab",
            menuHoverBg = "#084f92", menuHoverFg = "#ffffff",
            itemHoverBg = "#0b5cab", itemHoverFg = "#ffffff", hintFg = "#5a6e82"
        )
        else -> OpenOfficeTheme.pack(theme)
    }

    private fun skin(
        windowBg: String,
        ribbonBg: String,
        menuBg: String,
        menuFg: String,
        popupBg: String,
        popupFg: String,
        editorBg: String,
        editorFg: String,
        statusBg: String,
        statusFg: String,
        border: String,
        buttonHover: String,
        buttonFg: String,
        labelFg: String,
        accent: String,
        menuHoverBg: String,
        menuHoverFg: String,
        itemHoverBg: String,
        itemHoverFg: String,
        hintFg: String,
        onAccent: String = "#ffffff",
        menuRound: Int = 8
    ) = Pack(
        windowBg, ribbonBg, menuBg, menuFg, popupBg, popupFg, editorBg, editorFg,
        statusBg, statusFg, border, buttonHover, buttonFg, labelFg, accent,
        menuHoverBg, menuHoverFg, itemHoverBg, itemHoverFg, hintFg, onAccent, menuRound
    )

    fun applyCss(scene: Scene, pack: Pack) {
        scene.root.styleClass.remove("glass-office")
        if (pack == pack(AppTheme.GLASS)) scene.root.styleClass.add("glass-office")
        val ink = ink(pack)
        val itemRound = min(4, pack.menuRound)
        val css = """
            .root { -fx-background-color: ${pack.windowBg}; -fx-background-radius: 22; }
            .menu-bar {
                -fx-background-color: ${pack.menuBg};
                -fx-border-color: ${pack.border};
                -fx-border-width: 0 0 1 0;
                -fx-padding: 2 4 2 4;
            }
            .menu-bar .container { -fx-background-color: transparent; }
            .menu-bar .menu {
                -fx-background-color: transparent;
                -fx-background-radius: ${pack.menuRound};
                -fx-padding: 4 10 4 10;
            }
            .menu-bar .menu > .label { -fx-text-fill: ${pack.menuFg}; -fx-font-size: 13px; }
            .menu-bar .menu:hover,
            .menu-bar .menu:showing { -fx-background-color: ${ink.menuHoverBg}; }
            .menu-bar .menu:hover > .label,
            .menu-bar .menu:showing > .label { -fx-text-fill: ${ink.menuHoverFg}; }
            .context-menu {
                -fx-background-color: ${pack.popupBg};
                -fx-background-radius: ${pack.menuRound};
                -fx-border-color: ${pack.border};
                -fx-border-radius: ${pack.menuRound};
                -fx-border-width: 1;
                -fx-padding: 4 0 4 0;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 16, 0.18, 0, 4);
            }
            .context-menu .menu-item {
                -fx-background-color: transparent;
                -fx-padding: 5 14 5 10;
            }
            .context-menu .menu-item > .label { -fx-text-fill: ${pack.popupFg}; -fx-font-size: 13px; }
            .context-menu .menu-item > .accelerator-text {
                -fx-text-fill: ${ink.hintFg};
                -fx-font-size: 12px;
            }
            .context-menu .menu-item:focused,
            .context-menu .menu-item:showing {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-background-radius: ${itemRound};
            }
            .context-menu .menu-item:focused > .label,
            .context-menu .menu-item:showing > .label,
            .context-menu .menu-item:focused > .accelerator-text,
            .context-menu .menu-item:showing > .accelerator-text {
                -fx-text-fill: ${ink.itemHoverFg};
            }
            .context-menu .menu-item > .right-container > .arrow {
                -fx-background-color: ${pack.popupFg};
            }
            .context-menu .menu-item:focused > .right-container > .arrow,
            .context-menu .menu-item:showing > .right-container > .arrow {
                -fx-background-color: ${ink.itemHoverFg};
            }
            .context-menu .check-menu-item:checked > .left-container > .check,
            .context-menu .radio-menu-item:checked > .left-container > .radio {
                -fx-background-color: ${pack.popupFg};
            }
            .context-menu .check-menu-item:focused:checked > .left-container > .check,
            .context-menu .check-menu-item:showing:checked > .left-container > .check,
            .context-menu .radio-menu-item:focused:checked > .left-container > .radio,
            .context-menu .radio-menu-item:showing:checked > .left-container > .radio {
                -fx-background-color: ${ink.itemHoverFg};
            }
            .context-menu .menu-item:disabled,
            .context-menu .menu-item:disabled:focused,
            .context-menu .menu-item:disabled:showing {
                -fx-background-color: transparent;
                -fx-opacity: 0.45;
            }
            .context-menu .separator:horizontal .line {
                -fx-border-color: ${pack.border} transparent transparent transparent;
                -fx-border-insets: 1 8 0 8;
            }
            .tool-bar { -fx-background-color: transparent; -fx-padding: 0; }
            .office-inspector, .office-inspector .viewport { -fx-background-color: ${pack.menuBg}; }
            .text-area {
                -fx-background-color: ${pack.editorBg};
                -fx-text-fill: ${pack.editorFg};
                -fx-control-inner-background: ${pack.editorBg};
                -fx-highlight-fill: ${pack.accent};
                -fx-highlight-text-fill: ${ink.onAccent};
                -fx-prompt-text-fill: ${ink.hintFg};
            }
            .text-area .content, .text-area .viewport { -fx-background-color: ${pack.editorBg}; }
            .scroll-bar { -fx-background-color: transparent; }
            .scroll-bar .thumb { -fx-background-color: ${pack.border}; -fx-background-radius: 6; }
            .scroll-bar .thumb:hover { -fx-background-color: ${pack.accent}; }
            .scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-padding: 2; }
            .scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-background-color: ${ink.hintFg}; }
            .combo-box, .combo-box-base {
                -fx-background-color: ${pack.popupBg};
                -fx-border-color: ${pack.border};
                -fx-background-radius: 4;
                -fx-border-radius: 4;
            }
            .combo-box-base:focused, .combo-box:focused { -fx-border-color: ${pack.accent}; }
            .combo-box-base > .list-cell,
            .combo-box-base > .list-cell:filled,
            .combo-box-base > .list-cell:selected,
            .combo-box-base > .list-cell:filled:selected,
            .combo-box-base > .list-cell:hover,
            .combo-box-base > .list-cell:filled:hover,
            .combo-box > .list-cell,
            .combo-box > .list-cell:filled,
            .combo-box > .list-cell:selected,
            .combo-box > .list-cell:filled:selected,
            .combo-box > .list-cell:hover,
            .combo-box > .list-cell:filled:hover {
                -fx-background-color: transparent;
                -fx-text-fill: ${pack.popupFg};
                -fx-padding: 3 8 3 8;
            }
            .combo-box-base .arrow-button { -fx-background-color: transparent; }
            .combo-box-base .arrow { -fx-background-color: ${pack.popupFg}; }
            .combo-box-popup .list-view {
                -fx-background-color: ${pack.popupBg};
                -fx-control-inner-background: ${pack.popupBg};
            }
            .combo-box-popup .list-cell {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
            }
            .combo-box-popup .list-cell:filled:hover,
            .combo-box-popup .list-cell:filled:selected {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-text-fill: ${ink.itemHoverFg};
            }
            .text-field, .password-field {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-prompt-text-fill: ${ink.hintFg};
                -fx-highlight-fill: ${pack.accent};
                -fx-highlight-text-fill: ${ink.onAccent};
                -fx-border-color: ${pack.border};
            }
            .spinner { -fx-background-color: ${pack.popupBg}; -fx-border-color: ${pack.border}; }
            .spinner .text-field {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
            }
            .spinner .increment-arrow-button, .spinner .decrement-arrow-button {
                -fx-background-color: ${pack.popupBg};
            }
            .spinner .increment-arrow, .spinner .decrement-arrow { -fx-background-color: ${pack.popupFg}; }
            .list-view {
                -fx-background-color: ${pack.popupBg};
                -fx-control-inner-background: ${pack.popupBg};
            }
            .list-cell { -fx-text-fill: ${pack.popupFg}; -fx-background-color: ${pack.popupBg}; }
            .list-cell:filled:hover { -fx-background-color: ${ink.itemHoverBg}; -fx-text-fill: ${ink.itemHoverFg}; }
            .list-cell:filled:selected { -fx-background-color: ${ink.itemHoverBg}; -fx-text-fill: ${ink.itemHoverFg}; }
            .tab-pane .tab-header-background { -fx-background-color: ${pack.ribbonBg}; }
            .tab-pane .tab { -fx-background-color: ${pack.popupBg}; }
            .tab-pane .tab .tab-label { -fx-text-fill: ${pack.popupFg}; }
            .tab-pane .tab:selected { -fx-background-color: ${ink.itemHoverBg}; }
            .tab-pane .tab:selected .tab-label { -fx-text-fill: ${ink.itemHoverFg}; }
            .check-box, .radio-button { -fx-text-fill: ${pack.popupFg}; }
            .check-box .box, .radio-button .radio {
                -fx-background-color: ${pack.popupBg};
                -fx-border-color: ${pack.border};
            }
            .check-box:selected .mark, .radio-button:selected .dot { -fx-background-color: ${pack.accent}; }
            .slider .track { -fx-background-color: ${pack.border}; }
            .slider .thumb { -fx-background-color: ${pack.accent}; }
            .radio-button .text { -fx-fill: ${pack.popupFg}; }
            .toggle-button, .button {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-border-color: ${pack.border};
                -fx-background-radius: 7;
                -fx-border-radius: 7;
                -fx-padding: 5 10 5 10;
            }
            .toggle-button:hover, .button:hover {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-text-fill: ${ink.itemHoverFg};
            }
            .toggle-button:pressed, .button:pressed,
            .toggle-button:selected {
                -fx-background-color: ${pack.accent};
                -fx-text-fill: ${ink.onAccent};
            }
            .dialog-pane { -fx-background-color: ${pack.popupBg}; }
            .dialog-pane > .header-panel { -fx-background-color: ${pack.menuBg}; }
            .dialog-pane > .header-panel .label { -fx-text-fill: ${pack.menuFg}; }
            .dialog-pane .label, .dialog-pane .check-box, .dialog-pane .radio-button { -fx-text-fill: ${pack.popupFg}; }
            .dialog-pane > .content { -fx-background-color: ${pack.popupBg}; }
            .dialog-pane .button {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-border-color: ${pack.border};
            }
            .dialog-pane .button:hover {
                -fx-background-color: ${ink.itemHoverBg};
                -fx-text-fill: ${ink.itemHoverFg};
            }
            .dialog-pane .button-bar .button:default {
                -fx-background-color: ${pack.accent};
                -fx-text-fill: ${ink.onAccent};
            }
            .tooltip {
                -fx-background-color: ${pack.popupBg};
                -fx-text-fill: ${pack.popupFg};
                -fx-background-radius: 6;
                -fx-border-color: ${pack.border};
                -fx-border-radius: 6;
                -fx-font-size: 12px;
                -fx-padding: 6 8 6 8;
            }
            .office-icon { -fx-stroke: ${pack.popupFg}; -fx-fill: transparent; }
            .context-menu .menu-item:focused .office-icon,
            .context-menu .menu-item:showing .office-icon {
                -fx-stroke: ${ink.itemHoverFg};
            }
            .scroll-pane { -fx-background-color: transparent; }
            .office-ribbon-scroll > .viewport { -fx-background-color: transparent; }
            .editor-desk > .viewport { -fx-background-color: transparent; }
            .glass-office .office-ribbon-scroll .button { -fx-border-color: transparent; }
            .glass-office .office-ribbon-scroll .button:focused { -fx-border-color: ${pack.accent}; }
            .glass-office .separator .line { -fx-border-color: ${pack.border}; -fx-border-width: 0 0 0 1; }
        """.trimIndent()
        val file = File.createTempFile("g134-theme-", ".css")
        file.writeText(css)
        file.deleteOnExit()
        scene.stylesheets.setAll(file.toURI().toString())
    }
}
