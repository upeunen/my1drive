package by.w6.my1drive.utils

import android.content.Context
import by.w6.my1drive.data.local.MediaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the local preview thumbnail cache stored in:
 *   filesDir/my1drive_previews/{hash}.webp
 *
 * - Default limit: 500 MB (user-configurable in Settings via SharedPreferences)
 * - Eviction policy: LRU (Least Recently Used) based on MediaEntity.lastAccessed
 * - Originals are NEVER stored locally — only compressed ~15 KB previews
 */
class PreviewCacheManager(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    companion object {
        const val PREVIEW_DIR = "my1drive_previews"
        const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024   // 500 MB
        private const val PREFS_NAME = "my1drive_prefs"
        private const val PREF_CACHE_LIMIT = "preview_cache_limit_bytes"
    }

    val previewDir: File
        get() {
            val dir = File(context.filesDir, PREVIEW_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
                // Create .nomedia file to hide previews from other apps' galleries
                try {
                    File(dir, ".nomedia").createNewFile()
                } catch (_: Exception) {}
            }
            return dir
        }

    fun getMaxBytes(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(PREF_CACHE_LIMIT, DEFAULT_MAX_BYTES)
    }

    fun setMaxBytes(bytes: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(PREF_CACHE_LIMIT, bytes).apply()
    }

    /** Returns current cache size in bytes */
    fun getCacheSize(): Long =
        previewDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Returns count of cached preview files */
    fun getCacheFileCount(): Int =
        previewDir.listFiles()?.size ?: 0

    /** Returns the cache File for a given hash - uses .my1d extension to hide from other apps */
    fun cacheFileFor(hash: String): File =
        File(previewDir, "$hash.my1d")

    /** The actual extension for cache files */
    fun cacheExtension(): String = ".my1d"

    /**
     * Evicts oldest previews (by MediaEntity.lastAccessed) until cache fits within [maxBytes].
     * Also cleans up orphaned files (in cache dir but not in DB).
     * Clears thumbnailPath in DB for evicted items.
     */
    suspend fun evictIfNeeded(maxBytes: Long = getMaxBytes()) = withContext(Dispatchers.IO) {
        var currentSize = getCacheSize()
        if (currentSize <= maxBytes) return@withContext

        // Get DB items with cached previews, oldest first
        val oldestItems = mediaDao.getOldestByLastAccessed(limit = 10_000)

        for (item in oldestItems) {
            if (currentSize <= maxBytes) break
            val path = item.thumbnailPath ?: continue
            val file = File(path)
            if (file.exists()) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                    mediaDao.clearThumbnailPath(item.id)
                }
            } else {
                // File already gone — just clear the DB reference
                mediaDao.clearThumbnailPath(item.id)
            }
        }

        // Also remove orphaned files not tracked in DB
        cleanOrphans()
    }

    /** Removes cache files that have no corresponding DB entry */
    private fun cleanOrphans() {
        val cacheFiles = previewDir.listFiles() ?: return
        val dbIds = mediaDao.getAllSync().map { it.id }.toSet()
        for (file in cacheFiles) {
            val hash = file.nameWithoutExtension
            if (hash !in dbIds) {
                file.delete()
            }
        }
    }

    /** Clears ALL cached previews and resets thumbnailPath in DB */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        previewDir.listFiles()?.forEach { it.delete() }
        // Recreate .nomedia
        try { File(previewDir, ".nomedia").createNewFile() } catch (_: Exception) {}
        // Clear paths in DB — iterate all and clear
        val allWithThumbs = mediaDao.getOldestByLastAccessed(limit = Int.MAX_VALUE)
        for (item in allWithThumbs) {
            mediaDao.clearThumbnailPath(item.id)
        }
    }

    /**
     * Migrates old thumbnails from filesDir/thumbnails/ (previous format) to
     * filesDir/my1drive_previews/{hash}.webp.
     * Called once during DB v2→v3 upgrade.
     */
    fun migrateOldThumbnails() {
        val oldDir = File(context.filesDir, "thumbnails")
        if (!oldDir.exists()) return
        val newDir = previewDir
        oldDir.listFiles()?.forEach { oldFile ->
            val newFile = File(newDir, oldFile.name)
            if (!newFile.exists()) {
                oldFile.renameTo(newFile)
            }
        }
        // Don't delete oldDir — some paths in old DB rows might still point there
        // They'll be resolved naturally by OtgThumbnailFetcher
    }
}
