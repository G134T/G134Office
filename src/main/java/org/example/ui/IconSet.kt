package org.example.ui

/** Набор значков ленты и меню. В настройках по умолчанию — стандартные символы. */
internal enum class IconSet {
    STANDARD,
    CUSTOM;

    companion object {
        fun fromPref(raw: String?): IconSet =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: STANDARD
    }
}
