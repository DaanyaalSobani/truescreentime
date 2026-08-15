package dev.daanyaal.truescreentime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.ceil
import kotlin.math.max

/**
 * Digital-Wellbeing-style week chart: seven rounded bars over horizontal
 * hour gridlines with "0h/2h/…" labels on the right. The selected day's bar
 * is tinted; tapping a bar selects that day.
 */
class WeeklyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onDaySelected: ((Int) -> Unit)? = null

    private var values = LongArray(0)
    private var labels: List<String> = emptyList()
    private var selected = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint().apply { strokeWidth = dp(1f) }
    private val gridTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.LEFT
    }
    private val dayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        textAlign = Paint.Align.CENTER
    }

    /** Any number of bars; labels are matched by position. */
    fun setData(values: LongArray, labels: List<String>, selected: Int) {
        this.values = values.copyOf()
        this.labels = List(values.size) { labels.getOrElse(it) { "" } }
        this.selected = selected
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val chartWidth = width - rightPad()
                if (values.isNotEmpty() && chartWidth > 0 && event.x in 0f..chartWidth) {
                    val index = (event.x / (chartWidth / values.size))
                        .toInt().coerceIn(0, values.lastIndex)
                    performClick()
                    onDaySelected?.invoke(index)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val onSurface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        gridPaint.color = MaterialColors.compositeARGBWithAlpha(onSurfaceVariant, 60)
        gridTextPaint.color = onSurfaceVariant
        dayTextPaint.color = onSurfaceVariant
        val normalBarColor = MaterialColors.compositeARGBWithAlpha(onSurface, 235)

        val topPad = dp(8f)
        val bottomPad = dp(26f)
        val chartWidth = width - rightPad()
        val chartHeight = height - topPad - bottomPad
        if (values.isEmpty() || chartWidth <= 0 || chartHeight <= 0) return

        // Grid: 2-hour steps by default, coarser once days get very long.
        val maxHours = ceil(values.max() / 3_600_000.0).toInt()
        var step = 2
        var gridMax = max(step, ((maxHours + step - 1) / step) * step)
        while (gridMax / step > 4) {
            step += 2
            gridMax = ((maxHours + step - 1) / step) * step
        }
        var h = 0
        while (h <= gridMax) {
            val y = topPad + chartHeight * (1f - h.toFloat() / gridMax)
            canvas.drawLine(0f, y, chartWidth, y, gridPaint)
            canvas.drawText("${h}h", chartWidth + dp(10f), y + gridTextPaint.textSize / 3f, gridTextPaint)
            h += step
        }

        val column = chartWidth / values.size
        val barWidth = column * 0.52f
        val corner = dp(6f)
        val baseline = topPad + chartHeight
        // Long periods cannot fit a label under every bar; thin them out.
        val labelStride = if (values.size > 9) 2 else 1
        for (i in values.indices) {
            val hours = values[i] / 3_600_000f
            val barHeight = max(
                chartHeight * hours / gridMax,
                if (values[i] > 0) dp(6f) else dp(3f)
            )
            val left = i * column + (column - barWidth) / 2f
            barPaint.color = if (i == selected) SELECTED_BAR_COLOR else normalBarColor
            canvas.drawRoundRect(
                left, baseline - barHeight, left + barWidth, baseline, corner, corner, barPaint
            )
            if (i % labelStride == 0 || i == selected) {
                canvas.drawText(
                    labels[i], left + barWidth / 2f, baseline + dp(18f), dayTextPaint
                )
            }
        }
    }

    private fun rightPad(): Float = dp(40f)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        val SELECTED_BAR_COLOR = 0xFFBBC8F1.toInt() // light periwinkle
    }
}
