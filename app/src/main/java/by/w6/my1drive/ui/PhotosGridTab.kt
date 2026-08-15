package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Расстояние между элементами сетки — единая точка истины вместо магических констант 3f */
private val GRID_SPACING = 3.dp

// ─── Layout data model ───────────────────────────────────────────────────────

/** Предвычисленные коэффициенты для элемента в буфере строки — исключает двойной вызов [clampedAspectRatio] */
private data class BufferedMedia(
    val galleryItem: GalleryItem.Media,
    val originalRatio: Float,
    val displayRatio: Float,
    val cropFraction: Float
)

private data class JustifiedEntry(
    val galleryItem: GalleryItem.Media,
    val widthDp: Dp,
    val cropFraction: Float = 0f,
    val isCroppedHorizontally: Boolean = false
)

private sealed class JustifiedLayoutItem {
    data class SectionHeader(val title: String, val mediaInSection: List<MediaItem>) : JustifiedLayoutItem()
    data class PhotoRow(val entries: List<JustifiedEntry>, val rowHeightDp: Dp) : JustifiedLayoutItem()
}

// ─── Algorithm ───────────────────────────────────────────────────────────────

private fun buildJustifiedLayout(
    groupedItems: List<GalleryItem>,
    containerWidthDp: Float,
    spacingDp: Float,
    targetRowHeightDp: Float
): List<JustifiedLayoutItem> {
    val result = mutableListOf<JustifiedLayoutItem>()
    val rowBuf = mutableListOf<BufferedMedia>()  // предвычисленные ratios хранятся здесь
    var rowRawWidth = 0f
    val sectionItems = mutableListOf<MediaItem>()
    var lastHeaderIndex = -1

    fun patchLastHeader() {
        if (lastHeaderIndex < 0) return
        val old = result[lastHeaderIndex] as JustifiedLayoutItem.SectionHeader
        result[lastHeaderIndex] = old.copy(mediaInSection = sectionItems.toList())
        sectionItems.clear()
    }

    fun emitRow(isLastRow: Boolean) {
        if (rowBuf.isEmpty()) return
        val n = rowBuf.size
        val spacing = spacingDp * (n - 1).coerceAtLeast(0)
        // Используем предвычисленные displayRatio из BufferedMedia — без повторного вызова clampedAspectRatio
        val rawWidths = rowBuf.map { it.displayRatio * targetRowHeightDp }
        val rawTotal = rawWidths.sum()
        val (finalWidths, finalHeight) = if (isLastRow || n == 1) {
            Pair(rawWidths, targetRowHeightDp)
        } else if (rawTotal <= 0f) {
            Pair(rawWidths, targetRowHeightDp)
        } else {
            val scale = (containerWidthDp - spacing) / rawTotal
            val safeScale = if (scale.isNaN() || scale.isInfinite() || scale <= 0f) 1f else scale
            Pair(rawWidths.map { it * safeScale }, targetRowHeightDp * safeScale)
        }
        result.add(JustifiedLayoutItem.PhotoRow(
            entries = rowBuf.mapIndexed { i, m ->
                JustifiedEntry(
                    galleryItem = m.galleryItem,
                    widthDp = finalWidths[i].dp,
                    cropFraction = m.cropFraction,
                    isCroppedHorizontally = m.displayRatio > m.originalRatio
                )
            },
            rowHeightDp = finalHeight.takeIf { it.isFinite() && it > 0f }?.dp ?: targetRowHeightDp.dp
        ))
        rowBuf.clear()
        rowRawWidth = 0f
    }

    for (gi in groupedItems) {
        when (gi) {
            is GalleryItem.Header -> {
                emitRow(isLastRow = true)
                patchLastHeader()
                lastHeaderIndex = result.size
                result.add(JustifiedLayoutItem.SectionHeader(gi.title, emptyList()))
            }
            is GalleryItem.Media -> {
                sectionItems.add(gi.item)
                val original = if (gi.item.aspectRatio > 0f) gi.item.aspectRatio else 1f
                // Вызывается однажды, результат сохраняется в BufferedMedia
                val (displayRatio, crop) = clampedAspectRatio(original)
                val itemRawWidth = targetRowHeightDp * displayRatio
                val spacingIfAdded = if (rowBuf.isEmpty()) 0f else spacingDp
                if (rowBuf.isNotEmpty() && rowRawWidth + spacingIfAdded + itemRawWidth > containerWidthDp) {
                    emitRow(isLastRow = false)
                }
                rowBuf.add(BufferedMedia(gi, original, displayRatio, crop))
                rowRawWidth += (if (rowBuf.size == 1) 0f else spacingDp) + itemRawWidth
            }
        }
    }
    emitRow(isLastRow = true)
    patchLastHeader()
    return result
}

// ─── Composable ──────────────────────────────────────────────────────────────

@Composable
fun PhotosGridTab(
    groupedItems: List<GalleryItem>,
    selectedIds: Set<String>,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean = true,
    activeArchiveUuid: String? = null,
    archivingItemIds: Set<String> = emptySet(),
    copiedItemIds: Set<String> = emptySet(),
    gridColumnsCount: Int = 3,
    actionBarHeightPx: Float = 0f,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onSelectItems: (Collection<String>, Boolean) -> Unit = { _, _ -> },
    onScrollStateChanged: (Boolean) -> Unit = {}
) {
    if (groupedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SdStorage, contentDescription = stringResource(R.string.empty_category),
                    modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.empty_category), style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
            }
        }
        return
    }

    val targetRowHeightDp: Float = when (gridColumnsCount) {
        2 -> 220f
        4 -> 110f
        else -> 160f
    }
    val spacingDp = GRID_SPACING.value  // Float для алгоритма, Dp-константа GRID_SPACING для Compose

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerWidthDp = maxWidth.value
        val currentSelectedIds by rememberUpdatedState(selectedIds)

        // Пропускаем layout пока BoxWithConstraints ещё не измерен (containerWidthDp == 0)
        if (containerWidthDp <= 0f) return@BoxWithConstraints

        // Индустриальный стандарт: вычисление layout на Dispatchers.Default, чтобы не блокировать Main thread
        val layoutItems by produceState(
            initialValue = emptyList<JustifiedLayoutItem>(),
            key1 = groupedItems,
            key2 = containerWidthDp,
            key3 = targetRowHeightDp
        ) {
            value = withContext(Dispatchers.Default) {
                buildJustifiedLayout(groupedItems, containerWidthDp, spacingDp, targetRowHeightDp)
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(listState.isScrollInProgress) {
            onScrollStateChanged(listState.isScrollInProgress)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
            modifier = Modifier.fillMaxSize()
        ) {
            items(layoutItems, key = { li ->
                when (li) {
                    is JustifiedLayoutItem.SectionHeader -> "header_${li.title}"
                    // Детерминированный ключ: используем id + размер, а не hashCode() который меняется при каждом recompose
                    is JustifiedLayoutItem.PhotoRow -> "row_${li.entries.firstOrNull()?.galleryItem?.item?.id}_${li.entries.size}"
                }
            }) { li ->
                when (li) {
                    is JustifiedLayoutItem.SectionHeader -> {
                        val allSelected = remember(currentSelectedIds, li.mediaInSection) {
                            li.mediaInSection.isNotEmpty() && li.mediaInSection.all { currentSelectedIds.contains(it.id) }
                        }
                        DateCategoryHeader(
                            title = li.title,
                            isSelectionMode = currentSelectedIds.isNotEmpty(),
                            isSelected = allSelected,
                            onToggleSelection = { onSelectItems(li.mediaInSection.map { it.id }, !allSelected) }
                        )
                    }
                    is JustifiedLayoutItem.PhotoRow -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(li.rowHeightDp),
                            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)
                        ) {
                            li.entries.forEach { entry ->
                                val item = entry.galleryItem.item
                                GooglePhotosGridItem(
                                    item = item,
                                    isSelected = currentSelectedIds.contains(item.id),
                                    imageLoader = imageLoader,
                                    isOtgConnected = if (item.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG)
                                        (isOtgConnected && item.archiveUuid == activeArchiveUuid) else isOtgConnected,
                                    isArchiving = archivingItemIds.contains(item.id),
                                    isCopied = copiedItemIds.contains(item.id),
                                    cropFraction = entry.cropFraction,
                                    isCroppedHorizontally = entry.isCroppedHorizontally,
                                    modifier = Modifier.width(entry.widthDp).height(li.rowHeightDp),
                                    onClick = { onItemClick(item) },
                                    onLongClick = { onItemLongClick(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
