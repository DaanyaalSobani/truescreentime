package dev.daanyaal.truescreentime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Digital-Wellbeing-style donut: pastel arc per app with a small gap between
 * segments, labels around the ring, and the (filtered) total in the center.
 *
 * Tapping a wedge reports its index; double-tapping reports it separately so
 * the host can drill into a pooled "Other" slice.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Segment(val label: String, val value: Long)

    /** Where a wedge ended up on screen, so touches can be matched to it. */
    private data class DrawnArc(
        val index: Int,
        val startAngle: Float,
        val sweepAngle: Float,
    )

    var onSegmentTapped: ((Int) -> Unit)? = null
    var onSegmentDoubleTapped: ((Int) -> Unit)? = null

    private var segments: List<Segment> = emptyList()
    private var centerTitle = ""
    private var centerValue = ""
    private var selectedIndex = -1

    private val drawnArcs = mutableListOf<DrawnArc>()
    private var centerX = 0f
    private var centerY = 0f
    private var innerRadius = 0f
    private var outerRadius = 0f

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

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val index = segmentAt(e.x, e.y) ?: return false
                performClick()
                onSegmentTapped?.invoke(index)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val index = segmentAt(e.x, e.y) ?: return false
                onSegmentDoubleTapped?.invoke(index)
                return true
            }
        }
    )

    fun setData(segments: List<Segment>, centerTitle: String, centerValue: String) {
        this.segments = segments
        this.centerTitle = centerTitle
        this.centerValue = centerValue
        if (selectedIndex >= segments.size) selectedIndex = -1
        invalidate()
    }

    /** Highlights a wedge; pass -1 to clear. */
    fun setSelectedSegment(index: Int) {
        selectedIndex = if (index in segments.indices) index else -1
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Maps a touch to a wedge using polar coordinates, or null if off-ring. */
    private fun segmentAt(x: Float, y: Float): Int? {
        if (drawnArcs.isEmpty()) return null
        val distance = hypot(x - centerX, y - centerY)
        val slop = dp(12f) // forgiving: labels sit just outside the ring
        if (distance < innerRadius - slop || distance > outerRadius + slop) return null

        // drawArc measures degrees clockwise from 3 o'clock, and so does
        // atan2 in view coordinates (y grows downwards), so they agree.
        var angle = Math.toDegrees(
            atan2((y - centerY).toDouble(), (x - centerX).toDouble())
        ).toFloat()
        if (angle < 0f) angle += 360f

        for (arc in drawnArcs) {
            var start = arc.startAngle % 360f
            if (start < 0f) start += 360f
            val delta = ((angle - start) % 360f + 360f) % 360f
            // Include the inter-segment gap so taps on a seam still land.
            if (delta <= arc.sweepAngle + SEGMENT_GAP_DEGREES) return arc.index
        }
        return null
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
        val radius = min(width, height) / 2f - dp(46f) - stroke / 2f
        if (radius <= 0f) return

        centerX = cx
        centerY = cy
        innerRadius = radius - stroke / 2f
        outerRadius = radius + stroke / 2f
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawnArcs.clear()

        val total = segments.sumOf { it.value }
        if (total <= 0L) {
            arcPaint.strokeWidth = stroke
            arcPaint.color = MaterialColors.compositeARGBWithAlpha(onSurfaceVariant, 60)
            canvas.drawArc(arcBounds, 0f, 360f, false, arcPaint)
        } else {
            val visible = segments.withIndex().filter { it.value.value > 0 }
            val gap = if (visible.size > 1) SEGMENT_GAP_DEGREES else 0f
            val available = 360f - gap * visible.size
            var angle = -90f
            visible.forEachIndexed { position, (index, segment) ->
                val sweep = available * segment.value / total
                arcPaint.color = PALETTE[position % PALETTE.size]
                // The selected wedge is drawn thicker so the tap is visible.
                arcPaint.strokeWidth = if (index == selectedIndex) stroke * 1.35f else stroke
                canvas.drawArc(arcBounds, angle, sweep, false, arcPaint)
                drawnArcs += DrawnArc(index, angle, sweep)
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
        const val SEGMENT_GAP_DEGREES = 3f

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
