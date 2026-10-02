package org.example.ui

import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.example.engine.NavKind
import org.example.engine.NavSpot

/** Боковая область навигации: заголовки и страницы, как на вкладке «Вид» в Word. */
class NavigationPane : VBox(6.0) {
    var onPick: ((NavSpot) -> Unit)? = null
    private val title = Label("Навигация")
    private val list = ListView<NavSpot>()
    private var suppress = false
    private var ink = "#1b1b1b"

    init {
        padding = Insets(8.0, 8.0, 8.0, 8.0)
        prefWidth = 228.0
        minWidth = 180.0
        maxWidth = 280.0
        list.fixedCellSize = 28.0
        list.cellFactory = javafx.util.Callback {
            object : ListCell<NavSpot>() {
                override fun updateItem(item: NavSpot?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null
                        style = ""
                        return
                    }
                    text = item.title
                    val pad = if (item.kind == NavKind.HEADING) 8 + (item.depth - 1).coerceAtLeast(0) * 14 else 8
                    val weight = if (item.kind == NavKind.HEADING && item.depth <= 1) "bold" else "normal"
                    style = "-fx-text-fill: $ink; -fx-font-weight: $weight; -fx-padding: 3 8 3 $pad; -fx-background-color: transparent;"
                }
            }
        }
        list.selectionModel.selectedItemProperty().addListener { _, _, item ->
            if (!suppress && item != null) onPick?.invoke(item)
        }
        VBox.setVgrow(list, Priority.ALWAYS)
        children.addAll(title, list)
    }

    fun setEntries(items: List<NavSpot>) {
        val selected = list.selectionModel.selectedItem
        suppress = true
        list.items.setAll(items)
        val match = selected?.let { current ->
            items.firstOrNull {
                it.kind == current.kind && it.paragraph == current.paragraph && it.title == current.title
            }
        }
        if (match != null) list.selectionModel.select(match) else list.selectionModel.clearSelection()
        suppress = false
    }

    fun applyTheme(pack: Theme.Pack) {
        ink = pack.popupFg
        style = "-fx-background-color: ${pack.menuBg}; -fx-border-color: ${pack.border}; -fx-border-width: 0 1 0 0;"
        title.style = "-fx-text-fill: ${pack.labelFg}; -fx-font-size: 12px; -fx-font-weight: bold;"
        list.style = "-fx-background-color: ${pack.popupBg}; -fx-control-inner-background: ${pack.popupBg}; " +
            "-fx-background-insets: 0;"
        list.refresh()
    }
}
