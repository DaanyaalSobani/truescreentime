package dev.daanyaal.truescreentime

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR encoding and decoding.
 *
 * Error correction is set high on purpose: shared cards get recompressed by
 * chat apps, and a JPEG-mangled code still has to scan.
 */
object QrCodec {

    fun encode(text: String, sizePx: Int): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
        }
    } catch (e: WriterException) {
        null // payload too large for a QR code
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Reads the first QR code in an image, or null if there is none. */
    fun decode(bitmap: Bitmap): String? {
        val source = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val binary = BinaryBitmap(
            HybridBinarizer(RGBLuminanceSource(source.width, source.height, pixels))
        )
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                )
            )
        }
        return try {
            reader.decodeWithState(binary).text
        } catch (e: Exception) {
            null // NotFoundException and friends: no readable code here
        }
    }
}
