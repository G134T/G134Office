package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.web.WebView
import javafx.util.StringConverter
import org.example.engine.Document
import org.example.fanfic.FanficFormatter
import org.example.fanfic.FanficMeta
import org.example.fanfic.FanficPreviewSerializer
import org.example.fanfic.FanficStatus

/** Страница чтения как на ficbook.net: шапка, настройки бумаги, текст главы. */
class FanficPreviewPane(private val source: () -> Document) : BorderPane() {
    private val browser = WebView()
    private var zoom = 1.0
    private var paragraph = 0
    private var meta = FanficMeta()
    private var skin = FicbookSkin.DAY
    private var paper = ReaderPaper.WHITE
    private var indentScale = 100
    private var fontScale = 100
    var onPaper: (ReaderPaper) -> Unit = {}

    private val paperBox = ComboBox<ReaderPaper>().apply {
        items.addAll(ReaderPaper.entries)
        value = ReaderPaper.WHITE
        prefWidth = 120.0
        converter = object : StringConverter<ReaderPaper>() {
            override fun toString(t: ReaderPaper?) = t?.title ?: ""
            override fun fromString(s: String?) =
                ReaderPaper.entries.firstOrNull { it.title == s } ?: ReaderPaper.WHITE
        }
        tooltip = javafx.scene.control.Tooltip("Цвет фона текста")
        setOnAction {
            paper = value ?: ReaderPaper.WHITE
            onPaper(paper)
            refresh()
        }
    }
    private val indentBox = ComboBox<Int>().apply {
        items.addAll(100, 90, 80, 70, 60, 50)
        value = 100
        prefWidth = 72.0
        setOnAction {
            indentScale = value ?: 100
            refresh()
        }
    }
    private val fontBox = ComboBox<Int>().apply {
        items.addAll(70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170)
        value = 100
        prefWidth = 72.0
        setOnAction {
            fontScale = value ?: 100
            refresh()
        }
    }
    private val settings = HBox(10.0)

    init {
        style = "-fx-background-color: #e9ebee;"
        browser.isContextMenuEnabled = false
        settings.alignment = Pos.CENTER_LEFT
        settings.padding = Insets(8.0, 12.0, 8.0, 12.0)
        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        settings.children.addAll(
            Label("Настройки"),
            Label("Фон"), paperBox,
            Label("Отступы"), indentBox,
            Label("Шрифт"), fontBox,
            spacer
        )
        top = settings
        center = browser
    }

    fun setZoom(value: Double) {
        zoom = value
        refresh()
    }

    fun setMeta(next: FanficMeta) {
        meta = next
    }

    fun setSkin(next: FicbookSkin) {
        skin = next
        if (paperBox.value == null) paperBox.value = paper
        refreshChrome()
    }

    fun setPaper(next: ReaderPaper) {
        paper = next
        if (paperBox.value != next) paperBox.value = next
    }

    fun scrollToParagraph(index: Int) {
        paragraph = index.coerceAtLeast(0)
    }

    fun refresh() {
        val document = source()
        val body = FanficPreviewSerializer.toHtml(document)
        val words = document.toPlainText().split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = document.toPlainText().length
        val pages = FanficFormatter.sizePages(chars)
        browser.engine.loadContent(page(body, words, chars, pages), "text/html")
        refreshChrome()
    }

    private fun refreshChrome() {
        val pack = FicbookTheme.pack(skin)
        style = "-fx-background-color: ${pack.pageBg};"
        settings.style = "-fx-background-color: ${pack.cardBg}; -fx-border-color: ${pack.border}; -fx-border-width: 0 0 1 0;"
        settings.children.filterIsInstance<Label>().forEach {
            it.style = "-fx-text-fill: ${pack.muted}; -fx-font-size: 12px;"
        }
    }

    private fun page(body: String, words: Int, chars: Int, pages: Int): String {
        val pack = FicbookTheme.pack(skin)
        val tags = meta.tagList().joinToString("") { tag ->
            """<a class="tag">${escape(tag)}</a>"""
        }.ifBlank { """<span class="muted">нет меток</span>""" }
        val indent = (2.0 * indentScale / 100.0)
        val font = (17.0 * fontScale / 100.0 * zoom)
        val ratingColor = meta.rating.color
        val statusClass = when (meta.status) {
            FanficStatus.COMPLETE -> "ok"
            FanficStatus.DRAFT -> "draft"
            FanficStatus.FROZEN -> "frozen"
            FanficStatus.IN_PROGRESS -> "wip"
        }
        return """
            <!doctype html><html lang="ru"><head><meta charset="UTF-8">
            <style>
              html,body { margin:0; padding:0; background:${pack.pageBg}; color:${pack.text}; }
              body { font-family:"Segoe UI",Arial,Helvetica,sans-serif; font-size:${font}px; }
              .wrap { box-sizing:border-box; width:min(760px, 100%); margin:0 auto; padding:16px 12px 40px; }
              .hat, .chapter-card {
                background:${pack.cardBg}; border:1px solid ${pack.border};
                border-radius:4px; padding:20px 24px 16px; margin-bottom:14px;
              }
              h1 { font-size:1.6em; font-weight:700; margin:0 0 10px; color:${pack.text}; }
              .row { margin:6px 0; font-size:0.92em; line-height:1.45; }
              .k { color:${pack.muted}; display:inline-block; min-width:11.5em; }
              .badges { display:flex; gap:8px; flex-wrap:wrap; margin:8px 0 12px; }
              .badge { display:inline-block; padding:2px 8px; border-radius:3px; font-size:12px; color:#fff; }
              .dir { background:#6c757d; }
              .rating { background:$ratingColor; }
              .status.ok { background:#5cb85c; }
              .status.wip { background:#5bc0de; }
              .status.draft { background:#777; }
              .status.frozen { background:#9b7e4e; }
              .tag {
                display:inline-block; margin:0 6px 6px 0; padding:2px 8px;
                border:1px solid ${pack.brand}; color:${pack.link}; border-radius:10px;
                font-size:12px; text-decoration:none;
              }
              .muted { color:${pack.muted}; }
              .desc, .notes { white-space:pre-wrap; margin:4px 0 0; }
              .chapter-title { font-size:1.25em; font-weight:600; margin:0 0 16px; text-align:center; }
              .chapter-text { background:${paper.bg}; color:${paper.fg}; padding:8px 4px 24px; }
              .chapter-text p { line-height:1.62; text-indent:${indent}em; margin:0 0 0.85em; overflow-wrap:anywhere; }
              .chapter-text p[align="center"] { text-align:center; text-indent:0; }
              .chapter-text p:empty { text-align:center; text-indent:0; margin:1.5em 0 1.8em; }
              .chapter-text p:empty:after { content:"✦ ✦ ✦"; color:#888; letter-spacing:.18em; }
              .author { color:${pack.link}; }
            </style></head>
            <body>
              <div class="wrap">
                <header class="hat">
                  <h1>${escape(meta.displayTitle())}</h1>
                  <div class="badges">
                    <span class="badge dir">${escape(meta.direction.title)}</span>
                    <span class="badge rating">${escape(meta.rating.title)}</span>
                    <span class="badge status $statusClass">${escape(meta.status.title)}</span>
                  </div>
                  <div class="row"><span class="k">Автор:</span> <span class="author">${escape(meta.author.ifBlank { "автор" })}</span></div>
                  <div class="row"><span class="k">Фэндом:</span> ${escape(meta.fandom.ifBlank { "—" })}</div>
                  <div class="row"><span class="k">Пэйринг и персонажи:</span> ${escape(meta.pairing.ifBlank { "—" })}</div>
                  <div class="row"><span class="k">Размер:</span> $pages ${pageWord(pages)}, ${formatNum(words)} ${wordWord(words)}, 1 часть</div>
                  <div class="row"><span class="k">Метки:</span> $tags</div>
                  <div class="row"><span class="k">Описание:</span><div class="desc">${escape(meta.description.ifBlank { "—" })}</div></div>
                  <div class="row"><span class="k">Примечания:</span><div class="notes">${escape(meta.notes.ifBlank { "—" })}</div></div>
                </header>
                <article class="chapter-card">
                  <h2 class="chapter-title">${escape(meta.displayChapter())}</h2>
                  <div class="chapter-text">$body</div>
                </article>
              </div>
              <script>
                (function() {
                  var items = document.querySelectorAll('.chapter-text p');
                  var i = $paragraph;
                  if (i >= 0 && i < items.length) items[i].scrollIntoView(true);
                })();
              </script>
            </body></html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun formatNum(n: Int): String = "%,d".format(n).replace(',', ' ')

    private fun pageWord(n: Int) = when {
        n % 10 == 1 && n % 100 != 11 -> "страница"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "страницы"
        else -> "страниц"
    }

    private fun wordWord(n: Int) = when {
        n % 10 == 1 && n % 100 != 11 -> "слово"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "слова"
        else -> "слов"
    }
}
