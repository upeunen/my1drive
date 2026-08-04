package by.w6.my1drive.ui

import android.net.Uri
import by.w6.my1drive.domain.model.MediaItem

data class SuccessDialogData(val freedSpaceBytes: Long, val currentFreeSpaceBytes: Long, val totalSpaceBytes: Long)

sealed class AppDialog {
    object FirstLaunch : AppDialog()
    data class Paywall(val missingPhotos: Int, val missingVideos: Int) : AppDialog()
    object UnknownDrive : AppDialog()
    object UnreadableOtg : AppDialog()
    object WriteProtectedRoot : AppDialog()
    object LocalFolder : AppDialog()
    data class Naming(val uri: Uri) : AppDialog()
    data class CreateArchiveGuide(val uri: Uri) : AppDialog()
    data class ManageStoragePermission(val itemsToWait: List<MediaItem>?) : AppDialog()

    object CreateFolder : AppDialog()
    data class ArchiveFolderAccess(val folderPath: String?) : AppDialog()
    data class Success(val data: SuccessDialogData) : AppDialog()
    object UsbTooltip : AppDialog()
    object PromoCode : AppDialog()
}

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
