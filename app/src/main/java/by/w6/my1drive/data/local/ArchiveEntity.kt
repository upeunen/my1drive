package by.w6.my1drive.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archives")
data class ArchiveEntity(
    @PrimaryKey
    val uuid: String,            // Archive hardware UUID
    val name: String,            // User custom name of the archive
    val folderName: String,      // Physical folder name on OTG (e.g. "Arhiv-Диск А")
    val dateCreated: Long,       // Unix timestamp (ms)
    val lastConnected: Long      // Unix timestamp (ms) of last connection
)
