package by.w6.my1drive.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.Cloud
import android.widget.Toast
import kotlinx.coroutines.launch

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import by.w6.my1drive.R

@Composable
fun SettingsTab(
    onSelectOtgDirectory: () -> Unit = {},
    onClearCache: () -> Unit,
    isOtgConnected: Boolean = false,
    otgDirectoryDisplayName: String? = null,
    cacheSize: Long = 0L,
    cacheFilesCount: Int = 0,
    isLocalFolder: Boolean = false,
    currentArchiveSize: Long = 0L,
    isLimitActive: Boolean = true,
    vpsManager: by.w6.my1drive.utils.VpsConnectionManager? = null,
    onShowDebugLogs: () -> Unit = {},
    onSyncArchive: () -> Unit = {},
    onRefresh: () -> Unit = {},
    knownArchives: List<by.w6.my1drive.data.local.ArchiveEntity> = emptyList()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.title_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        // 1. OTG/USB Storage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.otg_archive_folder),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = otgDirectoryDisplayName ?: stringResource(R.string.drive_not_selected),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isOtgConnected) stringResource(R.string.drive_known_connected)
                        else stringResource(R.string.drive_known_disconnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOtgConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLocalFolder && otgDirectoryDisplayName != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "⚠️ Внимание: Выбрана папка во внутренней памяти телефона. Для резервного копирования рекомендуется выбрать папку на USB флешке.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSelectOtgDirectory,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (otgDirectoryDisplayName != null) stringResource(R.string.change_otg_folder)
                                else stringResource(R.string.select_otg_folder),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                    if (isOtgConnected && otgDirectoryDisplayName != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onSyncArchive,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.sync_archive_title),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 1.5 Multi-Archive settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var showOffline by remember {
                    mutableStateOf(
                        context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
                            .getBoolean("show_offline_archives", false)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Работа с несколькими архивами (дисками)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Отображать файлы с отключенных накопителей в галерее",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = showOffline,
                        onCheckedChange = { checked ->
                            showOffline = checked
                            context.getSharedPreferences("my1drive_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("show_offline_archives", checked)
                                .apply()
                            onRefresh()
                        }
                    )
                }

                // Легенда архивов — только при включённой мульти-архивности
                if (knownArchives.isNotEmpty() && showOffline) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Цвета носителей",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    knownArchives.forEachIndexed { idx, archive ->
                        val stripe = ARCHIVE_STRIPE_COLORS[idx % ARCHIVE_STRIPE_COLORS.size]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 28.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(stripe)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = archive.name.ifBlank { archive.folderName.ifBlank { archive.uuid.take(8) } },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (vpsManager != null) {
            Spacer(Modifier.height(16.dp))
            var vpsEnabled by remember { mutableStateOf(vpsManager.isVpsEnabled()) }
            var host by remember { mutableStateOf(vpsManager.getHost()) }
            var portStr by remember { mutableStateOf(vpsManager.getPort().toString()) }
            var username by remember { mutableStateOf(vpsManager.getUsername()) }
            var password by remember { mutableStateOf(vpsManager.getPassword()) }
            var remotePath by remember { mutableStateOf(vpsManager.getRemotePath()) }
            var vpsLimitGbStr by remember { mutableStateOf(vpsManager.getVpsLimitGb().toString()) }
            val coroutineScope = rememberCoroutineScope()
            var testingConnection by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "VPS-сервер",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(Для опытных пользователей, сохранение на виртуальный сервер)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Switch(
                            checked = vpsEnabled,
                            onCheckedChange = { checked ->
                                vpsEnabled = checked
                                vpsManager.setVpsEnabled(checked)
                                Toast.makeText(context, if (checked) "VPS архивация включена" else "VPS архивация выключена", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    if (vpsEnabled) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Хост (IP-адрес / домен)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = portStr,
                                onValueChange = { portStr = it },
                                label = { Text("Порт") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Имя пользователя") },
                                modifier = Modifier.weight(2f),
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Пароль") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = remotePath,
                            onValueChange = { remotePath = it },
                            label = { Text("Путь для архива на VPS") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = vpsLimitGbStr,
                            onValueChange = { vpsLimitGbStr = it },
                            label = { Text("Лимит архива на VPS (в ГБ)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val port = portStr.toIntOrNull() ?: 22
                                val limit = vpsLimitGbStr.toIntOrNull() ?: 10
                                testingConnection = true
                                coroutineScope.launch {
                                    val result = vpsManager.testConnection(host, port, username, password, remotePath)
                                    testingConnection = false
                                    if (result.isSuccess) {
                                        vpsManager.saveConfig(host, port, username, password, remotePath)
                                        vpsManager.setVpsLimitGb(limit)
                                        Toast.makeText(context, "Подключение успешно установлено и сохранено!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка подключения: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !testingConnection && host.isNotEmpty() && username.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (testingConnection) "Проверка..." else "Проверить и сохранить настройки")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. Storage Limit / Capacity Card
        val limitBytes = 128 * 1024 * 1024L
        val progress = if (isLimitActive) {
            (currentArchiveSize.toFloat() / limitBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
        val isFull = isLimitActive && currentArchiveSize >= limitBytes

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFull)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isLimitActive) "Объем архива (Бесплатная версия)" else "Объем архива",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))

                if (isLimitActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = if (progress >= 0.9f) {
                                            listOf(Color(0xFFE0AAFF), Color(0xFFF44336))
                                        } else {
                                            listOf(Color(0xFF8A2BE2), Color(0xFFE0AAFF))
                                        }
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val usedMb = currentArchiveSize.toDouble() / (1024.0 * 1024.0)
                Text(
                    text = if (isLimitActive) "Использовано: %.1f МБ из 128.0 МБ (%.1f%%)".format(usedMb, progress * 100)
                           else "Использовано: %.1f МБ".format(usedMb),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (isFull) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Достигнут лимит бесплатной версии. Приобретите PRO версию или удалите часть файлов из архива.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. Maintenance & Debug Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Обслуживание и отладка",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Cache Stats
                Text(
                    text = stringResource(R.string.thumbnail_cache),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.files_count, cacheFilesCount)} (${formatBytes(cacheSize)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClearCache,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.clear_thumb_cache),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))

                // Debug logs
                Button(
                    onClick = onShowDebugLogs,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Просмотр логов отладки",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))

                // Version Info
                val versionName = remember(context) {
                    try {
                        val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                        packageInfo.versionName ?: "2.0.1"
                    } catch (e: Exception) {
                        "2.0.1"
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Версия приложения:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = versionName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_open_settings),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> " B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
