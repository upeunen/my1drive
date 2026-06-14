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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader

@Composable
fun PhotosGridTab(
    groupedItems: List<GalleryItem>,
    selectedIds: Set<String>,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean = true,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
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
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxSize()
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
                    is GalleryItem.Header -> DateCategoryHeader(title = item.title)
                    is GalleryItem.Media -> {
                        val isSelected = selectedIds.contains(item.item.id)
                        GooglePhotosGridItem(
                            item = item.item,
                            isSelected = isSelected,
                            imageLoader = imageLoader,
                            isOtgConnected = isOtgConnected,
                            onClick = { onItemClick(item.item) },
                            onLongClick = { onItemLongClick(item.item) }
                        )
                    }
                }
            }
        }
    }
}
