package by.w6.my1drive.ui
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.utils.OtgThumbnailRequest
import coil.ImageLoader
import coil.compose.AsyncImage
@Composable
fun GooglePhotosGridItem(
    item: MediaItem,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Context menu state for archived items
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuItem by remember { mutableStateOf<MediaItem?>(null) }
    val scale by animateFloatAsState(targetValue = if (isSelected) 0.93f else 1.0f, label = "Scale")
    val isArchivedOffline = item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected
    val isArchivedOnline = item.status == MediaStatus.ARCHIVED_OTG && isOtgConnected
    val hasCachedPreview = item.hasCachedPreview
    // Build Coil model: use OtgThumbnailRequest for archived items, plain URI for local
    val imageModel: Any = if (item.status == MediaStatus.ARCHIVED_OTG && item.hash != null) {
        OtgThumbnailRequest(
            otgUri = item.otgUri ?: "",
            hash = item.hash,
            mimeType = item.mimeType,
            isConnected = isOtgConnected,
            existingCachePath = item.thumbnailPath
        )
    } else {
        item.uri
    }
    Card(
        modifier = Modifier
            .padding(3.dp)
            .aspectRatio(1f)
            .scale(scale)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            // Context menu for archived items on long click
            // Context menu dialog for archived items
            if (item.status == MediaStatus.ARCHIVED_OTG && showContextMenu && contextMenuItem == item) {
                AlertDialog(
                    onDismissRequest = { showContextMenu = false; contextMenuItem = null },
                    title = { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    contextMenuItem = null
                                    // Delete action - trigger info dialog via longClick
                                    onLongClick()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_delete_from_archive), color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(
                                onClick = {
                                    showContextMenu = false
                                    contextMenuItem = null
                                    // Properties - trigger info dialog via longClick
                                    onLongClick()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_properties))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showContextMenu = false; contextMenuItem = null }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
                )
            }
            if (isArchivedOffline && !hasCachedPreview) {
                // Placeholder: no cached preview and drive is not connected
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.PlayCircle else Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = Color(0xFF5E35B1).copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.preview_on_disk),
                            fontSize = 9.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    imageLoader = imageLoader,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isArchivedOffline) 0.5f else 1.0f)
                )
            }
            // Green/Red dot indicator for original availability (archived items only)
            if (item.status == MediaStatus.ARCHIVED_OTG) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(12.dp)
                        .background(
                            color = if (isOtgConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
                            shape = CircleShape
                        )
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
            // Video play badge
            if (item.isVideo && !(isArchivedOffline && !hasCachedPreview)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            // Storage status icon (Top Right)
            Icon(
                imageVector = if (item.status == MediaStatus.ON_DEVICE) Icons.Default.CloudOff else Icons.Default.CloudDone,
                contentDescription = null,
                tint = if (item.status == MediaStatus.ON_DEVICE) Color.White.copy(alpha = 0.7f) else Color(0xFF4CAF50),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(2.dp)
            )
            // Selection badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
