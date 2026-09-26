package org.example.engine

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
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
    var onZoomRequested: ((Double) -> Unit)? = null
    var showRuler: Boolean = false
    var showGrid: Boolean = false
    var scrollWithSpace: Boolean = false
    var fanficMode: Boolean = false
        private set
    var zoom: Double = 1.0
        private set

    private val canvas = Canvas(640.0, 900.0)
    private var layout: LayoutResult = Layout.build(document)
    private var caretPara = 0
    private var caretChar = 0
    private var anchorPara = 0
    private var anchorChar = 0
    private var hasSel = false
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

    init {
        content = canvas
        isFitToWidth = true
        padding = Insets(8.0)
        style = "-fx-background-color: #3a3a3a;"
        isFocusTraversable = true
        canvas.isFocusTraversable = true
        viewportBoundsProperty().addListener { _, _, _ -> relayout(notify = false) }
        vvalueProperty().addListener { _, _, _ -> paint() }
        setOnMouseClicked {
            requestFocus()
            canvas.requestFocus()
        }
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            requestFocus()
            canvas.requestFocus()
            if (beginGuideDrag(e.x / zoom, e.y / zoom)) {
                e.consume()
                return@addEventHandler
            }
            pickCaret(e.x / zoom, e.y / zoom)
            anchorPara = caretPara
            anchorChar = caretChar
            hasSel = false
            armCaret()
            paint()
            onCaretMoved?.invoke(caretPara)
        }
        canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            if (guideDrag != Guides.Handle.NONE) {
                dragGuide(e.x / zoom, e.y / zoom)
                e.consume()
                return@addEventHandler
            }
            pickCaret(e.x / zoom, e.y / zoom)
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
            }
        }
        canvas.addEventHandler(MouseEvent.MOUSE_MOVED) { e ->
            val x = e.x / zoom
            val y = e.y / zoom
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
    }

    fun zoomReset() {
        zoom = 1.0
        relayout(false)
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
        val from = if (hasSel) selStart() else offsetOf(caretPara, 0)
        val to = if (hasSel) selEnd() else {
            val para = document.paragraphs.getOrNull(caretPara) ?: return
            offsetOf(caretPara, para.text.length)
        }
        if (from >= to) return
        snapshot()
        var offset = 0
        document.paragraphs.forEach { paragraph ->
            val start = offset
            val end = offset + paragraph.text.length
            val a = from.coerceAtLeast(start)
            val b = to.coerceAtMost(end)
            if (a < b) {
                val localA = a - start
                val localB = b - start
                val source = paragraph.runs.ifEmpty { listOf(TextRun(paragraph.text)) }
                val sample = source.filter { it.text.isNotEmpty() }
                val allOn = when {
                    bold -> sample.all { it.bold || paragraph.bold }
                    italic -> sample.all { it.italic }
                    underline -> sample.all { it.underline }
                    strikethrough -> sample.all { it.strikethrough }
                    else -> false
                }
                val next = mutableListOf<TextRun>()
                var pos = 0
                source.forEach { run ->
                    val r0 = pos
                    val r1 = pos + run.text.length
                    pos = r1
                    if (r1 <= localA || r0 >= localB) {
                        if (run.text.isNotEmpty()) next += run
                    } else {
                        if (r0 < localA) next += run.copy(text = run.text.substring(0, localA - r0))
                        val midFrom = localA.coerceIn(r0, r1) - r0
                        val midTo = localB.coerceIn(r0, r1) - r0
                        if (midTo > midFrom) {
                            next += run.copy(
                                text = run.text.substring(midFrom, midTo),
                                bold = if (bold) !allOn else run.bold,
                                italic = if (italic) !allOn else run.italic,
                                underline = if (underline) !allOn else run.underline,
                                strikethrough = if (strikethrough) !allOn else run.strikethrough
                            )
                        }
                        if (r1 > localB) next += run.copy(text = run.text.substring(localB - r0))
                    }
                }
                paragraph.setRuns(next.filter { it.text.isNotEmpty() })
                if (bold) paragraph.bold = !allOn && localA == 0 && localB == paragraph.text.length
            }
            offset = end + 1
        }
        relayout()
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
        document.fromPlainText(text)
        caretPara = (document.paragraphs.size - 1).coerceAtLeast(0)
        caretChar = document.paragraphs.getOrNull(caretPara)?.text?.length ?: 0
        clearSel()
        relayout(false)
    }

    fun setZoom(value: Double) {
        zoom = value.coerceIn(0.5, 2.5)
        relayout(false)
    }

    fun loadDocument(source: Document) {
        undo.clear()
        redo.clear()
        imageCache.clear()
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
        relayout()
    }

    fun redo() {
        if (redo.isEmpty()) return
        undo.addLast(document.copy())
        restore(redo.removeLast())
        caretPara = 0
        caretChar = 0
        clearSel()
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

    fun changeIndent(more: Boolean) {
        val indices = ParagraphFormatting.affectedIndices(document, caretPara, selectedRange())
        val paragraphs = indices.map { document.paragraphs[it] }
        if (paragraphs.isEmpty() || (!more && paragraphs.all { it.leftIndentPt <= 0.0 })) return
        snapshot()
        Guides.stepIndent(document, paragraphs, more, showGrid)
        relayout()
    }

    fun copy() {
        val text = if (hasSel) selectedText() else {
            document.paragraphs.getOrNull(caretPara)?.text?.ifEmpty { plainText() } ?: ""
        }
        putClipboard(text)
    }

    fun copyAll() {
        putClipboard(plainText())
    }

    fun cut() {
        if (hasSel) {
            putClipboard(selectedText())
            replaceRange(selStart(), selEnd(), "")
            return
        }
        snapshot()
        val para = document.paragraphs.getOrNull(caretPara) ?: return
        putClipboard(para.text)
        para.text = ""
        caretChar = 0
        clearSel()
        relayout()
    }

    fun paste() {
        val clip = Clipboard.getSystemClipboard().string ?: return
        val clean = clip.replace("\r\n", "\n").replace('\r', '\n')
        if (hasSel) replaceRange(selStart(), selEnd(), clean)
        else replaceRange(caretOffset(), caretOffset(), clean)
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

    private fun putClipboard(text: String) {
        val content = ClipboardContent()
        content.putString(text)
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
        layout = Layout.build(if (fanficMode) fanficLayoutDocument() else document)
        val gap = if (showRuler && !fanficMode) ruler + 12.0 else 20.0
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val logicalW = layout.pageWidth + extra + 48.0
        val viewW = viewportBounds.width.coerceAtLeast(logicalW * zoom)
        canvas.width = viewW
        canvas.height = (extra + layout.pages.size * (layout.pageHeight + gap) + 24.0) * zoom
        paint()
        if (fanficMode) ensureCaretVisible()
        if (notify) onCaretMoved?.invoke(caretPara)
        if (notify) onChange?.invoke()
    }

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
        val total = (canvas.height - viewportBounds.height).coerceAtLeast(0.0)
        if (total == 0.0) return
        val top = vvalue * total
        val y = hit.y * zoom
        val margin = 32.0
        val next = when {
            y < top + margin -> y - margin
            y + hit.h * zoom > top + viewportBounds.height - margin ->
                y + hit.h * zoom - viewportBounds.height + margin
            else -> return
        }
        vvalue = (next / total).coerceIn(0.0, 1.0)
    }

    private fun pageOx(): Double {
        val extra = if (showRuler && !fanficMode) ruler else 0.0
        val logical = canvas.width / zoom
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
        g.fill = desk
        g.fillRect(0.0, 0.0, canvas.width, canvas.height)
        g.save()
        g.scale(zoom, zoom)
        val ox = pageOx()
        val selA = if (hasSel) selStart() else -1
        val selB = if (hasSel) selEnd() else -1
        val visibleTop = (vvalue * (canvas.height - viewportBounds.height).coerceAtLeast(0.0)) / zoom
        val visibleBottom = visibleTop + viewportBounds.height / zoom
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
                val font = fontOf(line.fontFamily, line.fontSize, line.bold)
                if (selA >= 0) drawSel(g, ox, oy, line, font, selA, selB)
                if (line.runs.isEmpty()) {
                    if (fanficMode && line.text.isEmpty()) {
                        val mark = "✦ ✦ ✦"
                        val markFont = fontOf("System", 14.0, false)
                        g.font = markFont
                        g.fill = Color.gray(0.55)
                        val w = measure(mark, markFont)
                        g.fillText(mark, ox + (layout.pageWidth - w) / 2.0, oy + line.y + line.height * 0.85)
                    } else {
                        g.font = font
                        g.fill = textFill
                        g.fillText(line.text, ox + line.x, oy + line.y + line.height * 0.85)
                    }
                } else {
                    var x = ox + line.x
                    val baseline = oy + line.y + line.height * 0.85
                    line.runs.forEach { run ->
                        val runFont = Font.font(run.fontFamily ?: line.fontFamily,
                            if (run.bold || line.bold) FontWeight.BOLD else FontWeight.NORMAL,
                            if (run.italic) FontPosture.ITALIC else FontPosture.REGULAR,
                            run.fontSize ?: line.fontSize)
                        g.font = runFont
                        g.fill = run.color?.let { runCatching { Color.web(it) }.getOrNull() } ?: textFill
                        g.fillText(run.text, x, baseline)
                        val width = measure(run.text, runFont)
                        if (run.underline) {
                            g.stroke = g.fill as Color
                            g.strokeLine(x, baseline + 2, x + width, baseline + 2)
                        }
                        if (run.strikethrough) {
                            g.stroke = g.fill as Color
                            g.strokeLine(x, baseline - runFont.size * 0.3, x + width, baseline - runFont.size * 0.3)
                        }
                        x += width
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
        font: Font,
        selA: Int,
        selB: Int
    ) {
        val start = lineStartOffset(line)
        val end = start + line.text.length
        val a = max(selA, start)
        val b = min(selB, end)
        if (a >= b) return
        val x0 = measure(line.text.take(a - start), font)
        val x1 = measure(line.text.take(b - start), font)
        g.fill = Color.rgb(160, 205, 255, 0.55)
        g.fillRect(ox + line.x + x0, oy + line.y, (x1 - x0).coerceAtLeast(3.0), line.height)
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
            else -> Cursor.DEFAULT
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
        val para = document.paragraphs.getOrNull(caretPara) ?: return null
        val font = if (fanficMode) fontOf("System", 17.0, para.bold)
            else fontOf(para.fontFamily, para.fontSize, para.bold)
        val ox = pageOx()
        val lines = layout.pages.flatMap { page ->
            page.lines.filter { it.paragraphIndex == caretPara }.map { page.index to it }
        }
        val choice = lines.lastOrNull { (_, line) -> line.startInParagraph <= caretChar } ?: return null
        val local = (caretChar - choice.second.startInParagraph).coerceIn(0, choice.second.text.length)
        val width = measure(choice.second.text.take(local), font)
        return Hit(ox + choice.second.x + width, pageOy(choice.first) + choice.second.y, choice.second.height)
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
        val font = if (fanficMode) fontOf("System", 17.0, para.bold)
            else fontOf(para.fontFamily, para.fontSize, para.bold)
        val localX = x - ox - line.x
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in 0..line.text.length) {
            val w = measure(line.text.take(i), font)
            val d = abs(w - localX)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        caretChar = (line.startInParagraph + best).coerceIn(0, para.text.length)
    }

    private fun onKey(e: KeyEvent) {
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
            KeyCode.BACK_SPACE -> {
                if (hasSel) replaceRange(selStart(), selEnd(), "")
                else {
                    snapshot()
                    if (caretChar > 0) {
                        para.text = para.text.removeRange(caretChar - 1, caretChar)
                        caretChar--
                    } else if (caretPara > 0) {
                        val prev = document.paragraphs[caretPara - 1]
                        caretChar = prev.text.length
                        prev.text += para.text
                        document.paragraphs.removeAt(caretPara)
                        caretPara--
                    }
                    relayout()
                }
                e.consume()
            }
            KeyCode.DELETE -> {
                if (hasSel) replaceRange(selStart(), selEnd(), "")
                else {
                    snapshot()
                    if (caretChar < para.text.length) {
                        para.text = para.text.removeRange(caretChar, caretChar + 1)
                    } else if (caretPara < document.paragraphs.lastIndex) {
                        para.text += document.paragraphs[caretPara + 1].text
                        document.paragraphs.removeAt(caretPara + 1)
                    }
                    relayout()
                }
                e.consume()
            }
            KeyCode.ENTER -> {
                snapshot()
                if (hasSel) replaceRange(selStart(), selEnd(), "")
                caretPara = ParagraphFormatting.splitAtCaret(document, caretPara, caretChar)
                caretChar = 0
                clearSel()
                e.consume()
                relayout()
            }
            KeyCode.LEFT, KeyCode.RIGHT, KeyCode.HOME, KeyCode.END, KeyCode.UP, KeyCode.DOWN -> {
                clearSel()
                when (e.code) {
                    KeyCode.LEFT -> {
                        if (caretChar > 0) caretChar--
                        else if (caretPara > 0) {
                            caretPara--
                            caretChar = document.paragraphs[caretPara].text.length
                        }
                    }
                    KeyCode.RIGHT -> {
                        if (caretChar < para.text.length) caretChar++
                        else if (caretPara < document.paragraphs.lastIndex) {
                            caretPara++
                            caretChar = 0
                        }
                    }
                    KeyCode.HOME -> caretChar = 0
                    KeyCode.END -> caretChar = para.text.length
                    KeyCode.UP -> if (caretPara > 0) {
                        caretPara--
                        caretChar = caretChar.coerceAtMost(document.paragraphs[caretPara].text.length)
                    }
                    KeyCode.DOWN -> if (caretPara < document.paragraphs.lastIndex) {
                        caretPara++
                        caretChar = caretChar.coerceAtMost(document.paragraphs[caretPara].text.length)
                    }
                    else -> Unit
                }
                e.consume()
                paint()
                if (fanficMode) ensureCaretVisible()
                onCaretMoved?.invoke(caretPara)
            }
            else -> Unit
        }
    }

    private fun onType(e: KeyEvent) {
        armCaret()
        if (e.isShortcutDown) return
        val ch = e.character
        if (ch.isEmpty() || ch[0] < ' ' || ch == "\u007F") return
        if (hasSel) replaceRange(selStart(), selEnd(), ch)
        else {
            snapshot()
            val para = document.paragraphs.getOrNull(caretPara) ?: return
            para.text = para.text.substring(0, caretChar) + ch + para.text.substring(caretChar)
            caretChar += ch.length
            relayout()
        }
        e.consume()
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
