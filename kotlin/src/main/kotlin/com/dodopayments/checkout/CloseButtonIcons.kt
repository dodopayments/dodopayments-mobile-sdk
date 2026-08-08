package com.dodopayments.checkout

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Draws the Custom Tab's optional back-arrow close icon at runtime, so the
 * SDK doesn't need to ship a drawable resource for one glyph. The color
 * picked here contrasts against [toolbarColor] the same way Chrome's own
 * light/dark icon set would — confirmed on-device that Chrome also
 * auto-tints the supplied bitmap for contrast against the live toolbar, so
 * this is a defensive default rather than the only thing standing between
 * the icon and invisibility.
 */
internal object CloseButtonIcons {
    fun backArrow(context: Context, toolbarColor: Int?): Bitmap {
        val sizePx = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColorFor(toolbarColor)
            style = Paint.Style.STROKE
            strokeWidth = sizePx / 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val inset = sizePx * 0.25f
        val mid = sizePx / 2f
        val path = Path().apply {
            moveTo(sizePx - inset, mid)
            lineTo(inset, mid)
            moveTo(inset + sizePx * 0.22f, mid - sizePx * 0.22f)
            lineTo(inset, mid)
            lineTo(inset + sizePx * 0.22f, mid + sizePx * 0.22f)
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    // Relative-luminance heuristic (unweighted by gamma — good enough for a
    // binary black/white choice), not Chrome's own internal formula.
    private fun iconColorFor(toolbarColor: Int?): Int {
        if (toolbarColor == null) return Color.BLACK
        val r = Color.red(toolbarColor) / 255.0
        val g = Color.green(toolbarColor) / 255.0
        val b = Color.blue(toolbarColor) / 255.0
        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}
