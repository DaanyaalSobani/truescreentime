package dev.daanyaal.truescreentime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Digital-Wellbeing-style donut: pastel arc per app with a small gap between
 * segments, labels around the ring, and the (filtered) total in the center.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Segment(val label: String, val value: Long)

    private var segments: List<Segment> = emptyList()
    private var centerTitle = ""
    private var centerValue = ""

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(15f)
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(27f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val arcBounds = RectF()

    fun setData(segments: List<Segment>, centerTitle: String, centerValue: String) {
        this.segments = segments
        this.centerTitle = centerTitle
        this.centerValue = centerValue
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val onSurface = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        labelPaint.color = onSurfaceVariant
        titlePaint.color = onSurfaceVariant
        valuePaint.color = onSurface

        val cx = width / 2f
        val cy = height / 2f
        val stroke = dp(26f)
        arcPaint.strokeWidth = stroke
        val radius = min(width, height) / 2f - dp(46f) - stroke / 2f
        if (radius <= 0f) return
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val total = segments.sumOf { it.value }
        if (total <= 0L) {
            arcPaint.color = MaterialColors.compositeARGBWithAlpha(onSurfaceVariant, 60)
            canvas.drawArc(arcBounds, 0f, 360f, false, arcPaint)
        } else {
            val visibleSegments = segments.filter { it.value > 0 }
            val gap = if (visibleSegments.size > 1) 3f else 0f
            val available = 360f - gap * visibleSegments.size
            var angle = -90f
            visibleSegments.forEachIndexed { index, segment ->
                val sweep = available * segment.value / total
                arcPaint.color = PALETTE[index % PALETTE.size]
                canvas.drawArc(arcBounds, angle, sweep, false, arcPaint)
                drawLabel(canvas, segment.label, angle + sweep / 2f, cx, cy, radius, stroke)
                angle += sweep + gap
            }
        }

        canvas.drawText(centerTitle, cx, cy - dp(12f), titlePaint)
        canvas.drawText(centerValue, cx, cy + dp(22f), valuePaint)
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        midAngleDeg: Float,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
    ) {
        val rad = Math.toRadians(midAngleDeg.toDouble())
        val labelRadius = radius + stroke / 2f + dp(10f)
        val x = cx + (cos(rad) * labelRadius).toFloat()
        val y = cy + (sin(rad) * labelRadius).toFloat() + labelPaint.textSize / 3f
        labelPaint.textAlign = when {
            cos(rad) < -0.25 -> Paint.Align.RIGHT
            cos(rad) > 0.25 -> Paint.Align.LEFT
            else -> Paint.Align.CENTER
        }
        val shown = if (text.length > 8) text.take(7) + "…" else text
        canvas.drawText(shown, x, y, labelPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        // Muted Material-You-like pastels, ordered to echo Digital Wellbeing.
        val PALETTE = intArrayOf(
            0xFFC2CEF4.toInt(), // periwinkle
            0xFFB9A7DA.toInt(), // muted purple
            0xFFA9AEBB.toInt(), // gray
            0xFF8FA0C6.toInt(), // slate blue
            0xFFD4BBE5.toInt(), // lilac
            0xFF9EC2B5.toInt(), // sage
            0xFFE0C0AF.toInt(), // sand
        )
    }
}
