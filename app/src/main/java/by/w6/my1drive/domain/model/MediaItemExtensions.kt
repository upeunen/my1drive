package by.w6.my1drive.domain.model

import by.w6.my1drive.utils.OtgThumbnailRequest
import java.io.File

/**
 * Returns the original full path combining the folder and file name.
 */
fun MediaItem.getOriginalFullPath(): String {
    val folder = originalRelativePath?.trim('/') ?: ""
    return if (folder.isNotEmpty()) "$folder/$displayName" else displayName
}

/**
 * Encapsulates the complex logic to determine what image model to pass to Coil.
 * It checks if the item is archived on OTG, if we have a valid hash, if the OTG
 * drive is connected, and if a local cache file exists.
 */
fun MediaItem.getThumbnailModel(isOtgConnected: Boolean): Any {
    if (status == MediaStatus.ARCHIVED_OTG && hash != null) {
        if (!isOtgConnected && hasCachedPreview && thumbnailPath != null) {
            val cacheFile = File(thumbnailPath!!)
            if (cacheFile.exists()) {
                return cacheFile
            }
        }
        return OtgThumbnailRequest(
            otgUri = otgUri ?: "",
            hash = hash,
            mimeType = mimeType,
            isConnected = isOtgConnected,
            existingCachePath = thumbnailPath
        )
    }
    return uri
}
