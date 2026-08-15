package dev.daanyaal.truescreentime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Turning scorecards into something a chat app will carry, and reading them
 * back out again.
 */
object ScorecardSharing {

    /** Writes the card to cache and returns a URI other apps may read. */
    fun writeShareableImage(context: Context, bitmap: Bitmap): Uri {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, "scorecard.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
    }

    /**
     * Decodes an image picked from the gallery or shared into the app.
     * Downsampled first: chat images are large and we only need the QR.
     */
    fun readQrFromImage(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return QrCodec.decode(bitmap)
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (e: Exception) {
        null // unreadable or revoked URI
    }

    /** Keeps the long edge near 1600px: plenty for a QR, cheap to decode. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest > 1600) {
            sample *= 2
            longest /= 2
        }
        return sample
    }
}
