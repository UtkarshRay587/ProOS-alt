package com.pro444yt.proos

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Draws digits as a 5x7 dot matrix with soft luminous glow on lit and accent dots. */
class DotMatrixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var text: String = ""
        set(value) {
            if (field != value) {
                field = value
                rebuild()
                requestLayout()
                invalidate()
            }
        }

    var pitch: Float = context.dp(8f)
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var litColor: Int = Color.WHITE
    var ghostColor: Int = Color.argb(44, 255, 255, 255)
    var accentColor: Int = Color.parseColor("#FF2E4C")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cells: Array<IntArray> = Array(7) { IntArray(0) }
    private var cols = 0

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun rebuild() {
        val rows = Array(7) { ArrayList<Int>() }
        text.forEachIndexed { i, ch ->
            val g = FONT[ch] ?: FONT[' ']!!
            for (r in 0 until 7) {
                for (c in g[r]) {
                    rows[r].add(if (c == '1') (if (ch == ':') 2 else 1) else 0)
                }
                if (i < text.length - 1) rows[r].add(0)
            }
        }
        cols = rows[0].size
        cells = Array(7) { rows[it].toIntArray() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (cols * pitch).toInt()
        val h = (7 * pitch).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val r = pitch * 0.36f
        glowPaint.maskFilter = BlurMaskFilter(pitch * 0.75f, BlurMaskFilter.Blur.NORMAL)

        for (row in 0 until 7) {
            val line = cells[row]
            for (c in line.indices) {
                val cx = c * pitch + pitch / 2f
                val cy = row * pitch + pitch / 2f
                val state = line[c]

                if (state == 1) {
                    glowPaint.color = Color.argb(95, Color.red(litColor), Color.green(litColor), Color.blue(litColor))
                    canvas.drawCircle(cx, cy, r * 1.35f, glowPaint)
                } else if (state == 2) {
                    glowPaint.color = Color.argb(165, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    canvas.drawCircle(cx, cy, r * 1.55f, glowPaint)
                }

                paint.color = when (state) {
                    1 -> litColor
                    2 -> accentColor
                    else -> ghostColor
                }
                canvas.drawCircle(cx, cy, r, paint)
            }
        }
    }

    companion object {
        private val FONT: Map<Char, Array<String>> = mapOf(
            '0' to arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
            '1' to arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
            '2' to arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
            '3' to arrayOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
            '4' to arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
            '5' to arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
            '6' to arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
            '7' to arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
            '8' to arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
            '9' to arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
            ':' to arrayOf("0", "0", "1", "0", "1", "0", "0"),
            '.' to arrayOf("0", "0", "0", "0", "0", "0", "1"),
            '-' to arrayOf("000", "000", "000", "111", "000", "000", "000"),
            ' ' to arrayOf("00", "00", "00", "00", "00", "00", "00")
        )
    }
}
