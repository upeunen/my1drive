package by.w6.my1drive.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import by.w6.my1drive.utils.OtgThumbnailRequest
import coil.ImageLoader
import coil.compose.AsyncImage
import java.io.File
import java.util.Locale
import android.content.Context
import android.media.ExifInterface
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InfoDialog(
    item: MediaItem,
    imageLoader: ImageLoader,
    isOtgConnected: Boolean,
    onOpenFullscreen: () -> Unit,
    onDismiss: () -> Unit
) {
    val sizeMb = remember(item.size) {
        String.format(Locale.US, "%.2f MB", item.size.toFloat() / (1024 * 1024))
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exifData by remember { mutableStateOf<Map<String, String>?>(null) }
    var isLoadingExif by remember { mutableStateOf(false) }
    var showExifSection by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.file_info_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                        // For videos, show thumbnail with play overlay
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

                Spacer(modifier = Modifier.height(12.dp))

                // ── Location status chip ──
                val (locationText, locationColor) = when {
                    item.status == MediaStatus.ON_DEVICE -> Pair(
                        stringResource(R.string.info_location_device),
                        Color(0xFF4CAF50)
                    )
                    isOtgConnected -> Pair(
                        stringResource(R.string.info_location_otg),
                        MaterialTheme.colorScheme.primary
                    )
                    else -> Pair(
                        stringResource(R.string.info_location_otg_disconnected),
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

                Spacer(modifier = Modifier.height(12.dp))

                // ── File details ──
                InfoRow(label = stringResource(R.string.info_name, ""), value = item.displayName)
                InfoRow(label = stringResource(R.string.info_type, ""), value = item.mimeType)
                InfoRow(label = stringResource(R.string.info_size, ""), value = sizeMb)

                item.originalRelativePath?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    InfoRow(label = stringResource(R.string.info_original_folder), value = it)
                }

                // ── Path to original (OTG URI) ──
                item.otgUri?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.info_otg_path),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Preview cache path ──
                item.thumbnailPath?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.info_preview_path),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── SHA-256 ──
                item.hash?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.info_sha256), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                if (!item.isVideo) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val isOtgOffline = item.status == MediaStatus.ARCHIVED_OTG && !isOtgConnected
                    
                    if (showExifSection) {
                        Text(
                            text = "Метаданные EXIF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        if (isLoadingExif) {
                            androidx.compose.material3.LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        } else if (exifData != null) {
                            val data = exifData!!
                            if (data.isEmpty()) {
                                Text(
                                    text = "EXIF-данные отсутствуют в этом файле",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        data.forEach { (key, value) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = key,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.Gray,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = value,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                if (isOtgOffline) return@TextButton
                                if (exifData == null) {
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
                                                readExifMetadata(context, uriToRead)
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
                            enabled = !isOtgOffline
                        ) {
                            Text(
                                text = if (isOtgOffline) "EXIF недоступен (OTG отключен)"
                                       else if (showExifSection) "Скрыть EXIF"
                                       else "Показать EXIF"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canOpen = item.status == MediaStatus.ON_DEVICE || (item.status == MediaStatus.ARCHIVED_OTG && isOtgConnected)
            if (canOpen) {
                Button(onClick = {
                    onOpenFullscreen()
                    onDismiss()
                }) {
                    Text("Открыть")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        },
        dismissButton = {
            val canOpen = item.status == MediaStatus.ON_DEVICE || (item.status == MediaStatus.ARCHIVED_OTG && isOtgConnected)
            if (canOpen) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    )

}

private fun readExifMetadata(context: Context, uri: Uri): Map<String, String> {
    val metadata = mutableMapOf<String, String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val exifInterface = ExifInterface(inputStream)
            
            val make = exifInterface.getAttribute(ExifInterface.TAG_MAKE)
            val model = exifInterface.getAttribute(ExifInterface.TAG_MODEL)
            if (!model.isNullOrEmpty()) {
                val fullModel = if (!make.isNullOrEmpty() && !model.startsWith(make)) "$make $model" else model
                metadata["Камера"] = fullModel.trim()
            }
            
            val dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            if (!dateTime.isNullOrEmpty()) {
                metadata["Дата съёмки"] = dateTime
            }
            
            val aperture = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER)
            if (!aperture.isNullOrEmpty()) {
                metadata["Диафрагма"] = "f/$aperture"
            }
            
            val shutterSpeed = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            if (!shutterSpeed.isNullOrEmpty()) {
                val speed = shutterSpeed.toDoubleOrNull()
                metadata["Выдержка"] = if (speed != null && speed < 1.0) {
                    "1/${(1.0 / speed).toInt()}"
                } else {
                    "$shutterSpeed с"
                }
            }
            
            val iso = exifInterface.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            if (!iso.isNullOrEmpty()) {
                metadata["ISO"] = iso
            }
            
            val focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            if (!focalLength.isNullOrEmpty()) {
                val focalDouble = focalLength.substringBefore("/").toDoubleOrNull()
                metadata["Фокусное расстояние"] = "${focalDouble ?: focalLength} мм"
            }
            
            val width = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val height = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                metadata["Разрешение"] = "${width}x${height}"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return metadata
}
