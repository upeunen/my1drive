package by.w6.my1drive.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import by.w6.my1drive.domain.model.MediaItem
import by.w6.my1drive.domain.repository.MediaRepository
import by.w6.my1drive.utils.ArchiveMetadataStore
import by.w6.my1drive.utils.DebugLogBuffer
import by.w6.my1drive.utils.OtgArchiveUtil
import by.w6.my1drive.utils.RestoreResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ArchiveInteractor(
    private val application: Application,
    private val repository: MediaRepository,
    private val otgManager: OtgConnectionManager,
    private val metadataStore: ArchiveMetadataStore,
    private val archiveUtil: OtgArchiveUtil,
    private val scope: CoroutineScope,
    private val restoreState: MutableStateFlow<RestoreState>,
    private val restoringItemIds: MutableStateFlow<Set<String>>,
    private val selectedIds: MutableStateFlow<Set<String>>
) {
    private var restoringJob: Job? = null
    var isRestoreCancellationRequested = false

    private var conflictDeferred: kotlinx.coroutines.CompletableDeferred<RestoreConflictDecision>? = null

    fun resolveRestoreConflict(decision: RestoreConflictDecision) {
        conflictDeferred?.complete(decision)
    }

    fun startRestoring(items: List<MediaItem>, targetDirUri: Uri?) {
        restoringItemIds.value = items.map { it.id }.toSet()
        isRestoreCancellationRequested = false
        restoringJob = scope.launch {
            val logTag = "RestoreManager"
            try {
                DebugLogBuffer.log(logTag, "Start startRestoring for ${items.size} items, targetDirUri=$targetDirUri")
                var successCount = 0; val errors = mutableListOf<String>()
                val otgUri = otgManager.otgDirectoryUri.value
                restoreState.value = RestoreState(isRestoring = true, totalFiles = items.size)
                var globalApplyToAll = false
                var globalFallbackUri: Uri? = null

                for ((index, item) in items.withIndex()) {
                    if (isRestoreCancellationRequested) {
                        DebugLogBuffer.log(logTag, "Restore cancelled by user request. Stopping.")
                        break
                    }
                    DebugLogBuffer.log(logTag, "Restoring item [${index + 1}/${items.size}]: ${item.displayName}")
                    
                    var currentTargetDirUri = if (globalApplyToAll) globalFallbackUri else targetDirUri
                    var retry = true
                    
                    while (retry) {
                        retry = false
                        restoreState.value = restoreState.value.copy(currentFileName = item.displayName, currentFileIndex = index + 1, currentStep = "", conflict = null)
                        archiveUtil.restoreItem(item, currentTargetDirUri).collect { result ->
                        when (result) {
                            is RestoreResult.Progress -> restoreState.value = restoreState.value.copy(
                                currentStep = result.step, progressFraction = (index.toFloat() + result.progressFraction) / items.size
                            )
                            is RestoreResult.Success -> {
                                successCount++
                                selectedIds.value = selectedIds.value - result.item.id
                                DebugLogBuffer.log(logTag, "Item restored successfully: ${result.item.displayName}. Starting cleanup on OTG...")
                                try {
                                    // 1. Remove from JSON metadata on OTG drive (source of truth)
                                    if (otgUri != null && result.item.hash != null) {
                                        metadataStore.removeEntry(otgUri, result.item.hash)
                                        DebugLogBuffer.log(logTag, "Removed metadata entry from JSON for ${result.item.displayName}")
                                    }
                                    // 2. Delete physical file from OTG drive
                                    result.item.otgUri?.let { fileUri ->
                                        try {
                                            val otgFile = DocumentFile.fromSingleUri(application, Uri.parse(fileUri))
                                            val deleted = otgFile?.delete() ?: false
                                            DebugLogBuffer.log(logTag, "Deleted physical file from OTG: ${result.item.displayName}, success=$deleted")
                                        } catch (ex: Exception) {
                                            DebugLogBuffer.log(logTag, "Failed to delete physical file on OTG for ${result.item.displayName}: ${ex.localizedMessage}")
                                        }
                                    }
                                    // 3. Remove from Room (local cache)
                                    repository.deleteArchivedItem(result.item)
                                    DebugLogBuffer.log(logTag, "Deleted item from local Room DB: ${result.item.displayName}")
                                } catch (e: Exception) {
                                    DebugLogBuffer.log(logTag, "Error in OTG cleanup after restore for ${result.item.displayName}: ${e.localizedMessage}")
                                }
                            }
                             is RestoreResult.Error -> {
                                 val msg = result.message.lowercase()
                                 if (msg.contains("restore_mediastore_insert_failed") ||
                                     msg.contains("restore_target_access_failed") ||
                                     msg.contains("restore_create_failed") ||
                                     msg.contains("securityexception") ||
                                     msg.contains("access") ||
                                     msg.contains("permission")
                                 ) {
                                     if (globalApplyToAll) {
                                         // Already applied to all and failed again? Just record error and skip.
                                         val errStr = "${result.displayName}: Destination folder not accessible."
                                         errors.add(errStr)
                                         DebugLogBuffer.log(logTag, "Item restoration failed despite applyToAll: $errStr")
                                     } else {
                                         // Pause and ask user
                                         val fallbackPath = if (item.mimeType.startsWith("video/")) "Movies/" else "Pictures/"
                                         restoreState.value = restoreState.value.copy(
                                             conflict = RestoreConflict(item.displayName, fallbackPath)
                                         )
                                         conflictDeferred = kotlinx.coroutines.CompletableDeferred()
                                         val decision = conflictDeferred!!.await()
                                         conflictDeferred = null
                                         restoreState.value = restoreState.value.copy(conflict = null)
                                         
                                         if (decision.applyToAll) {
                                             globalApplyToAll = true
                                             globalFallbackUri = decision.uri
                                         }
                                         if (decision.uri != null) {
                                             currentTargetDirUri = decision.uri
                                             retry = true
                                         } else {
                                             // User chose to skip or MediaStore fallback via null URI
                                             currentTargetDirUri = null
                                             retry = true
                                         }
                                     }
                                 } else {
                                     val errStr = "${result.displayName}: ${result.message}"
                                     errors.add(errStr)
                                     DebugLogBuffer.log(logTag, "Item restoration failed: $errStr")
                                 }
                             }
                        } // end when
                    } // end collect
                    } // end while (retry)
                    restoringItemIds.value = restoringItemIds.value - item.id
                } // end for
                repository.refresh()
                val finalError = when {
                    errors.isNotEmpty() -> "Восстановлено: $successCount из ${items.size}.\n\nОшибки:\n" + errors.joinToString("\n")
                    successCount < items.size -> "Восстановлено: $successCount из ${items.size}."
                    else -> null
                }
                DebugLogBuffer.log(logTag, "Restoration complete. Succeeded: $successCount, Failed: ${errors.size}. Final error: $finalError")
                restoreState.value = RestoreState(isRestoring = false, successCount = successCount, error = finalError)
                otgManager.updateArchiveSize()
            } finally {
                restoringItemIds.value = emptySet()
                if (restoreState.value.isRestoring) {
                    restoreState.value = restoreState.value.copy(isRestoring = false)
                }
                isRestoreCancellationRequested = false
                restoringJob = null
            }
        }
    }

    fun deleteArchivedItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        scope.launch {
            val otgUri = otgManager.otgDirectoryUri.value
            for (item in items) {
                try {
                    // 1. Remove from JSON metadata on OTG drive (source of truth)
                    if (otgUri != null && item.hash != null) {
                        metadataStore.removeEntry(otgUri, item.hash)
                    }
                    // 2. Delete physical file from OTG drive
                    item.otgUri?.let { fileUri ->
                        try { DocumentFile.fromSingleUri(application, Uri.parse(fileUri))?.delete() } catch (_: Exception) { }
                    }
                    // 3. Remove from Room (local cache)
                    repository.deleteArchivedItem(item)
                } catch (_: Exception) { }
            }
            repository.refresh()
            otgManager.updateArchiveSize()
        }
    }
}
