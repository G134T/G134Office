package org.example.ui

import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun `stock themes keep text readable on menus ribbon and popups`() {
        val failures = mutableListOf<String>()
        AppTheme.entries.forEach { theme ->
            val pack = Theme.pack(theme)
            val ink = Theme.ink(pack)
            val pairs = listOf(
                "menu" to (pack.menuFg to pack.menuBg),
                "menu hover" to (ink.menuHoverFg to ink.menuHoverBg),
                "popup" to (pack.popupFg to pack.popupBg),
                "submenu hover" to (ink.itemHoverFg to ink.itemHoverBg),
                "ribbon icon" to (pack.buttonFg to pack.ribbonBg),
                "ribbon icon hover" to (pack.buttonFg to pack.buttonHover),
                "ribbon label" to (pack.labelFg to pack.ribbonBg),
                "status" to (pack.statusFg to pack.statusBg),
                "shortcut" to (ink.hintFg to pack.popupBg),
                "accent" to (ink.onAccent to pack.accent)
            )
            pairs.forEach { (name, colors) ->
                val ratio = contrast(colors.first, colors.second)
                if (ratio < 4.5) {
                    failures += "$theme $name contrast ${"%.2f".format(java.util.Locale.US, ratio)} (${colors.first} on ${colors.second})"
                }
            }
        }
        FicbookSkin.entries.forEach { skin ->
            val pack = FicbookTheme.pack(skin).office
            val ink = Theme.ink(pack)
            val pairs = listOf(
                "menu" to (pack.menuFg to pack.menuBg),
                "menu hover" to (ink.menuHoverFg to ink.menuHoverBg),
                "popup" to (pack.popupFg to pack.popupBg),
                "submenu hover" to (ink.itemHoverFg to ink.itemHoverBg),
                "ribbon icon" to (pack.buttonFg to pack.ribbonBg),
                "ribbon icon hover" to (pack.buttonFg to pack.buttonHover),
                "shortcut" to (ink.hintFg to pack.popupBg),
                "accent" to (ink.onAccent to pack.accent)
            )
            pairs.forEach { (name, colors) ->
                val ratio = contrast(colors.first, colors.second)
                if (ratio < 4.5) {
                    failures += "ficbook $skin $name contrast ${"%.2f".format(java.util.Locale.US, ratio)} (${colors.first} on ${colors.second})"
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}

private fun contrast(fg: String, bg: String): Double {
    val lighter = maxOf(luminance(fg), luminance(bg))
    val darker = minOf(luminance(fg), luminance(bg))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun luminance(css: String): Double {
    val (r, g, b) = rgb(css)
    fun channel(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

private fun rgb(css: String): Triple<Int, Int, Int> {
    val hex = Regex("""#([0-9a-fA-F]{6})""").find(css)
    if (hex != null) {
        val raw = hex.groupValues[1].toInt(16)
        return Triple((raw shr 16) and 255, (raw shr 8) and 255, raw and 255)
    }
    val rgba = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""").find(css)
        ?: error("unsupported color $css")
    return Triple(rgba.groupValues[1].toInt(), rgba.groupValues[2].toInt(), rgba.groupValues[3].toInt())
}
