package by.w6.my1drive.ui

import androidx.compose.foundation.layout.*
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
import by.w6.my1drive.ui.settings.*
import by.w6.my1drive.utils.VpsConnectionManager

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
    hasPromoCodes: Boolean = false,
    onCleanOrphans: () -> Unit = {},
    onLanguageChanged: () -> Unit = {},
    onSearchOtherArchives: () -> Unit = {}
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

        Spacer(Modifier.height(16.dp))

        // 1. Top 2 Dashboard Tiles: Drive & Cache
        SettingsDashboardTiles(
            isOtgConnected = isOtgConnected,
            otgDirectoryDisplayName = otgDirectoryDisplayName,
            isLocalFolder = isLocalFolder,
            onSelectOtgDirectory = onSelectOtgDirectory,
            cacheFilesCount = cacheFilesCount,
            cacheSize = cacheSize,
            onClearCache = onClearCache
        )

        // Permission card if Manage External Storage is missing
        if (!hasAllFilesAccess) {
            Spacer(Modifier.height(16.dp))
            ManageStorageCard(
                onRequestManageStorage = onRequestManageStorage
            )
        }

        Spacer(Modifier.height(20.dp))

        // 2. Thumbnail Sync Section (if missing thumbnails exist or syncing)
        if (missingThumbnailsCount > 0 || isSyncingThumbnails) {
            FilesAndSyncSection(
                missingThumbnailsCount = missingThumbnailsCount,
                isSyncingThumbnails = isSyncingThumbnails,
                syncThumbnailsProgress = syncThumbnailsProgress,
                onSyncThumbnails = onSyncThumbnails,
                onCancelSyncThumbnails = onCancelSyncThumbnails,
                isOtgConnected = isOtgConnected
            )
            Spacer(Modifier.height(20.dp))
        }

        // 3. Archives & Storage Section
        ArchivesSettingsSection(
            knownArchives = knownArchives,
            onDeleteArchive = onDeleteArchive,
            activeArchiveUuid = activeArchiveUuid,
            isOtgConnected = isOtgConnected,
            onRefresh = onRefresh,
            onSearchOtherArchives = onSearchOtherArchives
        )

        Spacer(Modifier.height(20.dp))

        // 4. Promo Code Card (if available)
        if (hasPromoCodes) {
            PromoCodeCard(onPromoCode = onPromoCode)
            Spacer(Modifier.height(20.dp))
        }

        // 5. General / Language Settings Section
        LanguageSettingsSection(
            onLanguageChanged = onLanguageChanged
        )

        Spacer(Modifier.height(20.dp))

        // 6. Advanced (VPS, Maintenance, Logs) Collapsible Section
        AdvancedSettingsSection(
            vpsManager = vpsManager,
            onShowDebugLogs = onShowDebugLogs,
            onCleanOrphans = onCleanOrphans
        )

        Spacer(Modifier.height(24.dp))
    }
}
