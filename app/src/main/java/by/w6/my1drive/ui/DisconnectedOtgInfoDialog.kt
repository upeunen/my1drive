package by.w6.my1drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.getOriginalFullPath
import by.w6.my1drive.domain.model.getThumbnailModel
import by.w6.my1drive.ui.components.InfoPropertyRow
import by.w6.my1drive.utils.FormatterUtils
import by.w6.my1drive.utils.MediaShareHelper
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun DisconnectedOtgInfoDialog(
    item: MediaItem,
    imageLoader: ImageLoader,
    archive: by.w6.my1drive.data.local.ArchiveEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sizeMb = remember(item.size) {
        FormatterUtils.formatFileSize(item.size)
    }

    val otgPath = remember(item.otgUri) {
        item.otgUri?.let { MediaShareHelper.getReadableOtgPath(context, it) } ?: "Неизвестный путь"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.disconnected_info_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Маленькая миниатюра
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModel = item.getThumbnailModel(false)

                    AsyncImage(
                        model = imageModel,
                        imageLoader = imageLoader,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (item.isVideo) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Video",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoPropertyRow(
                        label = stringResource(R.string.info_file_name_title),
                        value = item.displayName
                    )

                    InfoPropertyRow(
                        label = stringResource(R.string.info_current_archive_path_title),
                        value = otgPath,
                        valueStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )

                    InfoPropertyRow(
                        label = stringResource(R.string.info_file_size_title),
                        value = sizeMb
                    )

                    val archiveName = archive?.name ?: item.archiveName
                    archiveName?.let {
                        InfoPropertyRow(
                            label = stringResource(R.string.info_storage_drive_title),
                            value = it
                        )
                    }

                    item.archiveUuid?.let {
                        InfoPropertyRow(
                            label = stringResource(R.string.info_storage_uuid_title),
                            value = it
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { MediaShareHelper.shareFileDetails(context, item, sizeMb, archive) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_share), maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { MediaShareHelper.copyFileDetailsToClipboard(context, item, sizeMb, archive) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_copy), maxLines = 1, fontSize = 11.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.btn_ok),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false,
                        fontSize = 11.sp
                    )
                }
            }
        }
    )
}
