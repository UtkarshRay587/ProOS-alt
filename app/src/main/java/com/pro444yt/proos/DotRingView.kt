package com.pro444yt.proos

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Ring of 36 glowing dots that fills clockwise. */
class DotRingView(context: Context) : View(context) {

    var percent: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    var accentColor: Int = Color.parseColor("#FF2E4C")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val dotR = size * 0.032f
        val ringR = size / 2f - dotR * 2.1f
        val cx = width / 2f
        val cy = height / 2f
        val n = 36
        val lit = (percent / 100f * n).roundToInt()
        glowPaint.maskFilter = BlurMaskFilter(dotR * 2.2f, BlurMaskFilter.Blur.NORMAL)

        for (i in 0 until n) {
            val a = i.toFloat() / n * 2f * Math.PI.toFloat() - Math.PI.toFloat() / 2f
            val px = cx + ringR * cos(a)
            val py = cy + ringR * sin(a)

            if (i < lit - 1) {
                glowPaint.color = Color.argb(85, 255, 255, 255)
                canvas.drawCircle(px, py, dotR * 1.4f, glowPaint)
                paint.color = Color.WHITE
            } else if (i == lit - 1) {
                glowPaint.color = Color.argb(190, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                canvas.drawCircle(px, py, dotR * 1.8f, glowPaint)
                paint.color = accentColor
            } else {
                paint.color = Color.argb(52, 255, 255, 255)
            }
            canvas.drawCircle(px, py, dotR, paint)
        }
    }
}
