package org.example.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

internal class RecentDocuments {
    data class Entry(val file: File, val openedAt: Long)

    private val prefs = Preferences.userNodeForPackage(RecentDocuments::class.java).node("recent")
    private val recentLimit get() = UiSettings().recentLimit

    fun entries(): List<Entry> = (0 until 30).mapNotNull { index ->
        val path = prefs.get("path.$index", null) ?: return@mapNotNull null
        Entry(File(path), prefs.getLong("date.$index", 0L))
    }.take(recentLimit)

    fun add(file: File) {
        val path = file.absoluteFile.normalize().path
        write(listOf(Entry(File(path), System.currentTimeMillis())) +
            entries().filterNot { it.file.absoluteFile.normalize().path.equals(path, ignoreCase = true) })
    }

    fun remove(file: File) = write(entries().filterNot {
        it.file.absoluteFile.normalize().path.equals(file.absoluteFile.normalize().path, ignoreCase = true)
    })

    fun clear() = write(emptyList())

    private fun write(items: List<Entry>) {
        (0 until 30).forEach { index ->
            val entry = items.getOrNull(index)?.takeIf { index < recentLimit }
            if (entry == null) {
                prefs.remove("path.$index")
                prefs.remove("date.$index")
            } else {
                prefs.put("path.$index", entry.file.path)
                prefs.putLong("date.$index", entry.openedAt)
            }
        }
    }
}

internal class StartCenter(
    private val recent: RecentDocuments,
    private val settings: UiSettings,
    private val onCreate: () -> Unit,
    private val onOpen: () -> Unit,
    private val onSettings: () -> Unit,
    private val onRecent: (File) -> Unit
) : VBox(20.0) {
    private val recentList = VBox(8.0)
    private val heading = Label()
    private val create = Button()
    private val open = Button()
    private val recentHeading = Label()
    private val gear = Button("⚙")
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    init {
        padding = Insets(48.0)
        alignment = Pos.TOP_CENTER
        style = "-fx-background-color: #f3f5f9;"
        val content = VBox(22.0).apply { maxWidth = 780.0 }
        heading.style = "-fx-font-size: 32px; -fx-font-weight: bold;"
        create.apply {
            style = "-fx-font-size: 16px; -fx-padding: 12 24;"
            setOnAction { onCreate() }
        }
        open.apply {
            style = "-fx-font-size: 16px; -fx-padding: 12 24;"
            setOnAction { onOpen() }
        }
        recentHeading.style = "-fx-font-size: 20px; -fx-font-weight: bold;"
        gear.apply { setOnAction { onSettings() }; style = "-fx-font-size: 22px;" }
        val top = HBox(heading, gear).apply { alignment = Pos.CENTER_LEFT; HBox.setHgrow(heading, Priority.ALWAYS) }
        content.children.addAll(top, HBox(12.0, create, open), recentHeading, recentList)
        children.add(content)
        refresh()
    }

    fun refresh() {
        val t = { key: String -> UiText.get(key, settings.language) }
        heading.text = t("Начало")
        create.text = t("Создать документ")
        open.text = t("Открыть…")
        recentHeading.text = t("Недавние файлы")
        gear.tooltip = javafx.scene.control.Tooltip(t("Настройки"))
        recentList.children.clear()
        val entries = recent.entries()
        if (entries.isEmpty()) {
            recentList.children.add(Label(t("Здесь появятся открытые документы.")))
            return
        }
        entries.forEach { entry ->
            val date = if (entry.openedAt > 0) dateFormat.format(
                Instant.ofEpochMilli(entry.openedAt).atZone(ZoneId.systemDefault())
            ) else ""
            val name = Label(entry.file.name).apply { style = "-fx-font-weight: bold;" }
            val path = Label(entry.file.absolutePath).apply {
                style = "-fx-text-fill: #596579;"
                isWrapText = true
            }
            val details = VBox(3.0, name, path)
            HBox.setHgrow(details, Priority.ALWAYS)
            val row = HBox(16.0, details, Label(date)).apply {
                alignment = Pos.CENTER_LEFT
                padding = Insets(12.0)
                style = "-fx-background-color: white; -fx-background-radius: 7;"
                setOnMouseClicked { onRecent(entry.file) }
            }
            recentList.children.add(row)
        }
    }
}
