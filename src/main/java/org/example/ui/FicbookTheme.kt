package org.example.ui

enum class FicbookSkin(val title: String) {
    DAY("Дневная"),
    NIGHT("Ночная")
}

enum class ReaderPaper(val title: String, val bg: String, val fg: String) {
    WHITE("Белый", "#ffffff", "#252525"),
    CREAM("Кремовый", "#f4ecd8", "#3b2f23"),
    GRAY("Серый", "#d0d0d0", "#222222"),
    GREEN("Зелёный", "#e4efe2", "#243024"),
    DARK("Тёмный", "#1c1c1c", "#d6d6d6")
}

data class FicbookPack(
    val navbarBg: String,
    val navbarFg: String,
    val brand: String,
    val pageBg: String,
    val cardBg: String,
    val text: String,
    val muted: String,
    val border: String,
    val link: String,
    val success: String,
    val successHover: String,
    val inputBg: String,
    val badgeBg: String,
    val office: Theme.Pack
)

object FicbookTheme {
    fun pack(skin: FicbookSkin): FicbookPack = when (skin) {
        FicbookSkin.DAY -> FicbookPack(
            navbarBg = "#ffffff",
            navbarFg = "#333333",
            brand = "#4cae4c",
            pageBg = "#e9ebee",
            cardBg = "#ffffff",
            text = "#252525",
            muted = "#777777",
            border = "#d3d6db",
            link = "#3c763d",
            success = "#5cb85c",
            successHover = "#4cae4c",
            inputBg = "#ffffff",
            badgeBg = "#f6f7f8",
            office = Theme.Pack(
                windowBg = "#e9ebee",
                ribbonBg = "#ffffff",
                menuBg = "#ffffff",
                menuFg = "#333333",
                popupBg = "#ffffff",
                popupFg = "#252525",
                editorBg = "#ffffff",
                editorFg = "#252525",
                statusBg = "#f6f7f8",
                statusFg = "#555555",
                border = "#d3d6db",
                buttonHover = "#e7f5e7",
                buttonFg = "#1a1a1a",
                labelFg = "#3d4a3d",
                accent = "#2f7d32",
                menuHoverBg = "#e7f5e7",
                menuHoverFg = "#1a1a1a",
                itemHoverBg = "#d9efd9",
                itemHoverFg = "#1a1a1a",
                hintFg = "#4d5c4d"
            )
        )
        FicbookSkin.NIGHT -> FicbookPack(
            navbarBg = "#242424",
            navbarFg = "#e8e8e8",
            brand = "#6dbd4c",
            pageBg = "#2d2d2d",
            cardBg = "#3a3a3a",
            text = "#e6e6e6",
            muted = "#a3a3a3",
            border = "#4a4a4a",
            link = "#8bc34a",
            success = "#5cb85c",
            successHover = "#4cae4c",
            inputBg = "#323232",
            badgeBg = "#333333",
            office = Theme.Pack(
                windowBg = "#2d2d2d",
                ribbonBg = "#242424",
                menuBg = "#242424",
                menuFg = "#e8e8e8",
                popupBg = "#3a3a3a",
                popupFg = "#e6e6e6",
                editorBg = "#3a3a3a",
                editorFg = "#e6e6e6",
                statusBg = "#242424",
                statusFg = "#cfcfcf",
                border = "#4a4a4a",
                buttonHover = "#3f4a3f",
                buttonFg = "#f2f2f2",
                labelFg = "#c8c8c8",
                accent = "#2f7d32",
                menuHoverBg = "#3f4a3f",
                menuHoverFg = "#f2f2f2",
                itemHoverBg = "#2f6a34",
                itemHoverFg = "#ffffff",
                hintFg = "#c8c8c8"
            )
        )
    }
}
