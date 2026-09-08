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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.content.Context
import android.text.format.DateUtils
import by.w6.my1drive.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.appcompat.app.AppCompatDelegate
import by.w6.my1drive.ui.model.MonthGroup
import by.w6.my1drive.ui.model.YearGroup

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
        prefs.getInt("grid_columns_count", 3).coerceIn(1, 5)
    )
    val gridColumnsCount = _gridColumnsCount.asStateFlow()

    fun setGridColumnsCount(count: Int) {
        val coerced = count.coerceIn(1, 5)
        _gridColumnsCount.value = coerced
        prefs.edit().putInt("grid_columns_count", coerced).apply()
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

    private val _archiveFilterUuid = MutableStateFlow<String?>(null)
    val archiveFilterUuid = _archiveFilterUuid.asStateFlow()

    fun setArchiveFilterUuid(uuid: String?) {
        _archiveFilterUuid.value = uuid
    }

    private val _mediaFilterMode = MutableStateFlow(MediaFilterMode.ALL)
    val mediaFilterMode = _mediaFilterMode.asStateFlow()

    fun setMediaFilterMode(mode: MediaFilterMode) {
        _mediaFilterMode.value = mode
    }

    val localeVersion = MutableStateFlow(0)

    fun getActiveLocale(): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0] ?: Locale.getDefault()
        } else {
            val configLocales = context.resources.configuration.locales
            if (!configLocales.isEmpty) {
                configLocales[0]
            } else {
                Locale.getDefault()
            }
        }
    }

    fun notifyLocaleChanged() {
        localeVersion.value++
    }

    private fun isYesterday(tc: Calendar, now: Calendar): Boolean {
        val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return tc.get(Calendar.YEAR) == yest.get(Calendar.YEAR) && tc.get(Calendar.DAY_OF_YEAR) == yest.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatDateHeader(dateSeconds: Long): String {
        val dateMs = dateSeconds * 1000
        val now = Calendar.getInstance()
        val tc = Calendar.getInstance().apply { timeInMillis = dateMs }

        val locale = getActiveLocale()
        val config = android.content.res.Configuration(context.resources.configuration)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.setLocale(locale)
        }
        val localizedContext = context.createConfigurationContext(config)

        return when {
            DateUtils.isToday(dateMs) -> localizedContext.getString(R.string.date_today)
            isYesterday(tc, now) -> localizedContext.getString(R.string.date_yesterday)
            tc.get(Calendar.YEAR) == now.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", locale).format(Date(dateMs))
            else -> SimpleDateFormat("d MMMM yyyy", locale).format(Date(dateMs))
        }
    }

    val groupedMediaItems: StateFlow<List<GalleryItem>> = combine(
        mediaItems,
        deviceSortMode,
        mediaFilterMode,
        localeVersion
    ) { list, sortMode, filterMode, _ ->
        val localItems = list.filter {
            it.status == MediaStatus.ON_DEVICE &&
            when (filterMode) {
                MediaFilterMode.ALL -> it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/")
                MediaFilterMode.PHOTOS_ONLY -> it.mimeType.startsWith("image/")
                MediaFilterMode.VIDEOS_ONLY -> it.mimeType.startsWith("video/")
            }
        }.run {
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

    private val bentoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<String>, List<by.w6.my1drive.ui.layout.BentoBlock>>>()

    val archiveYearGroups: StateFlow<List<YearGroup>> = combine(
        mediaItems,
        archiveSortMode,
        archiveFilterUuid,
        gridColumnsCount,
        localeVersion
    ) { list, sortMode, filterUuid, columnsCount, _ ->
        val archivedItems = list.filter {
            it.status == MediaStatus.ARCHIVED_OTG &&
                    (it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/"))
        }.let { all -> if (filterUuid != null) all.filter { it.archiveUuid == filterUuid } else all }

        if (archivedItems.isEmpty()) return@combine emptyList<YearGroup>()

        val zoneId = java.time.ZoneId.systemDefault()

        // Быстрая группировка за один проход по год/месяц без создания тысяч объектов Calendar
        val yearMonthGrouped = archivedItems.groupBy { item ->
            val timestamp = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                item.dateArchived ?: item.dateModified
            } else {
                item.dateModified
            }
            val zdt = java.time.Instant.ofEpochSecond(timestamp).atZone(zoneId)
            zdt.year to (zdt.monthValue - 1)
        }

        val monthLocale = getActiveLocale()
        val monthNames = java.text.DateFormatSymbols(monthLocale).months
        val yearGroupsMap = mutableMapOf<Int, MutableList<MonthGroup>>()

        for ((yearMonth, itemsInMonth) in yearMonthGrouped) {
            val year = yearMonth.first
            val monthIdx = yearMonth.second

            val sortedMonthItems = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                itemsInMonth.sortedByDescending { it.dateArchived ?: 0L }
            } else {
                itemsInMonth.sortedByDescending { it.dateModified }
            }

            val rawMonthName = if (monthIdx in 0..11) monthNames[monthIdx] else ""
            val monthName = rawMonthName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(monthLocale) else it.toString() }

            val cacheKey = "${year}_${monthIdx}_${columnsCount}_${sortMode.name}"
            val monthItemIds = sortedMonthItems.map { it.id }
            val cachedEntry = bentoCache[cacheKey]

            val blocks = if (cachedEntry != null && cachedEntry.first == monthItemIds) {
                cachedEntry.second
            } else {
                val newBlocks = by.w6.my1drive.ui.layout.BentoLayoutHelper.computeBlocks(sortedMonthItems, columnsCount)
                bentoCache[cacheKey] = monthItemIds to newBlocks
                newBlocks
            }

            val monthGroup = MonthGroup(
                monthIndex = monthIdx,
                monthName = monthName,
                items = sortedMonthItems,
                chunkedItems = sortedMonthItems.chunked(columnsCount),
                blocks = blocks
            )

            yearGroupsMap.getOrPut(year) { mutableListOf() }.add(monthGroup)
        }

        yearGroupsMap.map { (year, monthGroups) ->
            val sortedMonths = monthGroups.sortedByDescending { monthGroup ->
                monthGroup.items.maxOfOrNull { item ->
                    if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                        item.dateArchived ?: item.dateModified
                    } else {
                        item.dateModified
                    }
                } ?: 0L
            }
            YearGroup(
                year = year,
                months = sortedMonths
            )
        }.sortedByDescending { it.year }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Lazily, emptyList())

    val archivedGroupedItems: StateFlow<List<GalleryItem>> = archiveYearGroups.map { yearGroups ->
        yearGroups.flatMap { yearGroup ->
            yearGroup.months.flatMap { monthGroup ->
                monthGroup.items.map { GalleryItem.Media(it) }
            }
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())
}
