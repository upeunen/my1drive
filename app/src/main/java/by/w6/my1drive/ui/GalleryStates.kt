package by.w6.my1drive.ui

import android.net.Uri
import by.w6.my1drive.domain.model.MediaItem

data class DialogState(
    val showFirstLaunchDialog: Boolean = false,
    val showUnknownDriveDialog: Boolean = false,
    val showUnreadableOtgDialog: Boolean = false,
    val showWriteProtectedRootDialog: Boolean = false,
    val showLocalFolderDialog: Boolean = false,
    val showNamingDialog: Uri? = null,
    val showCreateArchiveGuideDialog: Uri? = null,
    val showLimitReachedDialog: Boolean = false,
    val showCreateFolderDialog: Boolean = false,
    val showArchiveFolderAccessDialog: Boolean = false,
    val archiveAccessFolderPath: String? = null
)

data class SyncState(
    val isSyncingThumbnails: Boolean = false,
    val syncThumbnailsProgress: Pair<Int, Int> = Pair(0, 0),
    val missingThumbnailsCount: Int = 0,
    val isSharingPreparing: Boolean = false,
    val isStorageLow: Boolean = false
)

data class GalleryConfigState(
    val gridColumnsCount: Int = 3,
    val deviceSortMode: DeviceSortMode = DeviceSortMode.BY_PHOTO_DATE,
    val archiveSortMode: ArchiveSortMode = ArchiveSortMode.BY_PHOTO_DATE,
    val isScrolling: Boolean = false
)

data class DeleteRestoreState(
    val pendingDelete: List<MediaItem>? = null,
    val askRestorePath: Boolean = false,
    val restoreRequest: RestoreRequest? = null
)
