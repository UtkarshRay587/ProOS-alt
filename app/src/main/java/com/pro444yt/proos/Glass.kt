package com.pro444yt.proos

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue

fun Context.dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

fun Context.dpi(v: Int): Int = dp(v.toFloat()).toInt()

/** Frosted-glass look with subtle ambient accent glow and crisp sheen edge. */
object Glass {
    fun make(
        ctx: Context,
        radiusDp: Float,
        fill: Int = 0x2612141C,
        stroke: Int = 0x52FFFFFF,
        glowAccent: Int? = null
    ): LayerDrawable {
        val r = Color.red(glowAccent ?: Color.WHITE)
        val g = Color.green(glowAccent ?: Color.WHITE)
        val b = Color.blue(glowAccent ?: Color.WHITE)

        val effectiveStroke = if (glowAccent != null) Color.argb(115, r, g, b) else stroke
        val auraTop = if (glowAccent != null) Color.argb(54, r, g, b) else 0x36FFFFFF
        val auraMid = if (glowAccent != null) Color.argb(16, r, g, b) else 0x08FFFFFF

        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dp(radiusDp)
            setColor(fill)
            setStroke(ctx.dpi(1), effectiveStroke)
        }
        val glowSheen = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(auraTop, auraMid, 0x00000000)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ctx.dp(radiusDp)
        }
        return LayerDrawable(arrayOf(base, glowSheen))
    }
}
