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
import java.io.File
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
    private var currentTheme = AppTheme.DARK
    private var textAlign = "left"
    private var docLang = DocLanguage.RU
    private var showRulerPref = false
    private var showGridPref = false
    private var scrollWithSpacePref = false
    private var rulerBeforeOpenOffice: Boolean? = null

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
        engineView.onZoomRequested = { setFanficZoom(it) }
        pdfWorkspace.onStateChanged = {
            if (pdfWorkspace.isOpen) session.dirty = pdfWorkspace.dirty
            refreshChrome()
        }
        loadPrefs()
        uiSettings.theme?.let { currentTheme = it }
    }

    fun show(startupPath: String? = null) {
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
        ribbonRef.showTheme(currentTheme)
        ribbonRef.langBox.value = docLang
        bind(menusRef, ribbonRef)
        ficbook = FicbookWorkspace(engineView, fanficPreview, object : FicbookWorkspace.Host {
            override fun exitFanfic() = setFanficMode(false)
            override fun saveDocument() { saveDocument(false) }
            override fun saveChapter() = saveFanficChapter()
            override fun find() { findDialog.show(stage, false) }
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

        rootRef = BorderPane().apply {
            top = VBox(menusRef.build(), ribbonRef)
            center = engineView
            bottom = status
        }

        stage.scene = Scene(rootRef, 1200.0, 760.0).apply {
            // Прозрачный слой нужен стеклянной теме, чтобы просвечивал рабочий стол.
            fill = Color.TRANSPARENT
        }
        applyUiAppearance()
        stage.scene.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
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
        applyTheme(currentTheme)
        showStartCenter()
        stage.show()
        spellWatch = SpellWatch(engineView) { docLang }
        spellWatch.attach()
        SpellEngine.warmup(docLang)
        Platform.runLater {
            if (startupPath != null) {
                openDocument(File(startupPath))
            }
        }
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
        menus.find.setOnAction { if (pdfWorkspace.isOpen) pdfWorkspace.focusSearch() else findDialog.show(stage, false) }
        menus.replace.setOnAction { if (pdfWorkspace.isOpen) pdfWorkspace.focusSearch() else findDialog.show(stage, true) }
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

        menus.viewRuler.setOnAction {
            engineView.showRuler = menus.viewRuler.isSelected
            showRulerPref = engineView.showRuler
            savePrefs()
            engineView.relayout(false)
            engineView.requestFocus()
        }
        menus.viewGrid.setOnAction {
            engineView.showGrid = menus.viewGrid.isSelected
            showGridPref = engineView.showGrid
            savePrefs()
            engineView.relayout(false)
            engineView.requestFocus()
        }
        menus.viewRibbon.setOnAction {
            ribbon.isVisible = menus.viewRibbon.isSelected
            ribbon.isManaged = menus.viewRibbon.isSelected
        }
        menus.viewStatus.setOnAction {
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
        ribbon.ruler.setOnAction {
            engineView.showRuler = !engineView.showRuler
            showRulerPref = engineView.showRuler
            savePrefs()
            if (::menusRef.isInitialized) menusRef.viewRuler.isSelected = engineView.showRuler
            engineView.relayout(false)
            engineView.requestFocus()
        }
        ribbon.grid.setOnAction {
            engineView.showGrid = !engineView.showGrid
            showGridPref = engineView.showGrid
            savePrefs()
            if (::menusRef.isInitialized) menusRef.viewGrid.isSelected = engineView.showGrid
            engineView.relayout(false)
            engineView.requestFocus()
        }
        ribbon.grow.setOnAction { changeFont(1.0) }
        ribbon.shrink.setOnAction { changeFont(-1.0) }
        ribbon.bold.setOnAction { toggleBold() }
        ribbon.italic.setOnAction {
            engineView.document.paragraphs.forEach { paragraph ->
                val next = !paragraph.runs.all { it.italic }
                paragraph.setRuns(paragraph.runs.map { it.copy(italic = next) })
            }
            engineView.relayout()
        }
        ribbon.underline.setOnAction {
            engineView.document.paragraphs.forEach { paragraph ->
                val next = !paragraph.runs.all { it.underline }
                paragraph.setRuns(paragraph.runs.map { it.copy(underline = next) })
            }
            engineView.relayout()
        }
        ribbon.alignLeft.onAction = menus.alignLeft.onAction
        ribbon.alignCenter.onAction = menus.alignCenter.onAction
        ribbon.alignRight.onAction = menus.alignRight.onAction
        ribbon.alignJustify.onAction = menus.alignJustify.onAction
        ribbon.fontFamily.setOnAction { applyFontAppearance() }
        ribbon.fontSizeBox.setOnAction { applyFontAppearance() }
        ribbon.themeFamilyBox.setOnAction { applyTheme(ribbon.resolvedTheme()) }
        ribbon.wordYearBox.setOnAction {
            if (ribbon.themeFamilyBox.value == ThemeFamily.WORD) applyTheme(ribbon.resolvedTheme())
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
            fontSize = p.getProperty("fontSize")?.toDoubleOrNull() ?: 16.0
            fontFamilyPref = p.getProperty("fontFamily") ?: "Segoe UI"
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

    private fun toggleBold() {
        engineView.document.paragraphs.forEach { it.bold = !it.bold }
        engineView.relayout()
        engineView.requestFocus()
    }

    private fun applyTheme(theme: AppTheme) {
        if (fanficMode) return
        currentTheme = theme
        uiSettings.theme = theme
        uiSettings.save()
        menusRef.selectTheme(theme)
        val pack = Theme.pack(theme)
        val chrome = UiChrome.of(theme)
        menusRef.setChrome(chrome)
        ribbonRef.setChrome(chrome)
        status.setChrome(chrome)
        engineView.setOfficeMode(chrome)
        if (chrome == UiChrome.OPEN_OFFICE || chrome == UiChrome.MY_OFFICE) {
            if (rulerBeforeOpenOffice == null) rulerBeforeOpenOffice = engineView.showRuler
            if (!engineView.showRuler) {
                engineView.showRuler = true
                menusRef.viewRuler.isSelected = true
            }
        } else if (rulerBeforeOpenOffice != null) {
            engineView.showRuler = rulerBeforeOpenOffice == true
            showRulerPref = engineView.showRuler
            menusRef.viewRuler.isSelected = engineView.showRuler
            rulerBeforeOpenOffice = null
        }
        rootRef.style = if (theme == AppTheme.GLASS) {
            "-fx-background-color: transparent; -fx-background-radius: 22; " +
                "-fx-border-color: rgba(210,225,255,0.42); -fx-border-width: 1; " +
                "-fx-border-radius: 22; -fx-padding: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(8,15,32,0.55), 26, 0.35, 0, 8);"
        } else {
            "-fx-background-color: ${pack.windowBg};"
        }
        applyUiAppearance()
        menusRef.applyTheme(pack)
        menusRef.applyLanguage(uiSettings.language)
        ribbonRef.applyTheme(pack)
        status.applyTheme(pack)
        ribbonRef.isVisible = !theme.compactRibbon && menusRef.viewRibbon.isSelected
        ribbonRef.isManaged = ribbonRef.isVisible
        ribbonRef.showTheme(theme)
        val family = ribbonRef.fontFamily.value ?: fontFamilyPref
        fontFamilyPref = family
        val size = ribbonRef.fontSizeBox.value?.toDoubleOrNull() ?: fontSize
        fontSize = size
        engineView.applyDesk(pack.windowBg)
        applyCaretSettings()
        engineView.relayout(false)
        Theme.applyCss(stage.scene, pack)
        stage.opacity = if (theme == AppTheme.GLASS) 0.90 else 1.0
        pdfWorkspace.applyTheme(pack)
        savePrefs()
        refreshChrome()
    }

    private fun setFanficMode(on: Boolean) {
        if (pdfWorkspace.isOpen) {
            menusRef.viewFanfic.isSelected = false
            return
        }
        if (fanficMode == on) return
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
            rootRef.center = engineView
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
        if (!file.isFile) {
            recentDocuments.remove(file)
            startCenter.refresh()
            error("Не смог открыть файл", IllegalArgumentException("Файл не найден: ${file.absolutePath}"))
            return
        }
        try {
            if (DocumentFormats.kind(file) == "pdf") {
                setFanficMode(false)
                enterPdf(file)
            } else {
                val document = DocumentFormats.readDocument(file)
                fanficSplit?.items?.clear()
                leavePdf()
                engineView.loadDocument(document)
                session.markSaved(file)
                atStartCenter = false
                showEditorChrome()
                showDocumentView()
                if (uiSettings.openMode == "ФФ") setFanficMode(true)
                engineView.requestFocus()
            }
            if (session.file == file) recentDocuments.add(file)
            refreshChrome()
        } catch (ex: Exception) {
            error("Не смог открыть файл", ex)
        }
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
            showEditorChrome()
        }
        rootRef.center = if (on) pdfWorkspace else engineView
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
        val alert = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "G134Office"
            headerText = "Сохранить изменения в «${session.displayName()}»?"
        }
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
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        engineView.replaceRange(engineView.caretOffset(), engineView.caretOffset(), stamp)
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
        if (!SettingsDialog.show(stage, uiSettings) {
                recentDocuments.clear()
                startCenter.refresh()
            }) return
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
        val size = uiSettings.fontSize * uiSettings.scale / 100.0
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
        SymbolDialog.show(stage, Theme.pack(currentTheme), UiChrome.of(currentTheme)) { symbol ->
            val range = engineView.selectedRange()
            if (range != null) engineView.replaceRange(range.first, range.last + 1, symbol)
            else engineView.replaceRange(engineView.caretOffset(), engineView.caretOffset(), symbol)
            engineView.requestFocus()
        }
    }

    private fun showWordCount() {
        val s = stats()
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "Количество слов"
            headerText = session.displayName()
            contentText = "Слов: ${s.words}\nСтрок: ${s.lines}\nЗнаков: ${s.chars}"
        }.showAndWait()
    }

    private fun about() {
        Alert(Alert.AlertType.INFORMATION).apply {
            title = "О программе"
            headerText = "G134Office"
            contentText = "Редактор на своём движке страниц. PDF — отдельно."
        }.showAndWait()
    }

    private fun changeFont(delta: Double) {
        fontSize = (fontSize + delta).coerceIn(10.0, 42.0)
        val shown = fontSize.toInt().toString()
        if (!ribbonRef.fontSizeBox.items.contains(shown)) {
            ribbonRef.fontSizeBox.items.add(shown)
            ribbonRef.fontSizeBox.items.sortWith(compareBy { it.toIntOrNull() ?: 0 })
        }
        ribbonRef.fontSizeBox.value = shown
        applyTheme(currentTheme)
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
        val family = if (::ribbonRef.isInitialized) ribbonRef.fontFamily.value ?: fontFamilyPref else fontFamilyPref
        if (pdfWorkspace.isOpen) {
            status.update(
                session.displayPath(),
                pdfWorkspace.currentPageIndex + 1, 1, pdfWorkspace.pageCount, 0, 0,
                languageTitle = docLang.title,
                themeTitle = currentTheme.title,
                alignTitle = "PDF",
                fontTitle = pdfWorkspace.statusText()
            )
        } else {
            status.update(
                session.displayPath(),
                s.line, s.col, s.lines, s.chars, s.words,
                languageTitle = docLang.title,
                themeTitle = currentTheme.title,
                alignTitle = when (textAlign) {
                    "center" -> "По центру"
                    "right" -> "Справа"
                    "justify" -> "По ширине"
                    else -> "Слева"
                },
                fontTitle = "$family ${fontSize.toInt()}"
            )
        }
        status.applyTheme(Theme.pack(currentTheme))
    }

    private fun stats(): Stats {
        val text = engineView.plainText()
        var lines = 1
        var words = 0
        var insideWord = false
        for (char in text) {
            if (char == '\n') lines++
            if (char.isWhitespace()) insideWord = false
            else if (!insideWord) {
                words++
                insideWord = true
            }
        }
        return Stats(1, 1, lines, text.length, words)
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
            val answer = Alert(Alert.AlertType.CONFIRMATION,
                "Файл ${targets.markdown.name} уже существует. Перезаписать?",
                ButtonType.OK, ButtonType.CANCEL).showAndWait()
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
        val family = ribbonRef.fontFamily.value ?: fontFamilyPref
        val size = ribbonRef.fontSizeBox.value?.toDoubleOrNull() ?: fontSize
        fontFamilyPref = family
        fontSize = size
        engineView.document.paragraphs.forEach {
            it.fontFamily = family
            it.fontSize = size
        }
        engineView.relayout()
        savePrefs()
    }

    private fun error(header: String, ex: Exception) {
        Alert(Alert.AlertType.ERROR, ex.message).apply {
            title = "Ошибка"
            headerText = header
        }.showAndWait()
        ex.printStackTrace()
    }

    private data class Stats(val line: Int, val col: Int, val lines: Int, val chars: Int, val words: Int)
}
