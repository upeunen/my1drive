package by.w6.my1drive.ui

import android.content.SharedPreferences
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import android.content.Context
import android.text.format.DateUtils
import by.w6.my1drive.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GalleryDisplayManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    mediaItems: StateFlow<List<MediaItem>>
) {

    private val _deviceSortMode = MutableStateFlow(
        try {
            DeviceSortMode.valueOf(
                prefs.getString("device_sort_mode", DeviceSortMode.BY_PHOTO_DATE.name) ?: DeviceSortMode.BY_PHOTO_DATE.name
            )
        } catch (e: Exception) {
            DeviceSortMode.BY_PHOTO_DATE
        }
    )
    val deviceSortMode = _deviceSortMode.asStateFlow()

    fun setDeviceSortMode(mode: DeviceSortMode) {
        _deviceSortMode.value = mode
        prefs.edit().putString("device_sort_mode", mode.name).apply()
    }

    private val _gridColumnsCount = MutableStateFlow(
        prefs.getInt("grid_columns_count", 3)
    )
    val gridColumnsCount = _gridColumnsCount.asStateFlow()

    fun setGridColumnsCount(count: Int) {
        _gridColumnsCount.value = count
        prefs.edit().putInt("grid_columns_count", count).apply()
    }

    private val _archiveSortMode = MutableStateFlow(
        try {
            ArchiveSortMode.valueOf(
                prefs.getString("archive_sort_mode", ArchiveSortMode.BY_PHOTO_DATE.name) ?: ArchiveSortMode.BY_PHOTO_DATE.name
            )
        } catch (e: Exception) {
            ArchiveSortMode.BY_PHOTO_DATE
        }
    )
    val archiveSortMode = _archiveSortMode.asStateFlow()

    fun setArchiveSortMode(mode: ArchiveSortMode) {
        _archiveSortMode.value = mode
        prefs.edit().putString("archive_sort_mode", mode.name).apply()
    }

    private fun isYesterday(tc: Calendar, now: Calendar): Boolean {
        val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return tc.get(Calendar.YEAR) == yest.get(Calendar.YEAR) && tc.get(Calendar.DAY_OF_YEAR) == yest.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatDateHeader(dateSeconds: Long): String {
        val dateMs = dateSeconds * 1000
        val now = Calendar.getInstance()
        val tc = Calendar.getInstance().apply { timeInMillis = dateMs }

        return when {
            DateUtils.isToday(dateMs) -> context.getString(R.string.date_today)
            isYesterday(tc, now) -> context.getString(R.string.date_yesterday)
            tc.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(dateMs))
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dateMs))
        }
    }

    val groupedMediaItems: StateFlow<List<GalleryItem>> = combine(
        mediaItems,
        deviceSortMode
    ) { list, sortMode ->
        val localItems = list.filter { it.status == MediaStatus.ON_DEVICE }.run {
            if (sortMode == DeviceSortMode.BY_RESTORE_DATE) {
                sortedByDescending { it.dateAdded ?: 0L }
            } else {
                sortedByDescending { it.dateModified }
            }
        }
        val resultList = mutableListOf<GalleryItem>()
        val grouped = localItems.groupBy { item ->
            val date = if (sortMode == DeviceSortMode.BY_RESTORE_DATE) {
                item.dateAdded ?: 0L
            } else {
                item.dateModified
            }
            formatDateHeader(date)
        }
        for ((headerText, items) in grouped) {
            resultList.add(GalleryItem.Header(headerText))
            items.forEach { resultList.add(GalleryItem.Media(it)) }
        }
        resultList
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Lazily, emptyList())

    val archivedGroupedItems: StateFlow<List<GalleryItem>> = combine(
        mediaItems,
        archiveSortMode
    ) { list, sortMode ->
        val archivedList = list.filter {
            it.status == MediaStatus.ARCHIVED_OTG &&
                    (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))
        }.run {
            if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                sortedByDescending { it.dateArchived ?: 0L }
            } else {
                sortedByDescending { it.dateModified }
            }
        }

        val resultList = mutableListOf<GalleryItem>()
        val grouped = archivedList.groupBy { item ->
            val date = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                item.dateArchived ?: 0L
            } else {
                item.dateModified
            }
            formatDateHeader(date)
        }
        for ((headerText, items) in grouped) {
            resultList.add(GalleryItem.Header(headerText))
            items.forEach { resultList.add(GalleryItem.Media(it)) }
        }
        resultList
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Lazily, emptyList())
}
