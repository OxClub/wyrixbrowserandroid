package com.wyrix.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A user-chosen home-screen photo, stored privately and re-encoded (which also strips EXIF/location metadata). */
object HomeBackgroundStore {
    private const val MAX_EDGE = 1600
    private const val MAX_INPUT_BYTES = 40L * 1024 * 1024
    private fun file(c: Context) = File(c.filesDir, "home_bg.jpg")

    suspend fun save(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
            var sample = 1
            while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use { s ->
                // Guard against absurd inputs without trusting declared size.
                BitmapFactory.decodeStream(s.buffered(), null, opts)
            } ?: return@runCatching false
            val scale = minOf(1f, MAX_EDGE / maxOf(bmp.width, bmp.height).toFloat())
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            file(context).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }.getOrDefault(false)
    }

    suspend fun load(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) null else runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    fun clear(context: Context) { file(context).delete() }
}
