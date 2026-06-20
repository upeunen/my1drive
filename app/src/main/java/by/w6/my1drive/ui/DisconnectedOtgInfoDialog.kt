package by.w6.my1drive.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.utils.OtgThumbnailRequest
import coil.ImageLoader
import coil.compose.AsyncImage
import java.util.Locale

@Composable
fun DisconnectedOtgInfoDialog(
    item: MediaItem,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit
) {
    val sizeMb = remember(item.size) {
        String.format(Locale.US, "%.2f MB", item.size.toFloat() / (1024 * 1024))
    }

    val originalPath = remember(item.originalRelativePath, item.displayName) {
        val folder = item.originalRelativePath?.trim('/') ?: ""
        if (folder.isNotEmpty()) "$folder/${item.displayName}" else item.displayName
    }

    val otgPath = remember(item.otgUri) {
        if (item.otgUri == null) "Неизвестно" else {
            try {
                val decoded = Uri.decode(item.otgUri)
                val documentSegment = decoded.substringAfter("/document/", "")
                if (documentSegment.isNotEmpty()) {
                    documentSegment.substringAfter(":", documentSegment)
                } else {
                    decoded.substringAfterLast("/")
                }
            } catch (e: Exception) {
                item.otgUri
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Детали архивного файла",
                style = MaterialTheme.typography.titleMedium,
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
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val imageModel: Any = if (item.hash != null) {
                        OtgThumbnailRequest(
                            otgUri = item.otgUri ?: "",
                            hash = item.hash,
                            mimeType = item.mimeType,
                            isConnected = false,
                            existingCachePath = item.thumbnailPath
                        )
                    } else {
                        item.uri
                    }

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
                    // Имя файла
                    Column {
                        Text(
                            text = "Имя файла",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Путь от куда архивания была произведенна
                    Column {
                        Text(
                            text = "Исходный путь",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = originalPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Где сейчас файл находится путь (OTG)
                    Column {
                        Text(
                            text = "Текущий путь в архиве",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = otgPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    // Размер файла
                    Column {
                        Text(
                            text = "Размер файла",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = sizeMb,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(
                    text = "ОК",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    softWrap = false
                )
            }
        }
    )
}
