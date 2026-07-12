package by.w6.my1drive.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.runtime.mutableStateMapOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.ui.GalleryItem
import by.w6.my1drive.utils.PreviewCacheManager
import coil.ImageLoader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Sync

import by.w6.my1drive.ui.components.UnknownDriveBanner
import by.w6.my1drive.ui.components.DisconnectedDriveBanner
import by.w6.my1drive.ui.components.PartialAccessBanner
import by.w6.my1drive.ui.components.OtgRequiredBanner
import by.w6.my1drive.ui.components.ConnectingUsbBanner
import by.w6.my1drive.ui.components.ProgressPanel
import by.w6.my1drive.ui.components.mapStepToText

@Composable
fun PhotosRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, gridColumnsCount: Int = 3, actionBarHeightPx: Float = 0f,
    onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit
) {
    val activeArchiveUuid by viewModel.otgManager.activeArchiveUuid.collectAsState()
    val groupedItems by viewModel.groupedMediaItems.collectAsState()
    val sortMode by viewModel.deviceSortMode.collectAsState()
    val archivingItemIds by viewModel.archivingItemIds.collectAsState()
    val copiedItemIds by viewModel.copiedItemIds.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = Color.Transparent
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Сортировка: ",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariantColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .background(
                        color = surfaceVariantColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(2.dp)
            ) {
                val isByPhotoActive = sortMode == DeviceSortMode.BY_PHOTO_DATE
                val photoDateBgColor by animateColorAsState(if (isByPhotoActive) primaryColor else transparentColor, label = "photoDateBg")
                val photoDateTextColor by animateColorAsState(if (isByPhotoActive) onPrimaryColor else onSurfaceVariantColor, label = "photoDateText")

                Box(
                    modifier = Modifier
                        .background(photoDateBgColor, RoundedCornerShape(14.dp))
                        .clickable { viewModel.setDeviceSortMode(DeviceSortMode.BY_PHOTO_DATE) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Дата фото",
                        color = photoDateTextColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val isByRestoreActive = sortMode == DeviceSortMode.BY_RESTORE_DATE
                val restoreDateBgColor by animateColorAsState(if (isByRestoreActive) primaryColor else transparentColor, label = "restoreDateBg")
                val restoreDateTextColor by animateColorAsState(if (isByRestoreActive) onPrimaryColor else onSurfaceVariantColor, label = "restoreDateText")

                Box(
                    modifier = Modifier
                        .background(restoreDateBgColor, RoundedCornerShape(14.dp))
                        .clickable { viewModel.setDeviceSortMode(DeviceSortMode.BY_RESTORE_DATE) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Дата разархивации",
                        color = restoreDateTextColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        PhotosGridTab(
            groupedItems = groupedItems,
            selectedIds = selectedIds,
            imageLoader = imageLoader,
            isOtgConnected = isOtgConnected,
            activeArchiveUuid = activeArchiveUuid,
            archivingItemIds = archivingItemIds,
            copiedItemIds = copiedItemIds,
            gridColumnsCount = gridColumnsCount,
            actionBarHeightPx = actionBarHeightPx,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onSelectItems = { ids, select ->
                if (select) {
                    viewModel.selectItems(ids)
                } else {
                    viewModel.deselectItems(ids)
                }
            }
        )
    }
}


private data class MonthGroup(
    val monthIndex: Int,
    val monthName: String,
    val items: List<MediaItem>
)

private data class YearGroup(
    val year: Int,
    val months: List<MonthGroup>
)

@Composable
fun ArchiveRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, gridColumnsCount: Int = 3, actionBarHeightPx: Float = 0f,
    onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit
) {
    val activeArchiveUuid by viewModel.otgManager.activeArchiveUuid.collectAsState()
    val archivedGroupedItems by viewModel.archivedGroupedItems.collectAsState()
    val sortMode by viewModel.archiveSortMode.collectAsState()
    val archivingItemIds by viewModel.archivingItemIds.collectAsState()
    val restoringItemIds by viewModel.restoringItemIds.collectAsState()
    val copiedItemIds by viewModel.copiedItemIds.collectAsState()
    val isSilentSyncing by viewModel.isSilentSyncingFlow.collectAsState()
    val archiveState by viewModel.archiveState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncProgressState by viewModel.syncProgressState.collectAsState()
    val knownArchives by viewModel.knownArchives.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE)
    var showOffline by remember { mutableStateOf(prefs.getBoolean("show_offline_archives", false)) }
    
    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "show_offline_archives") {
                showOffline = sharedPreferences.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSelectedIds by androidx.compose.runtime.rememberUpdatedState(selectedIds)
    val currentActionBarHeightPx by androidx.compose.runtime.rememberUpdatedState(actionBarHeightPx)

    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = Color.Transparent
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Фильтр по носителям: null = все, иначе uuid выбранной флешки
    var filterUuid by remember { mutableStateOf<String?>(null) }

    // Автоматически переключаем фильтр на подключенную флешку при её подключении
    LaunchedEffect(isOtgConnected, activeArchiveUuid) {
        if (isOtgConnected && activeArchiveUuid != null) {
            filterUuid = activeArchiveUuid
        }
    }

    // Карта uuid → Color по стабильному хэшу UUID
    val archiveColorMap = remember(knownArchives) {
        knownArchives.associate { archive ->
            archive.uuid to archiveStripeColor(archive.uuid)
        }
    }

    // 1. Извлекаем плоский список архивных медиафайлов (с учётом фильтра)
    val archivedItems = remember(archivedGroupedItems, filterUuid) {
        archivedGroupedItems.filterIsInstance<GalleryItem.Media>().map { it.item }
            .let { all -> if (filterUuid != null) all.filter { it.archiveUuid == filterUuid } else all }
    }

    // 2. Группируем архивные медиафайлы по Годам и Месяцам
    val yearGroups = remember(archivedItems, sortMode) {
        archivedItems.groupBy { item ->
            val timestamp = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                item.dateArchived ?: item.dateModified
            } else {
                item.dateModified
            }
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
            cal.get(Calendar.YEAR)
        }.map { (year, yearItems) ->
            val monthGroups = yearItems.groupBy { item ->
                val timestamp = if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) {
                    item.dateArchived ?: item.dateModified
                } else {
                    item.dateModified
                }
                val cal = Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
                cal.get(Calendar.MONTH)
            }.map { (monthIdx, monthItems) ->
                val sampleTimestamp = (monthItems.firstOrNull()?.run {
                    if (sortMode == ArchiveSortMode.BY_ARCHIVE_DATE) dateArchived ?: dateModified else dateModified
                } ?: 0L) * 1000
                
                val monthName = SimpleDateFormat("LLLL", Locale.getDefault()).format(Date(sampleTimestamp))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                MonthGroup(
                    monthIndex = monthIdx,
                    monthName = monthName,
                    items = monthItems
                )
            }.sortedByDescending { it.monthIndex }

            YearGroup(
                year = year,
                months = monthGroups
            )
        }.sortedByDescending { it.year }
    }

    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }
    var hasInitializedDefaults by remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(yearGroups) {
        if (!hasInitializedDefaults && yearGroups.isNotEmpty()) {
            val currentCal = Calendar.getInstance()
            val curYear = currentCal.get(Calendar.YEAR)
            val curMonth = currentCal.get(Calendar.MONTH)
            val curKey = "${curYear}_${curMonth}"
            expandedMonths[curKey] = true

            yearGroups.firstOrNull()?.months?.firstOrNull()?.let { firstMonth ->
                val firstYear = yearGroups.first().year
                val firstKey = "${firstYear}_${firstMonth.monthIndex}"
                expandedMonths[firstKey] = true
            }
            hasInitializedDefaults = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val isManualSyncing = syncProgressState.isSyncing
        val isOperationRunning = isSilentSyncing || archiveState.isArchiving || restoreState.isRestoring || isManualSyncing

        // Строка фильтра по флешкам с горизонтальной прокруткой и современными чипами
        if (showOffline && knownArchives.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // «Все» чип
                val allActive = filterUuid == null
                val allActiveScale by animateFloatAsState(targetValue = if (allActive) 1.0f else 0.95f, label = "allActiveScale")
                val allActiveBgColor by animateColorAsState(
                    targetValue = if (allActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    label = "allActiveBg"
                )
                val allActiveContentColor by animateColorAsState(
                    targetValue = if (allActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    label = "allActiveContent"
                )
                val allActiveBorderColor = if (allActive) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                Row(
                    modifier = Modifier
                        .graphicsLayer(scaleX = allActiveScale, scaleY = allActiveScale)
                        .height(32.dp)
                        .background(allActiveBgColor, RoundedCornerShape(16.dp))
                        .border(1.dp, allActiveBorderColor, RoundedCornerShape(16.dp))
                        .clickable { filterUuid = null }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        tint = allActiveContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Все",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = allActiveContentColor
                    )
                }

                // Чипы для каждой флешки
                knownArchives.forEach { archive ->
                    val baseColor = archiveColorMap[archive.uuid] ?: ARCHIVE_STRIPE_COLORS[0]
                    val isActive = filterUuid == archive.uuid
                    val isCurrentConnected = isOtgConnected && activeArchiveUuid == archive.uuid
                    
                    val chipScale by animateFloatAsState(targetValue = if (isActive) 1.0f else 0.95f, label = "chipScale")
                    val chipBgColor by animateColorAsState(
                        targetValue = if (isActive) baseColor else baseColor.copy(alpha = 0.4f),
                        label = "chipBg"
                    )
                    val chipContentColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else baseColor,
                        label = "chipContent"
                    )
                    val chipBorderColor = if (isActive) Color.Transparent else baseColor.copy(alpha = 0.2f)

                    Row(
                        modifier = Modifier
                            .graphicsLayer(scaleX = chipScale, scaleY = chipScale)
                            .height(32.dp)
                            .background(chipBgColor, RoundedCornerShape(16.dp))
                            .border(1.dp, chipBorderColor, RoundedCornerShape(16.dp))
                            .clickable {
                                filterUuid = if (isActive) null else archive.uuid
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isCurrentConnected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.UsbOff,
                                contentDescription = "Offline",
                                tint = chipContentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        
                        Text(
                            text = archive.name.ifBlank { archive.folderName.take(10).ifBlank { archive.uuid.take(6) } },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = chipContentColor
                        )
                    }
                }
            }
        }



        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isOperationRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сортировка: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariantColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .background(
                            color = surfaceVariantColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    val isByPhotoActive = sortMode == ArchiveSortMode.BY_PHOTO_DATE
                    val photoDateBgColor by animateColorAsState(if (isByPhotoActive) primaryColor else transparentColor, label = "archivePhotoBg")
                    val photoDateTextColor by animateColorAsState(if (isByPhotoActive) onPrimaryColor else onSurfaceVariantColor, label = "archivePhotoText")

                    Box(
                        modifier = Modifier
                            .background(photoDateBgColor, RoundedCornerShape(14.dp))
                            .clickable { viewModel.setArchiveSortMode(ArchiveSortMode.BY_PHOTO_DATE) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Дата фото",
                            color = photoDateTextColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val isByArchiveActive = sortMode == ArchiveSortMode.BY_ARCHIVE_DATE
                    val archiveDateBgColor by animateColorAsState(if (isByArchiveActive) primaryColor else transparentColor, label = "archiveDateBg")
                    val archiveDateTextColor by animateColorAsState(if (isByArchiveActive) onPrimaryColor else onSurfaceVariantColor, label = "archiveDateText")

                    Box(
                        modifier = Modifier
                            .background(archiveDateBgColor, RoundedCornerShape(14.dp))
                            .clickable { viewModel.setArchiveSortMode(ArchiveSortMode.BY_ARCHIVE_DATE) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Дата архивации",
                            color = archiveDateTextColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (yearGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = stringResource(R.string.empty_category),
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_category),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { containerCoordinates = it }
                ) {
                    yearGroups.forEach { yearGroup ->
                        // 1. Заголовок года
                        item(key = "year_${yearGroup.year}") {
                            Text(
                                text = yearGroup.year.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        // 2. Месяцы в этом году
                        yearGroup.months.forEach { monthGroup ->
                            val monthKey = "${yearGroup.year}_${monthGroup.monthIndex}"
                            val isExpanded = expandedMonths[monthKey] ?: false

                            // Карточка месяца (кнопка-аккордеон)
                            item(key = "month_header_$monthKey") {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    onClick = {
                                        expandedMonths[monthKey] = !isExpanded
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = monthGroup.monthName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${monthGroup.items.size} элементов",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            if (selectedIds.isNotEmpty()) {
                                                val allSelected = monthGroup.items.isNotEmpty() && monthGroup.items.all { selectedIds.contains(it.id) }
                                                IconButton(
                                                     onClick = {
                                                         val ids = monthGroup.items.map { it.id }
                                                         if (allSelected) {
                                                             viewModel.deselectItems(ids)
                                                         } else {
                                                             viewModel.selectItems(ids)
                                                         }
                                                     },
                                                     modifier = Modifier.size(24.dp)
                                                 ) {
                                                     Icon(
                                                         imageVector = if (allSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                                                         contentDescription = "Выбрать все за месяц",
                                                         tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                         modifier = Modifier.size(24.dp)
                                                     )
                                                 }
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) "Свернуть" else "Развернуть"
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Сетка фотографий (если раскрыто)
                            if (isExpanded) {
                                val chunkedItems = monthGroup.items.chunked(gridColumnsCount)
                                items(chunkedItems, key = { chunk -> "chunk_${chunk.first().id}" }) { rowItems ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (i in 0 until gridColumnsCount) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                            ) {
                                                if (i < rowItems.size) {
                                                    val mediaItem = rowItems[i]
                                                    val isSelected = selectedIds.contains(mediaItem.id)
                                                    val isArchiving = archivingItemIds.contains(mediaItem.id)
                                                    val isRestoring = restoringItemIds.contains(mediaItem.id)
                                                    val isCopied = copiedItemIds.contains(mediaItem.id)
                                                    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                                    GooglePhotosGridItem(
                                                        item = mediaItem,
                                                        isSelected = isSelected,
                                                        isArchiving = isArchiving || isRestoring,
                                                        isCopied = isCopied,
                                                        imageLoader = imageLoader,
                                                        isOtgConnected = if (mediaItem.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG) (isOtgConnected && mediaItem.archiveUuid == activeArchiveUuid) else isOtgConnected,
                                                        archiveStripeOverrideColor = if (showOffline && knownArchives.size > 1) archiveColorMap[mediaItem.archiveUuid] else null,
                                                        modifier = Modifier.onGloballyPositioned { itemCoordinates = it },
                                                        onClick = {
                                                            onItemClick(mediaItem)
                                                            val itemCoords = itemCoordinates
                                                            val containerCoords = containerCoordinates
                                                            if (itemCoords != null && containerCoords != null && itemCoords.isAttached && containerCoords.isAttached) {
                                                                val itemBounds = itemCoords.boundsInWindow()
                                                                val containerBounds = containerCoords.boundsInWindow()
                                                                val itemTop = itemBounds.top
                                                                val itemBottom = itemBounds.bottom
                                                                val itemHeight = itemBounds.height
                                                                val containerTop = containerBounds.top
                                                                var containerBottom = containerBounds.bottom
                                                                
                                                                if (currentSelectedIds.isNotEmpty()) {
                                                                    containerBottom -= currentActionBarHeightPx
                                                                }
                                                                
                                                                val hiddenTop = containerTop - itemTop
                                                                val hiddenBottom = itemBottom - containerBottom
                                                                val thirdOfHeight = itemHeight / 3f
                                                                
                                                                if (hiddenTop > thirdOfHeight || hiddenBottom > thirdOfHeight) {
                                                                    coroutineScope.launch {
                                                                        kotlinx.coroutines.delay(150)
                                                                        if (itemCoords.isAttached && containerCoords.isAttached) {
                                                                            val itemBoundsNew = itemCoords.boundsInWindow()
                                                                            val containerBoundsNew = containerCoords.boundsInWindow()
                                                                            val itemTopNew = itemBoundsNew.top
                                                                            val itemBottomNew = itemBoundsNew.bottom
                                                                            val itemHeightNew = itemBoundsNew.height
                                                                            val containerTopNew = containerBoundsNew.top
                                                                            var containerBottomNew = containerBoundsNew.bottom
                                                                            if (currentSelectedIds.isNotEmpty()) {
                                                                                containerBottomNew -= currentActionBarHeightPx
                                                                            }
                                                                            val hiddenTopNew = containerTopNew - itemTopNew
                                                                            val hiddenBottomNew = itemBottomNew - containerBottomNew
                                                                            val thirdOfHeightNew = itemHeightNew / 3f
                                                                            if (hiddenTopNew > thirdOfHeightNew) {
                                                                                listState.animateScrollBy(-hiddenTopNew)
                                                                            } else if (hiddenBottomNew > thirdOfHeightNew) {
                                                                                listState.animateScrollBy(hiddenBottomNew)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onLongClick = { onItemLongClick(mediaItem) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissingFilesDialog(missingNames: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_sync_missing_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.auto_sync_missing_msg, missingNames.joinToString("\n"))) },
        confirmButton = { Button(onClick = onDismiss) { Text(text = stringResource(R.string.btn_ok), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false) } }
    )
}

@Composable
fun UnknownDriveDialog(
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Неизвестный носитель", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text("Подключен неизвестный носитель. Создать новый архив, или если вернётся старый — сможете его синхронизировать")
        },
        confirmButton = {
            Button(onClick = onCreateNew) {
                Text(
                    text = "Создать новый",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Закрыть",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    pendingDelete: List<MediaItem>,
    isArchiveTab: Boolean,
    isOtgConnected: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pendingDelete.size == 1) stringResource(R.string.delete_confirm_title) else "${stringResource(R.string.delete_confirm_title)} (${pendingDelete.size})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (pendingDelete.size == 1) {
                    Text(stringResource(R.string.delete_confirm_msg, pendingDelete.first().displayName))
                } else {
                    Text(stringResource(R.string.action_delete_files_question, pendingDelete.size))
                }
                if (isArchiveTab) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.delete_archived_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = if (pendingDelete.size == 1) {
                        if (isArchiveTab) stringResource(R.string.btn_delete_otg) else stringResource(R.string.btn_delete_file)
                    } else {
                        stringResource(R.string.action_delete_all)
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}

@Composable
fun GalleryScreenContent(
    modifier: Modifier,
    selectedIds: Set<String>,
    otgDirectoryUri: Uri?,
    isOtgConnected: Boolean,
    hasPartialAccess: Boolean,
    currentScreenRoute: String,
    missingFilesNotification: List<String>?,
    autoSyncAddedCount: Int,
    activePreviewState: FullscreenState?,
    showInfoDialogItem: MediaItem?,
    showOtgGuideDialog: Boolean,
    imageLoader: ImageLoader,
    viewModel: GalleryViewModel,
    onSelectOtgDirectory: () -> Unit,
    onSelectDeviceDirectory: () -> Unit = {},
    onRequestFullAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSelection: (String) -> Unit = {},
    onSetActivePreview: (FullscreenState?) -> Unit,
    onSetShowInfoDialog: (MediaItem?) -> Unit,
    onSetShowOtgGuide: (Boolean) -> Unit,
    previewCacheManager: PreviewCacheManager,
    archiveState: ArchiveState = ArchiveState(),
    restoreState: RestoreState = RestoreState(),
    syncProgressState: SyncProgressState = SyncProgressState(),
    actionBarHeightPx: Float = 0f,
    onSyncArchive: () -> Unit = {},
    onSelectDateRangeClick: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val activeArchiveUuid by viewModel.otgManager.activeArchiveUuid.collectAsState()
    val isSharingPreparing by viewModel.isSharingPreparing.collectAsState()
    val isCheckingConnection by viewModel.otgManager.isCheckingConnection.collectAsState()
    val isSilentSyncing by viewModel.isSilentSyncingFlow.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE) }
    var showDisconnectedOtgItemInfo by remember { mutableStateOf<MediaItem?>(null) }
    var showDebugLogsDialog by remember { mutableStateOf(false) }
    var showEjectConfirmDialog by remember { mutableStateOf(false) }
    var showOfflineShareConfirm by remember { mutableStateOf(false) }
    val groupedItems by viewModel.groupedMediaItems.collectAsState()
    val archivedGroupedItems by viewModel.archivedGroupedItems.collectAsState()
    val mediaItems by viewModel.mediaItems.collectAsState()
    val gridColumnsCount by viewModel.gridColumnsCount.collectAsState()

    var showChangeFolderConfirmDialog by remember { mutableStateOf(false) }

    if (showOfflineShareConfirm) {
        var dontWarnAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showOfflineShareConfirm = false },
            title = { Text("Поделиться эскизами?") },
            text = {
                Column {
                    Text("Некоторые из выбранных файлов находятся на отключенных накопителях. Будут отправлены их эскизы низкого разрешения.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dontWarnAgain = !dontWarnAgain }
                    ) {
                        Checkbox(
                            checked = dontWarnAgain,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("я понимаю, больше не нужно предупреждать", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showOfflineShareConfirm = false
                    if (dontWarnAgain) {
                        prefs.edit().putBoolean("skip_offline_share_warning", true).apply()
                    }
                    viewModel.shareSelectedItems(context) { errMsg ->
                        android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("Поделиться", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineShareConfirm = false }) {
                    Text("Отмена", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    if (showChangeFolderConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showChangeFolderConfirmDialog = false },
            title = { Text("Сменить папку архива?", fontWeight = FontWeight.Bold) },
            text = { Text("Смена папки архива может нарушить текущую синхронизацию. Вы уверены, что хотите продолжить?") },
            confirmButton = {
                Button(onClick = {
                    showChangeFolderConfirmDialog = false
                    onSelectOtgDirectory()
                }) {
                    Text("Сменить", maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeFolderConfirmDialog = false }) {
                    Text("Отмена", maxLines = 1, softWrap = false)
                }
            }
        )
    }

    // Selection group chips state
    var isGroupExpanded by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) isGroupExpanded = false
    }

    val deleteEnabled = if (currentScreenRoute == "archive") isOtgConnected else true

    val visibleItemsForChips = remember(currentScreenRoute, mediaItems) {
        if (currentScreenRoute == "photos") {
            mediaItems.filter { it.status == by.w6.my1drive.domain.model.MediaStatus.ON_DEVICE }
        } else {
            mediaItems.filter { it.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG }
        }
    }
    val firstSelectedItem = remember(selectedIds, mediaItems) {
        mediaItems.firstOrNull { it.id in selectedIds }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            GooglePhotosTopBar(
                selectedCount = selectedIds.size,
                isOtgConnected = isOtgConnected,
                otgUriSet = otgDirectoryUri != null,
                isGroupExpanded = isGroupExpanded,
                deleteEnabled = deleteEnabled,
                onClearSelection = onClearSelection,
                onEjectClick = { showEjectConfirmDialog = true },
                onGroupClick = { isGroupExpanded = !isGroupExpanded },
                onShare = {
                    val selected = mediaItems.filter { it.id in selectedIds }
                    val hasOffline = selected.any { 
                        it.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG && 
                        !(isOtgConnected && it.archiveUuid == activeArchiveUuid)
                    }
                    if (hasOffline && !prefs.getBoolean("skip_offline_share_warning", false)) {
                        showOfflineShareConfirm = true
                    } else {
                        viewModel.shareSelectedItems(context) { errMsg ->
                            android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDelete = { viewModel.requestDeleteSelected() },
                gridColumnsCount = gridColumnsCount,
                onToggleGridColumns = { viewModel.setGridColumnsCount(if (gridColumnsCount == 3) 4 else 3) },
                showGridToggle = currentScreenRoute != "settings"
            )

            ConnectingUsbBanner(visible = isCheckingConnection || isSilentSyncing)

            // Expandable group chips row
            AnimatedVisibility(
                visible = isGroupExpanded && selectedIds.isNotEmpty(),
                enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeIn(),
                exit = slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { -it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Все
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            viewModel.selectItems(visibleItemsForChips.map { it.id })
                            isGroupExpanded = false
                        },
                        label = { Text("Все") }
                    )
                    // С датой
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetDate = firstSelectedItem.dateModified
                                val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = targetDate * 1000 }
                                val matching = visibleItemsForChips.filter { item ->
                                    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = item.dateModified * 1000 }
                                    cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                                        cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
                                }.map { it.id }
                                viewModel.selectItems(matching)
                            }
                            isGroupExpanded = false
                        },
                        enabled = firstSelectedItem != null,
                        label = { Text("С датой") }
                    )
                    // В папке
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            if (firstSelectedItem != null) {
                                val path = firstSelectedItem.originalRelativePath
                                val matching = visibleItemsForChips
                                    .filter { it.originalRelativePath == path }
                                    .map { it.id }
                                viewModel.selectItems(matching)
                            }
                            isGroupExpanded = false
                        },
                        enabled = firstSelectedItem != null && !firstSelectedItem.originalRelativePath.isNullOrEmpty(),
                        label = { Text("В папке") }
                    )
                    // Диапазон
                    androidx.compose.material3.AssistChip(
                        onClick = {
                            onSelectDateRangeClick()
                            isGroupExpanded = false
                        },
                        label = { Text("Диапазон") }
                    )
                }
            }

            // Прогресс-панель архивации/восстановления/синхронизации
            if (archiveState.isArchiving) {
                val queue = if (archiveState.pendingQueueSize > 0) " (+${archiveState.pendingQueueSize} в очереди)" else ""
                ProgressPanel(
                    title = "Архивация",
                    fileName = archiveState.currentFileName,
                    currentIndex = archiveState.currentFileIndex,
                    totalFiles = archiveState.totalFiles,
                    progressFraction = archiveState.progressFraction,
                    extraInfo = queue,
                    icon = Icons.Default.CloudUpload,
                    statusText = mapStepToText(archiveState.currentStep),
                    onCancel = { viewModel.cancelArchiving() }
                )
            } else if (restoreState.isRestoring) {
                ProgressPanel(
                    title = "Восстановление",
                    fileName = restoreState.currentFileName,
                    currentIndex = restoreState.currentFileIndex,
                    totalFiles = restoreState.totalFiles,
                    progressFraction = restoreState.progressFraction,
                    icon = Icons.Default.CloudDownload,
                    statusText = mapStepToText(restoreState.currentStep),
                    onCancel = { viewModel.cancelRestoring() }
                )
            } else if (syncProgressState.isSyncing) {
                ProgressPanel(
                    title = "Синхронизация архива",
                    fileName = syncProgressState.currentFileName,
                    currentIndex = syncProgressState.currentFileIndex,
                    totalFiles = syncProgressState.totalFiles,
                    progressFraction = syncProgressState.progressFraction,
                    icon = Icons.Default.Sync,
                    statusText = if (syncProgressState.totalFiles > 0) "Вычисление хэшей..." else "Поиск файлов..."
                )
            }

            val driveStatus by viewModel.otgManager.status.collectAsState()
            if (driveStatus == DriveStatus.UNKNOWN_DRIVE_CONNECTED) UnknownDriveBanner()
            else if (driveStatus == DriveStatus.KNOWN_DRIVE_DISCONNECTED && otgDirectoryUri != null && !(isCheckingConnection || isSilentSyncing)) DisconnectedDriveBanner()
            if (hasPartialAccess) PartialAccessBanner(onGrantFullAccess = onRequestFullAccess, onOpenSettings = onOpenSettings)
            if (otgDirectoryUri == null) OtgRequiredBanner()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val pages = listOf("photos", "archive", "settings")
                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = pages.indexOf(currentScreenRoute).coerceAtLeast(0),
                    pageCount = { pages.size }
                )

                androidx.compose.runtime.LaunchedEffect(currentScreenRoute) {
                    val targetPage = pages.indexOf(currentScreenRoute)
                    if (targetPage >= 0 && targetPage != pagerState.currentPage) {
                        pagerState.animateScrollToPage(targetPage)
                    }
                }

                androidx.compose.runtime.LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                    if (!pagerState.isScrollInProgress) {
                        onNavigate(pages[pagerState.currentPage])
                    }
                }

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (pages.getOrNull(page)) {
                        "photos" -> PhotosRoute(
                            viewModel = viewModel,
                            selectedIds = selectedIds,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            gridColumnsCount = gridColumnsCount,
                            actionBarHeightPx = actionBarHeightPx,
                            onItemClick = { item ->
                                if (selectedIds.isNotEmpty()) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    val grouped = viewModel.groupedMediaItems.value
                                    val allMediaItems = grouped.mapNotNull { (it as? GalleryItem.Media)?.item }
                                    val index = allMediaItems.indexOfFirst { it.id == item.id }
                                    if (index >= 0) {
                                        onSetActivePreview(FullscreenState(
                                            items = allMediaItems,
                                            initialIndex = index,
                                            sourceTab = SourceTab.PHOTOS
                                        ))
                                    }
                                }
                            },
                            onItemLongClick = { item -> viewModel.toggleSelection(item.id) }
                        )
                        "archive" -> ArchiveRoute(
                            viewModel = viewModel,
                            selectedIds = selectedIds,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            gridColumnsCount = gridColumnsCount,
                            actionBarHeightPx = actionBarHeightPx,
                            onItemClick = { item ->
                                val currentActiveUuid = viewModel.otgManager.activeArchiveUuid.value
                                val isItemActive = isOtgConnected && item.archiveUuid == currentActiveUuid
                                if (selectedIds.isNotEmpty()) {
                                    if (isItemActive || item.hasCachedPreview) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        showDisconnectedOtgItemInfo = item
                                    }
                                } else {
                                    if (isItemActive) {
                                        val grouped = viewModel.archivedGroupedItems.value
                                        val allMediaItems = grouped.mapNotNull { (it as? GalleryItem.Media)?.item }
                                        val index = allMediaItems.indexOfFirst { it.id == item.id }
                                        if (index >= 0) {
                                            onSetActivePreview(FullscreenState(
                                                items = allMediaItems,
                                                initialIndex = index,
                                                sourceTab = SourceTab.ARCHIVE
                                            ))
                                        }
                                    } else {
                                        showDisconnectedOtgItemInfo = item
                                    }
                                }
                            },
                            onItemLongClick = { item ->
                                val currentActiveUuid = viewModel.otgManager.activeArchiveUuid.value
                                val isItemActive = isOtgConnected && item.archiveUuid == currentActiveUuid
                                if (isItemActive || item.hasCachedPreview) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    Toast.makeText(context, "Подключите нужный OTG накопитель", Toast.LENGTH_SHORT).show()
                                }
                            })
                        "settings" -> {
                            val physicalArchiveSize by viewModel.physicalArchiveSize.collectAsState()
                            val otgDirectoryDisplayName by viewModel.otgDirectoryDisplayName.collectAsState()
                            val knownArchives by viewModel.knownArchives.collectAsState()
                            val activeArchiveUuid by viewModel.otgManager.activeArchiveUuid.collectAsState()
                            val isSyncingThumbnails by viewModel.isSyncingThumbnails.collectAsState()
                            val syncThumbnailsProgress by viewModel.syncThumbnailsProgress.collectAsState()
                            val missingThumbnailsCount by viewModel.missingThumbnailsCount.collectAsState()

                            LaunchedEffect(Unit) {
                                viewModel.updateMissingThumbnailsCount()
                            }

                            SettingsTab(
                                onSelectOtgDirectory = { showChangeFolderConfirmDialog = true },
                                onClearCache = { viewModel.clearPreviewCache() },
                                isOtgConnected = isOtgConnected,
                                otgDirectoryDisplayName = otgDirectoryDisplayName,
                                cacheSize = previewCacheManager.getCacheSize(),
                                cacheFilesCount = previewCacheManager.getCacheFileCount(),
                                isLocalFolder = viewModel.isOtgLocalFolder(),
                                currentArchiveSize = physicalArchiveSize,
                                isLimitActive = viewModel.isLimitActive,
                                vpsManager = viewModel.vpsManager,
                                onShowDebugLogs = { showDebugLogsDialog = true },
                                onSyncArchive = onSyncArchive,
                                onRefresh = { viewModel.refresh() },
                                knownArchives = knownArchives,
                                onDeleteArchive = { viewModel.deleteArchive(it) },
                                activeArchiveUuid = activeArchiveUuid,
                                isSyncingThumbnails = isSyncingThumbnails,
                                syncThumbnailsProgress = syncThumbnailsProgress,
                                missingThumbnailsCount = missingThumbnailsCount,
                                onSyncThumbnails = { viewModel.startThumbnailSync() },
                                onCancelSyncThumbnails = { viewModel.cancelThumbnailSync() }
                            )
                        }
                    }
                }
            }
        }

        showInfoDialogItem?.let { item ->
            InfoDialog(item = item, imageLoader = imageLoader ?: return@let, isOtgConnected = if (item.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG) (isOtgConnected && item.archiveUuid == activeArchiveUuid) else isOtgConnected,
                onOpenFullscreen = {
                    if (activePreviewState == null) {
                        // Open fullscreen from info: just this single item
                        onSetActivePreview(FullscreenState(
                            items = listOf(item),
                            initialIndex = 0,
                            sourceTab = if (currentScreenRoute == "archive") SourceTab.ARCHIVE else SourceTab.PHOTOS
                        ))
                    } else {
                        // Already in fullscreen, just close the properties dialog so we can continue swiping
                        onSetShowInfoDialog(null)
                    }
                }, onDismiss = { onSetShowInfoDialog(null) })
        }

        if (showOtgGuideDialog) {
            OtgGuideDialog(onConfirm = { onSetShowOtgGuide(false); onSelectOtgDirectory() }, onDismiss = { onSetShowOtgGuide(false) })
        }

        missingFilesNotification?.let { missingNames ->
            MissingFilesDialog(missingNames = missingNames, onDismiss = { viewModel.dismissMissingFilesNotification() })
        }

        activePreviewState?.let { state ->
            FullscreenPreview(
                state = state,
                imageLoader = imageLoader ?: return@let,
                isOtgConnected = isOtgConnected,
                otgDirectoryUri = otgDirectoryUri,
                activeArchiveUuid = activeArchiveUuid,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                onClose = {
                    onSetActivePreview(null)
                },
                onShowInfo = { item -> onSetShowInfoDialog(item) },
                onDeleteImmediate = { item -> viewModel.deleteSingleItemImmediate(item) },
                onArchiveSingle = { item, uri -> viewModel.archiveSingleItem(item, uri) },
                onRestoreSingle = { item -> viewModel.restoreSingleItem(item) },
                onShare = { item ->
                    viewModel.shareMediaItem(item, context) { errMsg ->
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                },
                isSharingPreparing = isSharingPreparing,
                isArchiving = archiveState.isArchiving
            )
        }

        pendingDelete?.let { items ->
            DeleteConfirmDialog(
                pendingDelete = items,
                isArchiveTab = currentScreenRoute == "archive",
                isOtgConnected = isOtgConnected,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.dismissDelete() }
            )
        }

        archiveState.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissArchiveError() },
                title = { Text("Ошибка архивирования") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissArchiveError() }) {
                        Text(
                            text = "ОК",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    val context = LocalContext.current
                    androidx.compose.material3.TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", err))
                    }) {
                        Text("Скопировать лог")
                    }
                }
            )
        }

        restoreState.error?.let { err ->
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { viewModel.dismissRestoreError() },
                title = { Text("Ошибка восстановления") },
                text = { Text(err) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissRestoreError() }) {
                        Text(
                            text = "ОК",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", err))
                    }) {
                        Text("Скопировать лог")
                    }
                }
            )
        }

        val syncState by viewModel.syncState.collectAsState()
        syncState?.let { stateMessage ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSync() },
                title = { Text(stringResource(R.string.sync_archive_title)) },
                text = { Text(stateMessage) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSync() }) {
                        Text(
                            text = "ОК",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    if (stateMessage.contains("Ошибка", ignoreCase = true)) {
                        val context = LocalContext.current
                        androidx.compose.material3.TextButton(onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Log", stateMessage))
                        }) {
                            Text("Скопировать лог")
                        }
                    }
                }
            )
        }

        val showNamingDialog by viewModel.showNamingDialog.collectAsState()
        showNamingDialog?.let { uri ->
            ArchiveNamingDialog(
                onConfirm = { name ->
                    viewModel.otgManager.saveOtgArchive(uri, name)
                    viewModel.refresh()
                },
                onDismiss = {
                    viewModel.dismissNamingDialog()
                }
            )
        }

        val showCreateArchiveGuide by viewModel.showCreateArchiveGuideDialog.collectAsState()
        showCreateArchiveGuide?.let { uri ->
            CreateArchiveGuideDialog(
                onConfirm = {
                    viewModel.dismissCreateArchiveGuideDialog()
                    // После подтверждения показываем окно для ввода имени архива
                    viewModel.showNamingDialog(uri)
                },
                onDismiss = {
                    viewModel.dismissCreateArchiveGuideDialog()
                }
            )
        }

        val showFirstLaunch by viewModel.showFirstLaunchDialog.collectAsState()
        if (showFirstLaunch) {
            LaunchedEffect(Unit) {
                viewModel.dismissFirstLaunchDialog()
                onSetShowOtgGuide(true)
            }
        }

                val showLocalFolder by viewModel.showLocalFolderDialog.collectAsState()
        val pendingFolder by viewModel.pendingDeviceFolderToRequest.collectAsState()
        if (showLocalFolder) {
            LocalFolderDialog(
                folderPath = pendingFolder ?: "DCIM",
                onSelectFolder = {
                    onSelectDeviceDirectory()
                },
                onDismiss = { viewModel.dismissLocalFolderDialog() }
            )
        }

        val showArchiveFolderAccess by viewModel.showArchiveFolderAccessDialog.collectAsState()
        val archiveFolderPath by viewModel.archiveAccessFolderPath.collectAsState()
        if (showArchiveFolderAccess && archiveFolderPath != null) {
            ArchiveFolderAccessDialog(
                folderPath = archiveFolderPath!!,
                onSelectFolder = {
                    viewModel.confirmArchiveFolderAccess()
                    onSelectDeviceDirectory()
                },
                onDismiss = { viewModel.dismissArchiveFolderAccessDialog() }
            )
        }

        val showUnknownDrive by viewModel.showUnknownDriveDialog.collectAsState()
        if (showUnknownDrive) {
            LaunchedEffect(Unit) {
                viewModel.dismissUnknownDriveDialog()
                onSetShowOtgGuide(true)
            }
        }

        if (showEjectConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showEjectConfirmDialog = false },
                title = { Text("Извлечь накопитель?", fontWeight = FontWeight.Bold) },
                text = { Text("Вы уверены, что хотите отключить USB-накопитель в приложении? Для возобновления работы потребуется выбрать папку заново.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showEjectConfirmDialog = false
                            viewModel.ejectOtg()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = "Извлечь",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEjectConfirmDialog = false }) {
                        Text(
                            text = "Отмена",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
            )
        }

        val isEjecting by viewModel.isEjecting.collectAsState()
        if (isEjecting) {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Подготовка к извлечению", fontWeight = FontWeight.Bold)
                    }
                },
                text = { Text("Завершаем запись данных и сбрасываем кеш на диск. Не извлекайте диск до появления подтверждения…") },
                confirmButton = {}
            )
        }

        val showEjectSuccess by viewModel.showEjectSuccessDialog.collectAsState()
        if (showEjectSuccess) {
            AlertDialog(
                onDismissRequest = { viewModel.otgManager.dismissEjectSuccessDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Можно извлекать", fontWeight = FontWeight.Bold)
                    }
                },
                text = { Text(stringResource(R.string.eject_success_toast)) },
                confirmButton = {
                    Button(onClick = { viewModel.otgManager.dismissEjectSuccessDialog() }) {
                        Text(stringResource(R.string.btn_ok), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, softWrap = false)
                    }
                }
            )
        }


        val showWriteProtectedRoot by viewModel.showWriteProtectedRootDialog.collectAsState()
        if (showWriteProtectedRoot) {
            WriteProtectedRootDialog(
                onRetry = {
                    viewModel.dismissWriteProtectedRootDialog()
                    onSelectOtgDirectory()
                },
                onDismiss = {
                    viewModel.dismissWriteProtectedRootDialog()
                }
            )
        }

        val showLimitReachedDialog by viewModel.showLimitReachedDialog.collectAsState()
        if (showLimitReachedDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissLimitReachedDialog() },
                title = { Text("Лимит бесплатной версии", fontWeight = FontWeight.Bold) },
                text = { Text("Вы достигли лимита бесплатной версии в 128 МБ. Для продолжения архивации необходимо приобрести PRO версию либо удалить часть фото из архива.") },
                confirmButton = {
                    Button(onClick = { viewModel.dismissLimitReachedDialog() }) {
                        Text(
                            text = "ОК",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Toast.makeText(context, "Покупка PRO версии временно недоступна", Toast.LENGTH_SHORT).show()
                    }) {
                        Text(
                            text = "Купить PRO",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
            )
        }

        showDisconnectedOtgItemInfo?.let { item ->
            DisconnectedOtgInfoDialog(
                item = item,
                imageLoader = imageLoader,
                onDismiss = { showDisconnectedOtgItemInfo = null }
            )
        }



        if (showDebugLogsDialog) {
            DebugLogsDialog(onDismiss = { showDebugLogsDialog = false })
        }
    }
}

@Composable
fun SelectionHelperChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    isRightAligned: Boolean = false,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = if (isRightAligned) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRightAligned) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.15f),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SelectionHelperPanel(
    firstSelectedItem: MediaItem?,
    visibleItems: List<MediaItem>,
    onSelectItems: (Collection<String>) -> Unit,
    onSelectDateRangeClick: () -> Unit,
    onClearSelection: () -> Unit
) {
    val hasSelected = firstSelectedItem != null
    val hasFolder = firstSelectedItem != null && !firstSelectedItem.originalRelativePath.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "ВЫДЕЛИТЬ",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 1: Все
                    SelectionHelperChip(
                        text = "Все",
                        icon = Icons.Outlined.CheckCircleOutline,
                        onClick = {
                            val allIds = visibleItems.map { it.id }
                            onSelectItems(allIds)
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.BOTTOM_RIGHT),
                        modifier = Modifier.weight(1f)
                    )
                    // Chip 2: С этой датой
                    SelectionHelperChip(
                        text = "С датой",
                        icon = Icons.Outlined.CalendarToday,
                        enabled = hasSelected,
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetDate = firstSelectedItem.dateModified
                                val cal1 = Calendar.getInstance().apply { timeInMillis = targetDate * 1000 }
                                val matching = visibleItems.filter { item ->
                                    val cal2 = Calendar.getInstance().apply { timeInMillis = item.dateModified * 1000 }
                                    cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                                            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                                } .map { it.id }
                                onSelectItems(matching)
                            }
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.BOTTOM_LEFT),
                        isRightAligned = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chip 3: В этой папке
                    SelectionHelperChip(
                        text = "В папке",
                        icon = Icons.Outlined.Folder,
                        enabled = hasFolder,
                        onClick = {
                            if (firstSelectedItem != null) {
                                val targetPath = firstSelectedItem.originalRelativePath
                                val matching = visibleItems.filter { it.originalRelativePath == targetPath } .map { it.id }
                                onSelectItems(matching)
                            }
                        },
                        shape = ConcaveCutoutShape(CutoutCorner.TOP_RIGHT),
                        modifier = Modifier.weight(1f)
                    )
                    // Chip 4: Выбрать диапазон
                    SelectionHelperChip(
                        text = "Диапазон",
                        icon = Icons.Outlined.DateRange,
                        onClick = onSelectDateRangeClick,
                        shape = ConcaveCutoutShape(CutoutCorner.TOP_LEFT),
                        isRightAligned = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Central circular button for clear/cancel selection (TV-remote style)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable { onClearSelection() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Снять выделение",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

enum class CutoutCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

class ConcaveCutoutShape(
    val cutoutCorner: CutoutCorner,
    val outerRadius: Dp = 12.dp,
    val cutoutRadius: Dp = 26.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val w = size.width
        val h = size.height
        val rOuter = with(density) { outerRadius.toPx() }
        val rCut = with(density) { cutoutRadius.toPx() }

        when (cutoutCorner) {
            CutoutCorner.BOTTOM_RIGHT -> {
                path.moveTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rCut)
                path.arcTo(
                    rect = Rect(w - rCut, h - rCut, w + rCut, h + rCut),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.BOTTOM_LEFT -> {
                path.moveTo(rOuter, 0f)
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rCut, h)
                path.arcTo(
                    rect = Rect(-rCut, h - rCut, rCut, h + rCut),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.TOP_RIGHT -> {
                path.moveTo(0f, rOuter)
                path.arcTo(
                    rect = Rect(0f, 0f, rOuter * 2, rOuter * 2),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w - rCut, 0f)
                path.arcTo(
                    rect = Rect(w - rCut, -rCut, w + rCut, rCut),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.close()
            }
            CutoutCorner.TOP_LEFT -> {
                path.moveTo(rCut, 0f)
                path.lineTo(w - rOuter, 0f)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, 0f, w, rOuter * 2),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(w, h - rOuter)
                path.arcTo(
                    rect = Rect(w - rOuter * 2, h - rOuter * 2, w, h),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(rOuter, h)
                path.arcTo(
                    rect = Rect(0f, h - rOuter * 2, rOuter * 2, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                path.lineTo(0f, rCut)
                path.arcTo(
                    rect = Rect(-rCut, -rCut, rCut, rCut),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                path.close()
            }
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (startDateMillis: Long?, endDateMillis: Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(state.selectedStartDateMillis, state.selectedEndDateMillis)
                }
            ) {
                Text(
                    text = "Выбрать",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Отмена",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    text = "Выберите диапазон дат",
                    modifier = Modifier.padding(start = 24.dp, top = 24.dp)
                )
            },
            headline = {
                // Default headline behaves fine, but we can customize if needed
            },
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}

fun mapStepToText(step: String): String {
    return when {
        step == "preparing" || step == "restore_preparing" -> "Подготовка..."
        step == "verifying" || step == "restore_verifying" -> "Проверка целостности..."
        step == "copying" -> "Копирование в архив..."
        step == "restore_reading" -> "Чтение из архива..."
        step == "restore_writing" -> "Запись на устройство..."
        step.startsWith("restore_writing_percent:") -> {
            val pct = step.substringAfter(":")
            "Запись на устройство... ($pct%)"
        }
        else -> "Обработка..."
    }
}

@Composable
fun ProgressPanel(
    title: String,
    fileName: String,
    currentIndex: Int,
    totalFiles: Int,
    progressFraction: Float,
    extraInfo: String = "",
    icon: ImageVector,
    statusText: String? = null,
    onCancel: (() -> Unit)? = null
) {

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val cardPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = cardPulseAlpha)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$title ($currentIndex из $totalFiles)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (fileName.isNotEmpty()) {
                        Text(
                            text = fileName + extraInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!statusText.isNullOrEmpty()) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round
                )
                if (onCancel != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.material3.TextButton(
                        onClick = onCancel,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Прервать",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}




