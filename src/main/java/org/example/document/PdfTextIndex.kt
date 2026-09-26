package org.example.document

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

class PdfTextIndex {
    data class Run(
        val page: Int,
        val unicode: String,
        val x: Float,
        val yTop: Float,
        val w: Float,
        val h: Float
    )

    data class Hit(
        val page: Int,
        val runs: List<Run>,
        val text: String
    )

    val runs = mutableListOf<Run>()
    private val pages = mutableListOf<String>()

    fun rebuild(document: PDDocument) {
        runs.clear()
        pages.clear()
        if (document.numberOfPages == 0) return
        val collector = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val page = currentPageNo - 1
                for (tp in textPositions) {
                    val u = tp.unicode ?: continue
                    if (u.isEmpty()) continue
                    runs += Run(page, u, tp.xDirAdj, tp.yDirAdj, tp.widthDirAdj, tp.heightDir)
                }
            }
        }
        collector.sortByPosition = true
        for (i in 1..document.numberOfPages) {
            collector.startPage = i
            collector.endPage = i
            pages += collector.getText(document)
        }
    }

    fun text(): String = pages.joinToString("\n").trim()

    fun pageText(index: Int): String = pages.getOrNull(index).orEmpty()

    fun search(query: String, ignoreCase: Boolean): List<Hit> {
        if (query.isEmpty()) return emptyList()
        val hits = mutableListOf<Hit>()
        runs.groupBy { it.page }.forEach { (page, list) ->
            val ranges = ArrayList<IntRange>(list.size)
            val sb = StringBuilder()
            list.forEach { run ->
                val start = sb.length
                sb.append(run.unicode)
                ranges += start until sb.length
            }
            val hay = if (ignoreCase) sb.toString().lowercase() else sb.toString()
            val needle = if (ignoreCase) query.lowercase() else query
            var from = 0
            while (from < hay.length) {
                val at = hay.indexOf(needle, from)
                if (at < 0) break
                val end = at + needle.length
                val involved = list.filterIndexed { idx, _ ->
                    val r = ranges[idx]
                    r.first < end && r.last + 1 > at
                }
                if (involved.isNotEmpty()) {
                    hits += Hit(page, involved, sb.substring(at, end.coerceAtMost(sb.length)))
                }
                from = at + 1
            }
        }
        return hits
    }

    fun runsIn(page: Int, x0: Float, y0: Float, x1: Float, y1: Float): List<Run> {
        val left = minOf(x0, x1)
        val right = maxOf(x0, x1)
        val top = minOf(y0, y1)
        val bottom = maxOf(y0, y1)
        return runs.filter { r ->
            r.page == page &&
                r.x < right && r.x + r.w > left &&
                (r.yTop - r.h) < bottom && r.yTop > top
        }
    }
}
