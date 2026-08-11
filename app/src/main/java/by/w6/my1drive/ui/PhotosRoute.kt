package by.w6.my1drive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
      val uiState by viewModel.uiState.collectAsState()
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isPremiumUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surfaceVariantColor)
                    .clickable { viewModel.showPaywall() }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val bannerText = if (uiState.isTrialActive) {
                    stringResource(by.w6.my1drive.R.string.trial_active_days, uiState.remainingTrialDays)
                } else {
                    val maxPhotos = uiState.maxPhotos
                    val maxVideos = uiState.maxVideos
                    stringResource(
                        by.w6.my1drive.R.string.free_version_limits,
                        (maxPhotos - photosArchivedCount).coerceAtLeast(0),
                        maxPhotos,
                        (maxVideos - videosArchivedCount).coerceAtLeast(0),
                        maxVideos
                    )
                }
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariantColor,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(by.w6.my1drive.R.string.pro_unlimited),
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
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
                        text = stringResource(by.w6.my1drive.R.string.sort_photo_date),
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
                        text = stringResource(by.w6.my1drive.R.string.sort_restore_date),
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
            },
            onScrollStateChanged = onScrollStateChanged
        )
    }
}
