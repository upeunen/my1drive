package by.w6.my1drive.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_archive")
data class MediaEntity(
    @PrimaryKey
    val id: String,                          // SHA-256 hash of the file
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val otgUri: String,                      // SAF Document URI on OTG drive
    val thumbnailPath: String?,              // Local cache path: filesDir/my1drive_previews/{hash}.webp
    val duration: Long? = null,              // Video duration in ms
    val originalRelativePath: String? = null,// Original MediaStore RELATIVE_PATH (e.g. "DCIM/Camera")
    @ColumnInfo(defaultValue = "0")
    val lastAccessed: Long = 0L              // Unix ms -- updated when preview is loaded (for LRU eviction)
)
