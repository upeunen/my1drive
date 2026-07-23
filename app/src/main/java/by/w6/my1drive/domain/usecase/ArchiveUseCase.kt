package by.w6.my1drive.domain.usecase

import android.content.Context
import android.widget.Toast
import by.w6.my1drive.data.local.LimitRepository
import by.w6.my1drive.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArchiveUseCase(
    private val context: Context,
    private val limitRepository: LimitRepository
) {
    /**
     * Filters items based on limits and triggers paywall if limit is exceeded.
     * Returns the list of items that are allowed to be archived.
     */
    suspend fun processItemsForArchiving(
        items: List<MediaItem>,
        onPaywallRequired: (missingPhotos: Int, missingVideos: Int) -> Unit
    ): List<MediaItem> = withContext(Dispatchers.Main) {
        if (limitRepository.isPremiumUnlocked) return@withContext items

        var allowedItems = items

        // Soft Cap первой сессии
        if (limitRepository.trustLevel == 0 && items.size > 20) {
            Toast.makeText(context, "Первая архивация ограничена 20 файлами", Toast.LENGTH_LONG).show()
            allowedItems = items.take(20)
        }

        val photos = allowedItems.filter { !it.isVideo }
        val videos = allowedItems.filter { it.isVideo }

        val photosRemaining = LimitRepository.MAX_PHOTOS - limitRepository.photosArchivedCount
        val videosRemaining = LimitRepository.MAX_VIDEOS - limitRepository.videosArchivedCount

        var photosToArchive = photos.size
        var videosToArchive = videos.size
        var hitLimit = false

        if (photos.size > photosRemaining) {
            photosToArchive = if (photosRemaining > 0) photosRemaining else 0
            hitLimit = true
        }

        if (videos.size > videosRemaining) {
            videosToArchive = if (videosRemaining > 0) videosRemaining else 0
            hitLimit = true
        }

        val finalItemsToArchive = mutableListOf<MediaItem>()
        finalItemsToArchive.addAll(photos.take(photosToArchive))
        finalItemsToArchive.addAll(videos.take(videosToArchive))

        if (hitLimit) {
            val missingPhotos = photos.size - photosToArchive
            val missingVideos = videos.size - videosToArchive
            // Приостановка процесса (возвращаем только допустимые файлы) 
            // и открываем Пейвол с передачей динамических цифр
            onPaywallRequired(missingPhotos, missingVideos)
        }

        return@withContext finalItemsToArchive
    }
}
