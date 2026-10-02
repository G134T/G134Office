package org.example.ui

import org.example.WindowsDisplay
import kotlin.jvm.JvmStatic
import org.example.WindowsIntegration
import java.util.Locale
import java.util.prefs.Preferences
import kotlin.math.abs
import kotlin.math.min

data class WindowBox(
    val width: Double,
    val height: Double,
    val minWidth: Double,
    val minHeight: Double
)

/**
 * Picks the JavaFX Windows UI scale and a window that fits the work area.
 * glass.win.uiScale replaces the system scale, so the returned value already includes Windows DPI.
 */
object UiScreen {
    private val steps = doubleArrayOf(0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 3.0)

    fun glassScale(widthPx: Int, heightPx: Int, dpi: Int, manualPercent: Int?): Double {
        val windows = (dpi.coerceIn(72, 480) / 96.0).coerceIn(0.75, 3.0)
        val boost = if (manualPercent == null) automaticBoost(widthPx, heightPx, windows)
        else manualPercent.coerceIn(75, 200) / 100.0
        return snap(windows * boost)
    }

    fun windowBox(workWidth: Double, workHeight: Double): WindowBox {
        val workW = workWidth.coerceAtLeast(640.0)
        val workH = workHeight.coerceAtLeast(480.0)
        val marginX = if (workW < 1100) 16.0 else 32.0
        val marginY = if (workH < 760) 16.0 else 32.0
        val maxW = (workW - marginX).coerceAtLeast(640.0)
        val maxH = (workH - marginY).coerceAtLeast(480.0)
        val aspect = workW / workH
        val prefW = when {
            maxW <= 1120 -> maxW
            aspect >= 2.0 -> min(maxW * 0.72, 1720.0)
            aspect >= 1.6 -> min(maxW * 0.80, 1500.0)
            else -> min(maxW * 0.90, 1280.0)
        }
        val prefH = if (maxH <= 820) maxH else min(maxH * 0.86, 980.0)
        return WindowBox(
            width = prefW.coerceIn(min(900.0, maxW), maxW),
            height = prefH.coerceIn(min(620.0, maxH), maxH),
            minWidth = min(820.0, maxW),
            minHeight = min(540.0, maxH)
        )
    }

    /** Sets glass.win.uiScale before JavaFX starts. A manual choice is a multiplier on top of Windows DPI. */
    @JvmStatic
    fun install() {
        if (!WindowsIntegration.isWindows()) return
        if (System.getProperty("glass.win.uiScale") != null) return
        val prefs = Preferences.userNodeForPackage(RecentDocuments::class.java)
        val manual = if (prefs.getBoolean("ui.scale.auto", true)) null else prefs.getInt("ui.scale", 100)
        val display = WindowsDisplay.read()
        val scale = glassScale(display.widthPx, display.heightPx, display.dpi, manual)
        System.setProperty("glass.win.uiScale", String.format(Locale.US, "%.2f", scale))
        System.err.println(
            "UI scale $scale for ${display.widthPx}x${display.heightPx} at ${display.dpi} dpi" +
                if (manual == null) " (auto)" else " (manual $manual%)"
        )
    }

    private fun automaticBoost(widthPx: Int, heightPx: Int, windows: Double): Double {
        if (windows >= 1.24) return 1.0
        return when {
            heightPx >= 2000 || widthPx >= 3800 -> 2.0
            heightPx >= 1400 || widthPx >= 2500 -> 1.5
            else -> 1.0
        }
    }

    private fun snap(value: Double): Double {
        val clamped = value.coerceIn(steps.first(), steps.last())
        return steps.minBy { abs(it - clamped) }
    }
}
