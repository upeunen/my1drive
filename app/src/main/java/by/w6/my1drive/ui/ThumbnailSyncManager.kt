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
    private val connectedArchiveUuidsFlow: StateFlow<Set<String>> = MutableStateFlow(emptySet()),
    private val isOtgConnectedFlow: StateFlow<Boolean>,
    private val isScrollingFlow: StateFlow<Boolean>,
    private val isPreviewActiveFlow: StateFlow<Boolean> = MutableStateFlow(false),
    private val refreshCacheStats: () -> Unit,
    private val onRepositoryRefresh: () -> Unit = {}
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
                }
            }
        }

        // Реакция на смену активного архива и подключение OTG
        scope.launch {
            kotlinx.coroutines.flow.combine(isOtgConnectedFlow, activeArchiveUuidFlow, connectedArchiveUuidsFlow) { isConnected, activeUuid, connectedUuids ->
                Triple(isConnected, activeUuid, connectedUuids)
            }.collect { (isConnected, activeUuid, connectedUuids) ->
                if (isConnected && (!activeUuid.isNullOrEmpty() || connectedUuids.isNotEmpty()) && !syncHelper.archiveState.value.isArchiving) {
                    updateMissingThumbnailsCount()
                    // Перезапускаем фоновый синк с приоритетом на новый активный архив
                    cancelSilentThumbnailSync()
                    startSilentThumbnailSync(activeUuid)
                } else if (!isConnected) {
                    cancelSilentThumbnailSync()
                    cancelThumbnailSync()
                    _missingThumbnailsCount.value = 0
                }
            }
        }
    }

    fun updateMissingThumbnailsCount() {
        val connectedUuids = connectedArchiveUuidsFlow.value
        val activeUuid = activeArchiveUuidFlow.value
        val targetUuids = if (connectedUuids.isNotEmpty()) connectedUuids else setOfNotNull(activeUuid)
        if (targetUuids.isEmpty()) {
            _missingThumbnailsCount.value = 0
            return
        }
        scope.launch(Dispatchers.IO) {
            var totalCount = 0
            for (uuid in targetUuids) {
                totalCount += db.mediaDao().getWithoutPreviewCount(uuid)
            }
            _missingThumbnailsCount.value = totalCount
        }
    }

    fun startThumbnailSync() {
        if (syncHelper.archiveState.value.isArchiving) {
            DebugLogBuffer.log("ThumbnailSyncManager", "startThumbnailSync skipped: archiving is currently active")
            return
        }
        val targetUuids = mutableListOf<String>()
        val activeUuid = activeArchiveUuidFlow.value
        if (!activeUuid.isNullOrEmpty()) targetUuids.add(activeUuid)
        for (u in connectedArchiveUuidsFlow.value) {
            if (u !in targetUuids) targetUuids.add(u)
        }
        if (targetUuids.isEmpty()) return

        _isSyncingThumbnails.value = true
        _syncThumbnailsProgress.value = Pair(0, 0)
        thumbnailSyncJob = scope.launch {
            val job = coroutineContext[Job]
            try {
                for (uuid in targetUuids) {
                    if (job?.isActive == false || !isOtgConnectedFlow.value || syncHelper.archiveState.value.isArchiving) break
                    syncHelper.syncAllThumbnails(
                        activeUuid = uuid,
                        isCancelled = { job?.isActive == false || !isOtgConnectedFlow.value || syncHelper.archiveState.value.isArchiving },
                        isPaused = { isScrollingFlow.value || isPreviewActiveFlow.value },
                        throttleMs = 40L,
                        batchSize = 25,
                        onProgress = { current, total ->
                            _syncThumbnailsProgress.value = Pair(current, total)
                        }
                    )
                }
            } finally {
                _isSyncingThumbnails.value = false
                updateMissingThumbnailsCount()
                refreshCacheStats()
                onRepositoryRefresh()
            }
        }
    }

    fun cancelThumbnailSync() {
        thumbnailSyncJob?.cancel()
        _isSyncingThumbnails.value = false
    }

    fun startSilentThumbnailSync(preferredUuid: String? = null) {
        if (syncHelper.archiveState.value.isArchiving) {
            DebugLogBuffer.log("ThumbnailSyncManager", "startSilentThumbnailSync skipped: archiving is currently active")
            return
        }
        if (silentThumbnailSyncJob?.isActive == true) {
            return
        }
        val targetUuids = mutableListOf<String>()
        val primary = preferredUuid ?: activeArchiveUuidFlow.value
        if (!primary.isNullOrEmpty()) targetUuids.add(primary)
        for (u in connectedArchiveUuidsFlow.value) {
            if (u !in targetUuids) targetUuids.add(u)
        }
        if (targetUuids.isEmpty()) return

        silentThumbnailSyncJob = scope.launch {
            val job = coroutineContext[Job]
            try {
                for (uuid in targetUuids) {
                    if (job?.isActive == false || !isOtgConnectedFlow.value || syncHelper.archiveState.value.isArchiving) break
                    syncHelper.syncAllThumbnails(
                        activeUuid = uuid,
                        isCancelled = { job?.isActive == false || !isOtgConnectedFlow.value || syncHelper.archiveState.value.isArchiving },
                        isPaused = { isScrollingFlow.value || isPreviewActiveFlow.value },
                        throttleMs = 150L,
                        batchSize = 25,
                        onProgress = { _, _ -> }
                    )
                }
            } catch (e: Exception) {
                DebugLogBuffer.log("ThumbnailSyncManager", "Silent thumbnail sync error: ${e.message}")
            } finally {
                updateMissingThumbnailsCount()
                refreshCacheStats()
                onRepositoryRefresh()
            }
        }
    }

    fun cancelSilentThumbnailSync() {
        silentThumbnailSyncJob?.cancel()
    }

    suspend fun stopAllThumbnailSync() {
        DebugLogBuffer.log("ThumbnailSyncManager", "stopAllThumbnailSync: stopping all thumbnail sync jobs...")
        val manualJob = thumbnailSyncJob
        val silentJob = silentThumbnailSyncJob
        manualJob?.cancel()
        silentJob?.cancel()

        kotlinx.coroutines.withTimeoutOrNull(2000L) {
            manualJob?.join()
            silentJob?.join()
        }

        thumbnailSyncJob = null
        silentThumbnailSyncJob = null
        _isSyncingThumbnails.value = false
        _syncThumbnailsProgress.value = Pair(0, 0)
        DebugLogBuffer.log("ThumbnailSyncManager", "stopAllThumbnailSync: all thumbnail sync stopped")
    }

    fun resetProgress() {
        _syncThumbnailsProgress.value = Pair(0, 0)
    }
}
