package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy

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
    onSelectItems: (Collection<String>, Boolean) -> Unit = { _, _ -> }
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentSelectedIds by androidx.compose.runtime.rememberUpdatedState(selectedIds)
    val currentActionBarHeightPx by androidx.compose.runtime.rememberUpdatedState(actionBarHeightPx)

    if (groupedItems.isEmpty()) {
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
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(gridColumnsCount),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerCoordinates = it }
        ) {
            items(
                items = groupedItems,
                key = { item ->
                    when (item) {
                        is GalleryItem.Header -> "header_${item.title}"
                        is GalleryItem.Media -> "media_${item.item.id}"
                    }
                },
                span = { item ->
                    when (item) {
                        is GalleryItem.Header -> GridItemSpan(maxLineSpan)
                        is GalleryItem.Media -> GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is GalleryItem.Header -> {
                        val headerIndex = groupedItems.indexOf(item)
                        val itemsUnderHeader = remember(groupedItems, headerIndex) {
                            val list = mutableListOf<MediaItem>()
                            if (headerIndex >= 0) {
                                for (i in (headerIndex + 1) until groupedItems.size) {
                                    val next = groupedItems[i]
                                    if (next is GalleryItem.Header) break
                                    if (next is GalleryItem.Media) {
                                        list.add(next.item)
                                    }
                                }
                            }
                            list
                        }
                        val allSelected = remember(selectedIds, itemsUnderHeader) {
                            itemsUnderHeader.isNotEmpty() && itemsUnderHeader.all { selectedIds.contains(it.id) }
                        }
                        DateCategoryHeader(
                            title = item.title,
                            isSelectionMode = selectedIds.isNotEmpty(),
                            isSelected = allSelected,
                            onToggleSelection = {
                                onSelectItems(itemsUnderHeader.map { it.id }, !allSelected)
                            }
                        )
                    }
                    is GalleryItem.Media -> {
                        val isSelected = selectedIds.contains(item.item.id)
                        val isArchiving = archivingItemIds.contains(item.item.id)
                        val isCopied = copiedItemIds.contains(item.item.id)
                        var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                        GooglePhotosGridItem(
                            item = item.item,
                            isSelected = isSelected,
                            imageLoader = imageLoader,
                            isOtgConnected = if (item.item.status == by.w6.my1drive.domain.model.MediaStatus.ARCHIVED_OTG) (isOtgConnected && item.item.archiveUuid == activeArchiveUuid) else isOtgConnected,
                            isArchiving = isArchiving,
                            isCopied = isCopied,
                            modifier = Modifier.onGloballyPositioned { itemCoordinates = it },
                            onClick = {
                                onItemClick(item.item)
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
                                                    gridState.animateScrollBy(-hiddenTopNew)
                                                } else if (hiddenBottomNew > thirdOfHeightNew) {
                                                    gridState.animateScrollBy(hiddenBottomNew)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = { onItemLongClick(item.item) }
                        )
                    }
                }
            }
        }
    }
}
