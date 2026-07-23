package by.w6.my1drive.ui

import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.ui.GalleryItem

data class GalleryUiState(
    val groupedItems: List<GalleryItem> = emptyList(),
    val archivedGroupedItems: List<GalleryItem> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val activeArchiveUuid: String? = null,
    val deviceSortMode: DeviceSortMode = DeviceSortMode.BY_PHOTO_DATE,
    val archiveSortMode: ArchiveSortMode = ArchiveSortMode.BY_PHOTO_DATE,
    val archivingItemIds: Set<String> = emptySet(),
    val restoringItemIds: Set<String> = emptySet(),
    val copiedItemIds: Set<String> = emptySet(),
    val photosArchivedCount: Int = 0,
    val videosArchivedCount: Int = 0,
    val isPremiumUnlocked: Boolean = false,
    val isSilentSyncing: Boolean = false,
    val syncState: String? = null,
    val isSharingPreparing: Boolean = false,
    val isCheckingConnection: Boolean = false,
    val gridColumnsCount: Int = 3,
    val physicalArchiveSize: Long = 0L,
    val otgDirectoryDisplayName: String? = null,
    val isSyncingThumbnails: Boolean = false,
    val syncThumbnailsProgress: Pair<Int, Int> = Pair(0, 0),
    val missingThumbnailsCount: Int = 0,
    val isStorageLow: Boolean = false,
    val activeDialog: AppDialog? = null,
    val pendingDelete: List<MediaItem>? = null
)
