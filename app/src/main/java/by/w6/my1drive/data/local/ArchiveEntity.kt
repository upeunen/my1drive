package by.w6.my1drive.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Identifies a unique OTG archive drive.
 * The UUID is written to / read from .my1drive_uuid on the OTG root.
 */
@Entity(tableName = "archive_info")
data class ArchiveEntity(
    @PrimaryKey val id: String,       // UUID (from .my1drive_uuid file on the drive)
    val otgUri: String,               // SAF tree URI of the archive folder
    val label: String,                // Display label, e.g. "Archive #1"
    val firstSeen: Long,              // Unix timestamp (seconds)
    val totalFiles: Int = 0           // Updated on each sync
)
