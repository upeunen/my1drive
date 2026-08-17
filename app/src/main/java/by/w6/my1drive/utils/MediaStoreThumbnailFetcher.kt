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
