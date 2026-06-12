package by.w6.my1drive.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Model class used with Coil to request an on-demand OTG thumbnail.
 *
 * @param otgUri    SAF Document URI of the file on the OTG drive
 * @param hash      SHA-256 hash used as the cache key
 * @param mimeType  MIME type to choose decoding strategy (image vs video)
 * @param isConnected Whether the OTG drive is currently accessible
 */
data class OtgThumbnailRequest(
    val otgUri: String,
    val hash: String,
    val mimeType: String,
    val isConnected: Boolean,
    // Fallback: path to already-cached preview (e.g. from old format or previous fetch)
    val existingCachePath: String? = null
)

/**
 * Coil [Fetcher] that loads archive thumbnails on demand.
 *
 * Priority order:
 *  1. existingCachePath — already-cached file (any path, old or new)
 *  2. filesDir/my1drive_previews/{hash}.webp — standard new cache location
 *  3. OTG drive accessible → generate thumbnail → cache → return
 *  4. Throw IOException → Coil shows error placeholder
 */
class OtgThumbnailFetcher(
    private val data: OtgThumbnailRequest,
    private val context: Context,
    private val previewDir: File,
    private val onCached: (hash: String, path: String) -> Unit  // notify ViewModel to update DB
) : Fetcher {

    class Factory(
        private val previewDir: File,
        private val onCached: (hash: String, path: String) -> Unit
    ) : Fetcher.Factory<OtgThumbnailRequest> {
        override fun create(
            data: OtgThumbnailRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = OtgThumbnailFetcher(data, options.context, previewDir, onCached)
    }

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // 1. Check existing cache path (old thumbnails dir or previously cached)
        if (data.existingCachePath != null) {
            val existing = File(data.existingCachePath)
            if (existing.exists() && existing.length() > 0) {
                return@withContext sourceResult(existing)
            }
        }

        // 2. Check standard new cache location (uses .my1d extension to hide from other apps)
        val cacheFile = File(previewDir, "${data.hash}.my1d")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            onCached(data.hash, cacheFile.absolutePath)
            return@withContext sourceResult(cacheFile)
        }

        // 3. Load from OTG on demand (only if drive is connected)
        if (!data.isConnected) {
            throw java.io.IOException("OTG drive not connected and no local preview for ${data.hash}")
        }

        val uri = Uri.parse(data.otgUri)
        val bitmap = generateThumbnail(uri)
            ?: throw java.io.IOException("Failed to generate thumbnail from OTG for ${data.hash}")

        // Save to cache
        previewDir.mkdirs()
        try {
            cacheFile.outputStream().buffered().use { out ->
                // Scale to max 256×256 keeping aspect ratio
                val scaled = scaleBitmap(bitmap, 256)
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 65, out)
                if (scaled !== bitmap) scaled.recycle()
            }
            bitmap.recycle()
        } catch (e: Exception) {
            cacheFile.delete()
            bitmap.recycle()
            throw java.io.IOException("Failed to write preview cache: ${e.message}")
        }

        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            throw java.io.IOException("Cache file empty after write for ${data.hash}")
        }

        onCached(data.hash, cacheFile.absolutePath)
        return@withContext sourceResult(cacheFile)
    }

    private fun generateThumbnail(uri: Uri): Bitmap? {
        return if (data.mimeType.startsWith("video")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        } else {
            // Two-pass decode: first get dimensions, then sub-sample
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOpts)
            }

            val sampleSize = calculateSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 256)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            }
        }
    }

    private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun calculateSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var size = 1
        if (width > reqSize || height > reqSize) {
            val halfW = width / 2
            val halfH = height / 2
            while (halfW / size >= reqSize && halfH / size >= reqSize) size *= 2
        }
        return size
    }

    private fun sourceResult(file: File): SourceResult = SourceResult(
        source = ImageSource(file.toOkioPath(), okio.FileSystem.SYSTEM),
        mimeType = "image/webp",
        dataSource = DataSource.DISK
    )
}
