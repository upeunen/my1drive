package by.w6.my1drive.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance hardware-accelerated MediaStore thumbnail fetcher for Android 10+ (API 29+).
 * Leverages system-cached MediaProvider pre-rendered thumbnails instead of decoding raw full-size files.
 */
class MediaStoreThumbnailFetcher(
    private val uri: Uri,
    private val context: Context,
    private val targetSize: Size = Size(512, 512)
) : Fetcher {

    class Factory(
        private val context: Context,
        private val targetSize: Size = Size(512, 512)
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != MediaStore.AUTHORITY) return null

            val isOriginal = options.size == coil.size.Size.ORIGINAL
            val widthPx = when (val w = options.size.width) {
                is coil.size.Dimension.Pixels -> w.px
                else -> Int.MAX_VALUE
            }
            val heightPx = when (val h = options.size.height) {
                is coil.size.Dimension.Pixels -> h.px
                else -> Int.MAX_VALUE
            }

            // Do NOT downscale full-size or original image requests (e.g. fullscreen viewer)
            if (isOriginal || widthPx > 600 || heightPx > 600) {
                return null
            }

            return MediaStoreThumbnailFetcher(data, context, targetSize)
        }
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null

        try {
            val bitmap = context.contentResolver.loadThumbnail(uri, targetSize, null)
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            // Fallback to standard Coil pipeline if thumbnail generation fails
            null
        }
    }
}
