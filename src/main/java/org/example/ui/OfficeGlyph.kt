package org.example.ui

import javafx.scene.Group
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.scene.shape.Rectangle
import javafx.scene.shape.SVGPath
import javafx.scene.shape.Shape
import javafx.scene.shape.StrokeLineCap
import javafx.scene.shape.StrokeLineJoin
import javafx.scene.transform.Scale

/**
 * Значок 24×24, вписанный в квадрат [size]. Обводка, не заливка:
 * цвет задаётся снаружи и совпадает с цветом текста темы.
 */
internal class OfficeGlyph(shapes: List<LucideShape>, size: Double) : Group() {
    private val strokes: List<Shape> = shapes.map { spec ->
        val node: Shape = when (spec) {
            is LucideShape.Path -> SVGPath().apply { content = spec.data }
            is LucideShape.Oval -> Circle(spec.cx, spec.cy, spec.r)
            is LucideShape.Segment -> Line(spec.x1, spec.y1, spec.x2, spec.y2)
            is LucideShape.Box -> Rectangle(spec.x, spec.y, spec.w, spec.h).apply {
                arcWidth = spec.rx * 2
                arcHeight = spec.ry * 2
            }
        }
        node.apply {
            fill = Color.TRANSPARENT
            stroke = Color.BLACK
            strokeWidth = 2.0
            strokeLineCap = StrokeLineCap.ROUND
            strokeLineJoin = StrokeLineJoin.ROUND
        }
    }

    init {
        val board = Group(*strokes.toTypedArray())
        board.transforms.add(Scale(size / 24.0, size / 24.0, 0.0, 0.0))
        children.addAll(Rectangle(size, size).apply { fill = Color.TRANSPARENT }, board)
        isMouseTransparent = true
    }

    fun color(next: Color) {
        strokes.forEach { it.stroke = next }
    }

    /** Цвет меню задаёт таблица стилей темы, в том числе при наведении. */
    fun followMenu() {
        strokes.forEach { it.styleClass.add("office-icon") }
    }
}

internal object LucideIcons {
    fun glyph(name: String, size: Double, color: Color): OfficeGlyph {
        val icon = OfficeGlyph(lucideShapes(lucideSvg(name)), size)
        icon.color(color)
        return icon
    }

    fun menuGlyph(name: String, size: Double = 15.0): OfficeGlyph {
        val icon = OfficeGlyph(lucideShapes(lucideSvg(name)), size)
        icon.followMenu()
        return icon
    }
}
