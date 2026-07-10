package by.w6.my1drive.ui

import android.content.IntentSender
import by.w6.my1drive.domain.model.MediaItem

// ─── Drive connection status ───

enum class DriveStatus {
    NO_URI_CONFIGURED,
    KNOWN_DRIVE_CONNECTED,
    KNOWN_DRIVE_DISCONNECTED,
    UNKNOWN_DRIVE_CONNECTED
}

// ─── State data classes ───

data class PendingDeleteRequest(
    val intentSender: IntentSender,
    val items: List<MediaItem>,
    val hashes: List<String>,
    val otgUris: List<String>,
    val thumbnailPaths: List<String?>
)

data class ArchiveState(
    val isArchiving: Boolean = false,
    val currentFileName: String = "",
    val currentStep: String = "",
    val progressFraction: Float = 0f,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val pendingQueueSize: Int = 0,
    val error: String? = null,
    val skippedFiles: List<Pair<String, String>> = emptyList() // (displayName, reason)
)

data class RestoreState(
    val isRestoring: Boolean = false,
    val currentFileName: String = "",
    val currentStep: String = "",
    val progressFraction: Float = 0f,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val successCount: Int = 0,
    val error: String? = null,
    val conflict: RestoreConflict? = null
)

data class RestoreConflict(
    val itemDisplayName: String,
    val defaultFallbackPath: String
)

data class RestoreConflictDecision(
    val uri: android.net.Uri?,
    val applyToAll: Boolean
)

data class SyncProgressState(
    val isSyncing: Boolean = false,
    val currentFileName: String = "",
    val progressFraction: Float = 0f,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0
)

data class ArchivedInfo(
    val item: MediaItem,
    val hash: String,
    val otgUri: String,
    val thumbnailPath: String?
)

sealed class RestoreRequest {
    object NeedFolderPicker : RestoreRequest()
    data class AskOriginalOrCustom(val items: List<MediaItem>, val originalPath: String) : RestoreRequest()
}
