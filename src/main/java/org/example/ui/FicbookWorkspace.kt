package org.example.ui

import javafx.application.Platform
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.concurrent.Worker
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.ProgressBar
import javafx.scene.control.SplitPane
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import javafx.util.Duration
import org.example.engine.Align
import org.example.engine.EditorCanvas
import org.example.fanfic.BrowserSession
import org.example.fanfic.FanficMeta
import org.example.fanfic.FicbookBrowserAuth
import org.example.fanfic.FicbookCookies
import org.example.fanfic.FicbookModeController

class FicbookWorkspace(
    private val editor: EditorCanvas,
    private val preview: FanficPreviewPane,
    private val host: Host
) {
    interface Host {
        fun exitFanfic()
        fun saveDocument()
        fun saveChapter()
        fun find()
        fun spell()
        fun formatText()
        fun undo()
        fun redo()
        fun copy()
        fun paste()
        fun cut()
        fun onSkin(skin: FicbookSkin)
        fun onMetaChanged()
        fun onUrl(url: String)
    }

    val meta = FanficMeta()
    val root = BorderPane()
    val header = VBox()
    val body = StackPane()
    val footer = HBox()
    var isSite = true
        private set
    var startUrl = FicbookModeController.HOME
        set(value) {
            field = value.ifBlank { HOME }
        }

    private val browser = WebView()
    private val engine: WebEngine = browser.engine
    private val address = TextField()
    private val status = Label("Фикбук")
    private val progress = ProgressBar(0.0)
    private val back = tool("←", "Назад")
    private val forward = tool("→", "Вперёд")
    private val reload = tool("↻", "Обновить")
    private val home = tool("Главная", "ficbook.net")
    private val mine = tool("Мои фанфики", "Список ваших работ")
    private val write = tool("Писать", "Новый фанфик")
    private val login = tool("Вход", "Страница входа")
    private val fromBrowser = tool("Войти в браузере", "Открыть вход в браузере Windows и вернуть сессию в приложение")
    private val resetSession = tool("Сбросить сессию", "Удалить локальный вход в Фикбук и открыть сайт заново")
    private val draftBtn = tool("Черновик", "Локальный текст в приложении")
    private val siteBtn = tool("Сайт", "Настоящий Фикбук")
    private val pasteDraft = tool("Копировать черновик", "HTML главы в буфер, чтобы вставить на сайте")
    private val exit = tool("Выйти из ФФ", "Вернуться в редактор")

    private val draftSplit = SplitPane()
    private val editorColumn = VBox(8.0)
    val chapterTitle = TextField().apply {
        promptText = "Название главы"
        text = meta.chapterTitle
    }
    val notes = javafx.scene.control.TextArea().apply {
        promptText = "Примечания к главе"
        prefRowCount = 3
        isWrapText = true
        prefHeight = 72.0
        maxHeight = 96.0
    }
    private val bold = tool("B", "Жирный")
    private val italic = tool("I", "Курсив")
    private val strike = tool("S", "Зачёркнутый")
    private val center = tool("по центру", "По центру")
    private val right = tool("вправо", "По правому краю")
    private val tabIndent = tool("Tab", "Красная строка")
    private val stars = tool("***", "Разрыв сцены")
    private val format = tool("Отформатировать текст", "Ёлочки и тире, как на Фикбуке")
    private val save = tool("Сохранить", "Сохранить файл")
    private val saveChapter = tool("Сохранить главу", "HTML для Фикбука")
    private var pack = FicbookTheme.pack(FicbookSkin.NIGHT)
    private var started = false
    private var browserLoginPoll: Timeline? = null
    private var browserLoginBusy = false
    private var browserLoginScanBusy = false

    init {
        FicbookCookies.install()
        browser.isContextMenuEnabled = true
        browser.minWidth = 0.0
        browser.minHeight = 0.0
        browser.maxWidth = Double.MAX_VALUE
        browser.maxHeight = Double.MAX_VALUE
        StackPane.setAlignment(browser, Pos.TOP_LEFT)
        FicbookModeController.configure(engine)
        engine.loadWorker.stateProperty().addListener { _, _, state ->
            progress.progress = engine.loadWorker.progress
            status.text = when (state) {
                Worker.State.RUNNING -> "Загрузка…"
                Worker.State.SUCCEEDED -> "Фикбук"
                Worker.State.FAILED -> engine.loadWorker.exception?.message ?: "Ошибка загрузки"
                Worker.State.CANCELLED -> "Отменено"
                else -> "Фикбук"
            }
            syncNav()
        }
        engine.loadWorker.progressProperty().addListener { _, _, value ->
            progress.progress = value.toDouble().coerceIn(0.0, 1.0)
        }
        engine.locationProperty().addListener { _, _, loc ->
            if (!loc.isNullOrBlank() && loc != address.text && !address.isFocused) address.text = loc
            if (!loc.isNullOrBlank() && loc.startsWith("http")) {
                startUrl = loc
                host.onUrl(loc)
            }
            syncNav()
        }
        engine.createPopupHandler = javafx.util.Callback { openPopup() }
        engine.onAlert = javafx.event.EventHandler { event ->
            javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, event.data).showAndWait()
        }
        engine.confirmHandler = javafx.util.Callback { message ->
            val alert = javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                message,
                javafx.scene.control.ButtonType.OK,
                javafx.scene.control.ButtonType.CANCEL
            )
            alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL) == javafx.scene.control.ButtonType.OK
        }
        address.setOnAction { go(address.text) }
        back.setOnAction { if (engine.history.currentIndex > 0) engine.history.go(-1) }
        forward.setOnAction {
            val history = engine.history
            if (history.currentIndex + 1 < history.entries.size) history.go(1)
        }
        reload.setOnAction { engine.reload() }
        home.setOnAction { go(HOME); showSite() }
        mine.setOnAction { go(MY_FICS); showSite() }
        write.setOnAction { go(WRITE); showSite() }
        login.setOnAction { go(LOGIN); showSite() }
        fromBrowser.setOnAction { openLoginInDefaultBrowser() }
        resetSession.setOnAction { resetFicbookSession() }
        siteBtn.setOnAction { showSite() }
        // В режиме ФФ всегда используется сайт Ficbook; локальный режим черновика скрыт.
        pasteDraft.setOnAction { host.copy() }
        exit.setOnAction { host.exitFanfic() }
        bold.setOnAction { editor.toggleRunStyle(bold = true); editor.requestFocus() }
        italic.setOnAction { editor.toggleRunStyle(italic = true); editor.requestFocus() }
        strike.setOnAction { editor.toggleRunStyle(strikethrough = true); editor.requestFocus() }
        center.setOnAction { editor.setAlignment(Align.CENTER); editor.requestFocus() }
        right.setOnAction { editor.setAlignment(Align.RIGHT); editor.requestFocus() }
        tabIndent.setOnAction { editor.changeIndent(true); editor.requestFocus() }
        stars.setOnAction { editor.insertSceneBreak(); editor.requestFocus() }
        format.setOnAction { host.formatText() }
        save.setOnAction { host.saveDocument() }
        saveChapter.setOnAction { host.saveChapter() }
        chapterTitle.textProperty().addListener { _, _, value ->
            meta.chapterTitle = value
            host.onMetaChanged()
        }
        notes.textProperty().addListener { _, _, value ->
            meta.notes = value
            host.onMetaChanged()
        }

        val nav = HBox(4.0).apply {
            alignment = Pos.CENTER_LEFT
            padding = Insets(4.0, 8.0, 4.0, 8.0)
            minHeight = 32.0
            prefHeight = 32.0
            maxHeight = 36.0
        }
        HBox.setHgrow(address, Priority.ALWAYS)
        address.promptText = "ficbook.net"
        nav.children.addAll(back, forward, reload, address, fromBrowser, resetSession, exit)
        header.children.add(nav)

        editorColumn.padding = Insets(12.0, 16.0, 12.0, 16.0)
        VBox.setVgrow(editor, Priority.ALWAYS)
        draftSplit.items.addAll(editorColumn, preview)
        draftSplit.setDividerPositions(0.55)

        body.children.addAll(browser, draftSplit)
        body.minWidth = 0.0
        body.minHeight = 0.0
        StackPane.setAlignment(browser, Pos.TOP_LEFT)

        progress.prefWidth = 120.0
        progress.maxHeight = 6.0
        footer.alignment = Pos.CENTER_LEFT
        footer.padding = Insets(4.0, 10.0, 4.0, 10.0)
        footer.spacing = 10.0
        val spacer = Region().also { HBox.setHgrow(it, Priority.ALWAYS) }
        footer.children.addAll(status, spacer, progress)

        root.top = header
        root.center = body
        root.bottom = footer
        root.style = "-fx-background-color: #111111;"

        applySkin(FicbookSkin.NIGHT)
        draftBtn.isVisible = false
        draftBtn.isManaged = false
        siteBtn.isVisible = false
        siteBtn.isManaged = false
        showSite()
    }

    fun attach() {
        if (!editorColumn.children.contains(editor)) {
            editorColumn.children.setAll(
                Label("Название"),
                chapterTitle,
                Label("Текст"),
                draftBar(),
                editor,
                Label("Примечания к главе"),
                notes
            )
        }
        if (!draftSplit.items.contains(editorColumn)) draftSplit.items.add(0, editorColumn)
        if (!draftSplit.items.contains(preview)) draftSplit.items.add(preview)
        draftSplit.orientation = Orientation.HORIZONTAL
        if (!started) {
            started = true
            Platform.runLater { go(startUrl) }
        }
        showSite()
    }

    fun detach() {
        browserLoginPoll?.stop()
        browserLoginPoll = null
        browserLoginBusy = false
        browserLoginScanBusy = false
        fromBrowser.isDisable = false
        editorColumn.children.remove(editor)
        draftSplit.items.clear()
        FicbookCookies.save()
    }

    fun setPlacement(placement: Orientation) {
        draftSplit.orientation = placement
    }

    fun applySkin(skin: FicbookSkin) {
        pack = FicbookTheme.pack(FicbookSkin.NIGHT)
        val p = pack
        header.style = "-fx-background-color: #141414; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;"
        footer.style = "-fx-background-color: #141414; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;"
        body.style = "-fx-background-color: #111111;"
        status.style = "-fx-text-fill: #bdbdbd; -fx-font-size: 12px;"
        address.style = "-fx-background-color: #1c1c1c; -fx-text-fill: #eeeeee; -fx-border-color: #333; " +
            "-fx-background-radius: 6; -fx-border-radius: 6; -fx-font-size: 13px;"
        editorColumn.style = "-fx-background-color: ${p.pageBg};"
        editor.applyFanficPalette(p.pageBg, p.cardBg, p.text, p.brand)
        listOf(
            back, forward, reload, home, mine, write, login, fromBrowser, resetSession, pasteDraft, draftBtn, siteBtn, exit,
            bold, italic, strike, center, right, tabIndent, stars, format, save, saveChapter
        ).forEach { styleTool(it) }
        styleField(chapterTitle)
        notes.style = "-fx-control-inner-background: #1c1c1c; -fx-text-fill: #eeeeee; -fx-border-color: #333;"
        syncModeButtons()
    }

    fun refreshStats(words: Int, chars: Int, pages: Int) {
        if (!isSite) {
            status.text = "$words слов · $chars знаков · $pages стр."
        }
    }

    fun currentPack(): FicbookPack = pack

    fun currentUrl(): String = engine.location?.takeIf { it.startsWith("http") } ?: startUrl

    fun loadMeta(source: FanficMeta) {
        meta.title = source.title
        meta.chapterTitle = source.chapterTitle
        meta.author = source.author
        meta.fandom = source.fandom
        meta.pairing = source.pairing
        meta.direction = source.direction
        meta.rating = source.rating
        meta.status = source.status
        meta.tags = source.tags
        meta.description = source.description
        meta.notes = source.notes
        chapterTitle.text = meta.chapterTitle
        notes.text = meta.notes
    }

    private fun showSite() {
        isSite = true
        browser.isVisible = true
        browser.isManaged = true
        draftSplit.isVisible = false
        draftSplit.isManaged = false
        footer.isVisible = false
        footer.isManaged = false
        syncModeButtons()
        // Creating the editor must not contact Ficbook before the user opens this mode.
        if (started && engine.location.isNullOrBlank()) go(startUrl)
    }

    private fun resetFicbookSession() {
        val confirm = Alert(Alert.AlertType.CONFIRMATION).apply {
            title = "Сброс сессии Фикбука"
            headerText = "Удалить локальную сессию Фикбука?"
            contentText = "Вход в аккаунт в этом приложении будет сброшен. Черновик останется на месте."
            buttonTypes.setAll(ButtonType.OK, ButtonType.CANCEL)
        }
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return

        browserLoginPoll?.stop()
        browserLoginPoll = null
        browserLoginBusy = false
        browserLoginScanBusy = false
        fromBrowser.isDisable = false
        engine.loadWorker.cancel()
        if (!FicbookCookies.clear()) {
            Alert(Alert.AlertType.ERROR, "Не удалось удалить локальные данные сессии Фикбука.").showAndWait()
            return
        }
        startUrl = HOME
        host.onUrl(HOME)
        go(HOME)
        showSite()
    }

    private fun importFromBrowser() {
        showSite()
        status.text = "Ищу вход в Фикбук в браузерах на этом ПК…"
        fromBrowser.isDisable = true
        Thread {
            val found = runCatching { FicbookBrowserAuth.discover() }.getOrElse { error ->
                Platform.runLater {
                    fromBrowser.isDisable = false
                    status.text = "Не удалось прочитать браузеры"
                    Alert(Alert.AlertType.ERROR, error.message ?: error.toString()).showAndWait()
                }
                return@Thread
            }
            Platform.runLater {
                fromBrowser.isDisable = false
                chooseBrowserSession(found)
            }
        }.apply { isDaemon = true; start() }
    }

    private fun openLoginInDefaultBrowser() {
        if (browserLoginBusy) return
        val opened = FicbookModeController.openLoginInDefaultBrowser()
        if (!opened) {
            importFromBrowser()
            return
        }
        browserLoginBusy = true
        fromBrowser.isDisable = true
        status.text = "Войдите в открывшемся браузере. Жду подтверждение входа…"
        val poll = Timeline()
        browserLoginPoll = poll
        poll.keyFrames.add(
            KeyFrame(Duration.seconds(2.0), javafx.event.EventHandler { checkBrowserLogin(poll) })
        )
        poll.cycleCount = 30
        poll.setOnFinished {
            browserLoginBusy = false
            browserLoginScanBusy = false
            fromBrowser.isDisable = false
            if (status.text.startsWith("Войдите в открывшемся")) {
                status.text = "Вход не найден. Нажмите кнопку ещё раз после входа."
            }
        }
        poll.play()
    }

    private fun checkBrowserLogin(poll: Timeline) {
        if (browserLoginScanBusy) return
        browserLoginScanBusy = true
        Thread {
            val sessions = runCatching { FicbookBrowserAuth.discover() }.getOrDefault(emptyList())
            val chosen = sessions.filter { it.cookies.isNotEmpty() }
                .sortedWith(
                    compareByDescending<BrowserSession> { it.browser.equals("Chrome", true) }
                        .thenByDescending { it.cookies.size }
                )
                .firstOrNull()
            if (chosen != null) {
                Platform.runLater {
                    browserLoginScanBusy = false
                    poll.stop()
                    browserLoginPoll = null
                    browserLoginBusy = false
                    fromBrowser.isDisable = false
                    val added = FicbookCookies.replaceFicbook(chosen.cookies)
                    status.text = "Вход получен из ${chosen.browser}: $added cookie"
                    showSite()
                    go(MY_FICS)
                }
            } else {
                Platform.runLater { browserLoginScanBusy = false }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun chooseBrowserSession(found: List<BrowserSession>) {
        val usable = found.filter { it.cookies.isNotEmpty() }
        if (usable.isEmpty()) {
            val details = found.joinToString("\n") { it.label() }
                .ifBlank { "Chrome, Edge, Firefox и Яндекс не отдали сессию." }
            Alert(Alert.AlertType.INFORMATION).apply {
                title = "Вход через браузер"
                headerText = "Сессию Фикбука не удалось взять"
                contentText = details +
                    "\n\nОткройте ficbook.net в Firefox, войдите в аккаунт и нажмите снова. " +
                    "Новый Chrome часто прячет cookies — тогда войдите кнопкой «Вход»."
            }.showAndWait()
            status.text = "Нет сессии в браузерах ПК"
            return
        }
        val dialog = Dialog<BrowserSession>()
        dialog.title = "Вход через браузер ПК"
        dialog.headerText = "Какой браузер уже вошёл на Фикбук?"
        dialog.dialogPane.buttonTypes.addAll(ButtonType.OK, ButtonType.CANCEL)
        val list = ListView<BrowserSession>().apply {
            items.addAll(usable)
            prefHeight = (usable.size * 28.0 + 16.0).coerceIn(80.0, 220.0)
            selectionModel.selectFirst()
            setCellFactory {
                object : javafx.scene.control.ListCell<BrowserSession>() {
                    override fun updateItem(item: BrowserSession?, empty: Boolean) {
                        super.updateItem(item, empty)
                        text = if (empty || item == null) null else item.label()
                    }
                }
            }
        }
        dialog.dialogPane.content = list
        dialog.setResultConverter { button ->
            if (button == ButtonType.OK) list.selectionModel.selectedItem else null
        }
        val chosen = dialog.showAndWait().orElse(null) ?: return
        val added = FicbookCookies.replaceFicbook(chosen.cookies)
        status.text = "Сессия из ${chosen.browser}: $added cookie"
        go(MY_FICS)
    }

    private fun go(raw: String) {
        val trimmed = raw.trim()
        val url = FicbookModeController.normalizeUrl(trimmed)
        address.text = url
        engine.load(url)
    }

    private fun openPopup(): WebEngine {
        val extra = WebView()
        extra.isContextMenuEnabled = true
        FicbookModeController.configure(extra.engine)
        val popup = Stage()
        popup.title = "Фикбук"
        popup.scene = Scene(extra, 780.0, 640.0)
        extra.engine.locationProperty().addListener { _, _, loc ->
            if (loc.isNullOrBlank()) return@addListener
            if (loc.contains("ficbook.net") && !loc.contains("social_login") && !loc.contains("oauth")) {
                Platform.runLater {
                    engine.load(loc)
                    popup.close()
                }
            }
        }
        popup.show()
        return extra.engine
    }

    private fun syncNav() {
        runCatching {
            val history = engine.history
            back.isDisable = history.currentIndex <= 0
            forward.isDisable = history.currentIndex + 1 >= history.entries.size
        }
    }

    private fun syncModeButtons() {
        siteBtn.style = modeStyle(isSite)
        draftBtn.style = modeStyle(!isSite)
    }

    private fun draftBar() = HBox(6.0, italic, bold, strike, tabIndent, stars, format).apply {
        alignment = Pos.CENTER_LEFT
    }

    private fun tool(text: String, tip: String) = Button(text).apply {
        tooltip = Tooltip(tip).apply {
            showDelay = Duration.millis(160.0)
            hideDelay = Duration.millis(80.0)
        }
        minHeight = 24.0
        prefHeight = 24.0
        padding = Insets(2.0, 8.0, 2.0, 8.0)
    }

    private fun styleTool(button: Button) {
        button.style = "-fx-background-color: #2a2a2a; -fx-text-fill: #eeeeee; -fx-border-color: #3a3a3a; " +
            "-fx-background-radius: 6; -fx-border-radius: 6; -fx-font-size: 12px;"
        button.setOnMouseEntered {
            button.style = "-fx-background-color: #3a3a3a; -fx-text-fill: #ffffff; -fx-border-color: #5cb85c; " +
                "-fx-background-radius: 6; -fx-border-radius: 6; -fx-font-size: 12px;"
        }
        button.setOnMouseExited {
            button.style = "-fx-background-color: #2a2a2a; -fx-text-fill: #eeeeee; -fx-border-color: #3a3a3a; " +
                "-fx-background-radius: 6; -fx-border-radius: 6; -fx-font-size: 12px;"
        }
    }

    private fun modeStyle(active: Boolean) = if (active)
        "-fx-background-color: #5cb85c; -fx-text-fill: white; -fx-background-radius: 6; -fx-font-size: 12px; -fx-font-weight: bold;"
    else
        "-fx-background-color: #2a2a2a; -fx-text-fill: #eeeeee; -fx-border-color: #3a3a3a; -fx-background-radius: 6; -fx-font-size: 12px;"

    private fun styleField(field: TextField) {
        field.style = "-fx-background-color: #1c1c1c; -fx-text-fill: #eeeeee; -fx-border-color: #333; " +
            "-fx-background-radius: 4; -fx-border-radius: 4;"
    }

    companion object {
        const val HOME = FicbookModeController.HOME
        const val MY_FICS = FicbookModeController.MY_FICS
        const val WRITE = FicbookModeController.WRITE
        const val LOGIN = FicbookModeController.LOGIN
    }
}
