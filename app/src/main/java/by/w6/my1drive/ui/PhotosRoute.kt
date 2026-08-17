package by.w6.my1drive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.domain.model.MediaItem
import coil.ImageLoader

@Composable
fun PhotosRoute(
    viewModel: GalleryViewModel, selectedIds: Set<String>, imageLoader: ImageLoader,
    isOtgConnected: Boolean, gridColumnsCount: Int = 3, actionBarHeightPx: Float = 0f,
    onItemClick: (MediaItem) -> Unit, onItemLongClick: (MediaItem) -> Unit,
    onScrollStateChanged: (Boolean) -> Unit = {}
) {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeArchiveUuid = uiState.activeArchiveUuid
    val groupedItems = uiState.groupedItems
    val sortMode = uiState.deviceSortMode
    val archivingItemIds = uiState.archivingItemIds
    val copiedItemIds = uiState.copiedItemIds
    
    val photosArchivedCount = uiState.photosArchivedCount
    val videosArchivedCount = uiState.videosArchivedCount
    val isPremiumUnlocked = uiState.isPremiumUnlocked

    val primaryColor = MaterialTheme.colorScheme.primary
    val transparentColor = Color.Transparent
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val filterMode by viewModel.mediaFilterMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Filter: All / Photos / Videos
            Row(
                modifier = Modifier
                    .background(
                        color = surfaceVariantColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(2.dp)
            ) {
                MediaFilterMode.values().forEach { mode ->
                    val isActive = filterMode == mode
                    val filterBg by animateColorAsState(if (isActive) primaryColor else transparentColor, label = "filterBg")
                    val filterText by animateColorAsState(if (isActive) onPrimaryColor else onSurfaceVariantColor, label = "filterText")
                    val labelRes = when (mode) {
                        MediaFilterMode.ALL -> by.w6.my1drive.R.string.filter_all
                        MediaFilterMode.PHOTOS_ONLY -> by.w6.my1drive.R.string.filter_photos
                        MediaFilterMode.VIDEOS_ONLY -> by.w6.my1drive.R.string.filter_videos
                    }
                    Box(
                        modifier = Modifier
                            .background(filterBg, RoundedCornerShape(14.dp))
                            .clickable { viewModel.setMediaFilterMode(mode) }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = filterText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sort by: Photo Date / Restore Date
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
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(by.w6.my1drive.R.string.sort_photo_date),
                        color = photoDateTextColor,
                        style = MaterialTheme.typography.labelSmall,
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
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(by.w6.my1drive.R.string.sort_restore_date),
                        color = restoreDateTextColor,
                        style = MaterialTheme.typography.labelSmall,
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
            },
            onGridColumnsChange = { viewModel.setGridColumnsCount(it) },
            onScrollStateChanged = onScrollStateChanged
        )
    }
}
