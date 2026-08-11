package by.w6.my1drive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader
import kotlinx.coroutines.launch

@Composable
fun ArchiveRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, gridColumnsCount: Int = 3, actionBarHeightPx: Float = 0f,
    onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeArchiveUuid = uiState.activeArchiveUuid
    val sortMode = uiState.archiveSortMode
    val archivingItemIds = uiState.archivingItemIds
    val restoringItemIds = uiState.restoringItemIds
    val copiedItemIds = uiState.copiedItemIds
    val isSilentSyncing = uiState.isSilentSyncing
    val archiveState by viewModel.archiveState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncProgressState by viewModel.syncProgressState.collectAsState()
    val knownArchives by viewModel.knownArchives.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("my1drive_prefs", android.content.Context.MODE_PRIVATE)
    var showOffline by remember { mutableStateOf(prefs.getBoolean("show_offline_archives", false)) }
    
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "show_offline_archives") {
                showOffline = sharedPreferences.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) {
        onScrollStateChanged(listState.isScrollInProgress)
    }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentActionBarHeightPx by rememberUpdatedState(actionBarHeightPx)

    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = Color.Transparent
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val filterUuid = viewModel.archiveFilterUuid.collectAsState().value

    // Автоматически переключаем фильтр на подключенную флешку при её подключении
    LaunchedEffect(isOtgConnected, activeArchiveUuid) {
        if (isOtgConnected && activeArchiveUuid != null) {
            viewModel.setArchiveFilterUuid(activeArchiveUuid)
        }
    }

    // Карта uuid → Color по стабильному хэшу UUID
    val archiveColorMap = remember(knownArchives) {
        knownArchives.associate { archive ->
            archive.uuid to archiveStripeColor(archive.uuid)
        }
    }

    // Получаем yearGroups прямо из стейта
    val yearGroups = uiState.archiveYearGroups

    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }
    var hasInitializedDefaults by remember { mutableStateOf(false) }

    LaunchedEffect(yearGroups) {
        if (!hasInitializedDefaults && yearGroups.isNotEmpty()) {
            val currentCal = java.util.Calendar.getInstance()
            val curYear = currentCal.get(java.util.Calendar.YEAR)
            val curMonth = currentCal.get(java.util.Calendar.MONTH)
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
                        .clickable { viewModel.setArchiveFilterUuid(null) }
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
                        text = stringResource(by.w6.my1drive.R.string.filter_all),
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
                                viewModel.setArchiveFilterUuid(if (isActive) null else archive.uuid)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isCurrentConnected) {
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
                    text = stringResource(by.w6.my1drive.R.string.sort_by),
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
                            text = stringResource(by.w6.my1drive.R.string.sort_photo_date),
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
                            text = stringResource(by.w6.my1drive.R.string.sort_archive_date),
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
                                                text = stringResource(by.w6.my1drive.R.string.items_count, monthGroup.items.size),
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
                                                         contentDescription = stringResource(by.w6.my1drive.R.string.select_all_month),
                                                         tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                         modifier = Modifier.size(24.dp)
                                                     )
                                                 }
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (isExpanded) stringResource(by.w6.my1drive.R.string.collapse) else stringResource(by.w6.my1drive.R.string.expand)
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Сетка фотографий (если раскрыто)
                            if (isExpanded) {
                                val chunkedItems = monthGroup.chunkedItems
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
                                                    GooglePhotosGridItem(
                                                        item = mediaItem,
                                                        isSelected = isSelected,
                                                        isArchiving = isArchiving || isRestoring,
                                                        isCopied = isCopied,
                                                        imageLoader = imageLoader,
                                                        isOtgConnected = if (mediaItem.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG) (isOtgConnected && mediaItem.archiveUuid == activeArchiveUuid) else isOtgConnected,
                                                        archiveStripeOverrideColor = if (showOffline && knownArchives.size > 1) archiveColorMap[mediaItem.archiveUuid] else null,
                                                        modifier = Modifier,
                                                        onClick = {
                                                            onItemClick(mediaItem)
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
