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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
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
            InfoRow(label = stringResource(R.string.info_name, ""), value = item.displayName)
            InfoRow(label = stringResource(R.string.info_type, ""), value = item.mimeType)
            InfoRow(label = stringResource(R.string.info_size, ""), value = sizeMb)

            item.archiveName?.let {
                Spacer(modifier = Modifier.height(4.dp))
                InfoRow(label = "Накопитель:", value = it)
            }

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
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
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
                            text = "Данные EXIF",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val dataMap = exifData!!
                        if (dataMap.isEmpty()) {
                            Text(
                                text = "EXIF теги отсутствуют или файл не содержит метаданных",
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
                            enabled = !isOtgOffline,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isOtgOffline) "EXIF недоступен (OTG отключен)"
                                       else if (showExifSection) "Скрыть EXIF"
                                       else "Показать EXIF",
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
                    onClick = { shareFileDetails(context, item, sizeMb) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Поделиться", maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { copyFileDetailsToClipboard(context, item, sizeMb) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Копировать", maxLines = 1, fontSize = 11.sp)
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

private fun getSharedImageUri(context: Context, sourceFile: File, displayName: String): Uri? {
    return try {
        val sharedTempDir = File(context.cacheDir, "shared_temp")
        sharedTempDir.mkdirs()
        val cleanName = displayName.substringBeforeLast(".") + "_preview.webp"
        val tempFile = File(sharedTempDir, cleanName)
        sourceFile.inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun shareFileDetails(context: Context, item: MediaItem, sizeMb: String) {
    val shareText = """
        Имя: ${item.displayName}
        Путь: ${item.otgUri ?: ""}
        Размер: $sizeMb
        Накопитель: ${item.archiveName ?: "Неизвестный"}
    """.trimIndent()

    val path = item.thumbnailPath ?: run {
        val previewDir = File(context.filesDir, "my1drive_previews")
        val cacheFile = File(previewDir, "${item.id}.my1d")
        if (cacheFile.exists()) cacheFile.absolutePath else null
    }

    if (path != null) {
        val file = File(path)
        if (file.exists()) {
            val uri = getSharedImageUri(context, file, item.displayName)
            if (uri != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/webp"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, item.displayName)
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Поделиться файлом"))
                return
            }
        }
    }

    // Fallback: text only
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, item.displayName)
        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Поделиться файлом"))
}

private fun copyFileDetailsToClipboard(context: Context, item: MediaItem, sizeMb: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val shareText = """
        Имя: ${item.displayName}
        Путь: ${item.otgUri ?: ""}
        Размер: $sizeMb
        Накопитель: ${item.archiveName ?: "Неизвестный"}
    """.trimIndent()

    val path = item.thumbnailPath ?: run {
        val previewDir = File(context.filesDir, "my1drive_previews")
        val cacheFile = File(previewDir, "${item.id}.my1d")
        if (cacheFile.exists()) cacheFile.absolutePath else null
    }

    if (path != null) {
        val file = File(path)
        if (file.exists()) {
            val uri = getSharedImageUri(context, file, item.displayName)
            if (uri != null) {
                val clip = android.content.ClipData.newUri(context.contentResolver, "file_thumbnail", uri).apply {
                    addItem(android.content.ClipData.Item(shareText))
                }
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                return
            }
        }
    }

    val clip = android.content.ClipData.newPlainText("file_details", shareText)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
}
