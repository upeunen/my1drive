package by.w6.my1drive.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    companion object {
        // Serializes OTG reads to prevent disk head thrashing on mechanical hard drives
        private val otgReadSemaphore = Semaphore(1)
    }

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // 1. Check existing cache path (old thumbnails dir or previously cached)
        if (data.existingCachePath != null) {
            val existing = File(data.existingCachePath)
            if (existing.exists() && existing.length() > 0) {
                onCached(data.hash, existing.absolutePath)
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

        // 3a. Fast-path: Check if the small preview file already exists on the OTG drive
        if (tryCopyFromOtgPreviews(uri, data.hash, cacheFile)) {
            onCached(data.hash, cacheFile.absolutePath)
            return@withContext sourceResult(cacheFile)
        }

        // Read sequentially through semaphore to protect mechanical HDD from concurrent seeks
        val bitmap: Bitmap? = otgReadSemaphore.withPermit {
            // Double check if another coroutine already cached it while waiting for the semaphore
            if (cacheFile.exists() && cacheFile.length() > 0) {
                null
            } else {
                generateThumbnail(uri)
            }
        }

        if (cacheFile.exists() && cacheFile.length() > 0) {
            onCached(data.hash, cacheFile.absolutePath)
            return@withContext sourceResult(cacheFile)
        }

        if (bitmap == null) {
            throw java.io.IOException("Failed to generate thumbnail from OTG for ${data.hash}")
        }

        // Save to cache
        previewDir.mkdirs()
        try {
            cacheFile.outputStream().buffered().use { out ->
                // Scale to max 512×512 keeping aspect ratio
                val scaled = scaleBitmap(bitmap, 512)
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, out)
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

        try {
            OtgFolderResolver.trySavePreviewToOtg(context, uri, data.hash, cacheFile)
        } catch (_: Exception) {}

        onCached(data.hash, cacheFile.absolutePath)
        return@withContext sourceResult(cacheFile)
    }

    private fun generateThumbnail(uri: Uri): Bitmap? {
        return if (data.mimeType.startsWith("video")) {
            val retriever = MediaMetadataRetriever()
            try {
                val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
                if (pfd != null) {
                    pfd.use {
                        retriever.setDataSource(it.fileDescriptor)
                        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else {
            try {
                val pfdBitmap = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val fd = pfd.fileDescriptor
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fd, null, boundsOpts)

                    try {
                        android.system.Os.lseek(fd, 0, android.system.OsConstants.SEEK_SET)
                    } catch (_: Exception) {}

                    val sampleSize = calculateSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 512)
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeFileDescriptor(fd, null, decodeOpts)
                }

                pfdBitmap ?: run {
                    // Fallback to stream if openFileDescriptor fails
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, boundsOpts)
                    }

                    val sampleSize = calculateSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 512)
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, decodeOpts)
                    }
                }
            } catch (e: Exception) {
                null
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

    private fun tryCopyFromOtgPreviews(uri: Uri, hash: String, cacheFile: File): Boolean {
        previewDir.mkdirs()
        return OtgFolderResolver.tryCopyPreviewFromOtg(context, uri, hash, cacheFile)
    }
}
