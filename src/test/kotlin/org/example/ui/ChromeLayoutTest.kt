package org.example.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ChromeLayoutTest {
    @Test
    fun `four palettes follow word openoffice and myoffice layouts`() {
        val palettes = listOf(AppTheme.LIGHT, AppTheme.GRAY, AppTheme.DARK, AppTheme.GLASS)
        palettes.forEach { theme ->
            assertEquals(UiChrome.WORD, UiChrome.resolve(theme, UiChrome.WORD))
            assertEquals(UiChrome.OPEN_OFFICE, UiChrome.resolve(theme, UiChrome.OPEN_OFFICE))
            assertEquals(UiChrome.MY_OFFICE, UiChrome.resolve(theme, UiChrome.MY_OFFICE))
        }
    }

    @Test
    fun `brand themes keep their own layout`() {
        assertEquals(UiChrome.STANDARD, UiChrome.resolve(AppTheme.WORD_2003, UiChrome.OPEN_OFFICE))
        assertEquals(UiChrome.STANDARD, UiChrome.resolve(AppTheme.WORD_2026, UiChrome.MY_OFFICE))
        assertEquals(UiChrome.OPEN_OFFICE, UiChrome.resolve(AppTheme.OPEN_OFFICE, UiChrome.WORD))
        assertEquals(UiChrome.OPEN_OFFICE, UiChrome.resolve(AppTheme.OO_2002, UiChrome.WORD))
        assertEquals(UiChrome.OPEN_OFFICE, UiChrome.resolve(AppTheme.OO_2023, UiChrome.MY_OFFICE))
        assertEquals(6, ThemeFamily.OPEN_OFFICE.variants.size)
        assertEquals(UiChrome.MY_OFFICE, UiChrome.resolve(AppTheme.MYOFFICE, UiChrome.WORD))
        assertEquals(UiChrome.STANDARD, UiChrome.resolve(AppTheme.NOTEPAD_WIN11, UiChrome.MY_OFFICE))
    }
}
