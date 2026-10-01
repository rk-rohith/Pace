package com.pace.tracker.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * All photos live in app-private storage (filesDir/photos) – never in the shared gallery.
 * Imports are downscaled to [MAX_EDGE] px and re-encoded as JPEG; EXIF rotation is applied by ImageDecoder.
 */
class PhotoStorage(private val context: Context) {

    /** App-private photo folder; everything in it is included in backups. */
    val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Temporary file for CameraX to write the full-size capture into. */
    fun newCaptureFile(): File = File(context.cacheDir, "capture_${UUID.randomUUID()}.jpg")

    suspend fun importUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        saveFrom(source)
    }

    suspend fun importCapture(file: File): String = withContext(Dispatchers.IO) {
        try {
            saveFrom(ImageDecoder.createSource(file))
        } finally {
            file.delete()
        }
    }

    private fun saveFrom(source: ImageDecoder.Source): String {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return out.absolutePath
    }

    fun delete(path: String) {
        val f = File(path)
        // Only ever delete inside our private photo directory.
        if (f.canonicalPath.startsWith(dir.canonicalPath)) f.delete()
    }

    companion object {
        const val MAX_EDGE = 1600
    }
}
