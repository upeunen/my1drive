package by.w6.my1drive.ui

import by.w6.my1drive.data.local.AppDatabase
import by.w6.my1drive.utils.DebugLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThumbnailSyncManager(
    private val db: AppDatabase,
    private val syncHelper: ArchiveSyncHelper,
    private val scope: CoroutineScope,
    private val activeArchiveUuidFlow: StateFlow<String?>,
    private val isOtgConnectedFlow: StateFlow<Boolean>,
    private val isScrollingFlow: StateFlow<Boolean>,
    private val refreshCacheStats: () -> Unit
) {
    private var thumbnailSyncJob: Job? = null
    private var silentThumbnailSyncJob: Job? = null

    private val _isSyncingThumbnails = MutableStateFlow(false)
    val isSyncingThumbnails: StateFlow<Boolean> = _isSyncingThumbnails.asStateFlow()

    private val _syncThumbnailsProgress = MutableStateFlow(Pair(0, 0))
    val syncThumbnailsProgress: StateFlow<Pair<Int, Int>> = _syncThumbnailsProgress.asStateFlow()

    private val _missingThumbnailsCount = MutableStateFlow(0)
    val missingThumbnailsCount: StateFlow<Int> = _missingThumbnailsCount.asStateFlow()

    init {
        scope.launch {
            syncHelper.archiveState.collect { state ->
                if (state.isArchiving) {
                    if (_isSyncingThumbnails.value || silentThumbnailSyncJob?.isActive == true) {
                        DebugLogBuffer.log("ThumbnailSyncManager", "Cancelling thumbnail sync due to active archiving")
                        cancelThumbnailSync()
                        cancelSilentThumbnailSync()
                    }
                } else {
                    updateMissingThumbnailsCount()
                    if (missingThumbnailsCount.value > 0 && isOtgConnectedFlow.value) {
                        DebugLogBuffer.log("ThumbnailSyncManager", "Archiving complete: starting silent thumbnail sync for ${missingThumbnailsCount.value} missing items")
                        startSilentThumbnailSync()
                    }
                }
            }
        }
    }

    fun updateMissingThumbnailsCount() {
        val activeUuid = activeArchiveUuidFlow.value ?: run {
            _missingThumbnailsCount.value = 0
            return
        }
        scope.launch(Dispatchers.IO) {
            val count = db.mediaDao().getWithoutPreviewCount(activeUuid)
            _missingThumbnailsCount.value = count
        }
    }

    fun startThumbnailSync() {
        if (syncHelper.archiveState.value.isArchiving) {
            DebugLogBuffer.log("ThumbnailSyncManager", "startThumbnailSync skipped: archiving is currently active")
            return
        }
        val activeUuid = activeArchiveUuidFlow.value ?: return
        _isSyncingThumbnails.value = true
        _syncThumbnailsProgress.value = Pair(0, 0)
        thumbnailSyncJob = scope.launch {
            val job = coroutineContext[Job]
            try {
                syncHelper.syncAllThumbnails(
                    activeUuid = activeUuid,
                    isCancelled = { job?.isActive == false || syncHelper.archiveState.value.isArchiving },
                    onProgress = { current, total ->
                        _syncThumbnailsProgress.value = Pair(current, total)
                    }
                )
            } finally {
                _isSyncingThumbnails.value = false
                updateMissingThumbnailsCount()
                refreshCacheStats()
            }
        }
    }

    fun cancelThumbnailSync() {
        thumbnailSyncJob?.cancel()
        _isSyncingThumbnails.value = false
    }

    fun startSilentThumbnailSync() {
        if (syncHelper.archiveState.value.isArchiving) {
            DebugLogBuffer.log("ThumbnailSyncManager", "startSilentThumbnailSync skipped: archiving is currently active")
            return
        }
        val activeUuid = activeArchiveUuidFlow.value ?: return
        silentThumbnailSyncJob?.cancel()
        silentThumbnailSyncJob = scope.launch {
            val job = coroutineContext[Job]
            try {
                syncHelper.syncAllThumbnails(
                    activeUuid = activeUuid,
                    isCancelled = { job?.isActive == false || !isOtgConnectedFlow.value || isScrollingFlow.value || syncHelper.archiveState.value.isArchiving },
                    onProgress = { _, _ -> }
                )
            } catch (e: Exception) {
                DebugLogBuffer.log("ThumbnailSyncManager", "Silent thumbnail sync error: ${e.message}")
            } finally {
                updateMissingThumbnailsCount()
                refreshCacheStats()
            }
        }
    }

    fun cancelSilentThumbnailSync() {
        silentThumbnailSyncJob?.cancel()
    }

    fun resetProgress() {
        _syncThumbnailsProgress.value = Pair(0, 0)
    }
}
