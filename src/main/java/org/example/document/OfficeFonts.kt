package org.example.document

import java.io.File

internal object OfficeFonts {
    fun cyrillicTtf(): File =
        sequenceOf(
            "arial.ttf", "Arial.ttf", "arialuni.ttf",
            "segoeui.ttf", "calibri.ttf", "tahoma.ttf",
            "times.ttf", "timesnr.ttf", "cour.ttf"
        ).map { File("C:/Windows/Fonts", it) }
            .firstOrNull { it.exists() }
            ?: throw IllegalStateException("Нет шрифта с кириллицей в C:\\Windows\\Fonts")
}
