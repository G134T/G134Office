package org.example.ui

import javafx.application.Platform
import javafx.animation.PauseTransition
import javafx.geometry.Orientation
import javafx.print.PrinterJob
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.PasswordField
import javafx.scene.control.SplitPane
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Screen
import javafx.stage.Stage
import javafx.stage.WindowEvent
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.example.document.DocumentFormats
import org.example.document.DocumentSession
import org.example.document.PdfWorkspace
import org.example.engine.Align
import org.example.engine.EditorCanvas
import org.example.fanfic.FanficPreviewSerializer
import org.example.fanfic.FanficChapterExporter
import org.example.fanfic.FanficChapterFiles
import org.example.fanfic.FanficChapterGit
import org.example.fanfic.FanficDirection
import org.example.fanfic.FanficFormatter
import org.example.fanfic.FanficMeta
import org.example.fanfic.FanficRating
import org.example.fanfic.FanficStatus
import org.example.fanfic.FicbookCookies
import org.example.spell.DocLanguage
import org.example.spell.SpellEngine
import org.example.spell.SpellCheckDialog
import org.example.spell.SpellWatch
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.Properties
import javafx.util.Duration

class MainWindow(private val stage: Stage) {

    private val session = DocumentSession()
    private val uiSettings = UiSettings()
    private val recentDocuments = RecentDocuments()
    private val startCenter = StartCenter(recentDocuments, uiSettings, { newDocument() }, { openDocument() }, { showSettings() }, { openRecent(it) })
    private var atStartCenter = true
    private val status = StatusBarPane()
    private val pdfWorkspace = PdfWorkspace()
    private val engineView = EditorCanvas()
    private val navigationPane = NavigationPane()
    private val editorHost = BorderPane()
    private var readMode = false
    private var navigationOn = false
    private var zoomBeforeRead = 1.0
    private val fanficPreview = FanficPreviewPane { engineView.document }
    private val fanficDelay = PauseTransition(Duration.millis(300.0))
    private var fanficMode = false
    private var fanficPlacement = Orientation.VERTICAL
    private var lastFanficChapter: FanficChapterFiles? = null
    private var fanficSplit: SplitPane? = null
    private lateinit var ficbook: FicbookWorkspace
    private var fanficSkin = FicbookSkin.DAY
    private var readerPaper = ReaderPaper.WHITE
    private var officeThemeBefore = AppTheme.DARK
    private var fanficStartUrl = ""
    private val findDialog = FindReplaceDialog(engineView)
    private var fontSize = 16.0
    private var fontFamilyPref = "Segoe UI"
    private var shownFamily = fontFamilyPref
    private var shownSize = fontSize
    private var suppressFontUi = false
    private var currentTheme = AppTheme.DARK
    private var currentChrome = UiChrome.WORD
    private var textAlign = "left"
    private var docLang = DocLanguage.RU
    private var showRulerPref = false
    private var showGridPref = false
    private var scrollWithSpacePref = false
    private var syncingGuides = false

    private val prefsFile = File(System.getProperty("user.home"), ".g134office/prefs.properties")

    private lateinit var menusRef: AppMenus
    private lateinit var ribbonRef: RibbonBar
    private lateinit var rootRef: BorderPane
    private lateinit var spellWatch: SpellWatch

    init {
        fanficDelay.setOnFinished {
            if (!fanficMode) return@setOnFinished
            refreshFanficPreview()
        }
        engineView.onChange = {
            session.dirty = true
            refreshChrome()
            if (fanficMode && ::ficbook.isInitialized && !ficbook.isSite) fanficDelay.playFromStart()
        }
        engineView.onCaretMoved = { index ->
            if (fanficMode && ::ficbook.isInitialized && !ficbook.isSite) fanficPreview.scrollToParagraph(index)
        }
        engineView.onStyleChanged = { family, size, align, boldOn, italicOn, underlineOn ->
            shownFamily = family
            shownSize = size
            textAlign = when (align) {
                Align.CENTER -> "center"
                Align.RIGHT -> "right"
                Align.JUSTIFY -> "justify"
                else -> "left"
            }
            if (::ribbonRef.isInitialized) {
                suppressFontUi = true
                if (family.isNotBlank() && ribbonRef.fontFamily.items.contains(family)) {
                    ribbonRef.fontFamily.value = family
                }
                val shown = size.toInt().toString()
                if (!ribbonRef.fontSizeBox.items.contains(shown)) {
                    ribbonRef.fontSizeBox.items.add(shown)
                    ribbonRef.fontSizeBox.items.sortWith(compareBy { it.toIntOrNull() ?: 0 })
                }
                ribbonRef.fontSizeBox.value = shown
                suppressFontUi = false
                ribbonRef.showFormatting(align, boldOn, italicOn, underlineOn)
            }
            refreshChrome()
        }
        engineView.onContextChanged = { selected, heading ->
            if (::ribbonRef.isInitialized) ribbonRef.showContext(selected, heading)
        }
        engineView.onZoomRequested = { setFanficZoom(it) }
        engineView.onFormatBrushChanged = { armed ->
            if (::ribbonRef.isInitialized) ribbonRef.setPressed(ribbonRef.formatPainter, armed)
        }
        navigationPane.onPick = { spot ->
            engineView.reveal(spot.paragraph, spot.charInParagraph)
            engineView.requestFocus()
        }
        editorHost.center = engineView
        pdfWorkspace.onStateChanged = {
            if (pdfWorkspace.isOpen) session.dirty = pdfWorkspace.dirty
            refreshChrome()
        }
        loadPrefs()
        uiSettings.theme?.let { currentTheme = it }
    }

    @JvmOverloads
    fun show(startupPaths: List<String> = emptyList()) {
        menusRef = AppMenus()
        ribbonRef = RibbonBar()
        if (ribbonRef.fontFamily.items.contains(fontFamilyPref)) {
            ribbonRef.fontFamily.value = fontFamilyPref
        }
        val sizeShown = fontSize.toInt().toString()
        if (!ribbonRef.fontSizeBox.items.contains(sizeShown)) {
            ribbonRef.fontSizeBox.items.add(sizeShown)
            ribbonRef.fontSizeBox.items.sortWith(compareBy { it.toIntOrNull() ?: 0 })
        }
        ribbonRef.fontSizeBox.value = sizeShown
        ribbonRef.showTheme(currentTheme, uiSettings.layout)
        ribbonRef.langBox.value = docLang
        ribbonRef.setIconSet(uiSettings.iconSet)
        bind(menusRef, ribbonRef)
        ficbook = FicbookWorkspace(engineView, fanficPreview, object : FicbookWorkspace.Host {
            override fun exitFanfic() = setFanficMode(false)
            override fun saveDocument() { saveDocument(false) }
            override fun saveChapter() = saveFanficChapter()
            override fun find() { findDialog.show(stage, false, Theme.pack(currentTheme)) }
            override fun spell() { SpellCheckDialog(stage, engineView).show(docLang) }
            override fun formatText() = formatFanficText()
            override fun undo() { engineView.undo(); engineView.requestFocus() }
            override fun redo() { engineView.redo(); engineView.requestFocus() }
            override fun copy() { copyFanficHtml(); engineView.requestFocus() }
            override fun paste() { engineView.paste(); engineView.requestFocus() }
            override fun cut() { engineView.cut(); engineView.requestFocus() }
            override fun onSkin(skin: FicbookSkin) = applyFicbookSkin(skin)
            override fun onMetaChanged() {
                if (!fanficMode || ficbook.isSite) return
                fanficDelay.playFromStart()
            }
            override fun onUrl(url: String) {
                if (!::ficbook.isInitialized) return
                ficbook.startUrl = url
                savePrefs()
            }
        })
        fanficPlacement = if (uiSettings.placement == "Справа") Orientation.HORIZONTAL else Orientation.VERTICAL
        ficbook.setPlacement(fanficPlacement)
        fanficPreview.prefWidth = uiSettings.previewWidth.toDouble()
        if (fanficStartUrl.isNotBlank()) ficbook.startUrl = fanficStartUrl
        restoreFanficMeta()
        fanficPreview.onPaper = {
            readerPaper = it
            savePrefs()
        }
        engineView.showRuler = showRulerPref
        engineView.showGrid = showGridPref
        engineView.scrollWithSpace = scrollWithSpacePref
        menusRef.viewRuler.isSelected = showRulerPref
        menusRef.viewGrid.isSelected = showGridPref
        menusRef.viewSpaceScroll.isSelected = scrollWithSpacePref
        menusRef.applyLanguage(uiSettings.language)

        val menuBar = menusRef.build()
        wireWordPages()
        rootRef = BorderPane().apply {
            top = VBox(menuBar, ribbonRef)
            center = editorHost
            bottom = status
        }

        val screen = Screen.getPrimary()
        val box = UiScreen.windowBox(screen.visualBounds.width, screen.visualBounds.height)
        stage.scene = Scene(rootRef, box.width, box.height).apply {
            // Прозрачный слой нужен стеклянной теме, чтобы просвечивал рабочий стол.
            fill = Color.TRANSPARENT
        }
        stage.minWidth = box.minWidth
        stage.minHeight = box.minHeight
        stage.x = screen.visualBounds.minX + (screen.visualBounds.width - box.width) / 2.0
        stage.y = screen.visualBounds.minY + (screen.visualBounds.height - box.height) / 2.0
        applyUiAppearance()
        stage.scene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
            if (event.code == KeyCode.ESCAPE && readMode) {
                setReadMode(false)
                event.consume()
                return@addEventFilter
            }
            if (fanficMode && event.isShortcutDown) {
                val next = when (event.code) {
                    KeyCode.PLUS, KeyCode.ADD, KeyCode.EQUALS -> engineView.zoom + 0.1
                    KeyCode.MINUS, KeyCode.SUBTRACT -> engineView.zoom - 0.1
                    KeyCode.DIGIT0, KeyCode.NUMPAD0 -> 1.0
                    else -> null
                }
                if (next != null) {
                    setFanficZoom(next)
                    event.consume()
                    return@addEventFilter
                }
            }
            if (fanficMode && ::ficbook.isInitialized && !ficbook.isSite &&
                event.isShortcutDown && event.code == KeyCode.C
            ) {
                copyFanficHtml()
                event.consume()
            }
        }
        stage.setOnCloseRequest { e ->
            fanficDelay.stop()
            if (::ficbook.isInitialized) {
                ficbook.startUrl = ficbook.currentUrl()
                FicbookCookies.save()
            }
            savePrefs()
            if (!confirmDiscard()) e.consume()
            else pdfWorkspace.dispose()
        }
        session.reset()
        engineView.loadPlain("")
        val opening = startupPaths.any { it.isNotBlank() }
        if (opening) atStartCenter = false
        applyTheme(currentTheme)
        if (!opening) showStartCenter()
        stage.show()
        spellWatch = SpellWatch(engineView) { docLang }
        spellWatch.attach()
        SpellEngine.warmup(docLang)
        if (opening) Platform.runLater { openCommandLine(startupPaths) }
    }

    /** Первый путь открывается сразу. Остальные только называются в строке состояния. */
    private fun openCommandLine(paths: List<String>) {
        var first: String? = null
        val rest = StringBuilder()
        var extra = 0
        for (path in paths) {
            if (path.isBlank()) continue
            if (first == null) {
                first = path
                continue
            }
            if (extra > 0) rest.append(", ")
            rest.append(File(path).name)
            extra++
        }
        val path = first ?: return
        openDocument(File(path))
        if (session.file == null) {
            showStartCenter()
            return
        }
        if (extra > 0) status.setMessage("Открыт только первый файл. Ещё не открыты: $rest")
    }

    /** Лента Word не рисует второй ряд вкладок: пункты общего меню сами открывают свою панель. */
    private fun wireWordPages() {
        val items = menusRef.bar.menus
        fun page(menu: javafx.scene.control.Menu, index: Int) {
            menu.setOnShowing { if (currentChrome == UiChrome.WORD) ribbonRef.selectWordTab(index) }
        }
        page(items[1], 0)
        page(items[2], 4)
        page(items[3], 1)
        page(items[4], 2)
        page(items[5], 3)
    }

    private fun bind(menus: AppMenus, ribbon: RibbonBar) {
        menus.neu.setOnAction { newDocument() }
        menus.open.setOnAction { openDocument() }
        menus.close.setOnAction { closeDocument() }
        menus.settings.setOnAction { showSettings() }
        menus.save.setOnAction { saveDocument(false) }
        menus.saveAs.setOnAction { saveDocument(true) }
        menus.saveFanficChapter.setOnAction { saveFanficChapter() }
        menus.commitFanficChapter.setOnAction { commitFanficChapter() }
        menus.print.setOnAction { printDocument() }
        menus.exit.setOnAction {
            stage.fireEvent(WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST))
        }

        menus.undo.setOnAction { engineView.undo(); engineView.requestFocus() }
        menus.redo.setOnAction { engineView.redo(); engineView.requestFocus() }
        menus.cut.setOnAction { engineView.cut(); engineView.requestFocus() }
        menus.copy.setOnAction { if (fanficMode) copyFanficHtml() else engineView.copy(); engineView.requestFocus() }
        menus.paste.setOnAction { engineView.paste(); engineView.requestFocus() }
        menus.selectAll.setOnAction { engineView.selectAll(); engineView.requestFocus() }
        menus.find.setOnAction { if (pdfWorkspace.isOpen) pdfWorkspace.focusSearch() else findDialog.show(stage, false, Theme.pack(currentTheme)) }
        menus.replace.setOnAction { if (pdfWorkspace.isOpen) pdfWorkspace.focusSearch() else findDialog.show(stage, true, Theme.pack(currentTheme)) }
        menus.insertDate.setOnAction { insertDate() }
        menus.insertSymbol.setOnAction { insertSymbol() }
        menus.wordCount.setOnAction { showWordCount() }
        menus.about.setOnAction { about() }
        menus.checkText.setOnAction { if (!pdfWorkspace.isOpen) SpellCheckDialog(stage, engineView).show(docLang) }
        menus.langRu.setOnAction { setLang(DocLanguage.RU) }
        menus.langUk.setOnAction { setLang(DocLanguage.UK) }
        menus.langBe.setOnAction { setLang(DocLanguage.BE) }
        menus.langEn.setOnAction { setLang(DocLanguage.EN) }
        menus.alignLeft.setOnAction { setAlign("left") }
        menus.alignCenter.setOnAction { setAlign("center") }
        menus.alignRight.setOnAction { setAlign("right") }
        menus.alignJustify.setOnAction { setAlign("justify") }
        menus.indentMore.setOnAction { engineView.changeIndent(true) }
        menus.indentLess.setOnAction { engineView.changeIndent(false) }
        menus.themeItems.forEach { (theme, item) -> item.setOnAction { applyTheme(theme) } }
        menus.layoutItems.forEach { (layout, item) -> item.setOnAction { applyLayout(layout) } }

        menus.viewRuler.setOnAction { setRuler(menus.viewRuler.isSelected) }
        menus.viewGrid.setOnAction { setGrid(menus.viewGrid.isSelected) }
        menus.viewRibbon.setOnAction {
            if (readMode) return@setOnAction
            ribbon.isVisible = menus.viewRibbon.isSelected
            ribbon.isManaged = menus.viewRibbon.isSelected
        }
        menus.viewStatus.setOnAction {
            if (readMode) return@setOnAction
            status.isVisible = menus.viewStatus.isSelected
            status.isManaged = menus.viewStatus.isSelected
        }
        menus.viewSpaceScroll.setOnAction {
            engineView.scrollWithSpace = menus.viewSpaceScroll.isSelected
            scrollWithSpacePref = engineView.scrollWithSpace
            savePrefs()
            engineView.requestFocus()
        }
        menus.viewFanfic.setOnAction { setFanficMode(menus.viewFanfic.isSelected) }
        menus.viewReadMode.setOnAction { setReadMode(menus.viewReadMode.isSelected) }
        menus.viewPrintLayout.setOnAction { setReadMode(false) }
        menus.viewNavigation.setOnAction { setNavigation(menus.viewNavigation.isSelected) }
        menus.zoomIn.setOnAction { setFanficZoom(engineView.zoom + 0.1) }
        menus.zoomOut.setOnAction { setFanficZoom(engineView.zoom - 0.1) }
        menus.zoomReset.setOnAction { setFanficZoom(1.0) }
        menus.fullScreen.setOnAction { stage.isFullScreen = !stage.isFullScreen }

        ribbon.neu.onAction = menus.neu.onAction
        ribbon.open.onAction = menus.open.onAction
        ribbon.save.onAction = menus.save.onAction
        ribbon.commitFanficChapter.onAction = menus.commitFanficChapter.onAction
        ribbon.cut.onAction = menus.cut.onAction
        ribbon.copy.onAction = menus.copy.onAction
        ribbon.paste.onAction = menus.paste.onAction
        ribbon.find.onAction = menus.find.onAction
        ribbon.replace.onAction = menus.replace.onAction
        ribbon.spell.onAction = menus.checkText.onAction
        ribbon.fanficMode.setOnAction { setFanficMode(!fanficMode) }
        ribbon.ruler.setOnAction { setRuler(!engineView.showRuler) }
        ribbon.grid.setOnAction { setGrid(!engineView.showGrid) }
        ribbon.insertSymbol.onAction = menus.insertSymbol.onAction
        ribbon.insertDate.onAction = menus.insertDate.onAction
        ribbon.pageBreak.setOnAction {
            if (pdfWorkspace.isOpen || fanficMode) return@setOnAction
            engineView.insertPageBreak()
            engineView.requestFocus()
        }
        ribbon.landscape.setOnAction { toggleOrientation() }
        ribbon.marginsLess.setOnAction { nudgeMargins(false) }
        ribbon.marginsMore.setOnAction { nudgeMargins(true) }
        ribbon.wordCount.onAction = menus.wordCount.onAction
        ribbon.zoomIn.setOnAction { setFanficZoom(engineView.zoom + 0.1) }
        ribbon.zoomOut.setOnAction { setFanficZoom(engineView.zoom - 0.1) }
        ribbon.zoomReset.setOnAction { setFanficZoom(1.0) }
        ribbon.grow.setOnAction { changeFont(1.0) }
        ribbon.shrink.setOnAction { changeFont(-1.0) }
        ribbon.bold.setOnAction { engineView.toggleRunStyle(bold = true); engineView.requestFocus() }
        ribbon.italic.setOnAction { engineView.toggleRunStyle(italic = true); engineView.requestFocus() }
        ribbon.underline.setOnAction { engineView.toggleRunStyle(underline = true); engineView.requestFocus() }
        ribbon.indentMore.onAction = menus.indentMore.onAction
        ribbon.indentLess.onAction = menus.indentLess.onAction
        ribbon.alignLeft.onAction = menus.alignLeft.onAction
        ribbon.alignCenter.onAction = menus.alignCenter.onAction
        ribbon.alignRight.onAction = menus.alignRight.onAction
        ribbon.alignJustify.onAction = menus.alignJustify.onAction
        ribbon.undo.onAction = menus.undo.onAction
        ribbon.redo.onAction = menus.redo.onAction
        ribbon.strike.setOnAction { engineView.toggleRunStyle(strikethrough = true); engineView.requestFocus() }
        ribbon.clearFormat.setOnAction { engineView.clearFormatting(); engineView.requestFocus() }
        ribbon.formatPainter.setOnAction {
            if (engineView.formatBrushArmed()) engineView.cancelFormatBrush()
            else engineView.captureFormatBrush()
            engineView.requestFocus()
        }
        ribbon.selectAll.onAction = menus.selectAll.onAction
        ribbon.styleNormal.setOnAction { engineView.applyWordStyle(0); engineView.requestFocus() }
        ribbon.styleHeading1.setOnAction { engineView.applyWordStyle(1); engineView.requestFocus() }
        ribbon.styleHeading2.setOnAction { engineView.applyWordStyle(2); engineView.requestFocus() }
        ribbon.printLayout.setOnAction { setReadMode(false) }
        ribbon.readMode.setOnAction { setReadMode(!readMode) }
        ribbon.navigation.setOnAction { setNavigation(!navigationOn) }
        ribbon.fitWidth.setOnAction { engineView.zoomToFitWidth(); engineView.requestFocus() }
        ribbon.fullScreen.onAction = menus.fullScreen.onAction
        ribbon.fontColor.setOnAction {
            if (suppressFontUi) return@setOnAction
            engineView.applyFontColor(cssColor(ribbon.fontColor.value))
            engineView.requestFocus()
        }
        ribbon.lineSpacingBox.setOnAction {
            if (suppressFontUi) return@setOnAction
            val label = ribbon.lineSpacingBox.value ?: return@setOnAction
            engineView.setLineSpacing(spacingValue(label))
            engineView.requestFocus()
        }
        ribbon.fontFamily.setOnAction { applyFontAppearance() }
        ribbon.fontSizeBox.setOnAction { applyFontAppearance() }
        ribbon.layoutBox.setOnAction {
            if (ribbon.layoutBoxQuiet()) return@setOnAction
            ribbon.layoutBox.value?.let { applyLayout(it) }
        }
        ribbon.themeFamilyBox.setOnAction { applyTheme(ribbon.resolvedTheme()) }
        ribbon.wordYearBox.setOnAction {
            if (ribbon.layoutBoxQuiet()) return@setOnAction
            if (ribbon.themeFamilyBox.value == ThemeFamily.WORD) applyTheme(ribbon.resolvedTheme())
        }
        ribbon.officeYearBox.setOnAction {
            if (ribbon.layoutBoxQuiet()) return@setOnAction
            if (ribbon.themeFamilyBox.value == ThemeFamily.OPEN_OFFICE) applyTheme(ribbon.resolvedTheme())
        }
        ribbon.langBox.setOnAction { ribbon.langBox.value?.let { setLang(it) } }

        menus.pdfRotateLeft.setOnAction { pdfWorkspace.rotateCurrent(-90) }
        menus.pdfRotateRight.setOnAction { pdfWorkspace.rotateCurrent(90) }
        menus.pdfInsertBlank.setOnAction { pdfWorkspace.insertBlank() }
        menus.pdfDeletePage.setOnAction { pdfWorkspace.deleteCurrent() }
        menus.pdfMoveUp.setOnAction { pdfWorkspace.moveCurrent(-1) }
        menus.pdfMoveDown.setOnAction { pdfWorkspace.moveCurrent(1) }
        menus.pdfInsertFile.setOnAction { pdfWorkspace.insertPdf(stage) }
        menus.pdfExtract.setOnAction { pdfWorkspace.extractPages(stage) }
        menus.pdfProps.setOnAction { pdfWorkspace.showProperties(stage) }
    }

    private fun loadPrefs() {
        if (!prefsFile.isFile) return
        runCatching {
            val p = Properties()
            prefsFile.inputStream().use { p.load(it) }
            currentTheme = AppTheme.entries.firstOrNull { it.name == p.getProperty("theme") } ?: AppTheme.DARK
            val savedLayout = UiChrome.fromPref(p.getProperty("layout"))
            uiSettings.layout = savedLayout ?: when {
                currentTheme.officeYear != null -> UiChrome.OPEN_OFFICE
                currentTheme == AppTheme.MYOFFICE -> UiChrome.MY_OFFICE
                else -> uiSettings.layout
            }
            fontSize = p.getProperty("fontSize")?.toDoubleOrNull() ?: 16.0
            fontFamilyPref = p.getProperty("fontFamily") ?: "Segoe UI"
            shownFamily = fontFamilyPref
            shownSize = fontSize
            textAlign = p.getProperty("align") ?: "left"
            docLang = DocLanguage.entries.firstOrNull { it.name == p.getProperty("lang") } ?: DocLanguage.RU
            showRulerPref = p.getProperty("showRuler")?.toBooleanStrictOrNull() ?: false
            showGridPref = p.getProperty("showGrid")?.toBooleanStrictOrNull() ?: false
            scrollWithSpacePref = p.getProperty("scrollWithSpace")?.toBooleanStrictOrNull() ?: false
            fanficPlacement = if (p.getProperty("fanficPlacement") == "right")
                Orientation.HORIZONTAL else Orientation.VERTICAL
            fanficSkin = FicbookSkin.entries.firstOrNull { it.name == p.getProperty("fanficSkin") }
                ?: FicbookSkin.DAY
            readerPaper = ReaderPaper.entries.firstOrNull { it.name == p.getProperty("readerPaper") }
                ?: ReaderPaper.WHITE
            fanficStartUrl = p.getProperty("fanficUrl").orEmpty()
        }
    }

    private fun savePrefs() {
        runCatching {
            prefsFile.parentFile.mkdirs()
            val p = Properties()
            p.setProperty("theme", currentTheme.name)
            p.setProperty("layout", uiSettings.layout.name)
            p.setProperty("fontSize", fontSize.toString())
            p.setProperty(
                "fontFamily",
                if (::ribbonRef.isInitialized) ribbonRef.fontFamily.value ?: fontFamilyPref else fontFamilyPref
            )
            p.setProperty("align", textAlign)
            p.setProperty("lang", docLang.name)
            p.setProperty("showRuler", showRulerPref.toString())
            p.setProperty("showGrid", showGridPref.toString())
            p.setProperty("scrollWithSpace", scrollWithSpacePref.toString())
            p.setProperty("fanficPlacement", if (fanficPlacement == Orientation.HORIZONTAL) "right" else "below")
            p.setProperty("fanficSkin", fanficSkin.name)
            p.setProperty("readerPaper", readerPaper.name)
            if (::ficbook.isInitialized) p.setProperty("fanficUrl", ficbook.startUrl)
            else if (fanficStartUrl.isNotBlank()) p.setProperty("fanficUrl", fanficStartUrl)
            if (::ficbook.isInitialized) {
                val m = ficbook.meta
                p.setProperty("fanficTitle", m.title)
                p.setProperty("fanficChapter", m.chapterTitle)
                p.setProperty("fanficAuthor", m.author)
                p.setProperty("fanficFandom", m.fandom)
                p.setProperty("fanficPairing", m.pairing)
                p.setProperty("fanficDirection", m.direction.name)
                p.setProperty("fanficRating", m.rating.name)
                p.setProperty("fanficStatus", m.status.name)
                p.setProperty("fanficTags", m.tags)
                p.setProperty("fanficDescription", m.description)
                p.setProperty("fanficNotes", m.notes)
            }
            prefsFile.outputStream().use { p.store(it, "G134Office") }
        }
    }

    private fun restoreFanficMeta() {
        if (!prefsFile.isFile || !::ficbook.isInitialized) return
        runCatching {
            val p = Properties()
            prefsFile.inputStream().use { p.load(it) }
            ficbook.loadMeta(
                FanficMeta(
                    title = p.getProperty("fanficTitle") ?: "Без названия",
                    chapterTitle = p.getProperty("fanficChapter") ?: "Глава 1",
                    author = p.getProperty("fanficAuthor") ?: "автор",
                    fandom = p.getProperty("fanficFandom").orEmpty(),
                    pairing = p.getProperty("fanficPairing").orEmpty(),
                    direction = FanficDirection.entries.firstOrNull { it.name == p.getProperty("fanficDirection") }
                        ?: FanficDirection.GEN,
                    rating = FanficRating.entries.firstOrNull { it.name == p.getProperty("fanficRating") }
                        ?: FanficRating.G,
                    status = FanficStatus.entries.firstOrNull { it.name == p.getProperty("fanficStatus") }
                        ?: FanficStatus.DRAFT,
                    tags = p.getProperty("fanficTags").orEmpty(),
                    description = p.getProperty("fanficDescription").orEmpty(),
                    notes = p.getProperty("fanficNotes").orEmpty()
                )
            )
        }
    }

    private fun setLang(lang: DocLanguage) {
        docLang = lang
        if (::ribbonRef.isInitialized && ribbonRef.langBox.value != lang) ribbonRef.langBox.value = lang
        savePrefs()
        refreshChrome()
    }

    private fun setAlign(value: String) {
        textAlign = value
        val a = when (value) {
            "center" -> Align.CENTER
            "right" -> Align.RIGHT
            "justify" -> Align.JUSTIFY
            else -> Align.LEFT
        }
        engineView.setAlignment(a)
        savePrefs()
        applyTheme(currentTheme)
        engineView.requestFocus()
    }

    private fun applyLayout(layout: UiChrome) {
        if (layout !in UiChrome.layouts) return
        uiSettings.layout = layout
        val theme = if (currentTheme.isPalette) currentTheme else AppTheme.LIGHT
        applyTheme(theme)
    }

    private fun applyTheme(theme: AppTheme) {
        if (fanficMode) return
        when {
            theme.officeYear != null -> uiSettings.layout = UiChrome.OPEN_OFFICE
            theme == AppTheme.MYOFFICE -> uiSettings.layout = UiChrome.MY_OFFICE
        }
        currentTheme = theme
        uiSettings.theme = theme
        uiSettings.save()
        menusRef.selectTheme(theme)
        val chrome = UiChrome.resolve(theme, uiSettings.layout)
        currentChrome = chrome
        val pack = Theme.pack(theme)
        menusRef.selectLayout(chrome.takeIf { it in UiChrome.layouts })
        menusRef.setChrome(chrome)
        ribbonRef.setChrome(chrome)
        status.setChrome(chrome)
        engineView.setOfficeMode(chrome)
        rootRef.style = if (theme == AppTheme.GLASS) {
            "-fx-background-color: linear-gradient(to bottom right, #c7dfed 0%, #eef3f7 42%, #cdd8eb 72%, #dbece9 100%); " +
                "-fx-border-color: rgba(255,255,255,0.85); -fx-border-width: 1;"
        } else {
            "-fx-background-color: ${pack.windowBg};"
        }
        applyUiAppearance()
        menusRef.applyTheme(pack)
        menusRef.applyLanguage(uiSettings.language)
        menusRef.setIconSet(uiSettings.iconSet, pack.popupFg)
        ribbonRef.setIconSet(uiSettings.iconSet)
        ribbonRef.applyTheme(pack)
        status.applyTheme(pack)
        if (readMode) {
            ribbonRef.isVisible = false
            ribbonRef.isManaged = false
            status.isVisible = false
            status.isManaged = false
        } else {
            ribbonRef.isVisible = !theme.compactRibbon && menusRef.viewRibbon.isSelected
            ribbonRef.isManaged = ribbonRef.isVisible
            status.isVisible = menusRef.viewStatus.isSelected
            status.isManaged = status.isVisible
        }
        ribbonRef.showTheme(theme, uiSettings.layout)
        ribbonRef.showContext(engineView.hasSelection(), engineView.caretOutline() > 0)
        val family = ribbonRef.fontFamily.value ?: fontFamilyPref
        fontFamilyPref = family
        val size = ribbonRef.fontSizeBox.value?.toDoubleOrNull() ?: fontSize
        fontSize = size
        engineView.applyDesk(pack.windowBg)
        applyCaretSettings()
        engineView.relayout(false)
        Theme.applyCss(stage.scene, pack)
        stage.opacity = 1.0
        pdfWorkspace.applyTheme(pack)
        navigationPane.applyTheme(pack)
        startCenter.applyTheme(pack)
        applyNavigation()
        savePrefs()
        refreshChrome()
        engineView.syncCaret()
    }

    private fun setFanficMode(on: Boolean) {
        if (pdfWorkspace.isOpen) {
            menusRef.viewFanfic.isSelected = false
            return
        }
        if (fanficMode == on) return
        if (on && readMode) setReadMode(false)
        fanficMode = on
        if (!atStartCenter) {
            uiSettings.openMode = if (on) "ФФ" else "Лист"
            uiSettings.save()
        }
        menusRef.viewFanfic.isSelected = on
        ribbonRef.fanficMode.text = "ФФ"
        menusRef.viewRuler.isDisable = on
        menusRef.viewGrid.isDisable = on
        ribbonRef.ruler.isVisible = !on
        ribbonRef.ruler.isManaged = !on
        ribbonRef.grid.isVisible = !on
        ribbonRef.grid.isManaged = !on
        engineView.setFanficMode(on)
        if (on) {
            officeThemeBefore = currentTheme
            enterFicbookChrome()
            applyFicbookSkin(fanficSkin)
        } else {
            fanficDelay.stop()
            leaveFicbookChrome()
            applyTheme(officeThemeBefore)
        }
        showDocumentView()
        engineView.requestFocus()
    }

    private fun enterFicbookChrome() {
        menusRef.bar.isVisible = false
        menusRef.bar.isManaged = false
        ribbonRef.isVisible = false
        ribbonRef.isManaged = false
        status.isVisible = false
        status.isManaged = false
        rootRef.top = menusRef.bar
        rootRef.center = ficbook.root
        rootRef.bottom = null
    }

    private fun leaveFicbookChrome() {
        ficbook.detach()
        menusRef.bar.isVisible = true
        menusRef.bar.isManaged = true
        ribbonRef.isVisible = !officeThemeBefore.compactRibbon && menusRef.viewRibbon.isSelected
        ribbonRef.isManaged = ribbonRef.isVisible
        status.isVisible = menusRef.viewStatus.isSelected
        status.isManaged = status.isVisible
        rootRef.top = VBox(menusRef.bar, ribbonRef)
        rootRef.bottom = status
    }

    private fun applyFicbookSkin(skin: FicbookSkin) {
        fanficSkin = skin
        val pack = FicbookTheme.pack(skin)
        ficbook.applySkin(skin)
        fanficPreview.setSkin(skin)
        fanficPreview.setPaper(readerPaper)
        fanficPreview.setMeta(ficbook.meta)
        rootRef.style = "-fx-background-color: ${pack.pageBg};"
        Theme.applyCss(stage.scene, pack.office)
        stage.opacity = 1.0
        applyCaretSettings()
        refreshFanficPreview()
        savePrefs()
    }

    private fun refreshFanficPreview() {
        if (!::ficbook.isInitialized) return
        if (ficbook.isSite) {
            stage.title = "Фикбук"
            return
        }
        fanficPreview.setMeta(ficbook.meta)
        fanficPreview.setSkin(fanficSkin)
        fanficPreview.setZoom(engineView.zoom)
        fanficPreview.refresh()
        val s = stats()
        ficbook.refreshStats(s.words, s.chars, FanficFormatter.sizePages(s.chars))
        stage.title = "Фикбук — черновик ${ficbook.meta.displayTitle()}"
    }

    private fun formatFanficText() {
        FanficFormatter.applyTo(engineView.document)
        engineView.relayout()
        refreshFanficPreview()
        engineView.requestFocus()
    }

    private fun showDocumentView() {
        fanficSplit?.items?.clear()
        rootRef.center = null
        if (fanficMode) {
            ficbook.attach()
            rootRef.center = ficbook.root
            fanficDelay.stop()
            if (!ficbook.isSite) refreshFanficPreview()
            stage.title = "Фикбук"
        } else {
            fanficSplit = null
            rootRef.center = editorHost
            applyNavigation()
        }
    }

    private fun setFanficZoom(value: Double) {
        engineView.setZoom(value)
        if (fanficMode) refreshFanficPreview()
        engineView.requestFocus()
    }

    private fun copyFanficHtml() {
        val html = FanficPreviewSerializer.toHtml(engineView.document)
        val content = ClipboardContent()
        content.putString(engineView.document.toExportText())
        content.putHtml(html)
        Clipboard.getSystemClipboard().setContent(content)
    }

    private fun newDocument(force: Boolean = false) {
        if (!force && !confirmDiscard()) return
        if (!fanficMode) {
            val choice = NewDocumentDialog.show(stage, Theme.pack(currentTheme), uiSettings.paper, uiSettings.landscape) ?: return
            engineView.document.applyPaper(choice.paper, choice.landscape)
        }
        fanficSplit?.items?.clear()
        leavePdf()
        engineView.loadPlain("")
        engineView.applyCharacterFont(fontFamilyPref, fontSize)
        atStartCenter = false
        showEditorChrome()
        showDocumentView()
        session.reset()
        refreshChrome()
        engineView.requestFocus()
    }

    private fun openDocument() {
        if (!confirmDiscard()) return
        val file = chooser(false).showOpenDialog(stage) ?: return
        openDocument(file)
    }

    private fun openRecent(file: File) {
        if (!file.isFile) {
            recentDocuments.remove(file)
            startCenter.refresh()
            return
        }
        if (confirmDiscard()) openDocument(file)
    }

    private fun openDocument(file: File) {
        val opened = existingFile(file)
        if (!opened.isFile) {
            recentDocuments.remove(file)
            startCenter.refresh()
            error("Не смог открыть файл", IllegalArgumentException("Файл не найден: ${file.absolutePath}"))
            return
        }
        try {
            if (DocumentFormats.kind(opened) == "pdf") {
                setFanficMode(false)
                enterPdf(opened)
            } else {
                val document = DocumentFormats.readDocument(opened)
                fanficSplit?.items?.clear()
                leavePdf()
                engineView.loadDocument(document)
                session.markSaved(opened)
                atStartCenter = false
                showEditorChrome()
                showDocumentView()
                if (uiSettings.openMode == "ФФ") setFanficMode(true)
                engineView.requestFocus()
            }
            if (session.file == opened) recentDocuments.add(opened)
            refreshChrome()
        } catch (ex: Exception) {
            error("Не смог открыть файл", ex)
        }
    }

    /**
     * NTFS хранит имя как есть. Лаунчер Windows может отдать тот же путь в другой
     * канонической форме, и тогда [File.isFile] не находит файл.
     */
    private fun existingFile(file: File): File {
        if (file.isFile) return file
        val path = file.path
        val nfc = Normalizer.normalize(path, Normalizer.Form.NFC)
        if (nfc !== path) {
            val normalized = File(nfc)
            if (normalized.isFile) return normalized
        }
        val resolved = resolveNfc(file)
        return if (resolved.isFile) resolved else file
    }

    private fun resolveNfc(file: File): File {
        val parent = file.parentFile ?: return file
        val directory = if (parent.isDirectory) parent else {
            val resolvedParent = resolveNfc(parent)
            if (!resolvedParent.isDirectory) return file
            resolvedParent
        }
        return matchNfc(directory, file.name) ?: file
    }

    private fun matchNfc(directory: File, name: String): File? {
        val wanted = Normalizer.normalize(name, Normalizer.Form.NFC)
        return runCatching {
            Files.newDirectoryStream(directory.toPath()).use { stream ->
                var found: File? = null
                for (entry in stream) {
                    val entryName = entry.fileName.toString()
                    if (entryName == name) {
                        found = entry.toFile()
                        break
                    }
                    if (found == null && Normalizer.normalize(entryName, Normalizer.Form.NFC) == wanted) {
                        found = entry.toFile()
                    }
                }
                found
            }
        }.getOrNull()
    }

    private fun enterPdf(file: File, password: String? = null) {
        try {
            pdfWorkspace.open(file, password)
        } catch (_: InvalidPasswordException) {
            val pass = askPdfPassword(file) ?: return
            enterPdf(file, pass)
            return
        }
        session.markSaved(file)
        showPdfView(true)
    }

    private fun leavePdf() {
        if (pdfWorkspace.isOpen) pdfWorkspace.close()
        showPdfView(false)
    }

    private fun showPdfView(on: Boolean) {
        if (on) {
            atStartCenter = false
            readMode = false
            if (::menusRef.isInitialized) {
                menusRef.viewReadMode.isSelected = false
                menusRef.viewPrintLayout.isSelected = true
            }
            showEditorChrome()
        }
        rootRef.center = if (on) pdfWorkspace else editorHost
        if (!on) applyNavigation()
        menusRef.setPdfEnabled(on)
        pdfWorkspace.applyTheme(Theme.pack(currentTheme))
        if (on) pdfWorkspace.requestFocus() else engineView.requestFocus()
    }

    private fun askPdfPassword(file: File): String? {
        val dialog = Dialog<String>()
        dialog.title = "Пароль PDF"
        dialog.headerText = "Файл «${file.name}» защищён паролем"
        dialog.initOwner(stage)
        val field = PasswordField()
        dialog.dialogPane.content = field
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        dialog.setResultConverter { if (it == ButtonType.OK) field.text else null }
        Platform.runLater { field.requestFocus() }
        return dialog.showAndWait().orElse(null)
    }

    private fun saveDocument(saveAs: Boolean): Boolean {
        var file = session.file
        val dialog = chooser(true)
        if (saveAs || file == null) {
            file = dialog.showSaveDialog(stage) ?: return false
            file = ensureExtension(file, dialog)
        }
        return try {
            if (pdfWorkspace.isOpen && DocumentFormats.kind(file) == "pdf") pdfWorkspace.save(file)
            else if (pdfWorkspace.isOpen) DocumentFormats.write(file, pdfWorkspace.plainText())
            else DocumentFormats.write(file, engineView.document)
            session.markSaved(file)
            recentDocuments.add(file)
            refreshChrome()
            true
        } catch (ex: Exception) {
            error("Не смог сохранить файл", ex)
            false
        }
    }

    private fun printDocument() {
        try {
            if (pdfWorkspace.isOpen) {
                pdfWorkspace.print(stage)
                return
            }
            val job = PrinterJob.createPrinterJob() ?: return
            if (!job.showPrintDialog(stage)) return
            job.printPage(engineView)
            job.endJob()
        } catch (ex: Exception) {
            error("Не смог напечатать", ex)
        }
    }

    private fun confirmDiscard(): Boolean {
        if (!session.dirty) return true
        val alert = themed(Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "G134Office"
            headerText = "Сохранить изменения в «${session.displayName()}»?"
        })
        val save = ButtonType("Сохранить")
        val discard = ButtonType("Не сохранять")
        val cancel = ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE)
        alert.buttonTypes.setAll(save, discard, cancel)
        val result: Optional<ButtonType> = alert.showAndWait()
        if (result.isEmpty || result.get() == cancel) return false
        if (result.get() == save) return saveDocument(false)
        return true
    }

    private fun chooser(saving: Boolean): FileChooser {
        val dialog = FileChooser()
        if (saving && uiSettings.saveFolder.isNotBlank()) {
            File(uiSettings.saveFolder).takeIf { it.isDirectory }?.let { dialog.initialDirectory = it }
        }
        dialog.title = if (saving) "Сохранение документа" else "Открытие документа"
        val wordNew = FileChooser.ExtensionFilter("Word 2007+ (*.docx, *.docm, *.dotx)", "*.docx", "*.docm", "*.dotx")
        val wordOld = FileChooser.ExtensionFilter("Word 97–2003 (*.doc, *.dot)", "*.doc", "*.dot")
        val odt = FileChooser.ExtensionFilter("OpenDocument (*.odt, *.ott)", "*.odt", "*.ott")
        val txt = FileChooser.ExtensionFilter("Текст (*.txt, *.md)", "*.txt", "*.md")
        val html = FileChooser.ExtensionFilter("HTML (*.html, *.htm)", "*.html", "*.htm")
        val rtf = FileChooser.ExtensionFilter("RTF (*.rtf)", "*.rtf")
        val pdf = FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf")
        val books = FileChooser.ExtensionFilter("Книги (*.fb2, *.epub)", "*.fb2", "*.epub")
        val fb2 = FileChooser.ExtensionFilter("FictionBook (*.fb2)", "*.fb2")
        val docs = FileChooser.ExtensionFilter(
            "Документы",
            "*.docx", "*.docm", "*.dotx", "*.doc", "*.dot",
            "*.odt", "*.ott", "*.rtf", "*.pdf", "*.html", "*.htm",
            "*.txt", "*.md", "*.fb2", "*.epub", "*.csv", "*.xml", "*.json"
        )
        val all = FileChooser.ExtensionFilter("Все файлы", "*.*")
        if (saving) dialog.extensionFilters.addAll(docs, wordNew, odt, txt, html, rtf, pdf, fb2, all)
        else dialog.extensionFilters.addAll(docs, wordNew, wordOld, odt, txt, html, rtf, pdf, books, all)
        dialog.selectedExtensionFilter = when (DocumentFormats.kind(session.file)) {
            "docx" -> wordNew
            "doc" -> if (saving) wordNew else wordOld
            "odt" -> odt
            "html" -> html
            "rtf" -> rtf
            "pdf" -> pdf
            "fb2", "epub" -> if (saving) fb2 else books
            else -> if (saving && uiSettings.format == "ODT") odt else if (saving) wordNew else docs
        }
        return dialog
    }

    private fun ensureExtension(file: File, dialog: FileChooser): File {
        if (file.name.contains('.')) return file
        val desc = dialog.selectedExtensionFilter?.description?.lowercase().orEmpty()
        val ext = when {
            desc.contains("docx") || desc.contains("2007") -> ".docx"
            desc.contains("odt") || desc.contains("opendocument") -> ".odt"
            desc.contains("html") -> ".html"
            desc.contains("rtf") -> ".rtf"
            desc.contains("pdf") -> ".pdf"
            desc.contains("fb2") || desc.contains("fiction") -> ".fb2"
            else -> ".txt"
        }
        return File(file.absolutePath + ext)
    }

    private fun insertDate() {
        if (pdfWorkspace.isOpen || fanficMode) return
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        engineView.replaceRange(engineView.caretOffset(), engineView.caretOffset(), stamp)
    }

    private fun setRuler(on: Boolean) {
        if (syncingGuides) return
        syncingGuides = true
        try {
            engineView.showRuler = on
            showRulerPref = on
            if (::menusRef.isInitialized) menusRef.viewRuler.isSelected = on
            if (::ribbonRef.isInitialized) ribbonRef.setPressed(ribbonRef.ruler, on && !fanficMode)
            savePrefs()
            engineView.relayout(false)
            engineView.requestFocus()
        } finally {
            syncingGuides = false
        }
    }

    private fun setGrid(on: Boolean) {
        if (syncingGuides) return
        syncingGuides = true
        try {
            engineView.showGrid = on
            showGridPref = on
            if (::menusRef.isInitialized) menusRef.viewGrid.isSelected = on
            if (::ribbonRef.isInitialized) ribbonRef.setPressed(ribbonRef.grid, on && !fanficMode)
            savePrefs()
            engineView.relayout(false)
            engineView.requestFocus()
        } finally {
            syncingGuides = false
        }
    }

    private fun toggleOrientation() {
        if (pdfWorkspace.isOpen || fanficMode) return
        engineView.toggleOrientation()
        ribbonRef.setPressed(ribbonRef.landscape, engineView.document.landscape)
        engineView.requestFocus()
    }

    private fun nudgeMargins(outward: Boolean) {
        if (pdfWorkspace.isOpen || fanficMode) return
        engineView.nudgePageMargins(outward)
        engineView.requestFocus()
    }

    private fun closeDocument() {
        if (atStartCenter || !confirmDiscard()) return
        if (fanficMode) {
            setFanficMode(false)
            uiSettings.openMode = "ФФ"
            uiSettings.save()
        }
        leavePdf()
        session.reset()
        engineView.loadPlain("")
        showStartCenter()
    }

    private fun showStartCenter() {
        if (readMode) {
            readMode = false
            engineView.setZoom(zoomBeforeRead)
            if (::menusRef.isInitialized) {
                menusRef.viewReadMode.isSelected = false
                menusRef.viewPrintLayout.isSelected = true
            }
        }
        atStartCenter = true
        recentDocuments.entries().filterNot { it.file.isFile }.forEach { recentDocuments.remove(it.file) }
        startCenter.refresh()
        rootRef.top = menusRef.bar
        rootRef.center = startCenter
        rootRef.bottom = null
        menusRef.save.isDisable = true
        menusRef.saveAs.isDisable = true
        menusRef.close.isDisable = true
        stage.title = "${UiText.get("Начало", uiSettings.language)} — G134Office"
    }

    private fun showSettings() {
        val previousAuto = uiSettings.scaleAuto
        val previousScale = uiSettings.scale
        if (!SettingsDialog.show(stage, uiSettings, onIcons = { set ->
                uiSettings.iconSet = set
                ribbonRef.setIconSet(set)
                menusRef.setIconSet(set, Theme.pack(currentTheme).popupFg)
                startCenter.applyTheme(Theme.pack(currentTheme))
            }) {
                recentDocuments.clear()
                startCenter.refresh()
            }) return
        if (previousAuto != uiSettings.scaleAuto || previousScale != uiSettings.scale) {
            status.setMessage("Масштаб интерфейса изменится после перезапуска")
        }
        uiSettings.theme?.let { applyTheme(it) }
        applyCaretSettings()
        fanficPlacement = if (uiSettings.placement == "Справа") Orientation.HORIZONTAL else Orientation.VERTICAL
        ficbook.setPlacement(fanficPlacement)
        fanficPreview.prefWidth = uiSettings.previewWidth.toDouble()
        menusRef.applyLanguage(uiSettings.language)
        startCenter.refresh()
        applyUiAppearance()
        if (atStartCenter) stage.title = "${UiText.get("Начало", uiSettings.language)} — G134Office"
    }

    private fun applyCaretSettings() {
        engineView.applyCaret(uiSettings.caretColor, uiSettings.caretBlinkMs)
    }

    private fun applyUiAppearance() {
        if (!::rootRef.isInitialized) return
        val size = uiSettings.fontSize.toDouble()
        val family = uiSettings.font.takeIf { it.isNotBlank() } ?: "System"
        val base = rootRef.style.replace(Regex("-fx-font-(size|family):[^;]+;?"), "")
        rootRef.style = "$base -fx-font-size: ${size}px; -fx-font-family: '$family';"
    }

    private fun showEditorChrome() {
        rootRef.top = VBox(menusRef.bar, ribbonRef)
        rootRef.bottom = status
        menusRef.save.isDisable = false
        menusRef.saveAs.isDisable = false
        menusRef.close.isDisable = false
    }

    private fun insertSymbol() {
        if (pdfWorkspace.isOpen || fanficMode) return
        SymbolDialog.show(stage, Theme.pack(currentTheme), currentChrome) { symbol ->
            val range = engineView.selectedRange()
            if (range != null) engineView.replaceRange(range.first, range.last + 1, symbol)
            else engineView.replaceRange(engineView.caretOffset(), engineView.caretOffset(), symbol)
            engineView.requestFocus()
        }
    }

    private fun showWordCount() {
        val s = stats()
        val place = engineView.caretPlace()
        val text = engineView.plainText()
        val withoutSpaces = text.count { !it.isWhitespace() }
        val paragraphs = engineView.document.paragraphs.count { it.text.isNotEmpty() }
        themed(Alert(Alert.AlertType.INFORMATION).apply {
            title = "Количество слов"
            headerText = session.displayName()
            contentText = "Страниц: ${place.pages}\nСлов: ${s.words}\nЗнаков (без пробелов): $withoutSpaces\n" +
                "Знаков (с пробелами): ${s.chars}\nАбзацев: $paragraphs\nСтрок: ${s.lines}"
        }).showAndWait()
    }

    private fun setReadMode(on: Boolean) {
        if (pdfWorkspace.isOpen || fanficMode || atStartCenter) {
            readMode = false
            if (::menusRef.isInitialized) {
                menusRef.viewReadMode.isSelected = false
                menusRef.viewPrintLayout.isSelected = true
            }
            return
        }
        if (readMode == on) {
            if (::menusRef.isInitialized) {
                menusRef.viewReadMode.isSelected = on
                menusRef.viewPrintLayout.isSelected = !on
            }
            refreshChrome()
            return
        }
        readMode = on
        if (::menusRef.isInitialized) {
            menusRef.viewReadMode.isSelected = on
            menusRef.viewPrintLayout.isSelected = !on
        }
        if (on) {
            zoomBeforeRead = engineView.zoom
            ribbonRef.isVisible = false
            ribbonRef.isManaged = false
            status.isVisible = false
            status.isManaged = false
            Platform.runLater { if (readMode) engineView.zoomToFitWidth() }
        } else {
            ribbonRef.isVisible = !currentTheme.compactRibbon && menusRef.viewRibbon.isSelected
            ribbonRef.isManaged = ribbonRef.isVisible
            status.isVisible = menusRef.viewStatus.isSelected
            status.isManaged = status.isVisible
            engineView.setZoom(zoomBeforeRead)
        }
        applyNavigation()
        refreshChrome()
        engineView.requestFocus()
    }

    private fun setNavigation(on: Boolean) {
        navigationOn = on && !pdfWorkspace.isOpen && !fanficMode && !atStartCenter
        if (::menusRef.isInitialized) menusRef.viewNavigation.isSelected = navigationOn
        applyNavigation()
        refreshChrome()
        if (navigationOn) refreshNavigation(force = true)
    }

    private fun applyNavigation() {
        val editor = !readMode && !fanficMode && !pdfWorkspace.isOpen && !atStartCenter
        editorHost.left = if (editor && navigationOn) navigationPane else null
        val inspector = editor && ::ribbonRef.isInitialized && currentChrome == UiChrome.MY_OFFICE
        editorHost.right = if (inspector) ribbonRef.inspectorPane else null
    }

    private var navigationKey = ""

    private fun refreshNavigation(force: Boolean = false) {
        if (editorHost.left != navigationPane) return
        val items = engineView.navigation()
        val key = items.joinToString("|") { "${it.kind}:${it.paragraph}:${it.charInParagraph}:${it.depth}:${it.title}" }
        if (!force && key == navigationKey) return
        navigationKey = key
        navigationPane.setEntries(items)
    }

    private fun spacingValue(label: String): Double = when (label) {
        "1,0" -> 1.0
        "1,15" -> 1.15
        "1,5" -> 1.5
        "2,0" -> 2.0
        else -> label.replace(',', '.').toDoubleOrNull() ?: 1.15
    }

    private fun spacingLabel(value: Double): String = when {
        kotlin.math.abs(value - 1.0) < 0.05 -> "1,0"
        kotlin.math.abs(value - 1.15) < 0.05 -> "1,15"
        kotlin.math.abs(value - 1.5) < 0.05 -> "1,5"
        kotlin.math.abs(value - 2.0) < 0.05 -> "2,0"
        else -> "1,15"
    }

    private fun cssColor(color: javafx.scene.paint.Color): String {
        fun channel(value: Double) = (value * 255).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${channel(color.red)}${channel(color.green)}${channel(color.blue)}"
    }

    private fun parseCss(raw: String?): javafx.scene.paint.Color? {
        if (raw.isNullOrBlank()) return null
        val hex = if (raw.startsWith("#")) raw else "#$raw"
        return runCatching { javafx.scene.paint.Color.web(hex) }.getOrNull()
    }

    private fun about() {
        AboutDialog.create(stage, Theme.pack(currentTheme), ::openProjectPage).showAndWait()
    }

    private fun openProjectPage() {
        val address = AboutDialog.PROJECT_URL
        val desktop = if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop() }.getOrNull()
        } else {
            null
        }
        if (desktop == null || !runCatching { desktop.isSupported(Desktop.Action.BROWSE) }.getOrDefault(false)) {
            showProjectAddress(address)
            return
        }
        runCatching { desktop.browse(URI.create(address)) }
            .onFailure { showProjectAddress(address) }
    }

    private fun showProjectAddress(address: String) {
        themed(Alert(Alert.AlertType.INFORMATION).apply {
            title = "О программе"
            headerText = "Открыть на GitHub"
            contentText = address
        }).showAndWait()
    }

    private fun changeFont(delta: Double) {
        engineView.bumpFontSize(delta)
        val (family, size) = engineView.typingFont()
        fontFamilyPref = family
        fontSize = size
        savePrefs()
        engineView.requestFocus()
    }

    private fun refreshChrome() {
        if (atStartCenter) {
            stage.title = "${UiText.get("Начало", uiSettings.language)} — G134Office"
            return
        }
        if (fanficMode && ::ficbook.isInitialized) {
            val s = stats()
            ficbook.refreshStats(s.words, s.chars, FanficFormatter.sizePages(s.chars))
            stage.title = "Книга фанфиков — ${ficbook.meta.displayTitle()}"
            return
        }
        stage.title = session.windowTitle()
        val s = stats()
        val family = shownFamily.ifBlank { fontFamilyPref }
        val place = engineView.caretPlace()
        if (::ribbonRef.isInitialized) {
            ribbonRef.setPressed(ribbonRef.ruler, engineView.showRuler && !fanficMode)
            ribbonRef.setPressed(ribbonRef.grid, engineView.showGrid && !fanficMode)
            ribbonRef.setPressed(ribbonRef.fanficMode, fanficMode)
            ribbonRef.setPressed(ribbonRef.landscape, engineView.document.landscape)
            ribbonRef.setPressed(ribbonRef.strike, engineView.caretStrike())
            val outline = engineView.caretOutline()
            ribbonRef.setPressed(ribbonRef.styleNormal, outline == 0)
            ribbonRef.setPressed(ribbonRef.styleHeading1, outline == 1)
            ribbonRef.setPressed(ribbonRef.styleHeading2, outline == 2)
            ribbonRef.setPressed(ribbonRef.printLayout, !readMode)
            ribbonRef.setPressed(ribbonRef.readMode, readMode)
            ribbonRef.setPressed(ribbonRef.navigation, navigationOn && !readMode)
            ribbonRef.setPressed(ribbonRef.formatPainter, engineView.formatBrushArmed())
            ribbonRef.showZoom(engineView.zoomPercent())
            suppressFontUi = true
            ribbonRef.showLineSpacing(spacingLabel(engineView.caretSpacing()))
            if (!ribbonRef.fontColor.isShowing) {
                parseCss(engineView.caretColorCss())?.let { ribbonRef.fontColor.value = it }
            }
            suppressFontUi = false
            refreshNavigation()
        }
        if (pdfWorkspace.isOpen) {
            status.update(
                session.displayPath(),
                pdfWorkspace.currentPageIndex + 1, pdfWorkspace.pageCount.coerceAtLeast(1),
                1, 1, 1, 0, 0, 100,
                languageTitle = docLang.title,
                themeTitle = currentTheme.title,
                alignTitle = "PDF",
                fontTitle = "",
                summary = pdfWorkspace.statusText(),
                showCaret = false
            )
        } else {
            status.update(
                session.displayPath(),
                place.page, place.pages, place.line, place.column,
                s.lines, s.words, s.chars, engineView.zoomPercent(),
                languageTitle = docLang.title,
                themeTitle = currentTheme.title,
                alignTitle = when (textAlign) {
                    "center" -> "По центру"
                    "right" -> "Справа"
                    "justify" -> "По ширине"
                    else -> "Слева"
                },
                fontTitle = "$family ${shownSize.toInt()}"
            )
        }
        status.applyTheme(Theme.pack(currentTheme))
    }

    private fun stats(): Stats {
        val counted = engineView.document.textStats()
        return Stats(1, 1, counted.lines, counted.chars, counted.words)
    }

    private fun saveFanficChapter() {
        if (pdfWorkspace.isOpen) return
        val source = session.file
        val chooser = FileChooser().apply {
            title = "Сохранить главу для Фикбук…"
            extensionFilters.add(FileChooser.ExtensionFilter("HTML (*.html)", "*.html"))
            initialFileName = "${source?.nameWithoutExtension ?: "глава"}-ficbook.html"
            source?.parentFile?.takeIf { it.isDirectory }?.let { initialDirectory = it }
        }
        val chosen = chooser.showSaveDialog(stage) ?: return
        val html = if (chosen.extension.equals("html", true)) chosen else File(chosen.parentFile, "${chosen.name}.html")
        val targets = FanficChapterExporter.targets(html)
        if (source != null && (source.canonicalFile == targets.html.canonicalFile ||
                    source.canonicalFile == targets.markdown.canonicalFile)) {
            status.setMessage("Выберите имя, отличное от исходного файла")
            return
        }
        if (targets.markdown.exists()) {
            val answer = themed(Alert(Alert.AlertType.CONFIRMATION,
                "Файл ${targets.markdown.name} уже существует. Перезаписать?",
                ButtonType.OK, ButtonType.CANCEL)).showAndWait()
            if (answer.orElse(ButtonType.CANCEL) != ButtonType.OK) return
        }
        try {
            lastFanficChapter = FanficChapterExporter.save(engineView.document, html, html.nameWithoutExtension)
            status.setMessage("Глава сохранена: ${html.name}")
        } catch (ex: Exception) {
            error("Не удалось сохранить главу", ex)
        }
    }

    private fun commitFanficChapter() {
        if (!File(System.getProperty("user.dir"), ".git").exists()) {
            status.setMessage("git не найден")
            return
        }
        val files = lastFanficChapter
        if (files == null) {
            status.setMessage("Сначала сохраните главу")
            return
        }
        Thread {
            val result = FanficChapterGit.commit(File(System.getProperty("user.dir")), files)
            Platform.runLater { status.setMessage(result) }
        }.apply { isDaemon = true; start() }
    }

    private fun applyFontAppearance() {
        if (suppressFontUi) return
        val family = ribbonRef.fontFamily.value ?: fontFamilyPref
        val size = ribbonRef.fontSizeBox.value?.toDoubleOrNull() ?: fontSize
        fontFamilyPref = family
        fontSize = size
        shownFamily = family
        shownSize = size
        engineView.applyCharacterFont(family, size)
        savePrefs()
    }

    private fun <T : Alert> themed(alert: T): T {
        alert.dialogPane.stylesheets.setAll(Theme.dialogStylesheet(Theme.pack(currentTheme)))
        return alert
    }

    private fun error(header: String, ex: Exception) {
        themed(Alert(Alert.AlertType.ERROR, ex.message).apply {
            title = "Ошибка"
            headerText = header
        }).showAndWait()
        ex.printStackTrace()
    }

    private data class Stats(val line: Int, val col: Int, val lines: Int, val chars: Int, val words: Int)
}
