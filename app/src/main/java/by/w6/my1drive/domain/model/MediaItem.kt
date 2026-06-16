package by.w6.my1drive.domain.model

import android.net.Uri

data class MediaItem(
    val id: String,                          // "local_${mediaStoreId}" or "archived_${hash}"
    val displayName: String,
    val uri: Uri,                            // local MediaStore URI or file:// thumbnail URI
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val status: MediaStatus,
    val duration: Long? = null,              // video duration in ms
    val hash: String? = null,               // SHA-256 hash (archived items only)
    val otgUri: String? = null,             // SAF Document URI on OTG drive
    val thumbnailPath: String? = null,       // Local cache path (filesDir/my1drive_previews/{hash}.webp)
    val originalRelativePath: String? = null,// MediaStore RELATIVE_PATH (e.g. "DCIM/Camera")
    val isVideo: Boolean = mimeType.startsWith("video/"),
    val dateArchived: Long? = null          // Unix timestamp in seconds
) {
    /** True if a local preview thumbnail is cached and the file exists */
    val hasCachedPreview: Boolean
        get() = thumbnailPath != null && java.io.File(thumbnailPath).exists()
}

