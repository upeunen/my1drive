package by.w6.my1drive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.w6.my1drive.R
import by.w6.my1drive.data.local.ArchiveEntity
import by.w6.my1drive.utils.VpsConnectionManager
import by.w6.my1drive.ui.settings.ArchiveProgressSection
import by.w6.my1drive.ui.settings.MaintenanceAndDebugSection
import by.w6.my1drive.ui.settings.OtgSettingsSection
import by.w6.my1drive.ui.settings.PromoCodeCard
import by.w6.my1drive.ui.settings.VpsSettingsSection

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
    vpsManager: VpsConnectionManager? = null,
    onShowDebugLogs: () -> Unit = {},
    onSyncArchive: () -> Unit = {},
    onRefresh: () -> Unit = {},
    knownArchives: List<ArchiveEntity> = emptyList(),
    onDeleteArchive: (String) -> Unit = {},
    activeArchiveUuid: String? = null,
    isSyncingThumbnails: Boolean = false,
    syncThumbnailsProgress: Pair<Int, Int> = Pair(0, 0),
    missingThumbnailsCount: Int = 0,
    onSyncThumbnails: () -> Unit = {},
    onCancelSyncThumbnails: () -> Unit = {},
    isStorageLow: Boolean = false,
    hasAllFilesAccess: Boolean = true,
    onRequestManageStorage: () -> Unit = {},
    onPromoCode: () -> Unit = {},
    hasPromoCodes: Boolean = false
) {
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

        if (!hasAllFilesAccess) {
            by.w6.my1drive.ui.settings.ManageStorageCard(
                onRequestManageStorage = onRequestManageStorage
            )
            Spacer(Modifier.height(16.dp))
        }

        // 1. OTG/USB Storage Card & Multi-Archive settings
        OtgSettingsSection(
            isOtgConnected = isOtgConnected,
            otgDirectoryDisplayName = otgDirectoryDisplayName,
            isLocalFolder = isLocalFolder,
            onSelectOtgDirectory = onSelectOtgDirectory,
            knownArchives = knownArchives,
            onDeleteArchive = onDeleteArchive,
            activeArchiveUuid = activeArchiveUuid,
            onRefresh = onRefresh
        )

        Spacer(Modifier.height(16.dp))

        // VPS Settings Card
        VpsSettingsSection(vpsManager = vpsManager)

        Spacer(Modifier.height(16.dp))

        // 2. Storage Limit / Capacity Card
        ArchiveProgressSection(
            currentArchiveSize = currentArchiveSize,
            isLimitActive = isLimitActive
        )

        // Промокод — показываем если в Remote Config есть коды
        if (hasPromoCodes) {
            Spacer(Modifier.height(16.dp))
            PromoCodeCard(onPromoCode = onPromoCode)
        }

        Spacer(Modifier.height(16.dp))

        // 3. Maintenance & Debug Info Card
        MaintenanceAndDebugSection(
            cacheFilesCount = cacheFilesCount,
            cacheSize = cacheSize,
            isStorageLow = isStorageLow,
            onClearCache = onClearCache,
            onShowDebugLogs = onShowDebugLogs
        )
    }
}
