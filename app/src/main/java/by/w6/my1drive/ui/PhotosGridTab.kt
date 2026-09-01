package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.ArrowUpward
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
import by.w6.my1drive.ui.layout.BentoBlock
import by.w6.my1drive.ui.layout.BentoBlockView
import by.w6.my1drive.ui.layout.BentoLayoutHelper
import coil.ImageLoader

import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight

private sealed interface BentoFeedItem {
    data class HeaderItem(val title: String, val sectionItems: List<MediaItem>) : BentoFeedItem
    data class BlockItem(val block: BentoBlock) : BentoFeedItem
}

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
    onGridColumnsChange: (Int) -> Unit = {},
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

    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val listState = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        onScrollStateChanged(listState.isScrollInProgress)
    }

    var zoomAccumulator by remember { mutableFloatStateOf(1f) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gridColumnsCount) {
                detectTransformGestures { _, _, zoom, _ ->
                    zoomAccumulator *= zoom
                    if (zoomAccumulator > 1.30f) {
                        // Pinch out -> larger items, fewer columns
                        if (gridColumnsCount > 2) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onGridColumnsChange(gridColumnsCount - 1)
                        }
                        zoomAccumulator = 1f
                    } else if (zoomAccumulator < 0.75f) {
                        // Pinch in -> smaller items, more columns
                        if (gridColumnsCount < 4) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onGridColumnsChange(gridColumnsCount + 1)
                        }
                        zoomAccumulator = 1f
                    }
                }
            }
    ) {
        val horizontalPadding = 2.dp
        val spacing = 3.dp
        val availableWidth = (maxWidth - (horizontalPadding * 2)).coerceAtLeast(0.dp)
        val unitSize = remember(availableWidth, gridColumnsCount) {
            BentoLayoutHelper.calculateUnitSize(availableWidth, gridColumnsCount, spacing)
        }

        val feedItems = remember(groupedItems, gridColumnsCount) {
            val result = mutableListOf<BentoFeedItem>()
            var currentHeader: String? = null
            val currentSectionMedia = mutableListOf<MediaItem>()

            fun flushSection() {
                if (currentHeader != null || currentSectionMedia.isNotEmpty()) {
                    val sectionList = currentSectionMedia.toList()
                    if (currentHeader != null) {
                        result.add(BentoFeedItem.HeaderItem(currentHeader!!, sectionList))
                    }
                    if (sectionList.isNotEmpty()) {
                        val blocks = BentoLayoutHelper.computeBlocks(
                            items = sectionList,
                            gridColumnsCount = gridColumnsCount
                        )
                        for (block in blocks) {
                            result.add(BentoFeedItem.BlockItem(block))
                        }
                    }
                    currentSectionMedia.clear()
                }
            }

            for (gi in groupedItems) {
                when (gi) {
                    is GalleryItem.Header -> {
                        flushSection()
                        currentHeader = gi.title
                    }
                    is GalleryItem.Media -> {
                        currentSectionMedia.add(gi.item)
                    }
                }
            }
            flushSection()
            result
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = feedItems,
                key = { item ->
                    when (item) {
                        is BentoFeedItem.HeaderItem -> "header_${item.title}"
                        is BentoFeedItem.BlockItem -> item.block.key
                    }
                }
            ) { feedItem ->
                when (feedItem) {
                    is BentoFeedItem.HeaderItem -> {
                        val sectionItems = feedItem.sectionItems
                        val isSelectionMode = currentSelectedIds.isNotEmpty()
                        val sectionIds = remember(sectionItems) { sectionItems.map { it.id }.toSet() }
                        val allSelected = remember(isSelectionMode, currentSelectedIds.size, sectionIds) {
                            isSelectionMode && sectionIds.isNotEmpty() && currentSelectedIds.containsAll(sectionIds)
                        }
                        DateCategoryHeader(
                            title = feedItem.title,
                            isSelectionMode = isSelectionMode,
                            isSelected = allSelected,
                            onToggleSelection = {
                                onSelectItems(sectionIds, !allSelected)
                            }
                        )
                    }
                    is BentoFeedItem.BlockItem -> {
                        BentoBlockView(
                            block = feedItem.block,
                            unitSize = unitSize,
                            spacing = spacing,
                            selectedIds = currentSelectedIds,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            activeArchiveUuid = activeArchiveUuid,
                            archivingItemIds = archivingItemIds,
                            copiedItemIds = copiedItemIds,
                            onItemClick = onItemClick,
                            onItemLongClick = onItemLongClick
                        )
                    }
                }
            }
        }

        // Floating FastScroll Date Indicator
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val currentDateLabel = remember(firstVisibleIndex, feedItems) {
            if (feedItems.isEmpty()) ""
            else {
                var currentTitle = ""
                for (i in 0..minOf(firstVisibleIndex, feedItems.lastIndex)) {
                    val item = feedItems[i]
                    if (item is BentoFeedItem.HeaderItem) {
                        currentTitle = item.title
                    }
                }
                currentTitle
            }
        }

        AnimatedVisibility(
            visible = listState.isScrollInProgress && currentDateLabel.isNotEmpty(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = currentDateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Scroll to Top FAB
        val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
        val showScrollToTop by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 5 }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + androidx.compose.animation.scaleIn(),
            exit = fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
        ) {
            androidx.compose.material3.SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.cd_scroll_to_top)
                )
            }
        }
    }
}
