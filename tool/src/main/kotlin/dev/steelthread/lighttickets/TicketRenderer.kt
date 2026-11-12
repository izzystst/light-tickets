package dev.steelthread.lighttickets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object TicketRenderer {
    fun render(file: File, kind: String, page: Int): Bitmap {
        if (kind == "pdf") {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(page).use { pdf ->
                        val factor = min(2400f / pdf.width, min(4000f / pdf.height,
                            sqrt(8_000_000.0 / (pdf.width.toDouble() * pdf.height)).toFloat()))
                        val bitmap = Bitmap.createBitmap((pdf.width * factor).roundToInt().coerceAtLeast(1),
                            (pdf.height * factor).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        try { pdf.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); return bitmap }
                        catch (error: Exception) { bitmap.recycle(); throw error }
                    }
                }
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        var sample = 1
        while (options.outWidth / sample > 4096 || options.outHeight / sample > 4096 ||
            (options.outWidth.toLong() / sample) * (options.outHeight / sample) > 8_000_000) sample *= 2
        options.inSampleSize = sample
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(file.path, options) ?: throw IOException("Image unavailable.")
    }
}
