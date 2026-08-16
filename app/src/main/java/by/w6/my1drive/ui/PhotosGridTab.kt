package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader

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
        return
    }

    // Предвычисляем map: заголовок → список MediaItem в разделе.
    // Используется для кнопки «выбрать все» в DateCategoryHeader.
    val sectionMediaMap = remember(groupedItems) {
        val map = mutableMapOf<String, List<MediaItem>>()
        var currentHeader: String? = null
        val buffer = mutableListOf<MediaItem>()
        for (gi in groupedItems) {
            when (gi) {
                is GalleryItem.Header -> {
                    currentHeader?.let { map[it] = buffer.toList() }
                    currentHeader = gi.title
                    buffer.clear()
                }
                is GalleryItem.Media -> buffer.add(gi.item)
            }
        }
        currentHeader?.let { map[it] = buffer.toList() }
        map
    }

    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState.isScrollInProgress) {
        onScrollStateChanged(gridState.isScrollInProgress)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumnsCount),
        state = gridState,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = groupedItems,
            key = { item ->
                when (item) {
                    is GalleryItem.Header -> "header_${item.title}"
                    is GalleryItem.Media  -> "media_${item.item.id}"
                }
            },
            span = { item ->
                when (item) {
                    is GalleryItem.Header -> GridItemSpan(maxLineSpan)
                    is GalleryItem.Media  -> GridItemSpan(1)
                }
            }
        ) { item ->
            when (item) {
                is GalleryItem.Header -> {
                    val sectionItems = sectionMediaMap[item.title] ?: emptyList()
                    val allSelected = remember(currentSelectedIds, sectionItems) {
                        sectionItems.isNotEmpty() && sectionItems.all { currentSelectedIds.contains(it.id) }
                    }
                    DateCategoryHeader(
                        title = item.title,
                        isSelectionMode = currentSelectedIds.isNotEmpty(),
                        isSelected = allSelected,
                        onToggleSelection = {
                            onSelectItems(sectionItems.map { it.id }, !allSelected)
                        }
                    )
                }
                is GalleryItem.Media -> {
                    val mediaItem = item.item
                    GooglePhotosGridItem(
                        item = mediaItem,
                        isSelected = currentSelectedIds.contains(mediaItem.id),
                        imageLoader = imageLoader,
                        isOtgConnected = if (mediaItem.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG)
                            (isOtgConnected && mediaItem.archiveUuid == activeArchiveUuid) else isOtgConnected,
                        isArchiving = archivingItemIds.contains(mediaItem.id),
                        isCopied = copiedItemIds.contains(mediaItem.id),
                        onClick    = { onItemClick(mediaItem) },
                        onLongClick = { onItemLongClick(mediaItem) }
                    )
                }
            }
        }
    }
}
