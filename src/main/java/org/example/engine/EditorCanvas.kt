package org.example.engine

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.Pane
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DataFormat
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.Cursor
import javafx.scene.image.Image
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.scene.text.FontPosture
import javafx.scene.text.Text
import javafx.util.Duration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.io.ByteArrayInputStream
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import org.example.ui.UiChrome

class EditorCanvas : ScrollPane() {

    val document = Document()
    var onChange: (() -> Unit)? = null
    var onCaretMoved: ((Int) -> Unit)? = null
    var onStyleChanged: ((
        family: String,
        size: Double,
        align: Align,
        bold: Boolean,
        italic: Boolean,
        underline: Boolean
    ) -> Unit)? = null
    /** Выделение и заголовок: компактный вид меняет по ним правый инспектор. */
    var onContextChanged: ((selected: Boolean, heading: Boolean) -> Unit)? = null
    var onZoomRequested: ((Double) -> Unit)? = null
    var onFormatBrushChanged: ((Boolean) -> Unit)? = null
    var showRuler: Boolean = false
    var showGrid: Boolean = false
    var scrollWithSpace: Boolean = false
    var fanficMode: Boolean = false
        private set
    var zoom: Double = 1.0
        private set

    private val sheet = Pane()
    private val canvas = Canvas(640.0, 900.0)
    private var layout: LayoutResult = Layout.build(document)
    private var caretPara = 0
    private var caretChar = 0
    private var anchorPara = 0
    private var anchorChar = 0
    private var hasSel = false
    private var pendingFamily: String? = null
    private var pendingSize: Double? = null
    private var pendingBold: Boolean? = null
    private var pendingItalic: Boolean? = null
    private var pendingUnderline: Boolean? = null
    private var pendingStrike: Boolean? = null
    private var pendingColor: String? = null
    private var pendingColorSet = false
    private var formatBrush: FormatBrush? = null
    private var stickX: Double? = null
    private var desk = Color.web("#3a3a3a")
    private var pageFill = Color.WHITE
    private var textFill = Color.rgb(20, 20, 20)
    private var caretColor = Color.web("#00B4D8")
    private var caretColorLocked = false
    private var caretOn = true
    private var caretBlinkKey = ""
    private var caretPeriodMs = systemCaretBlinkMs
    private val caretBlink = Timeline().apply { cycleCount = Timeline.INDEFINITE }
    private val undo = ArrayDeque<Document>()
    private val redo = ArrayDeque<Document>()
    private val imageCache = IdentityHashMap<ImageBlock, Image>()

    private val ptPerMm = Guides.ptPerMm
    private val ruler = Guides.rulerSize
    private var guideDrag = Guides.Handle.NONE
    private var guidePage = 0
    private var guideSnapshotTaken = false
    private var guidePosition = 0.0
    private val guideTooltip = Tooltip()
    private var hoveredGuide = Guides.Handle.NONE
    private var officeMode = UiChrome.STANDARD
    private var layingOut = false

    init {
        styleClass.add("editor-desk")
        sheet.children += canvas
        content = sheet
        isFitToWidth = true
        padding = Insets(8.0)
        style = "-fx-background-color: #3a3a3a;"
        isFocusTraversable = true
        canvas.isFocusTraversable = true
        viewportBoundsProperty().addListener { _, _, _ -> relayout(notify = false) }
        vvalueProperty().addListener { _, _, _ -> positionCanvas() }
        hvalueProperty().addListener { _, _, _ -> positionCanvas() }
        setOnMouseClicked {
            requestFocus()
            canvas.requestFocus()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            requestFocus()
            canvas.requestFocus()
            if (beginGuideDrag(documentX(e.x), documentY(e.y))) {
                e.consume()
                return@addEventHandler
            }
            val extend = e.isShiftDown
            pickCaret(documentX(e.x), documentY(e.y))
            if (!extend) {
                anchorPara = caretPara
                anchorChar = caretChar
                hasSel = false
                clearTypingStyle()
            } else hasSel = offsetOf(anchorPara, anchorChar) != caretOffset()
            stickX = null
            armCaret()
            paint()
            onCaretMoved?.invoke(caretPara)
            publishStyle()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED) { e ->
            if (e.clickCount < 2) return@addEventHandler
            val para = document.paragraphs.getOrNull(caretPara) ?: return@addEventHandler
            if (e.clickCount >= 3) {
                anchorChar = 0
                caretChar = para.text.length
            } else if (para.text.isNotEmpty()) {
                val bounds = ParagraphFormatting.wordBounds(para.text, (caretChar - 1).coerceIn(0, para.text.lastIndex))
                if (bounds.isEmpty()) return@addEventHandler
                anchorChar = bounds.first
                caretChar = (bounds.last + 1).coerceAtMost(para.text.length)
            }
            hasSel = anchorChar != caretChar
            paint()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            if (guideDrag != Guides.Handle.NONE) {
                dragGuide(documentX(e.x), documentY(e.y))
                e.consume()
                return@addEventHandler
            }
            pickCaret(documentX(e.x), documentY(e.y))
            hasSel = offsetOf(anchorPara, anchorChar) != caretOffset()
            paint()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_RELEASED) { e ->
            if (guideDrag != Guides.Handle.NONE) {
                guideDrag = Guides.Handle.NONE
                guideSnapshotTaken = false
                canvas.cursor = Cursor.DEFAULT
                paint()
                e.consume()
                return@addEventHandler
            }
            if (formatBrush != null) paintFormatBrush()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED) { e ->
            val x = documentX(e.x)
            val y = documentY(e.y)
            canvas.cursor = guideCursor(x, y)
            val handle = guideHandle(x, y)
            if (handle != hoveredGuide) {
                hoveredGuide = handle
                Tooltip.uninstall(canvas, guideTooltip)
                if (handle != Guides.Handle.NONE) {
                    guideTooltip.text = guideDescription(handle)
                    Tooltip.install(canvas, guideTooltip)
                }
            }
        }
        addEventFilter(KeyEvent.KEY_PRESSED) { onKey(it) }
        addEventFilter(KeyEvent.KEY_TYPED) { onType(it) }
        focusedProperty().addListener { _, _, _ -> paint() }
        canvas.focusedProperty().addListener { _, _, _ -> paint() }
        sceneProperty().addListener { _, _, scene ->
            if (scene == null) {
                caretBlink.stop()
                caretBlinkKey = ""
            }
        }
        installCaretBlink()
        addEventFilter(ScrollEvent.SCROLL) { event ->
            if (event.isControlDown && event.deltaY != 0.0) {
                val next = zoom + if (event.deltaY > 0) 0.1 else -0.1
                val handler = onZoomRequested
                if (handler != null) handler(next) else setZoom(next)
                event.consume()
            }
        }
        relayout(notify = false)
    }

    fun zoomBy(delta: Double) {
        zoom = (zoom + delta).coerceIn(0.5, 2.5)
        relayout(false)
        publishStyle()
    }

    fun zoomReset() {
        zoom = 1.0
        relayout(false)
        publishStyle()
    }

    fun applyDesk(colorCss: String) {
        desk = runCatching { Color.web(colorCss) }.getOrDefault(Color.web("#3a3a3a"))
        if (!fanficMode) {
            pageFill = Color.WHITE
            textFill = Color.rgb(20, 20, 20)
        }
        style = "-fx-background-color: $colorCss;"
        paint()
    }

    fun applyFanficPalette(deskCss: String, pageCss: String, textCss: String, caretCss: String) {
        desk = runCatching { Color.web(deskCss) }.getOrDefault(Color.web("#e9ebee"))
        pageFill = runCatching { Color.web(pageCss) }.getOrDefault(Color.WHITE)
        textFill = runCatching { Color.web(textCss) }.getOrDefault(Color.rgb(20, 20, 20))
        if (!caretColorLocked) {
            caretColor = runCatching { Color.web(caretCss) }.getOrDefault(Color.web("#5cb85c"))
        }
        style = "-fx-background-color: $deskCss;"
        paint()
    }

    /**
     * @param blinkMs 0 — скорость мигания Windows, отрицательное — курсор горит постоянно,
     * иначе интервал в миллисекундах.
     */
    fun applyCaret(colorCss: String, blinkMs: Int) {
        caretColorLocked = true
        caretColor = runCatching { Color.web(colorCss) }.getOrDefault(Color.web("#00B4D8"))
        caretPeriodMs = when {
            blinkMs < 0 -> -1.0
            blinkMs == 0 -> systemCaretBlinkMs
            else -> blinkMs.coerceIn(150, 1200).toDouble()
        }
        installCaretBlink()
        caretBlinkKey = ""
        caretOn = true
        paint()
    }

    fun setOfficeMode(mode: UiChrome) {
        officeMode = mode
        paint()
    }

    fun toggleRunStyle(
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        strikethrough: Boolean = false
    ) {
        if (!bold && !italic && !underline && !strikethrough) return
        if (!hasSel) {
            val para = document.paragraphs.getOrNull(caretPara) ?: return
            val base = ParagraphFormatting.styleAt(para, caretChar)
            if (bold) pendingBold = !(pendingBold ?: (base.bold || para.bold))
            if (italic) pendingItalic = !(pendingItalic ?: base.italic)
            if (underline) pendingUnderline = !(pendingUnderline ?: base.underline)
            if (strikethrough) pendingStrike = !(pendingStrike ?: base.strikethrough)
            if (para.text.isEmpty() && bold) para.bold = pendingBold == true
            publishStyle()
            return
        }
        snapshot()
        if (bold) {
            document.paragraphs.forEach { para ->
                if (!para.bold || para.text.isEmpty()) return@forEach
                para.setRuns(para.runs.ifEmpty { listOf(TextRun(para.text)) }.map { it.copy(bold = true) })
                para.bold = false
            }
        }
        val from = selStart()
        val to = selEnd()
        val selected = ParagraphFormatting.clipParts(document, from, to).flatten()
        val allOn = selected.isNotEmpty() && selected.all { run ->
            when {
                bold -> run.bold
                italic -> run.italic
                underline -> run.underline
                else -> run.strikethrough
            }
        }
        ParagraphFormatting.mapRange(document, from, to) { run ->
            run.copy(
                bold = if (bold) !allOn else run.bold,
                italic = if (italic) !allOn else run.italic,
                underline = if (underline) !allOn else run.underline,
                strikethrough = if (strikethrough) !allOn else run.strikethrough
            )
        }
        relayout()
    }

    fun caretStrike(): Boolean {
        val para = document.paragraphs.getOrNull(caretPara) ?: return false
        if (hasSel) {
            val selected = ParagraphFormatting.clipParts(document, selStart(), selEnd()).flatten()
            return selected.isNotEmpty() && selected.all { it.strikethrough }
        }
        return pendingStrike ?: ParagraphFormatting.styleAt(para, caretChar).strikethrough
    }

    fun caretSpacing(): Double = document.paragraphs.getOrNull(caretPara)?.lineSpacing ?: 1.0

    fun caretOutline(): Int = document.paragraphs.getOrNull(caretPara)?.outlineLevel ?: 0

    fun hasSelection(): Boolean = hasSel

    fun caretColorCss(): String? {
        if (pendingColorSet) return pendingColor
        val para = document.paragraphs.getOrNull(caretPara) ?: return null
        return ParagraphFormatting.styleAt(para, caretChar).color
    }

    fun applyFontColor(css: String?) {
        val color = css?.trim()?.ifBlank { null }
        if (!hasSel) {
            pendingColor = color
            pendingColorSet = true
            publishStyle()
            return
        }
        snapshot()
        ParagraphFormatting.mapRange(document, selStart(), selEnd()) { it.copy(color = color) }
        relayout()
    }

    fun clearFormatting() {
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        if (indices.isEmpty()) return
        snapshot()
        if (!hasSel) {
            indices.forEach { ParagraphFormatting.applyWordStyle(document.paragraphs[it], 0) }
        } else {
            val from = selStart()
            val to = selEnd()
            ParagraphFormatting.mapRange(document, from, to, ParagraphFormatting::clearRun)
            var offset = 0
            document.paragraphs.forEachIndexed { index, paragraph ->
                val start = offset
                val end = offset + paragraph.text.length
                if (index in indices && from <= start && to >= end) {
                    ParagraphFormatting.applyWordStyle(paragraph, 0)
                }
                offset = end + 1
            }
        }
        clearTypingStyle()
        relayout()
    }

    fun setLineSpacing(value: Double) {
        val next = value.coerceIn(0.8, 3.0)
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        if (indices.isEmpty() || indices.all { document.paragraphs[it].lineSpacing == next }) return
        snapshot()
        indices.forEach { document.paragraphs[it].lineSpacing = next }
        relayout()
    }

    fun applyWordStyle(level: Int) {
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        if (indices.isEmpty()) return
        snapshot()
        indices.forEach { ParagraphFormatting.applyWordStyle(document.paragraphs[it], level) }
        clearTypingStyle()
        relayout()
    }

    fun formatBrushArmed(): Boolean = formatBrush != null

    fun captureFormatBrush() {
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        val run = ParagraphFormatting.styleAt(para, caretChar)
        formatBrush = FormatBrush(
            run = run.copy(text = ""),
            align = para.align,
            leftIndentPt = para.leftIndentPt,
            rightIndentPt = para.rightIndentPt,
            firstLineIndentPt = para.firstLineIndentPt,
            spacingBeforePt = para.spacingBeforePt,
            spacingAfterPt = para.spacingAfterPt,
            lineSpacing = para.lineSpacing,
            outlineLevel = para.outlineLevel
        )
        onFormatBrushChanged?.invoke(true)
    }

    fun cancelFormatBrush() {
        if (formatBrush == null) return
        formatBrush = null
        onFormatBrushChanged?.invoke(false)
    }

    fun zoomToFitWidth() {
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val logical = layout.pageWidth + extra + 36.0
        val view = viewportBounds.width
        if (logical <= 1.0 || view <= 1.0) return
        setZoom((view - 16.0) / logical)
    }

    fun reveal(paragraph: Int, charInParagraph: Int) {
        val para = document.paragraphs.getOrNull(paragraph) ?: return
        caretPara = paragraph
        caretChar = charInParagraph.coerceIn(0, para.text.length)
        anchorPara = caretPara
        anchorChar = caretChar
        hasSel = false
        stickX = null
        armCaret()
        paint()
        ensureCaretVisible()
        publishStyle()
    }

    fun navigation(): List<NavSpot> {
        val items = mutableListOf<NavSpot>()
        document.paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.outlineLevel <= 0) return@forEachIndexed
            val title = paragraph.text.trim()
            if (title.isEmpty()) return@forEachIndexed
            items += NavSpot(title.take(72), index, 0, paragraph.outlineLevel.coerceIn(1, 6), NavKind.HEADING)
        }
        layout.pages.forEach { page ->
            val line = page.lines.firstOrNull()
            items += NavSpot(
                "Страница ${page.index + 1}",
                line?.paragraphIndex ?: 0,
                line?.startInParagraph ?: 0,
                0,
                NavKind.PAGE
            )
        }
        return items
    }

    /** Applies the ribbon font to the selection, or to the next characters when nothing is selected. */
    fun applyCharacterFont(family: String?, size: Double?) {
        if (family == null && size == null) return
        if (hasSel) {
            snapshot()
            ParagraphFormatting.mapRange(document, selStart(), selEnd()) { run ->
                run.copy(fontFamily = family ?: run.fontFamily, fontSize = size ?: run.fontSize)
            }
            relayout()
            return
        }
        if (family != null) pendingFamily = family
        if (size != null) pendingSize = size
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        if (para.text.isEmpty()) {
            if (family != null) para.fontFamily = family
            if (size != null) para.fontSize = size
            relayout(false)
        }
        publishStyle()
    }

    fun bumpFontSize(delta: Double) {
        if (hasSel) {
            snapshot()
            var offset = 0
            val from = selStart()
            val to = selEnd()
            document.paragraphs.forEach { para ->
                val start = offset
                val end = offset + para.text.length
                val localA = (from.coerceAtLeast(start) - start)
                val localB = (to.coerceAtMost(end) - start)
                if (localA < localB) {
                    val mid = ParagraphFormatting.sliceRuns(para, localA, localB).map { run ->
                        run.copy(fontSize = ((run.fontSize ?: para.fontSize) + delta).coerceIn(8.0, 96.0))
                    }
                    para.setRuns(ParagraphFormatting.mergeAdjacent(
                        ParagraphFormatting.sliceRuns(para, 0, localA) + mid +
                            ParagraphFormatting.sliceRuns(para, localB, para.text.length)
                    ))
                }
                offset = end + 1
            }
            relayout()
            return
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        val base = pendingSize ?: ParagraphFormatting.styleAt(para, caretChar).fontSize ?: para.fontSize
        pendingSize = (base + delta).coerceIn(8.0, 96.0)
        if (para.text.isEmpty()) {
            para.fontSize = pendingSize ?: para.fontSize
            relayout(false)
        }
        publishStyle()
    }

    fun typingFont(): Pair<String, Double> {
        val para = document.paragraphs.getOrNull(caretPara)
        val style = para?.let { ParagraphFormatting.styleAt(it, caretChar) }
        val family = pendingFamily ?: style?.fontFamily ?: para?.fontFamily ?: "Segoe UI"
        val size = pendingSize ?: style?.fontSize ?: para?.fontSize ?: 16.0
        return family to size
    }

    fun insertSceneBreak() {
        snapshot()
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        when {
            para.text.isEmpty() -> Unit
            caretChar == 0 -> document.insertParagraph(caretPara, Paragraph())
            caretChar >= para.text.length -> {
                document.insertParagraph(caretPara + 1, Paragraph())
                caretPara++
                caretChar = 0
            }
            else -> {
                caretPara = ParagraphFormatting.splitAtCaret(document, caretPara, caretChar)
                caretChar = 0
                document.insertParagraph(caretPara, Paragraph())
            }
        }
        clearSel()
        relayout()
    }

    fun loadPlain(text: String) {
        undo.clear()
        redo.clear()
        imageCache.clear()
        clearTypingStyle()
        document.fromPlainText(text)
        caretPara = (document.paragraphs.size - 1).coerceAtLeast(0)
        caretChar = document.paragraphs.getOrNull(caretPara)?.text?.length ?: 0
        clearSel()
        relayout(false)
        publishStyle()
    }

    fun setZoom(value: Double) {
        zoom = value.coerceIn(0.5, 2.5)
        relayout(false)
        publishStyle()
    }

    fun loadDocument(source: Document) {
        undo.clear()
        redo.clear()
        imageCache.clear()
        clearTypingStyle()
        document.loadBlocks(source.blocks)
        document.applyPaper(source.paper, source.landscape)
        document.pageWidthPt = source.pageWidthPt
        document.pageHeightPt = source.pageHeightPt
        document.marginPt = source.marginPt
        document.marginLeftPt = source.marginLeftPt
        document.marginRightPt = source.marginRightPt
        document.marginTopPt = source.marginTopPt
        document.marginBottomPt = source.marginBottomPt
        caretPara = 0
        caretChar = 0
        clearSel()
        relayout(false)
        publishStyle()
    }

    fun plainText(): String = document.toPlainText()

    fun caretOffset(): Int = offsetOf(caretPara, caretChar)

    fun undo() {
        if (undo.isEmpty()) return
        redo.addLast(document.copy())
        restore(undo.removeLast())
        caretPara = 0
        caretChar = 0
        clearSel()
        clearTypingStyle()
        relayout()
    }

    fun redo() {
        if (redo.isEmpty()) return
        undo.addLast(document.copy())
        restore(redo.removeLast())
        caretPara = 0
        caretChar = 0
        clearSel()
        clearTypingStyle()
        relayout()
    }

    fun replaceRange(from: Int, to: Int, word: String) {
        snapshot()
        val a = from.coerceIn(0, document.toPlainText().length)
        document.replaceRange(from, to, word)
        placeCaret(a + word.length)
        clearSel()
        relayout()
    }

    fun replaceRanges(ranges: List<IntRange>, replacement: String) {
        if (ranges.isEmpty()) return
        snapshot()
        document.replaceRanges(ranges, replacement)
        placeCaret(ranges.minOf { it.first } + replacement.length)
        clearSel()
        clearTypingStyle()
        relayout()
    }

    fun selectAll() {
        anchorPara = 0
        anchorChar = 0
        caretPara = document.paragraphs.lastIndex.coerceAtLeast(0)
        caretChar = document.paragraphs.last().text.length
        hasSel = caretOffset() > 0
        paint()
    }

    fun selectRange(from: Int, to: Int) {
        placeCaret(from)
        anchorPara = caretPara
        anchorChar = caretChar
        placeCaret(to)
        hasSel = from != to
        paint()
    }

    fun selectedRange(): IntRange? = if (hasSel) selStart() until selEnd() else null

    fun setAlignment(alignment: Align) {
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        if (indices.none { document.paragraphs[it].align != alignment }) return
        snapshot()
        indices.forEach { document.paragraphs[it].align = alignment }
        relayout()
    }

    fun insertPageBreak() {
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        snapshot()
        when {
            para.text.isEmpty() || caretChar <= 0 -> para.pageBreakBefore = true
            caretChar >= para.text.length -> {
                val next = Paragraph()
                next.pageBreakBefore = true
                document.insertParagraph(caretPara + 1, next)
                caretPara += 1
                caretChar = 0
            }
            else -> {
                val nextIndex = ParagraphFormatting.splitAtCaret(document, caretPara, caretChar)
                document.paragraphs[nextIndex].pageBreakBefore = true
                caretPara = nextIndex
                caretChar = 0
            }
        }
        clearSel()
        relayout()
    }

    fun toggleOrientation() {
        snapshot()
        document.applyPaper(document.paper, !document.landscape)
        relayout()
    }

    /** Сдвигает все поля страницы на полсантиметра. false — уже, true — шире. */
    fun nudgePageMargins(outward: Boolean) {
        val step = if (outward) 14.0 else -14.0
        fun moved(value: Double) = (value + step).coerceIn(28.0, 113.0)
        val left = moved(document.marginLeftPt)
        val right = moved(document.marginRightPt)
        val top = moved(document.marginTopPt)
        val bottom = moved(document.marginBottomPt)
        if (left == document.marginLeftPt && right == document.marginRightPt &&
            top == document.marginTopPt && bottom == document.marginBottomPt
        ) return
        snapshot()
        document.marginLeftPt = left
        document.marginRightPt = right
        document.marginTopPt = top
        document.marginBottomPt = bottom
        relayout()
    }

    fun changeIndent(more: Boolean) {
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        val paragraphs = indices.map { document.paragraphs[it] }
        if (paragraphs.isEmpty() || (!more && paragraphs.all { it.leftIndentPt <= 0.0 })) return
        snapshot()
        Guides.stepIndent(document, paragraphs, more, showGrid)
        relayout()
    }

    fun copy() {
        val from: Int
        val to: Int
        if (hasSel) {
            from = selStart()
            to = selEnd()
        } else {
            val para = document.paragraphs.getOrNull(caretPara) ?: return
            if (para.text.isEmpty()) return
            from = offsetOf(caretPara, 0)
            to = offsetOf(caretPara, para.text.length)
        }
        if (from >= to) return
        putRich(document.toPlainText().substring(from, to), ParagraphFormatting.clipParts(document, from, to))
    }

    fun copyAll() {
        putRich(plainText(), ParagraphFormatting.clipParts(document, 0, plainText().length))
    }

    fun cut() {
        if (hasSel) {
            copy()
            replaceRange(selStart(), selEnd(), "")
            return
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        if (para.text.isEmpty()) return
        copy()
        snapshot()
        para.setRuns(emptyList())
        caretChar = 0
        clearSel()
        relayout()
    }

    fun paste() {
        val clipboard = Clipboard.getSystemClipboard()
        val parts = (clipboard.getContent(richFormat) as? String)?.let { ParagraphFormatting.decodeClip(it) }
        if (!parts.isNullOrEmpty()) {
            snapshot()
            val at = if (hasSel) selStart() else caretOffset()
            val end = if (hasSel) selEnd() else at
            document.replaceParts(at, end, parts)
            val inserted = parts.sumOf { runs -> runs.sumOf { it.text.length } } + (parts.size - 1).coerceAtLeast(0)
            placeCaret(at + inserted)
            clearSel()
            relayout()
            return
        }
        val clip = clipboard.string ?: return
        val clean = clip.replace("\r\n", "\n").replace('\r', '\n')
        if (clean.isEmpty()) return
        val para = document.paragraphs.getOrNull(caretPara)
        val style = para?.let { typingRun(it) ?: ParagraphFormatting.styleAt(it, caretChar) }
        snapshot()
        val at = if (hasSel) selStart() else caretOffset()
        val end = if (hasSel) selEnd() else at
        if (style == null) document.replaceRange(at, end, clean)
        else document.replaceParts(at, end, clean.split('\n').map { line ->
            if (line.isEmpty()) emptyList() else listOf(style.copy(text = line))
        })
        placeCaret(at + clean.length)
        clearSel()
        relayout()
    }

    private fun snapshot() {
        undo.addLast(document.copy())
        if (undo.size > 80) undo.removeFirst()
        redo.clear()
    }

    private fun restore(source: Document) {
        document.loadBlocks(source.blocks)
        document.applyPaper(source.paper, source.landscape)
        document.pageWidthPt = source.pageWidthPt
        document.pageHeightPt = source.pageHeightPt
        document.marginPt = source.marginPt
        document.marginLeftPt = source.marginLeftPt
        document.marginRightPt = source.marginRightPt
        document.marginTopPt = source.marginTopPt
        document.marginBottomPt = source.marginBottomPt
    }

    private fun putRich(text: String, parts: List<List<TextRun>>) {
        val content = ClipboardContent()
        content.putString(text)
        content.putHtml(ParagraphFormatting.toClipboardHtml(parts))
        content.put(richFormat, ParagraphFormatting.encodeClip(parts))
        Clipboard.getSystemClipboard().setContent(content)
    }

    fun setGuides(rulerOn: Boolean, gridOn: Boolean) {
        showRuler = rulerOn
        showGrid = gridOn
        paint()
    }

    fun setFanficMode(on: Boolean) {
        if (fanficMode == on) return
        fanficMode = on
        relayout(false)
    }

    fun relayout(notify: Boolean = true) {
        if (layingOut) return
        layingOut = true
        try {
            layout = Layout.build(if (fanficMode) fanficLayoutDocument() else document)
            val metrics = pageMetrics()
            val docW = metrics.logicalWidth * zoom
            val docH = metrics.documentHeight * zoom
            sheet.minWidth = docW
            sheet.prefWidth = docW
            sheet.minHeight = docH
            sheet.prefHeight = docH
            // Commit the tall sheet before scrolling, or ScrollPane clamps vvalue back to 0.
            super.layout()
            ensureCaretVisible()
            positionCanvas()
            if (notify) onCaretMoved?.invoke(caretPara)
            if (notify) publishStyle()
            if (notify) onChange?.invoke()
        } finally {
            layingOut = false
        }
    }

    private fun documentX(localX: Double) = (canvas.layoutX + localX) / zoom

    private fun documentY(localY: Double) = (canvas.layoutY + localY) / zoom

    private data class PageMetrics(val extra: Double, val gap: Double, val logicalWidth: Double, val documentHeight: Double)

    private fun pageMetrics(): PageMetrics {
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val gap = if (showRuler && !fanficMode) ruler + 12.0 else 20.0
        val logicalWidth = layout.pageWidth + extra + 48.0
        val documentHeight = extra + layout.pages.size * (layout.pageHeight + gap) + 24.0
        return PageMetrics(extra, gap, logicalWidth, documentHeight)
    }

    /** Drawing surface stays viewport-sized. The sheet keeps the full scrollable height. */
    private fun positionCanvas() {
        val viewW = viewportBounds.width.coerceAtLeast(1.0)
        val viewH = viewportBounds.height.coerceAtLeast(1.0)
        canvas.width = viewW
        canvas.height = viewH
        val maxX = (sheetWidth() - viewW).coerceAtLeast(0.0)
        val maxY = (sheetHeight() - viewH).coerceAtLeast(0.0)
        canvas.layoutX = if (maxX == 0.0) 0.0 else hvalue * maxX
        canvas.layoutY = if (maxY == 0.0) 0.0 else vvalue * maxY
        paint()
    }

    private fun sheetWidth(): Double =
        maxOf(sheet.layoutBounds.width, sheet.prefWidth, viewportBounds.width).coerceAtLeast(1.0)

    private fun sheetHeight(): Double =
        maxOf(sheet.layoutBounds.height, sheet.prefHeight).coerceAtLeast(1.0)

    private fun fanficLayoutDocument(): Document = document.copy().apply {
        pageWidthPt = 720.0
        pageHeightPt = 4000.0
        marginLeftPt = 28.0
        marginRightPt = 28.0
        marginTopPt = 32.0
        marginBottomPt = 48.0
        paragraphs.forEach { paragraph ->
            paragraph.fontFamily = "System"
            paragraph.fontSize = 17.0
            paragraph.leftIndentPt = 0.0
            paragraph.rightIndentPt = 0.0
            paragraph.firstLineIndentPt = if (paragraph.align == Align.CENTER) 0.0 else 34.0
            paragraph.spacingBeforePt = 0.0
            paragraph.spacingAfterPt = 17.0
            paragraph.lineSpacing = 1.3
            paragraph.pageBreakBefore = false
            paragraph.setRuns(paragraph.runs.map { it.copy(fontFamily = "System", fontSize = 17.0) })
        }
    }

    private fun ensureCaretVisible() {
        val hit = caretHit() ?: return
        val viewW = viewportBounds.width.takeIf { it > 1.0 } ?: return
        val viewH = viewportBounds.height.takeIf { it > 1.0 } ?: return
        val totalY = (sheetHeight() - viewH).coerceAtLeast(0.0)
        if (totalY > 0.0) {
            val top = vvalue * totalY
            val y = hit.y * zoom
            val margin = 32.0
            val next = when {
                y < top + margin -> y - margin
                y + hit.h * zoom > top + viewH - margin -> y + hit.h * zoom - viewH + margin
                else -> null
            }
            if (next != null) vvalue = (next / totalY).coerceIn(0.0, 1.0)
        }
        val totalX = (sheetWidth() - viewW).coerceAtLeast(0.0)
        if (totalX > 0.0) {
            val left = hvalue * totalX
            val x = hit.x * zoom
            val margin = 24.0
            val next = when {
                x < left + margin -> x - margin
                x > left + viewW - margin -> x - viewW + margin
                else -> null
            }
            if (next != null) hvalue = (next / totalX).coerceIn(0.0, 1.0)
        }
    }

    private fun pageOx(): Double {
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val logical = sheetWidth() / zoom
        return extra + ((logical - extra - layout.pageWidth) / 2.0).coerceAtLeast(12.0)
    }

    private fun pageOy(index: Int): Double {
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val gap = if (showRuler && !fanficMode) ruler + 12.0 else 20.0
        return extra + if (fanficMode) index * (layout.pageHeight + gap)
            else 12.0 + index * (layout.pageHeight + gap)
    }

    private fun paint() {
        syncCaretBlink()
        val g = canvas.graphicsContext2D
        // Translucent desks must not accumulate paint from previous frames.
        g.clearRect(0.0, 0.0, canvas.width, canvas.height)
        g.fill = desk
        g.fillRect(0.0, 0.0, canvas.width, canvas.height)
        g.save()
        g.translate(-canvas.layoutX, -canvas.layoutY)
        g.scale(zoom, zoom)
        val ox = pageOx()
        val selA = if (hasSel) selStart() else -1
        val selB = if (hasSel) selEnd() else -1
        val visibleTop = canvas.layoutY / zoom
        val visibleBottom = visibleTop + canvas.height / zoom
        layout.pages.forEach { page ->
            val oy = pageOy(page.index)
            if (oy + layout.pageHeight < visibleTop || oy > visibleBottom) return@forEach
            if (!fanficMode) {
                if (showRuler) drawRulers(g, ox, oy)
                g.fill = Color.WHITE
                g.fillRect(ox, oy, layout.pageWidth, layout.pageHeight)
                if (showGrid) drawGrid(g, ox, oy)
                g.stroke = Color.gray(0.7)
                g.lineWidth = 1.0
                g.strokeRect(ox, oy, layout.pageWidth, layout.pageHeight)
            } else {
                g.fill = pageFill
                g.fillRect(ox, oy, layout.pageWidth, layout.pageHeight)
            }
            page.lines.forEach { line ->
                if (selA >= 0) drawSel(g, ox, oy, line, selA, selB)
                if (fanficMode && line.text.isEmpty()) {
                    val mark = "✦ ✦ ✦"
                    val markFont = fontOf("System", 14.0, false)
                    g.font = markFont
                    g.fill = Color.gray(0.55)
                    val w = measure(mark, markFont)
                    g.fillText(mark, ox + (layout.pageWidth - w) / 2.0, oy + line.y + line.height * 0.85)
                    return@forEach
                }
                val baseline = oy + line.y + line.height * 0.85
                Layout.spans(line).forEach { span ->
                    if (span.text.isEmpty() || span.text == "\t") return@forEach
                    val runFont = Font.font(
                        span.fontFamily,
                        if (span.bold) FontWeight.BOLD else FontWeight.NORMAL,
                        if (span.italic) FontPosture.ITALIC else FontPosture.REGULAR,
                        span.fontSize
                    )
                    g.font = runFont
                    g.fill = span.color?.let { runCatching { Color.web(it) }.getOrNull() } ?: textFill
                    g.fillText(span.text, ox + span.x, baseline)
                    val glyph = measure(span.text, runFont)
                    if (span.underline) {
                        g.stroke = g.fill as Color
                        g.strokeLine(ox + span.x, baseline + 2, ox + span.x + glyph, baseline + 2)
                    }
                    if (span.strikethrough) {
                        g.stroke = g.fill as Color
                        g.strokeLine(ox + span.x, baseline - runFont.size * 0.3, ox + span.x + glyph, baseline - runFont.size * 0.3)
                    }
                }
            }
            page.objects.forEach { item ->
                when (val block = item.block) {
                    is TableBlock -> {
                        val columns = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                        val rowHeight = item.height / block.rows.size.coerceAtLeast(1)
                        val cellWidth = item.width / columns
                        g.stroke = Color.GRAY
                        g.fill = Color.rgb(20, 20, 20)
                        g.font = Font.font("Segoe UI", 12.0)
                        block.rows.forEachIndexed { row, cells ->
                            cells.forEachIndexed { col, cell ->
                                val x = ox + item.x + col * cellWidth
                                val y = oy + item.y + row * rowHeight
                                g.strokeRect(x, y, cellWidth, rowHeight)
                                var baseline = y + 17
                                cell.paragraphs.forEach { paragraph ->
                                    val words = paragraph.text.split(' ')
                                    var line = ""
                                    words.forEach { word ->
                                        val candidate = if (line.isEmpty()) word else "$line $word"
                                        if (line.isNotEmpty() && measure(candidate, g.font) > cellWidth - 10) {
                                            if (baseline < y + rowHeight - 3) g.fillText(line, x + 5, baseline)
                                            baseline += 18
                                            line = word
                                        } else line = candidate
                                    }
                                    if (line.isNotEmpty() && baseline < y + rowHeight - 3) g.fillText(line, x + 5, baseline)
                                    baseline += 18
                                }
                            }
                        }
                    }
                    is ImageBlock -> {
                        val image = imageCache.getOrPut(block) { Image(ByteArrayInputStream(block.bytes)) }
                        if (!image.isError) g.drawImage(image, ox + item.x, oy + item.y, item.width, item.height)
                    }
                    is Paragraph -> Unit
                }
            }
        }
        if (guideDrag != Guides.Handle.NONE) {
            val guideY = pageOy(guidePage)
            g.stroke = Color.rgb(28, 105, 190, 0.8)
            g.lineWidth = 1.0
            g.setLineDashes(4.0, 3.0)
            if (Guides.isHorizontal(guideDrag)) {
                g.strokeLine(ox + guidePosition, guideY, ox + guidePosition, guideY + layout.pageHeight)
            } else {
                g.strokeLine(ox, guideY + guidePosition, ox + layout.pageWidth, guideY + guidePosition)
            }
            g.setLineDashes()
        }
        drawCaret(g)
        g.restore()
    }

    private fun drawSel(
        g: GraphicsContext,
        ox: Double,
        oy: Double,
        line: LaidLine,
        selA: Int,
        selB: Int
    ) {
        val start = lineStartOffset(line)
        val end = start + line.text.length
        if (selB <= start || selA >= end) return
        g.fill = Color.rgb(160, 205, 255, 0.55)
        val spans = Layout.spans(line)
        if (spans.isEmpty()) {
            if (selA <= start && selB >= end) {
                g.fillRect(ox + line.x, oy + line.y, 3.0, line.height)
            }
            return
        }
        spans.forEach { span ->
            val spanStart = start + span.startInLine
            val spanEnd = spanStart + span.text.length
            val a = max(selA, spanStart)
            val b = min(selB, spanEnd)
            if (a >= b) return@forEach
            val font = Font.font(
                span.fontFamily,
                if (span.bold) FontWeight.BOLD else FontWeight.NORMAL,
                if (span.italic) FontPosture.ITALIC else FontPosture.REGULAR,
                span.fontSize
            )
            val localA = a - spanStart
            val localB = b - spanStart
            val x0 = if (span.text == "\t" || span.text.all { it == ' ' }) span.width * localA / span.text.length
                else measure(span.text.take(localA), font)
            val x1 = if (span.text == "\t" || span.text.all { it == ' ' }) span.width * localB / span.text.length
                else measure(span.text.take(localB), font)
            g.fillRect(ox + span.x + x0, oy + line.y, (x1 - x0).coerceAtLeast(2.0), line.height)
        }
    }

    private fun lineStartOffset(line: LaidLine): Int {
        var off = 0
        document.paragraphs.forEachIndexed { i, p ->
            if (i < line.paragraphIndex) off += p.text.length + 1
        }
        return off + line.startInParagraph
    }

    private fun drawGrid(g: GraphicsContext, ox: Double, oy: Double) {
        val step = Guides.gridStepCm * Guides.ptPerCm
        g.stroke = Color.rgb(220, 228, 235)
        g.lineWidth = 0.6
        var x = ox + step
        while (x < ox + layout.pageWidth - 0.5) {
            g.strokeLine(x, oy, x, oy + layout.pageHeight)
            x += step
        }
        var y = oy + step
        while (y < oy + layout.pageHeight - 0.5) {
            g.strokeLine(ox, y, ox + layout.pageWidth, y)
            y += step
        }
        g.stroke = Color.rgb(198, 210, 220)
        val step5 = Guides.ptPerCm
        x = ox + step5
        while (x < ox + layout.pageWidth - 0.5) {
            g.strokeLine(x, oy, x, oy + layout.pageHeight)
            x += step5
        }
        y = oy + step5
        while (y < oy + layout.pageHeight - 0.5) {
            g.strokeLine(ox, y, ox + layout.pageWidth, y)
            y += step5
        }
    }

    private fun beginGuideDrag(x: Double, y: Double): Boolean {
        if (!showRuler || fanficMode || layout.pages.isEmpty()) return false
        val page = layout.pages.firstOrNull { index ->
            val top = pageOy(index.index)
            x >= pageOx() - ruler - Guides.hitSlop && x <= pageOx() + layout.pageWidth + Guides.hitSlop &&
                y >= top - ruler - Guides.hitSlop && y <= top + layout.pageHeight + Guides.hitSlop
        } ?: return false
        val ox = pageOx()
        val oy = pageOy(page.index)
        val paragraph = document.paragraphs.getOrNull(caretPara)
        val handle = Guides.hit(x, y, ox, oy, layout.pageWidth, layout.pageHeight, Guides.marks(document, paragraph))
        if (handle == Guides.Handle.NONE) return false
        guideDrag = handle
        guidePage = page.index
        guidePosition = if (Guides.isHorizontal(handle)) x - ox else y - oy
        guideSnapshotTaken = false
        canvas.cursor = if (Guides.isVertical(handle)) Cursor.V_RESIZE else Cursor.H_RESIZE
        return true
    }

    private fun dragGuide(x: Double, y: Double) {
        val ox = pageOx()
        val oy = pageOy(guidePage)
        val pageX = (x - ox).coerceIn(0.0, layout.pageWidth)
        val pageY = (y - oy).coerceIn(0.0, layout.pageHeight)
        val paragraph = document.paragraphs.getOrNull(caretPara)
        val selected = if (hasSel) {
            val from = selStart()
            val to = selEnd()
            document.paragraphs.mapIndexedNotNull { index, item ->
                val start = offsetOf(index, 0)
                val end = start + item.text.length
                item.takeIf { from < end && to > start }
            }
        } else listOfNotNull(paragraph)
        if (!guideSnapshotTaken) {
            snapshot()
            guideSnapshotTaken = true
        }
        Guides.apply(guideDrag, pageX, pageY, document, selected, showGrid)
        guidePosition = if (Guides.isHorizontal(guideDrag)) pageX else pageY
        relayout()
    }

    private fun guideCursor(x: Double, y: Double): Cursor {
        val handle = guideHandle(x, y)
        return when {
            Guides.isVertical(handle) -> Cursor.V_RESIZE
            Guides.isHorizontal(handle) -> Cursor.H_RESIZE
            overPage(x, y) -> Cursor.TEXT
            else -> Cursor.DEFAULT
        }
    }

    private fun overPage(x: Double, y: Double): Boolean {
        val ox = pageOx()
        return layout.pages.any { page ->
            val oy = pageOy(page.index)
            x in ox..(ox + layout.pageWidth) && y in oy..(oy + layout.pageHeight)
        }
    }

    private fun guideHandle(x: Double, y: Double): Guides.Handle {
        if (!showRuler || fanficMode || layout.pages.isEmpty()) return Guides.Handle.NONE
        val page = layout.pages.firstOrNull { index ->
            val top = pageOy(index.index)
            x >= pageOx() - ruler - Guides.hitSlop && x <= pageOx() + layout.pageWidth + Guides.hitSlop &&
                y >= top - ruler - Guides.hitSlop && y <= top + layout.pageHeight + Guides.hitSlop
        } ?: return Guides.Handle.NONE
        return Guides.hit(
            x, y, pageOx(), pageOy(page.index), layout.pageWidth, layout.pageHeight,
            Guides.marks(document, document.paragraphs.getOrNull(caretPara))
        )
    }

    private fun guideDescription(handle: Guides.Handle): String {
        val marks = Guides.marks(document, document.paragraphs.getOrNull(caretPara))
        val (label, value) = when (handle) {
            Guides.Handle.LEFT_MARGIN -> "Левое поле" to marks.leftMargin
            Guides.Handle.RIGHT_MARGIN -> "Правое поле" to (document.pageWidthPt - marks.rightMargin)
            Guides.Handle.TOP_MARGIN -> "Верхнее поле" to marks.topMargin
            Guides.Handle.BOTTOM_MARGIN -> "Нижнее поле" to (document.pageHeightPt - marks.bottomMargin)
            Guides.Handle.LEFT_INDENT -> "Отступ слева" to (marks.leftIndent - marks.leftMargin)
            Guides.Handle.FIRST_LINE -> "Первая строка" to (marks.firstLine - marks.leftIndent)
            Guides.Handle.RIGHT_INDENT -> "Отступ справа" to (marks.rightMargin - marks.rightIndent)
            Guides.Handle.NONE -> return ""
        }
        return "$label: ${"%.2f".format(java.util.Locale.ROOT, value / Guides.ptPerCm)} см"
    }

    private fun drawRulers(g: GraphicsContext, ox: Double, oy: Double) {
        val marks = Guides.marks(document, document.paragraphs.getOrNull(caretPara))
        g.fill = Color.rgb(236, 236, 236)
        g.fillRect(ox, oy - ruler, layout.pageWidth, ruler)
        g.fillRect(ox - ruler, oy, ruler, layout.pageHeight)
        g.fill = Color.rgb(206, 211, 217)
        g.fillRect(ox, oy - ruler, marks.leftMargin, ruler)
        g.fillRect(ox + marks.rightMargin, oy - ruler, layout.pageWidth - marks.rightMargin, ruler)
        g.fillRect(ox - ruler, oy, ruler, marks.topMargin)
        g.fillRect(ox - ruler, oy + marks.bottomMargin, ruler, layout.pageHeight - marks.bottomMargin)
        g.fill = Color.rgb(210, 210, 210)
        g.fillRect(ox - ruler, oy - ruler, ruler, ruler)
        g.stroke = Color.gray(0.55)
        g.strokeRect(ox, oy - ruler, layout.pageWidth, ruler)
        g.strokeRect(ox - ruler, oy, ruler, layout.pageHeight)
        g.fill = Color.rgb(40, 40, 40)
        g.font = Font.font("Segoe UI", 9.0)
        var mm = 0
        while (mm * ptPerMm <= layout.pageWidth + 0.1) {
            val x = ox + mm * ptPerMm
            val major = mm % 10 == 0
            val mid = mm % 5 == 0
            val h = when {
                major -> 10.0
                mid -> 7.0
                else -> 4.0
            }
            g.stroke = Color.gray(0.35)
            g.strokeLine(x, oy - 1, x, oy - 1 - h)
            if (major && mm > 0) g.fillText((mm / 10).toString(), x + 2, oy - ruler + 11)
            mm++
        }
        mm = 0
        while (mm * ptPerMm <= layout.pageHeight + 0.1) {
            val y = oy + mm * ptPerMm
            val major = mm % 10 == 0
            val mid = mm % 5 == 0
            val w = when {
                major -> 10.0
                mid -> 7.0
                else -> 4.0
            }
            g.stroke = Color.gray(0.35)
            g.strokeLine(ox - 1, y, ox - 1 - w, y)
            if (major && mm > 0) g.fillText((mm / 10).toString(), ox - ruler + 3, y + 10)
            mm++
        }
        g.fill = Color.rgb(35, 105, 185)
        fun marginMarker(x: Double) {
            g.fillRoundRect(ox + x - 4.0, oy - ruler + 1.0, 8.0, 6.0, 2.0, 2.0)
        }
        marginMarker(marks.leftMargin)
        marginMarker(marks.rightMargin)
        g.fillPolygon(
            doubleArrayOf(ox + marks.firstLine - 5, ox + marks.firstLine + 5, ox + marks.firstLine),
            doubleArrayOf(oy - ruler + 8, oy - ruler + 8, oy - ruler + 15), 3
        )
        g.fillPolygon(
            doubleArrayOf(ox + marks.leftIndent - 5, ox + marks.leftIndent + 5, ox + marks.leftIndent),
            doubleArrayOf(oy - 1, oy - 1, oy - 9), 3
        )
        g.fillPolygon(
            doubleArrayOf(ox + marks.rightIndent - 5, ox + marks.rightIndent + 5, ox + marks.rightIndent),
            doubleArrayOf(oy - 1, oy - 1, oy - 9), 3
        )
        g.fillRoundRect(ox - ruler + 1.0, oy + marks.topMargin - 4.0, 7.0, 8.0, 2.0, 2.0)
        g.fillRoundRect(ox - ruler + 1.0, oy + marks.bottomMargin - 4.0, 7.0, 8.0, 2.0, 2.0)
    }

    private fun installCaretBlink() {
        caretBlink.stop()
        caretBlink.keyFrames.clear()
        if (caretPeriodMs <= 0) return
        caretBlink.keyFrames.add(KeyFrame(Duration.millis(caretPeriodMs), EventHandler {
            caretOn = !caretOn
            paint()
        }))
    }

    private fun editorFocused() = isFocused || canvas.isFocused

    private fun armCaret() {
        caretBlinkKey = ""
    }

    private fun syncCaretBlink() {
        val key = if (!editorFocused() || hasSel) "hide" else "$caretPara:$caretChar"
        if (key == caretBlinkKey) return
        caretBlinkKey = key
        if (key == "hide" || caretPeriodMs < 0) {
            caretBlink.stop()
            caretOn = key != "hide"
            return
        }
        caretOn = true
        caretBlink.playFromStart()
    }

    private fun drawCaret(g: GraphicsContext) {
        if (!caretOn || hasSel || !editorFocused()) return
        val hit = caretHit() ?: return
        g.stroke = caretColor
        g.lineWidth = 2.0
        val x = kotlin.math.floor(hit.x) + 0.5
        g.strokeLine(x, hit.y + 1.0, x, hit.y + hit.h - 1.0)
    }

    private data class Hit(val x: Double, val y: Double, val h: Double)

    private fun caretHit(): Hit? {
        val lines = layout.pages.flatMap { page ->
            page.lines.filter { it.paragraphIndex == caretPara }.map { page.index to it }
        }
        val choice = lines.lastOrNull { (_, line) ->
            caretChar >= line.startInParagraph && caretChar <= line.startInParagraph + line.text.length
        } ?: return null
        val local = (caretChar - choice.second.startInParagraph).coerceIn(0, choice.second.text.length)
        return Hit(pageOx() + Layout.prefixX(choice.second, local), pageOy(choice.first) + choice.second.y, choice.second.height)
    }

    private fun pickCaret(x: Double, y: Double) {
        val ox = pageOx()
        var found: LaidLine? = null
        layout.pages.forEach { page ->
            val oy = pageOy(page.index)
            page.lines.forEach { line ->
                val top = oy + line.y
                val left = ox + line.x
                if (y >= top && y <= top + line.height && x >= left - 8 && x <= left + line.width + 48) {
                    found = line
                }
            }
        }
        val line = found
        if (line == null) {
            caretPara = document.paragraphs.lastIndex.coerceAtLeast(0)
            caretChar = document.paragraphs.getOrNull(caretPara)?.text?.length ?: 0
            return
        }
        caretPara = line.paragraphIndex
        val para = document.paragraphs[caretPara]
        val localX = x - ox - line.x
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0..line.text.length) {
            val d = abs(Layout.prefixX(line, i) - line.x - localX)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        caretChar = (line.startInParagraph + best).coerceIn(0, para.text.length)
    }

    private fun paintFormatBrush() {
        val brush = formatBrush ?: return
        val from: Int
        val to: Int
        if (hasSel) {
            from = selStart()
            to = selEnd()
        } else {
            val para = document.paragraphs.getOrNull(caretPara)
            val bounds = para?.let {
                ParagraphFormatting.wordBounds(it.text, caretChar.coerceAtMost((it.text.length - 1).coerceAtLeast(0)))
            }
            if (para == null || para.text.isEmpty() || bounds == null || bounds.isEmpty()) {
                from = caretOffset()
                to = from
            } else {
                from = offsetOf(caretPara, bounds.first)
                to = offsetOf(caretPara, bounds.last + 1)
            }
        }
        snapshot()
        if (from < to) {
            ParagraphFormatting.mapRange(document, from, to) { run ->
                run.copy(
                    fontFamily = brush.run.fontFamily,
                    fontSize = brush.run.fontSize,
                    bold = brush.run.bold,
                    italic = brush.run.italic,
                    underline = brush.run.underline,
                    strikethrough = brush.run.strikethrough,
                    color = brush.run.color
                )
            }
        }
        var offset = 0
        document.paragraphs.forEachIndexed { index, paragraph ->
            val start = offset
            val end = offset + paragraph.text.length
            val wholeParagraph = from <= start && to >= end && end > start
            val emptyAtCaret = paragraph.text.isEmpty() && index == caretPara
            if (wholeParagraph || emptyAtCaret) {
                paragraph.align = brush.align
                paragraph.leftIndentPt = brush.leftIndentPt
                paragraph.rightIndentPt = brush.rightIndentPt
                paragraph.firstLineIndentPt = brush.firstLineIndentPt
                paragraph.spacingBeforePt = brush.spacingBeforePt
                paragraph.spacingAfterPt = brush.spacingAfterPt
                paragraph.lineSpacing = brush.lineSpacing
                paragraph.outlineLevel = brush.outlineLevel
                brush.run.fontSize?.let { paragraph.fontSize = it }
                paragraph.bold = brush.run.bold
            }
            offset = end + 1
        }
        formatBrush = null
        onFormatBrushChanged?.invoke(false)
        relayout()
    }

    private fun onKey(e: KeyEvent) {
        if (e.code == KeyCode.ESCAPE && formatBrush != null) {
            cancelFormatBrush()
            e.consume()
            return
        }
        armCaret()
        if (e.code == KeyCode.SPACE && !scrollWithSpace) {
            // ScrollPane treats Space as a page-scroll command. Keep it as text input.
            e.consume()
            return
        }
        if (e.isShortcutDown) {
            when (e.code) {
                KeyCode.C -> { copy(); e.consume(); return }
                KeyCode.X -> { cut(); e.consume(); return }
                KeyCode.V -> { paste(); e.consume(); return }
                KeyCode.B -> { toggleRunStyle(bold = true); e.consume(); return }
                KeyCode.I -> { toggleRunStyle(italic = true); e.consume(); return }
                KeyCode.U -> { toggleRunStyle(underline = true); e.consume(); return }
                KeyCode.A -> {
                    anchorPara = 0
                    anchorChar = 0
                    caretPara = document.paragraphs.lastIndex.coerceAtLeast(0)
                    caretChar = document.paragraphs.last().text.length
                    hasSel = plainText().isNotEmpty()
                    paint()
                    e.consume(); return
                }
                KeyCode.Z -> { undo(); e.consume(); return }
                KeyCode.Y -> { redo(); e.consume(); return }
                KeyCode.PLUS, KeyCode.ADD, KeyCode.EQUALS -> { zoomBy(0.1); e.consume(); return }
                KeyCode.MINUS, KeyCode.SUBTRACT -> { zoomBy(-0.1); e.consume(); return }
                KeyCode.DIGIT0, KeyCode.NUMPAD0 -> { zoomReset(); e.consume(); return }
                else -> Unit
            }
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        when (e.code) {
            KeyCode.TAB -> {
                e.consume()
                if (e.isShiftDown) {
                    changeIndent(false)
                    return
                }
                // Word: Tab at the start or over a selection indents the paragraph.
                // In the middle of a line it inserts a tab stop, as in OpenOffice.
                if (hasSel || caretChar == 0) {
                    changeIndent(true)
                    return
                }
                insertCharacters("\t")
            }
            KeyCode.BACK_SPACE -> {
                deleteBackward()
                e.consume()
            }
            KeyCode.DELETE -> {
                deleteForward()
                e.consume()
            }
            KeyCode.ENTER -> {
                splitParagraph()
                e.consume()
            }
            KeyCode.LEFT -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = true) {
                if (e.isShortcutDown) {
                    if (caretChar == 0 && caretPara > 0) {
                        caretPara--
                        caretChar = document.paragraphs[caretPara].text.length
                    } else caretChar = ParagraphFormatting.moveWord(para.text, caretChar, false)
                } else if (caretChar > 0) caretChar--
                else if (caretPara > 0) {
                    caretPara--
                    caretChar = document.paragraphs[caretPara].text.length
                }
            }
            KeyCode.RIGHT -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = false) {
                if (e.isShortcutDown) {
                    if (caretChar >= para.text.length && caretPara < document.paragraphs.lastIndex) {
                        caretPara++
                        caretChar = 0
                    } else caretChar = ParagraphFormatting.moveWord(para.text, caretChar, true)
                } else if (caretChar < para.text.length) caretChar++
                else if (caretPara < document.paragraphs.lastIndex) {
                    caretPara++
                    caretChar = 0
                }
            }
            KeyCode.HOME -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = true) {
                if (e.isShortcutDown) {
                    caretPara = 0
                    caretChar = 0
                } else caretChar = currentLine()?.startInParagraph ?: 0
            }
            KeyCode.END -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = false) {
                if (e.isShortcutDown) {
                    caretPara = document.paragraphs.lastIndex.coerceAtLeast(0)
                    caretChar = document.paragraphs.lastOrNull()?.text?.length ?: 0
                } else {
                    val line = currentLine()
                    val host = document.paragraphs.getOrNull(caretPara)
                    caretChar = if (line == null || host == null) host?.text?.length ?: 0
                    else (line.startInParagraph + line.text.length).coerceAtMost(host.text.length)
                }
            }
            KeyCode.UP -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = true) { moveVertical(false) }
            KeyCode.DOWN -> moveHorizontal(e, extend = e.isShiftDown, collapseToStart = false) { moveVertical(true) }
            else -> Unit
        }
    }

    private fun onType(e: KeyEvent) {
        armCaret()
        if (e.isShortcutDown) return
        val ch = e.character
        if (ch.isEmpty() || ch[0] < ' ' || ch == "\u007F") return
        insertCharacters(ch)
        e.consume()
    }

    private fun insertCharacters(text: String) {
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        val style = typingRun(para)
        stickX = null
        snapshot()
        if (hasSel) {
            val at = selStart()
            document.replaceRange(at, selEnd(), "")
            placeCaret(at)
            clearSel()
        }
        val target = document.paragraphs.getOrNull(caretPara) ?: return
        ParagraphFormatting.insertText(target, caretChar, text, style)
        caretChar += text.length
        relayout()
    }

    private fun deleteBackward() {
        if (hasSel) {
            replaceRange(selStart(), selEnd(), "")
            return
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        if (caretChar == 0 && para.firstLineIndentPt > 0.4) {
            snapshot()
            val step = Guides.gridStepCm * Guides.ptPerCm
            para.firstLineIndentPt = (para.firstLineIndentPt - step).coerceAtLeast(0.0)
            relayout()
            return
        }
        if (caretChar == 0 && para.leftIndentPt > 0.4) {
            changeIndent(false)
            return
        }
        snapshot()
        if (caretChar > 0) {
            ParagraphFormatting.deleteSpan(para, caretChar - 1, caretChar)
            caretChar--
        } else if (caretPara > 0) {
            val prevLen = document.paragraphs[caretPara - 1].text.length
            ParagraphFormatting.mergeNext(document, caretPara - 1)
            caretPara--
            caretChar = prevLen
        }
        relayout()
    }

    private fun deleteForward() {
        if (hasSel) {
            replaceRange(selStart(), selEnd(), "")
            return
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        snapshot()
        if (caretChar < para.text.length) ParagraphFormatting.deleteSpan(para, caretChar, caretChar + 1)
        else if (caretPara < document.paragraphs.lastIndex) ParagraphFormatting.mergeNext(document, caretPara)
        relayout()
    }

    private fun splitParagraph() {
        snapshot()
        if (hasSel) {
            val at = selStart()
            document.replaceRange(at, selEnd(), "")
            placeCaret(at)
            clearSel()
        }
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        caretPara = ParagraphFormatting.splitAtCaret(document, paraIndexSafe(para), caretChar)
        caretChar = 0
        relayout()
    }

    private fun paraIndexSafe(para: Paragraph): Int {
        val index = document.paragraphs.indexOf(para)
        return if (index >= 0) index else caretPara
    }

    private fun moveHorizontal(e: KeyEvent, extend: Boolean, collapseToStart: Boolean, move: () -> Unit) {
        if (!extend && hasSel) {
            placeCaret(if (collapseToStart) selStart() else selEnd())
            clearSel()
            stickX = null
            clearTypingStyle()
            finishMove()
            e.consume()
            return
        }
        if (!extend) {
            clearSel()
            clearTypingStyle()
            if (e.code != KeyCode.UP && e.code != KeyCode.DOWN) stickX = null
        } else if (!hasSel) {
            anchorPara = caretPara
            anchorChar = caretChar
        }
        move()
        if (extend) hasSel = offsetOf(anchorPara, anchorChar) != caretOffset()
        finishMove()
        e.consume()
    }

    private fun finishMove() {
        ensureCaretVisible()
        paint()
        onCaretMoved?.invoke(caretPara)
        publishStyle()
    }

    private fun currentLine(): LaidLine? = layout.pages.asSequence()
        .flatMap { it.lines.asSequence() }
        .lastOrNull { line ->
            line.paragraphIndex == caretPara &&
                caretChar >= line.startInParagraph &&
                caretChar <= line.startInParagraph + line.text.length
        }

    private fun moveVertical(down: Boolean) {
        val lines = layout.pages.flatMap { it.lines }
        if (lines.isEmpty()) return
        val index = lines.indexOfLast { line ->
            line.paragraphIndex == caretPara &&
                caretChar >= line.startInParagraph &&
                caretChar <= line.startInParagraph + line.text.length
        }
        val x = stickX ?: (caretHit()?.x ?: pageOx())
        stickX = x
        val target = index + if (down) 1 else -1
        if (target !in lines.indices) return
        val line = lines[target]
        val localTarget = x - pageOx() - line.x
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0..line.text.length) {
            val dist = abs(Layout.prefixX(line, i) - line.x - localTarget)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        caretPara = line.paragraphIndex
        val host = document.paragraphs.getOrNull(caretPara) ?: return
        caretChar = (line.startInParagraph + best).coerceIn(0, host.text.length)
    }

    private fun typingRun(paragraph: Paragraph): TextRun? {
        if (pendingFamily == null && pendingSize == null && pendingBold == null &&
            pendingItalic == null && pendingUnderline == null && pendingStrike == null &&
            !pendingColorSet
        ) return null
        val base = ParagraphFormatting.styleAt(paragraph, caretChar)
        return base.copy(
            text = "",
            fontFamily = pendingFamily ?: base.fontFamily,
            fontSize = pendingSize ?: base.fontSize,
            bold = pendingBold ?: base.bold,
            italic = pendingItalic ?: base.italic,
            underline = pendingUnderline ?: base.underline,
            strikethrough = pendingStrike ?: base.strikethrough,
            color = if (pendingColorSet) pendingColor else base.color
        )
    }

    private fun clearTypingStyle() {
        pendingFamily = null
        pendingSize = null
        pendingBold = null
        pendingItalic = null
        pendingUnderline = null
        pendingStrike = null
        pendingColor = null
        pendingColorSet = false
    }

    fun caretPlace(): CaretPlace = Layout.caretPlace(layout, caretPara, caretChar)

    fun zoomPercent(): Int = (zoom * 100).roundToInt()

    fun syncCaret() = publishStyle()

    private fun publishStyle() {
        val para = document.paragraphs.getOrNull(caretPara)
        val style = para?.let { ParagraphFormatting.styleAt(it, caretChar) }
        val (family, size) = typingFont()
        val selected = if (hasSel) ParagraphFormatting.clipParts(document, selStart(), selEnd()).flatten() else emptyList()
        val bold = if (selected.isNotEmpty()) selected.all { it.bold } else pendingBold ?: (style?.bold == true || para?.bold == true)
        val italic = if (selected.isNotEmpty()) selected.all { it.italic } else pendingItalic ?: (style?.italic == true)
        val underline = if (selected.isNotEmpty()) selected.all { it.underline } else pendingUnderline ?: (style?.underline == true)
        onStyleChanged?.invoke(family, size, para?.align ?: Align.LEFT, bold, italic, underline)
        onContextChanged?.invoke(hasSel, (para?.outlineLevel ?: 0) > 0)
    }

    private fun offsetOf(para: Int, ch: Int): Int {
        var off = 0
        document.paragraphs.forEachIndexed { i, p ->
            if (i < para) off += p.text.length + 1
            else return off + ch.coerceIn(0, p.text.length)
        }
        return off
    }

    private fun placeCaret(pos: Int) {
        var left = pos.coerceAtLeast(0)
        caretPara = 0
        caretChar = 0
        document.paragraphs.forEachIndexed { i, p ->
            if (left <= p.text.length) {
                caretPara = i
                caretChar = left
                return
            }
            left -= p.text.length + 1
        }
    }

    private fun selectedText(): String {
        val t = plainText()
        return t.substring(selStart().coerceIn(0, t.length), selEnd().coerceIn(0, t.length))
    }

    private fun selStart() = min(offsetOf(anchorPara, anchorChar), caretOffset())
    private fun selEnd() = max(offsetOf(anchorPara, anchorChar), caretOffset())
    private fun clearSel() { hasSel = false }

    private fun fontOf(family: String, size: Double, bold: Boolean): Font {
        val weight = if (bold) FontWeight.BOLD else FontWeight.NORMAL
        return Font.font(family, weight, size)
    }

    private fun measure(s: String, font: Font): Double {
        val node = Text(s)
        node.font = font
        return if (s.isEmpty()) 0.0 else node.layoutBounds.width
    }

    companion object {
        private val richFormat = DataFormat("application/x-g134office-runs")

        /** Windows caret blink interval. Negative means the system caret does not blink. */
        val systemCaretBlinkMs: Double by lazy { readSystemCaretBlinkMs() }

        private fun readSystemCaretBlinkMs(): Double {
            val os = System.getProperty("os.name").orEmpty()
            if (!os.contains("Windows", ignoreCase = true)) return 530.0
            return runCatching {
                val proc = ProcessBuilder(
                    "reg", "query", "HKCU\\Control Panel\\Desktop", "/v", "CursorBlinkRate"
                ).redirectErrorStream(true).start()
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    return 530.0
                }
                val text = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val raw = Regex("""CursorBlinkRate\s+REG_\w+\s+(-?\d+)""")
                    .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return 530.0
                if (raw < 0) -1.0 else raw.coerceIn(200, 2000).toDouble()
            }.getOrDefault(530.0)
        }
    }
}

enum class NavKind { HEADING, PAGE }

data class NavSpot(
    val title: String,
    val paragraph: Int,
    val charInParagraph: Int,
    val depth: Int,
    val kind: NavKind
)

private data class FormatBrush(
    val run: TextRun,
    val align: Align,
    val leftIndentPt: Double,
    val rightIndentPt: Double,
    val firstLineIndentPt: Double,
    val spacingBeforePt: Double,
    val spacingAfterPt: Double,
    val lineSpacing: Double,
    val outlineLevel: Int
)
