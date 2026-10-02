package org.example.ui

/**
 * Палитры панелей OpenOffice по релизам, а не перенос всего LibreOffice.
 * 2002 — серые панели OpenOffice.org 1 / StarOffice (системный COL_3DFACE).
 * 2005 — сине-серые панели OpenOffice.org 2.
 * 2008 — плоские панели и синий Galaxy из OpenOffice.org 3.
 * 2013 и новее — светлый вид Apache OpenOffice 4 с фирменным синим
 * (wiki Brand Colors, RGB 14, 133, 205), затемнённым так, чтобы белый текст на нём читался.
 */
internal object OpenOfficeTheme {
    fun pack(theme: AppTheme): Theme.Pack = when (theme) {
        AppTheme.OO_2002 -> face(
            windowBg = "#808080", ribbonBg = "#d4d0c8", menuBg = "#d4d0c8", menuFg = "#000000",
            popupBg = "#ffffff", popupFg = "#000000", editorBg = "#ffffff", editorFg = "#000000",
            statusBg = "#d4d0c8", statusFg = "#000000", border = "#808080",
            buttonHover = "#c8c4bc", buttonFg = "#000000", labelFg = "#000000", accent = "#0a246a",
            menuHoverBg = "#0a246a", menuHoverFg = "#ffffff",
            itemHoverBg = "#0a246a", itemHoverFg = "#ffffff", hintFg = "#555555"
        )
        AppTheme.OPEN_OFFICE -> face(
            windowBg = "#b7bcc4", ribbonBg = "#e3e8f0", menuBg = "#e7eaf0", menuFg = "#1b2838",
            popupBg = "#f7f9fb", popupFg = "#1b2838", editorBg = "#ffffff", editorFg = "#202020",
            statusBg = "#e6e9ee", statusFg = "#1b2838", border = "#a8b2c0",
            buttonHover = "#d5e4f4", buttonFg = "#1b2838", labelFg = "#3d4e62", accent = "#2f6ea3",
            menuHoverBg = "#d5e4f4", menuHoverFg = "#1b2838",
            itemHoverBg = "#cfe3f6", itemHoverFg = "#1b2838", hintFg = "#4d5d70"
        )
        AppTheme.OO_2008 -> face(
            windowBg = "#a8b4c0", ribbonBg = "#f3f5f8", menuBg = "#f7f8fa", menuFg = "#1b2836",
            popupBg = "#ffffff", popupFg = "#1b2836", editorBg = "#ffffff", editorFg = "#1b2836",
            statusBg = "#eef1f4", statusFg = "#1b2836", border = "#c5cdd6",
            buttonHover = "#d5e4f4", buttonFg = "#1b2836", labelFg = "#3e5164", accent = "#0e4f86",
            menuHoverBg = "#d5e4f4", menuHoverFg = "#1b2836",
            itemHoverBg = "#d5e4f4", itemHoverFg = "#1b2836", hintFg = "#4e5e70"
        )
        AppTheme.OO_2013 -> face(
            windowBg = "#d9dde2", ribbonBg = "#f4f4f4", menuBg = "#ffffff", menuFg = "#1a1a1a",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#eeeeee", statusFg = "#1a1a1a", border = "#c8c8c8",
            buttonHover = "#e3f1fa", buttonFg = "#1a1a1a", labelFg = "#3f3f3f", accent = "#0a5580",
            menuHoverBg = "#e3f1fa", menuHoverFg = "#1a1a1a",
            itemHoverBg = "#d6eaf8", itemHoverFg = "#1a1a1a", hintFg = "#5c5c5c"
        )
        AppTheme.OO_2014 -> face(
            windowBg = "#e6e6e6", ribbonBg = "#fbfbfb", menuBg = "#ffffff", menuFg = "#1a1a1a",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#f3f3f3", statusFg = "#1a1a1a", border = "#d0d0d0",
            buttonHover = "#e7f3fb", buttonFg = "#1a1a1a", labelFg = "#3f3f3f", accent = "#0a5580",
            menuHoverBg = "#e7f3fb", menuHoverFg = "#1a1a1a",
            itemHoverBg = "#d6eaf8", itemHoverFg = "#1a1a1a", hintFg = "#5c5c5c"
        )
        AppTheme.OO_2023 -> face(
            windowBg = "#e8eef2", ribbonBg = "#ffffff", menuBg = "#ffffff", menuFg = "#1a1a1a",
            popupBg = "#ffffff", popupFg = "#1a1a1a", editorBg = "#ffffff", editorFg = "#1a1a1a",
            statusBg = "#f7f7f7", statusFg = "#1a1a1a", border = "#d5dde3",
            buttonHover = "#e7f3fb", buttonFg = "#1a1a1a", labelFg = "#3a3a3a", accent = "#0c6a9a",
            menuHoverBg = "#e7f3fb", menuHoverFg = "#1a1a1a",
            itemHoverBg = "#d7ebf8", itemHoverFg = "#1a1a1a", hintFg = "#5c5c5c"
        )
        else -> pack(AppTheme.OPEN_OFFICE)
    }

    private fun face(
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
        hintFg: String
    ) = Theme.Pack(
        windowBg, ribbonBg, menuBg, menuFg, popupBg, popupFg, editorBg, editorFg,
        statusBg, statusFg, border, buttonHover, buttonFg, labelFg, accent,
        menuHoverBg, menuHoverFg, itemHoverBg, itemHoverFg, hintFg, "#ffffff", 2
    )
}
