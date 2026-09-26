package org.example.document

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.print.PrinterJob
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.ScrollPane
import javafx.scene.control.Separator
import javafx.scene.control.SplitPane
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextInputDialog
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.FileChooser
import javafx.stage.Window
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.rendering.ImageType
import org.example.ui.Theme
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javafx.scene.control.Dialog
import javafx.scene.layout.GridPane

class PdfWorkspace : BorderPane() {
    enum class Tool(val title: String) {
        SELECT("Выделение"),
        HIGHLIGHT("Маркер"),
        UNDERLINE("Подчёркивание"),
        STRIKE("Зачёркивание"),
        NOTE("Заметка"),
        RECT("Рамка")
    }

    enum class ZoomMode { FIT_WIDTH, FIT_PAGE, MANUAL }

    val session = PdfSession()
    var onStateChanged: () -> Unit = {}

    private val textIndex = PdfTextIndex()
    private val pagesBox = VBox(18.0).apply {
        alignment = Pos.TOP_CENTER
        padding = Insets(18.0, 24.0, 40.0, 24.0)
    }
    private val scroll = ScrollPane(pagesBox).apply {
        isFitToWidth = true
        pannableProperty().value = true
        style = "-fx-background: #4a4e52; -fx-background-color: #4a4e52;"
    }
    private val tiles = mutableListOf<PdfPageTile>()
    private val thumbs = ListView<Int>()
    private val bookmarks = ListView<BookmarkRow>()
    private val infoLabel = Label("PDF не открыт")
    private val textPane = TextArea().apply { isWrapText = true; isEditable = false }
    private val searchField = TextField().apply { promptText = "Найти в PDF"; prefWidth = 180.0 }
    private val searchStatus = Label("")
    private val pageField = TextField("1").apply { prefWidth = 48.0; maxWidth = 56.0 }
    private val pageCountLabel = Label("/ 0")
    private val zoomLabel = Label("100%")
    private val toolGroup = ToggleGroup()
    private var tool = Tool.SELECT
    private var zoomMode = ZoomMode.FIT_WIDTH
    private var dpi = 110f
    private var currentPage = 0
    private var searchHits = listOf<PdfTextIndex.Hit>()
    private var searchAt = -1
    private var dragStart: Pair<Double, Double>? = null
    private var dragTile: PdfPageTile? = null
    private var dragRect: Rectangle? = null
    private var selectedRuns = listOf<PdfTextIndex.Run>()
    private val renderPool = Executors.newSingleThreadExecutor { r ->
        Thread(r, "g134-pdf-render").apply { isDaemon = true }
    }
    private val renderEpoch = AtomicLong(0)
    private var sidebar: TabPane? = null

    val isOpen: Boolean get() = session.isOpen
    val dirty: Boolean get() = session.dirty
    val currentPageIndex: Int get() = currentPage
    val pageCount: Int get() = session.pageCount

    init {
        top = buildToolbar()
        val side = buildSidebar()
        sidebar = side
        center = SplitPane(side, scroll).apply { setDividerPositions(0.22) }
        isFocusTraversable = true
        scroll.vvalueProperty().addListener { _, _, _ -> onScroll() }
        scroll.viewportBoundsProperty().addListener { _, _, _ ->
            if (zoomMode != ZoomMode.MANUAL) applyFit()
            requestRender()
        }
        addEventFilter(KeyEvent.KEY_PRESSED) { e -> onKey(e) }
        scroll.addEventFilter(ScrollEvent.SCROLL) { e ->
            if (e.isControlDown) {
                e.consume()
                zoomBy(if (e.deltaY > 0) 16f else -16f)
            }
        }
    }

    fun open(target: File, password: String? = null) {
        session.open(target, password)
        currentPage = 0
        searchHits = emptyList()
        searchAt = -1
        selectedRuns = emptyList()
        rebuildView()
        notifyState()
    }

    fun close() {
        renderEpoch.incrementAndGet()
        session.close()
        tiles.clear()
        pagesBox.children.clear()
        thumbs.items.clear()
        bookmarks.items.clear()
        textPane.clear()
        infoLabel.text = "PDF не открыт"
        pageField.text = "1"
        pageCountLabel.text = "/ 0"
        notifyState()
    }

    fun dispose() {
        close()
        renderPool.shutdownNow()
    }

    fun save(target: File) {
        session.save(target)
        notifyState()
    }

    fun plainText(): String = textIndex.text()

    fun focusSearch() {
        searchField.requestFocus()
        searchField.selectAll()
    }

    fun zoomTitle(): String = "${(dpi / 72f * 100).toInt()}%"

    fun statusText(): String {
        if (!isOpen) return "PDF"
        return "Стр ${currentPage + 1} / ${session.pageCount}   ${zoomTitle()}   ${tool.title}"
    }

    fun applyTheme(pack: Theme.Pack) {
        style = "-fx-background-color: ${pack.windowBg};"
        val canvas = if (pack.editorBg.startsWith("#0") || pack.editorBg.startsWith("#1") || pack.editorBg == "#000000") {
            "#2b2d31"
        } else {
            "#5b5f63"
        }
        scroll.style = "-fx-background: $canvas; -fx-background-color: $canvas;"
        pagesBox.style = "-fx-background-color: $canvas;"
        textPane.style = "-fx-control-inner-background: ${pack.editorBg}; -fx-text-fill: ${pack.editorFg};"
        infoLabel.style = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 12px;"
        searchStatus.style = "-fx-text-fill: ${pack.labelFg};"
        pageCountLabel.style = "-fx-text-fill: ${pack.labelFg};"
        zoomLabel.style = "-fx-text-fill: ${pack.labelFg};"
    }

    fun print(owner: Window) {
        if (!isOpen) return
        val job = PrinterJob.createPrinterJob() ?: return
        if (!job.showPrintDialog(owner)) return
        val layout = job.jobSettings.pageLayout
        val n = session.pageCount
        for (i in 0 until n) {
            val img: BufferedImage = session.withDocument { _, renderer ->
                renderer.renderImageWithDPI(i, 140f, ImageType.RGB)
            }
            val view = ImageView(SwingFXUtils.toFXImage(img, null)).apply {
                isPreserveRatio = true
                fitWidth = layout.printableWidth
                fitHeight = layout.printableHeight
            }
            if (!job.printPage(view)) break
        }
        job.endJob()
    }

    fun rotateCurrent(degrees: Int) = mutate { session.rotatePage(currentPage, degrees) }

    fun deleteCurrent() = mutate {
        session.deletePage(currentPage)
        currentPage = currentPage.coerceAtMost(session.pageCount - 1)
    }

    fun insertBlank() = mutate { session.insertBlank(currentPage + 1) }

    fun moveCurrent(delta: Int) = mutate {
        val to = (currentPage + delta).coerceIn(0, session.pageCount - 1)
        session.movePage(currentPage, to)
        currentPage = to
    }

    fun insertPdf(owner: Window) {
        if (!isOpen) return
        val dlg = FileChooser()
        dlg.extensionFilters += FileChooser.ExtensionFilter("PDF", "*.pdf")
        val file = dlg.showOpenDialog(owner) ?: return
        mutate { session.mergeFrom(file) }
    }

    fun extractPages(owner: Window) {
        if (!isOpen) return
        val dlg = TextInputDialog("${currentPage + 1}-${session.pageCount}")
        dlg.title = "Извлечь страницы"
        dlg.headerText = "Диапазон страниц (например 2-5)"
        dlg.contentText = "Страницы:"
        val spec = dlg.showAndWait().orElse(null) ?: return
        val parts = spec.split("-", "–", "..", limit = 2).map { it.trim().toIntOrNull() }
        val from = ((parts.getOrNull(0) ?: return) - 1).coerceIn(0, session.pageCount - 1)
        val to = ((parts.getOrNull(1) ?: parts[0] ?: return) - 1).coerceIn(from, session.pageCount - 1)
        val save = FileChooser()
        save.extensionFilters += FileChooser.ExtensionFilter("PDF", "*.pdf")
        save.initialFileName = (session.file?.nameWithoutExtension ?: "pages") + "-$spec.pdf"
        val dest = save.showSaveDialog(owner) ?: return
        val out = if (dest.name.lowercase().endsWith(".pdf")) dest else File(dest.absolutePath + ".pdf")
        session.extractPages(from, to, out)
    }

    fun showProperties(owner: Window) {
        if (!isOpen) return
        val info = session.info()
        val title = TextField(info.title)
        val author = TextField(info.author)
        val subject = TextField(info.subject)
        val keywords = TextField(info.keywords)
        val grid = GridPane().apply {
            hgap = 8.0
            vgap = 8.0
            padding = Insets(8.0)
            add(Label("Файл:"), 0, 0)
            add(Label("${info.fileName}  (${info.pages} стр., ${info.fileSize} байт)"), 1, 0)
            add(Label("Версия PDF:"), 0, 1)
            add(Label(info.version + if (info.encrypted) "  • пароль" else ""), 1, 1)
            add(Label("Название:"), 0, 2)
            add(title, 1, 2)
            add(Label("Автор:"), 0, 3)
            add(author, 1, 3)
            add(Label("Тема:"), 0, 4)
            add(subject, 1, 4)
            add(Label("Ключевые слова:"), 0, 5)
            add(keywords, 1, 5)
        }
        val dialog = Dialog<ButtonType>()
        dialog.initOwner(owner)
        dialog.title = "Свойства PDF"
        dialog.dialogPane.content = grid
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        dialog.showAndWait().ifPresent { result ->
            if (result == ButtonType.OK) {
                session.setInfo(title.text, author.text, subject.text, keywords.text)
                refreshInfo()
                notifyState()
            }
        }
    }

    private fun mutate(block: () -> Unit) {
        if (!isOpen) return
        try {
            block()
            rebuildView()
            goTo(currentPage, false)
            notifyState()
        } catch (ex: Exception) {
            Alert(Alert.AlertType.ERROR, ex.message).apply {
                title = "PDF"
                headerText = "Не удалось изменить документ"
            }.showAndWait()
        }
    }

    private fun buildToolbar(): HBox {
        fun btn(text: String, tip: String, action: () -> Unit) = Button(text).apply {
            tooltip = Tooltip(tip)
            minWidth = 36.0
            setOnAction { action() }
        }
        fun toggle(text: String, tip: String, value: Tool) = ToggleButton(text).apply {
            tooltip = Tooltip(tip)
            toggleGroup = toolGroup
            userData = value
            isSelected = value == Tool.SELECT
        }
        val zoomBox = ComboBox<String>().apply {
            items.addAll("По ширине", "Страница целиком", "50%", "75%", "100%", "125%", "150%", "200%")
            value = "По ширине"
            prefWidth = 150.0
            setOnAction {
                when (value) {
                    "По ширине" -> {
                        zoomMode = ZoomMode.FIT_WIDTH
                        applyFit()
                    }
                    "Страница целиком" -> {
                        zoomMode = ZoomMode.FIT_PAGE
                        applyFit()
                    }
                    else -> {
                        zoomMode = ZoomMode.MANUAL
                        val pct = value?.removeSuffix("%")?.toFloatOrNull() ?: 100f
                        setDpi(72f * pct / 100f)
                    }
                }
            }
        }
        val select = toggle("Выдел.", "Выделить текст", Tool.SELECT)
        val highlight = toggle("Маркер", "Выделить цветом", Tool.HIGHLIGHT)
        val underline = toggle("Ч", "Подчеркнуть", Tool.UNDERLINE)
        val strike = toggle("abc", "Зачеркнуть", Tool.STRIKE)
        val note = toggle("Заметка", "Липкая заметка", Tool.NOTE)
        val rect = toggle("Рамка", "Прямоугольник", Tool.RECT)
        toolGroup.selectedToggleProperty().addListener { _, _, neo ->
            val data = neo?.userData as? Tool
            if (data != null) tool = data
        }
        searchField.setOnAction { runSearch(true) }
        val bar = HBox(
            6.0,
            btn("⏮", "Первая страница") { goTo(0) },
            btn("◀", "Предыдущая") { goTo(currentPage - 1) },
            pageField,
            pageCountLabel,
            btn("▶", "Следующая") { goTo(currentPage + 1) },
            btn("⏭", "Последняя") { goTo(session.pageCount - 1) },
            Separator(Orientation.VERTICAL),
            btn("−", "Мельче") { zoomBy(-16f) },
            zoomLabel,
            btn("+", "Крупнее") { zoomBy(16f) },
            zoomBox,
            Separator(Orientation.VERTICAL),
            select, highlight, underline, strike, note, rect,
            Separator(Orientation.VERTICAL),
            searchField,
            btn("Найти", "Искать") { runSearch(true) },
            btn("Далее", "Следующее совпадение") { stepSearch(1) },
            searchStatus
        ).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(8.0, 10.0, 8.0, 10.0)
        }
        pageField.setOnAction {
            val n = pageField.text.trim().toIntOrNull() ?: return@setOnAction
            goTo(n - 1)
        }
        HBox.setHgrow(searchField, Priority.SOMETIMES)
        return bar
    }

    private fun buildSidebar(): TabPane {
        thumbs.cellFactory = javafx.util.Callback {
            object : ListCell<Int>() {
                private val iv = ImageView().apply { isPreserveRatio = true; fitWidth = 118.0 }
                private val lab = Label()
                private val box = VBox(4.0, iv, lab).apply { alignment = Pos.CENTER; padding = Insets(6.0) }
                override fun updateItem(item: Int?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        graphic = null
                    } else {
                        lab.text = "${item + 1}"
                        iv.image = tiles.getOrNull(item)?.imageView?.image
                        graphic = box
                    }
                }
            }
        }
        thumbs.selectionModel.selectedItemProperty().addListener { _, _, neo ->
            if (neo != null) goTo(neo)
        }
        bookmarks.setOnMouseClicked { e ->
            if (e.clickCount >= 1) {
                bookmarks.selectionModel.selectedItem?.page?.let { goTo(it) }
            }
        }
        bookmarks.cellFactory = javafx.util.Callback {
            object : ListCell<BookmarkRow>() {
                override fun updateItem(item: BookmarkRow?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (empty || item == null) null
                    else "  ".repeat(item.depth) + item.title + (item.page?.let { "  · ${it + 1}" } ?: "")
                }
            }
        }
        infoLabel.isWrapText = true
        infoLabel.padding = Insets(10.0)
        val infoScroll = ScrollPane(infoLabel).apply { isFitToWidth = true }
        val tabs = TabPane(
            Tab("Страницы", thumbs),
            Tab("Закладки", bookmarks),
            Tab("Текст", textPane),
            Tab("Сведения", infoScroll)
        )
        tabs.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
        tabs.minWidth = 180.0
        tabs.prefWidth = 220.0
        return tabs
    }

    private fun rebuildView() {
        renderEpoch.incrementAndGet()
        tiles.clear()
        pagesBox.children.clear()
        val n = session.pageCount
        pageCountLabel.text = "/ $n"
        for (i in 0 until n) {
            val (w, h) = session.pageSize(i)
            val tile = PdfPageTile(i)
            tile.layoutFor(dpi, w, h)
            wireTile(tile)
            tiles += tile
            pagesBox.children += tile
        }
        thumbs.items.setAll((0 until n).toList())
        session.withDocument { doc, _ -> textIndex.rebuild(doc) }
        textPane.text = textIndex.text()
        loadBookmarks()
        refreshInfo()
        applyFit()
        paintOverlays()
        requestRender()
        requestThumbs()
    }

    private fun loadBookmarks() {
        val rows = mutableListOf<BookmarkRow>()
        session.withDocument { doc, _ ->
            val outline = doc.documentCatalog.documentOutline ?: return@withDocument
            fun walk(node: org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode, depth: Int) {
                var cur = node.firstChild
                while (cur != null) {
                    val page = try {
                        cur.findDestinationPage(doc)?.let { target ->
                            doc.pages.indexOfFirst { it.cosObject == target.cosObject }
                        }?.takeIf { it >= 0 }
                    } catch (_: Exception) {
                        null
                    }
                    rows += BookmarkRow(cur.title ?: "Закладка", page, depth)
                    walk(cur, depth + 1)
                    cur = cur.nextSibling
                }
            }
            walk(outline, 0)
        }
        bookmarks.items.setAll(rows)
    }

    private fun refreshInfo() {
        val i = session.info()
        infoLabel.text = buildString {
            appendLine(i.fileName)
            appendLine("Страниц: ${i.pages}")
            appendLine("Размер: ${i.fileSize} байт")
            appendLine("Версия: ${i.version}")
            appendLine("Шифрование: ${if (i.encrypted) "да" else "нет"}")
            appendLine()
            appendLine("Название: ${i.title}")
            appendLine("Автор: ${i.author}")
            appendLine("Тема: ${i.subject}")
            appendLine("Ключевые слова: ${i.keywords}")
            appendLine("Создан: ${i.creator}")
        }
    }

    private fun wireTile(tile: PdfPageTile) {
        tile.setOnMousePressed { e ->
            if (e.button != MouseButton.PRIMARY) return@setOnMousePressed
            tile.requestFocus()
            currentPage = tile.index
            pageField.text = "${tile.index + 1}"
            dragTile = tile
            dragStart = e.x to e.y
            if (tool == Tool.NOTE) {
                val dlg = TextInputDialog()
                dlg.title = "Заметка"
                dlg.headerText = "Текст заметки"
                dlg.showAndWait().ifPresent { note ->
                    if (note.isNotBlank()) {
                        val x = tile.toPdfX(e.x)
                        val yTop = tile.toPdfYTop(e.y)
                        val y = tile.pageHeightPts - yTop
                        session.addNote(tile.index, x, y, note)
                        requestRender(tile.index)
                        notifyState()
                    }
                }
                dragStart = null
                return@setOnMousePressed
            }
            val rect = Rectangle(e.x, e.y, 0.0, 0.0).apply {
                fill = Color.rgb(80, 140, 255, 0.22)
                stroke = Color.rgb(40, 90, 200, 0.9)
            }
            dragRect = rect
            tile.overlay.children.add(rect)
        }
        tile.setOnMouseDragged { e ->
            val start = dragStart ?: return@setOnMouseDragged
            val rect = dragRect ?: return@setOnMouseDragged
            val x = minOf(start.first, e.x)
            val y = minOf(start.second, e.y)
            rect.x = x
            rect.y = y
            rect.width = kotlin.math.abs(e.x - start.first)
            rect.height = kotlin.math.abs(e.y - start.second)
        }
        tile.setOnMouseReleased { e ->
            val start = dragStart
            val rect = dragRect
            dragStart = null
            dragRect = null
            dragTile = null
            if (start == null || rect == null) return@setOnMouseReleased
            tile.overlay.children.remove(rect)
            val x0 = tile.toPdfX(start.first)
            val y0 = tile.toPdfYTop(start.second)
            val x1 = tile.toPdfX(e.x)
            val y1 = tile.toPdfYTop(e.y)
            if (kotlin.math.abs(e.x - start.first) < 3 && kotlin.math.abs(e.y - start.second) < 3) {
                selectedRuns = emptyList()
                paintOverlays()
                return@setOnMouseReleased
            }
            when (tool) {
                Tool.SELECT -> {
                    selectedRuns = textIndex.runsIn(tile.index, x0, y0, x1, y1)
                    paintOverlays()
                }
                Tool.HIGHLIGHT, Tool.UNDERLINE, Tool.STRIKE -> {
                    val runs = textIndex.runsIn(tile.index, x0, y0, x1, y1)
                    val subtype = when (tool) {
                        Tool.UNDERLINE -> "Underline"
                        Tool.STRIKE -> "StrikeOut"
                        else -> "Highlight"
                    }
                    val color = when (tool) {
                        Tool.UNDERLINE -> floatArrayOf(0.1f, 0.35f, 0.9f)
                        Tool.STRIKE -> floatArrayOf(0.85f, 0.1f, 0.1f)
                        else -> floatArrayOf(1f, 0.92f, 0.2f)
                    }
                    if (runs.isNotEmpty()) {
                        val (quads, box) = quadsOf(runs, tile.pageHeightPts)
                        session.addMarkup(tile.index, subtype, quads, box, color, runs.joinToString("") { it.unicode })
                    } else {
                        val left = minOf(x0, x1)
                        val right = maxOf(x0, x1)
                        val top = minOf(y0, y1)
                        val bottom = maxOf(y0, y1)
                        val h = (bottom - top).coerceAtLeast(8f)
                        val lly = tile.pageHeightPts - bottom
                        val box = PDRectangle(left, lly, (right - left).coerceAtLeast(8f), h)
                        val quads = floatArrayOf(
                            left, lly + h, right, lly + h,
                            left, lly, right, lly
                        )
                        session.addMarkup(tile.index, subtype, quads, box, color, "")
                    }
                    requestRender(tile.index)
                    notifyState()
                }
                Tool.RECT -> {
                    val left = minOf(x0, x1)
                    val right = maxOf(x0, x1)
                    val top = minOf(y0, y1)
                    val bottom = maxOf(y0, y1)
                    val lly = tile.pageHeightPts - bottom
                    session.addSquare(
                        tile.index,
                        PDRectangle(left, lly, (right - left).coerceAtLeast(8f), (bottom - top).coerceAtLeast(8f))
                    )
                    requestRender(tile.index)
                    notifyState()
                }
                Tool.NOTE -> {}
            }
        }
    }

    private fun quadsOf(runs: List<PdfTextIndex.Run>, pageHeight: Float): Pair<FloatArray, PDRectangle> {
        val quads = FloatArray(runs.size * 8)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        runs.forEachIndexed { i, r ->
            val lly = pageHeight - r.yTop
            val ury = lly + r.h
            val llx = r.x
            val urx = r.x + r.w
            val o = i * 8
            quads[o] = llx
            quads[o + 1] = ury
            quads[o + 2] = urx
            quads[o + 3] = ury
            quads[o + 4] = llx
            quads[o + 5] = lly
            quads[o + 6] = urx
            quads[o + 7] = lly
            minX = minOf(minX, llx)
            minY = minOf(minY, lly)
            maxX = maxOf(maxX, urx)
            maxY = maxOf(maxY, ury)
        }
        return quads to PDRectangle(minX, minY, maxX - minX, maxY - minY)
    }

    private fun paintOverlays() {
        tiles.forEach { it.overlay.children.clear() }
        selectedRuns.groupBy { it.page }.forEach { (page, runs) ->
            val tile = tiles.getOrNull(page) ?: return@forEach
            runs.forEach { run ->
                tile.overlay.children += tile.overlayRect(run.x, run.yTop, run.w, run.h).apply {
                    fill = Color.rgb(70, 140, 255, 0.28)
                    stroke = Color.TRANSPARENT
                    isMouseTransparent = true
                }
            }
        }
        val active = searchHits.getOrNull(searchAt)
        searchHits.forEach { hit ->
            val tile = tiles.getOrNull(hit.page) ?: return@forEach
            val current = hit === active
            hit.runs.forEach { run ->
                tile.overlay.children += tile.overlayRect(run.x, run.yTop, run.w, run.h).apply {
                    fill = if (current) Color.rgb(255, 140, 0, 0.45) else Color.rgb(255, 220, 40, 0.35)
                    stroke = Color.TRANSPARENT
                    isMouseTransparent = true
                }
            }
        }
    }

    private fun runSearch(fromStart: Boolean) {
        val q = searchField.text ?: ""
        searchHits = textIndex.search(q, true)
        searchAt = if (searchHits.isEmpty()) -1 else 0
        searchStatus.text = if (q.isBlank()) "" else "${searchHits.size}"
        if (fromStart && searchHits.isNotEmpty()) {
            goTo(searchHits[0].page)
        }
        paintOverlays()
    }

    private fun stepSearch(delta: Int) {
        if (searchHits.isEmpty()) {
            runSearch(true)
            return
        }
        searchAt = (searchAt + delta).mod(searchHits.size)
        goTo(searchHits[searchAt].page)
        searchStatus.text = "${searchAt + 1} / ${searchHits.size}"
        paintOverlays()
    }

    private fun goTo(index: Int, scrollTo: Boolean = true) {
        if (!isOpen || tiles.isEmpty()) return
        currentPage = index.coerceIn(0, tiles.lastIndex)
        pageField.text = "${currentPage + 1}"
        if (scrollTo) {
            Platform.runLater {
                val tile = tiles.getOrNull(currentPage) ?: return@runLater
                val contentH = pagesBox.height
                val viewH = scroll.viewportBounds.height
                if (contentH > viewH) {
                    val y = tile.boundsInParent.minY - 12.0
                    scroll.vvalue = (y / (contentH - viewH)).coerceIn(0.0, 1.0)
                }
            }
        }
        if (thumbs.selectionModel.selectedItem != currentPage) {
            thumbs.selectionModel.select(currentPage)
        }
        notifyState()
        requestRender()
    }

    private fun onScroll() {
        if (tiles.isEmpty()) return
        val view = scroll.viewportBounds
        val contentY = scroll.vvalue * (pagesBox.height - view.height).coerceAtLeast(0.0)
        val mid = contentY + view.height / 3.0
        val idx = tiles.indexOfLast { it.boundsInParent.minY <= mid }.coerceAtLeast(0)
        if (idx != currentPage) {
            currentPage = idx
            pageField.text = "${idx + 1}"
            if (thumbs.selectionModel.selectedItem != idx) thumbs.selectionModel.select(idx)
            notifyState()
        }
        requestRender()
    }

    private fun visibleRange(): IntRange {
        if (tiles.isEmpty()) return 0..-1
        val view = scroll.viewportBounds
        val contentY = scroll.vvalue * (pagesBox.height - view.height).coerceAtLeast(0.0)
        val top = contentY
        val bottom = contentY + view.height
        val first = tiles.indexOfFirst { it.boundsInParent.maxY >= top }.let { if (it < 0) 0 else it }
        val last = tiles.indexOfLast { it.boundsInParent.minY <= bottom }.let { if (it < 0) first else it }
        return first..last
    }

    private fun requestRender(only: Int? = null) {
        if (!isOpen || tiles.isEmpty()) return
        val epoch = renderEpoch.incrementAndGet()
        val range = if (only != null) only..only else {
            val vis = visibleRange()
            if (vis.isEmpty()) 0..0
            else (vis.first - 1).coerceAtLeast(0)..(vis.last + 1).coerceAtMost(tiles.lastIndex)
        }
        val keep = if (only != null) only..only else {
            val vis = visibleRange()
            (vis.first - 2).coerceAtLeast(0)..(vis.last + 2).coerceAtMost(tiles.lastIndex)
        }
        tiles.forEachIndexed { idx, tile ->
            if (idx !in keep) tile.clearImage()
        }
        val dpiNow = dpi
        range.forEach { idx ->
            renderPool.submit {
                if (epoch != renderEpoch.get()) return@submit
                try {
                    val img = session.withDocument { _, renderer ->
                        renderer.renderImageWithDPI(idx, dpiNow, ImageType.RGB)
                    }
                    if (epoch != renderEpoch.get()) return@submit
                    Platform.runLater {
                        if (epoch != renderEpoch.get()) return@runLater
                        tiles.getOrNull(idx)?.showImage(SwingFXUtils.toFXImage(img, null))
                        thumbs.refresh()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun requestThumbs() {
        Platform.runLater { thumbs.refresh() }
    }

    private fun applyFit() {
        if (!isOpen || tiles.isEmpty()) return
        val view = scroll.viewportBounds
        if (view.width <= 0 || view.height <= 0) {
            requestRender()
            return
        }
        val (w, h) = session.pageSize(currentPage.coerceIn(0, session.pageCount - 1))
        val next = when (zoomMode) {
            ZoomMode.FIT_WIDTH -> ((view.width - 48.0) / w * 72.0).toFloat()
            ZoomMode.FIT_PAGE -> {
                val byW = (view.width - 48.0) / w * 72.0
                val byH = (view.height - 24.0) / h * 72.0
                minOf(byW, byH).toFloat()
            }
            ZoomMode.MANUAL -> dpi
        }
        if (zoomMode != ZoomMode.MANUAL) setDpi(next)
        else relayout()
    }

    private fun zoomBy(delta: Float) {
        zoomMode = ZoomMode.MANUAL
        setDpi(dpi + delta)
    }

    private fun setDpi(value: Float) {
        dpi = value.coerceIn(48f, 220f)
        zoomLabel.text = zoomTitle()
        relayout()
        requestRender()
    }

    private fun relayout() {
        tiles.forEach { tile ->
            val (w, h) = session.pageSize(tile.index)
            tile.layoutFor(dpi, w, h)
        }
        paintOverlays()
    }

    private fun onKey(e: KeyEvent) {
        if (!isOpen) return
        when {
            e.isControlDown && e.code == KeyCode.F -> {
                e.consume()
                focusSearch()
            }
            e.isControlDown && e.code == KeyCode.C -> {
                val text = selectedRuns.joinToString("") { it.unicode }
                if (text.isNotEmpty()) {
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                        javafx.scene.input.ClipboardContent().apply { putString(text) }
                    )
                    e.consume()
                }
            }
            e.isControlDown && (e.code == KeyCode.PLUS || e.code == KeyCode.EQUALS || e.code == KeyCode.ADD) -> {
                zoomBy(16f)
                e.consume()
            }
            e.isControlDown && (e.code == KeyCode.MINUS || e.code == KeyCode.SUBTRACT) -> {
                zoomBy(-16f)
                e.consume()
            }
            e.code == KeyCode.PAGE_DOWN || e.code == KeyCode.DOWN && e.isAltDown -> {
                goTo(currentPage + 1)
                e.consume()
            }
            e.code == KeyCode.PAGE_UP || e.code == KeyCode.UP && e.isAltDown -> {
                goTo(currentPage - 1)
                e.consume()
            }
            e.code == KeyCode.HOME && e.isControlDown -> {
                goTo(0)
                e.consume()
            }
            e.code == KeyCode.END && e.isControlDown -> {
                goTo(session.pageCount - 1)
                e.consume()
            }
            e.code == KeyCode.ENTER && searchField.isFocused -> {
                stepSearch(1)
                e.consume()
            }
            e.code == KeyCode.F3 -> {
                stepSearch(if (e.isShiftDown) -1 else 1)
                e.consume()
            }
        }
    }

    private fun notifyState() {
        onStateChanged()
    }

    private data class BookmarkRow(val title: String, val page: Int?, val depth: Int)
}
