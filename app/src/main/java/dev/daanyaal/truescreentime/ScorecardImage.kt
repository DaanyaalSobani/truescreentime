package dev.daanyaal.truescreentime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Renders a scorecard as one image that works for both audiences: a
 * readable leaderboard for the humans in the chat, and a QR code carrying
 * the same data for their phones.
 *
 * Colours are fixed rather than theme-derived — the image is viewed inside
 * someone else's chat app, not inside ours.
 */
object ScorecardImage {

    private const val WIDTH = 1080
    private const val PADDING = 64f
    private const val QR_SIZE = 560

    fun render(
        context: Context,
        rules: GroupRules,
        blocks: Collection<MemberBlock>,
        payload: String,
        highlightMemberId: String,
    ): Bitmap {
        val ordered = ScorecardMerge.leaderboard(blocks)
        val rowHeight = 96f
        val headerHeight = 300f
        val footerHeight = QR_SIZE + 160f
        val height = (headerHeight + ordered.size * rowHeight + footerHeight).toInt()

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREGROUND
            textSize = 64f
            isFakeBoldText = true
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 36f
        }
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREGROUND
            textSize = 44f
        }
        val score = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FOREGROUND
            textSize = 44f
            textAlign = Paint.Align.RIGHT
            isFakeBoldText = true
        }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            textSize = 44f
            isFakeBoldText = true
        }

        var y = 120f
        canvas.drawText(rules.name, PADDING, y, title)
        y += 56f
        canvas.drawText(
            context.getString(R.string.card_subtitle, rules.periodDays),
            PADDING, y, subtitle,
        )

        y = headerHeight
        ordered.forEachIndexed { index, block ->
            val isMe = block.memberId == highlightMemberId
            val label = buildString {
                append("${index + 1}. ")
                append(block.displayName.ifBlank { context.getString(R.string.unnamed_member) })
                if (isMe) append(context.getString(R.string.card_you_suffix))
                if (!block.isFirstHand) append(context.getString(R.string.card_relayed_suffix))
            }
            canvas.drawText(label, PADDING, y, if (isMe) badge else name)
            canvas.drawText(
                TimeFormat.format(context, block.totalMs),
                WIDTH - PADDING, y, score,
            )
            y += rowHeight
        }

        val qr = QrCodec.encode(payload, QR_SIZE)
        if (qr != null) {
            val left = (WIDTH - qr.width) / 2
            val top = (height - qr.height - 80).coerceAtLeast(y.toInt() + 40)
            canvas.drawBitmap(qr, null, Rect(left, top, left + qr.width, top + qr.height), null)
            canvas.drawText(
                context.getString(R.string.card_scan_hint),
                PADDING, (top + qr.height + 56).toFloat(), subtitle,
            )
        } else {
            canvas.drawText(
                context.getString(R.string.card_too_large), PADDING, y + 60f, subtitle
            )
        }
        return bitmap
    }

    private const val BACKGROUND = 0xFF131318.toInt()
    private const val FOREGROUND = Color.WHITE
    private const val MUTED = 0xFF9E9EA8.toInt()
    private const val ACCENT = 0xFFC2CEF4.toInt()
}
