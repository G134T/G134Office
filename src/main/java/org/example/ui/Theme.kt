package org.example.ui

import javafx.scene.Scene
import java.io.File
import kotlin.math.min

enum class AppTheme(val title: String, val compactRibbon: Boolean = false, val wordYear: String? = null) {
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
    OPEN_OFFICE("OpenOffice"),
    MYOFFICE("МойОфис");

    val family: ThemeFamily get() = ThemeFamily.of(this)
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
        OPEN_OFFICE -> listOf(AppTheme.OPEN_OFFICE)
        MYOFFICE -> listOf(AppTheme.MYOFFICE)
    }

    companion object {
        fun of(theme: AppTheme) = entries.first { theme in it.variants }
    }
}

enum class UiChrome {
    STANDARD, OPEN_OFFICE, MY_OFFICE;

    companion object {
        fun of(theme: AppTheme) = when (theme) {
            AppTheme.OPEN_OFFICE -> OPEN_OFFICE
            AppTheme.MYOFFICE -> MY_OFFICE
            else -> STANDARD
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

    fun ink(pack: Pack) = Ink(
        menuHoverBg = pack.menuHoverBg.ifBlank { pack.buttonHover },
        menuHoverFg = pack.menuHoverFg.ifBlank { pack.menuFg },
        itemHoverBg = pack.itemHoverBg.ifBlank { pack.accent },
        itemHoverFg = pack.itemHoverFg.ifBlank { pack.onAccent },
        hintFg = pack.hintFg.ifBlank { pack.labelFg },
        onAccent = pack.onAccent
    )

    fun pack(theme: AppTheme) = when (theme) {
        AppTheme.LIGHT -> skin(
            windowBg = "#e7eef6", ribbonBg = "#f4f7fb", menuBg = "#f8fafc", menuFg = "#1a2333",
            popupBg = "#ffffff", popupFg = "#1a2333", editorBg = "#ffffff", editorFg = "#182235",
            statusBg = "#eef3f8", statusFg = "#334155", border = "#c5d2e2",
            buttonHover = "#e3edfb", buttonFg = "#1a2333", labelFg = "#4b5d73", accent = "#2563eb",
            menuHoverBg = "#e3edfb", menuHoverFg = "#1a2333",
            itemHoverBg = "#dbe7f8", itemHoverFg = "#1a2333", hintFg = "#5c6b80"
        )
        AppTheme.GRAY -> skin(
            windowBg = "#c5ccd4", ribbonBg = "#e6eaef", menuBg = "#e6eaef", menuFg = "#1c2630",
            popupBg = "#f7f8fa", popupFg = "#1c2630", editorBg = "#ffffff", editorFg = "#1c2630",
            statusBg = "#d5dbe3", statusFg = "#1c2630", border = "#a8b3c0",
            buttonHover = "#d5dce6", buttonFg = "#1c2630", labelFg = "#2f3d4c", accent = "#3d5a73",
            menuHoverBg = "#d5dce6", menuHoverFg = "#1c2630",
            itemHoverBg = "#d3deea", itemHoverFg = "#1c2630", hintFg = "#4a5968"
        )
        AppTheme.DARK -> skin(
            windowBg = "#000000", ribbonBg = "#0c0e13", menuBg = "#0c0e13", menuFg = "#f5f7ff",
            popupBg = "#141820", popupFg = "#f5f7ff", editorBg = "#0c0e13", editorFg = "#f5f7ff",
            statusBg = "#0c0e13", statusFg = "#d5deee", border = "#2c3648",
            buttonHover = "#1c2838", buttonFg = "#f5f7ff", labelFg = "#c5d0e2", accent = "#2563eb",
            menuHoverBg = "#1c2838", menuHoverFg = "#ffffff",
            itemHoverBg = "#1d4ed8", itemHoverFg = "#ffffff", hintFg = "#a8b6cc"
        )
        AppTheme.GLASS -> skin(
            windowBg = "rgba(8,14,28,0.40)", ribbonBg = "rgba(14,22,40,0.90)",
            menuBg = "rgba(12,20,36,0.92)", menuFg = "#f7f9ff",
            popupBg = "rgba(16,24,42,0.96)", popupFg = "#f7f9ff",
            editorBg = "#101828", editorFg = "#f7f9ff",
            statusBg = "rgba(12,20,36,0.92)", statusFg = "#e7eeff",
            border = "rgba(186,208,255,0.55)",
            buttonHover = "rgba(47,111,222,0.72)", buttonFg = "#ffffff",
            labelFg = "#dce6ff", accent = "#2f6fde",
            menuHoverBg = "#2f6fde", menuHoverFg = "#ffffff",
            itemHoverBg = "#2f6fde", itemHoverFg = "#ffffff", hintFg = "#c9d7f5"
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
        AppTheme.OPEN_OFFICE -> skin(
            windowBg = "#b7bcc4", ribbonBg = "#e3e8f0", menuBg = "#e7eaf0", menuFg = "#1b2838",
            popupBg = "#f7f9fb", popupFg = "#1b2838", editorBg = "#ffffff", editorFg = "#202020",
            statusBg = "#e6e9ee", statusFg = "#1b2838", border = "#a8b2c0",
            buttonHover = "#d5e4f4", buttonFg = "#1b2838", labelFg = "#3d4e62", accent = "#2f6ea3",
            menuHoverBg = "#d5e4f4", menuHoverFg = "#1b2838",
            itemHoverBg = "#cfe3f6", itemHoverFg = "#1b2838", hintFg = "#4d5d70",
            menuRound = 2
        )
        AppTheme.MYOFFICE -> skin(
            windowBg = "#d5e2ee", ribbonBg = "#ffffff", menuBg = "#0b5cab", menuFg = "#ffffff",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#0b5cab", statusFg = "#ffffff", border = "#9bb8d4",
            buttonHover = "#d7e8f8", buttonFg = "#0b5cab", labelFg = "#3d5470", accent = "#0b5cab",
            menuHoverBg = "#084f92", menuHoverFg = "#ffffff",
            itemHoverBg = "#0b5cab", itemHoverFg = "#ffffff", hintFg = "#5a6e82"
        )
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
            .scroll-pane { -fx-background-color: transparent; }
        """.trimIndent()
        val file = File.createTempFile("g134-theme-", ".css")
        file.writeText(css)
        file.deleteOnExit()
        scene.stylesheets.setAll(file.toURI().toString())
    }
}
