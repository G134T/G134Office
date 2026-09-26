package org.example.document

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.effect.DropShadow
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

class PdfPageTile(var index: Int) : StackPane() {
    val imageView = ImageView().apply {
        isPreserveRatio = true
        isSmooth = true
    }
    val overlay = Pane()
    private val placeholder = Label()
    var pageWidthPts = 595f
    var pageHeightPts = 842f
    var dpi = 110f
        private set

    init {
        alignment = Pos.CENTER
        style = "-fx-background-color: white;"
        effect = DropShadow(16.0, Color.rgb(0, 0, 0, 0.38))
        placeholder.style = "-fx-text-fill: #888888; -fx-font-size: 13px;"
        children.addAll(placeholder, imageView, overlay)
        isFocusTraversable = true
    }

    fun layoutFor(dpi: Float, widthPts: Float, heightPts: Float) {
        this.dpi = dpi
        pageWidthPts = widthPts
        pageHeightPts = heightPts
        val w = (widthPts * dpi / 72f).toDouble()
        val h = (heightPts * dpi / 72f).toDouble()
        prefWidth = w
        prefHeight = h
        minWidth = w
        minHeight = h
        maxWidth = w
        maxHeight = h
        overlay.prefWidth = w
        overlay.prefHeight = h
        overlay.minWidth = w
        overlay.minHeight = h
        overlay.maxWidth = w
        overlay.maxHeight = h
        placeholder.text = "Стр. ${index + 1}"
        if (imageView.image != null) {
            imageView.fitWidth = w
            imageView.fitHeight = h
        }
    }

    fun showImage(image: Image) {
        imageView.image = image
        imageView.fitWidth = prefWidth
        imageView.fitHeight = prefHeight
        placeholder.isVisible = false
    }

    fun clearImage() {
        imageView.image = null
        placeholder.isVisible = true
    }

    fun toPdfX(localX: Double): Float = (localX * 72.0 / dpi).toFloat()

    fun toPdfYTop(localY: Double): Float = (localY * 72.0 / dpi).toFloat()

    fun overlayRect(x: Float, yTop: Float, w: Float, h: Float): Rectangle {
        val s = dpi / 72f
        return Rectangle(
            (x * s).toDouble(),
            ((yTop - h) * s).toDouble(),
            (w * s).toDouble(),
            (h * s).toDouble()
        )
    }
}
