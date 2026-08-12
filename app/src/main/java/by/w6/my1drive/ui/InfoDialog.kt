package by.w6.my1drive.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.model.MediaStatus
import by.w6.my1drive.domain.model.getOriginalFullPath
import by.w6.my1drive.domain.model.getThumbnailModel
import by.w6.my1drive.ui.components.InfoPropertyRow
import by.w6.my1drive.utils.ExifHelper
import by.w6.my1drive.utils.FormatterUtils
import by.w6.my1drive.utils.MediaShareHelper
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoDialog(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    archive: by.w6.my1drive.data.local.ArchiveEntity? = null,
    onOpenFullscreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val sizeMb = remember(item.size) {
        FormatterUtils.formatFileSize(item.size)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exifData by remember { mutableStateOf<Map<String, String>?>(null) }
    var isLoadingExif by remember { mutableStateOf(false) }
    var showExifSection by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.file_info_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Preview thumbnail ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A2A))
                    .then(
                        if (item.status == MediaStatus.ON_DEVICE || item.status == MediaStatus.ARCHIVED_OTG)
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        while (true) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed) {
                                                onOpenFullscreen()
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    val videoThumbUri = if (item.status == MediaStatus.ARCHIVED_OTG && item.thumbnailPath != null) {
                        Uri.fromFile(File(item.thumbnailPath))
                    } else {
                        item.uri
                    }
                    AsyncImage(
                        model = videoThumbUri,
                        imageLoader = imageLoader,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    val imageModel = item.getThumbnailModel(isOtgConnected)
                    AsyncImage(
                        model = imageModel,
                        imageLoader = imageLoader,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Location status chip ──
            val (locationText, locationColor) = when {
                item.status == MediaStatus.ON_DEVICE -> Pair(
                    stringResource(R.string.info_location_device),
                    Color(0xFF4CAF50)
                )
                isOtgConnected -> Pair(
                    stringResource(R.string.info_location_otg) + (item.archiveName?.let { " ($it)" } ?: ""),
                    MaterialTheme.colorScheme.primary
                )
                else -> Pair(
                    stringResource(R.string.info_location_otg_disconnected) + (item.archiveName?.let { " ($it)" } ?: ""),
                    Color(0xFFFF9800)
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = locationColor.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = locationText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = locationColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── File details ──
            InfoPropertyRow(label = stringResource(R.string.info_name, "").replace(":", ""), value = item.displayName)
            InfoPropertyRow(label = stringResource(R.string.info_type, "").replace(":", ""), value = item.mimeType)
            InfoPropertyRow(label = stringResource(R.string.info_size, "").replace(":", ""), value = sizeMb)

            val archiveName = archive?.name ?: item.archiveName
            archiveName?.let {
                Spacer(modifier = Modifier.height(4.dp))
                InfoPropertyRow(label = stringResource(R.string.info_drive), value = it)
            }

            item.archiveUuid?.let {
                Spacer(modifier = Modifier.height(4.dp))
                InfoPropertyRow(label = stringResource(R.string.info_drive_uuid), value = it)
            }

            if (item.status == MediaStatus.ON_DEVICE) {
                item.originalRelativePath?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    InfoPropertyRow(label = stringResource(R.string.info_original_folder).replace(":", ""), value = item.getOriginalFullPath())
                }
            }

            // ── Path to original (OTG URI) ──
            item.otgUri?.let {
                Spacer(modifier = Modifier.height(8.dp))
                InfoPropertyRow(
                    label = stringResource(R.string.info_otg_path).replace(":", ""),
                    value = MediaShareHelper.getReadableOtgPath(context, it),
                    valueStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // EXIF section
            val isOtgOffline = item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (showExifSection && exifData != null) {
                        Text(
                            text = stringResource(R.string.info_exif_data),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val dataMap = exifData!!
                        if (dataMap.isEmpty()) {
                            Text(
                                text = stringResource(R.string.info_exif_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        } else {
                            dataMap.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (exifData == null && !isLoadingExif) {
                                    isLoadingExif = true
                                    showExifSection = true
                                    scope.launch {
                                        val data = withContext(Dispatchers.IO) {
                                            val uriToRead = if (item.status == MediaStatus.ARCHIVED_OTG) {
                                                item.otgUri?.let { Uri.parse(it) }
                                            } else {
                                                item.uri
                                            }
                                            if (uriToRead != null) {
                                                ExifHelper.readExifMetadata(context, uriToRead)
                                            } else {
                                                emptyMap()
                                            }
                                        }
                                        exifData = data
                                        isLoadingExif = false
                                    }
                                } else {
                                    showExifSection = !showExifSection
                                }
                            },
                            enabled = !isOtgOffline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isOtgOffline) stringResource(R.string.info_exif_unavailable_offline)
                                       else if (showExifSection) stringResource(R.string.info_hide_exif)
                                       else stringResource(R.string.info_show_exif),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                    Text(stringResource(R.string.info_btn_share), maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { MediaShareHelper.copyFileDetailsToClipboard(context, item, sizeMb, archive) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.info_btn_copy), maxLines = 1, fontSize = 11.sp)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.btn_close),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
