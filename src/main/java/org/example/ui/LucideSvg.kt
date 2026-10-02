package org.example.ui

/**
 * Контурные значки Lucide (SVG). Линии перекрашиваются цветом темы,
 * поэтому один набор подходит и светлым, и тёмным оформлениям.
 */
internal object OfficeIconCatalog {
    const val NEW = "file-plus"
    const val OPEN = "folder-open"
    const val SAVE = "save"
    const val COMMIT = "git-commit-horizontal"
    const val CUT = "scissors"
    const val COPY = "copy"
    const val PASTE = "clipboard-paste"
    const val BOLD = "bold"
    const val ITALIC = "italic"
    const val UNDERLINE = "underline"
    const val GROW = "a-arrow-up"
    const val SHRINK = "a-arrow-down"
    const val ALIGN_LEFT = "align-left"
    const val ALIGN_CENTER = "align-center"
    const val ALIGN_RIGHT = "align-right"
    const val ALIGN_JUSTIFY = "align-justify"
    const val INDENT_MORE = "indent-increase"
    const val INDENT_LESS = "indent-decrease"
    const val FIND = "search"
    const val REPLACE = "replace"
    const val SPELL = "spell-check"
    const val RULER = "ruler"
    const val GRID = "grid-3x3"
    const val FANFIC = "book-open"
    const val UNDO = "undo-2"
    const val REDO = "redo-2"
    const val PRINT = "printer"
    const val SETTINGS = "settings"
    const val ZOOM_IN = "zoom-in"
    const val ZOOM_OUT = "zoom-out"
    const val CALENDAR = "calendar"
    const val SYMBOL = "hash"
    const val WORD_COUNT = "baseline"
    const val PAGE_BREAK = "separator-horizontal"
    const val LANDSCAPE = "rectangle-horizontal"
    const val EXIT = "log-out"

    val files = listOf(
        NEW, OPEN, SAVE, COMMIT, CUT, COPY, PASTE, BOLD, ITALIC, UNDERLINE,
        GROW, SHRINK, ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT, ALIGN_JUSTIFY,
        INDENT_MORE, INDENT_LESS, FIND, REPLACE, SPELL, RULER, GRID, FANFIC,
        UNDO, REDO, PRINT, SETTINGS, ZOOM_IN, ZOOM_OUT, CALENDAR, SYMBOL,
        WORD_COUNT, PAGE_BREAK, LANDSCAPE, EXIT
    )
}

internal fun lucideSvg(name: String): String {
    val stream = OfficeIconCatalog::class.java.getResourceAsStream("/icons/lucide/$name.svg")
        ?: error("Нет значка $name")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

internal sealed class LucideShape {
    data class Path(val data: String) : LucideShape()
    data class Oval(val cx: Double, val cy: Double, val r: Double) : LucideShape()
    data class Segment(val x1: Double, val y1: Double, val x2: Double, val y2: Double) : LucideShape()
    data class Box(val x: Double, val y: Double, val w: Double, val h: Double, val rx: Double, val ry: Double) : LucideShape()
}

/** Разбирает path, circle, rect и line из SVG Lucide. */
internal fun lucideShapes(svg: String): List<LucideShape> {
    val tags = Regex("""<(path|circle|rect|line)\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    return tags.findAll(svg).mapNotNull { match ->
        val attrs = svgAttributes(match.groupValues[2])
        when (match.groupValues[1].lowercase()) {
            "path" -> attrs["d"]?.takeIf { it.isNotBlank() }?.let { LucideShape.Path(it) }
            "circle" -> LucideShape.Oval(attr(attrs, "cx"), attr(attrs, "cy"), attr(attrs, "r"))
            "line" -> LucideShape.Segment(attr(attrs, "x1"), attr(attrs, "y1"), attr(attrs, "x2"), attr(attrs, "y2"))
            "rect" -> rectShape(attrs)
            else -> null
        }
    }.toList()
}

private fun svgAttributes(raw: String): Map<String, String> {
    val map = linkedMapOf<String, String>()
    Regex("""([\w:-]+)="([^"]*)"""").findAll(raw).forEach { map[it.groupValues[1]] = it.groupValues[2] }
    return map
}

private fun attr(attrs: Map<String, String>, key: String): Double =
    attrs[key]?.trim()?.toDoubleOrNull() ?: 0.0

private fun rectShape(attrs: Map<String, String>): LucideShape.Box {
    val w = attr(attrs, "width")
    val h = attr(attrs, "height")
    val rxRaw = attr(attrs, "rx")
    val ryRaw = attr(attrs, "ry")
    val rx = minOf(if (rxRaw > 0.0) rxRaw else ryRaw, w / 2.0)
    val ry = minOf(if (ryRaw > 0.0) ryRaw else rx, h / 2.0)
    return LucideShape.Box(attr(attrs, "x"), attr(attrs, "y"), w, h, rx, ry)
}
