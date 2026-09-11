package by.w6.my1drive.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_archive ORDER BY dateModified DESC")
    fun getAllFlow(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_archive ORDER BY dateModified DESC LIMIT :limit OFFSET :offset")
    fun getChunkSync(limit: Int, offset: Int): List<MediaEntity>

    @Query("SELECT * FROM media_archive WHERE archiveUuid = :archiveUuid ORDER BY dateModified DESC LIMIT :limit OFFSET :offset")
    fun getChunkByArchiveUuidSync(archiveUuid: String, limit: Int, offset: Int): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media_archive")
    fun getCountFlow(): Flow<Int>

    /**
     * Safely reads all archived entities in small chunks (800 rows per query)
     * to avoid Android SQLite 2MB CursorWindow overflow on large libraries (1400+ items).
     */
    fun getAllInChunksSync(chunkSize: Int = 800): List<MediaEntity> {
        val result = ArrayList<MediaEntity>()
        var offset = 0
        while (true) {
            val chunk = getChunkSync(limit = chunkSize, offset = offset)
            result.addAll(chunk)
            if (chunk.size < chunkSize) break
            offset += chunkSize
        }
        return result
    }

    /**
     * Safely reads archived entities for a specific archive in chunks.
     */
    fun getByArchiveUuidInChunksSync(archiveUuid: String, chunkSize: Int = 800): List<MediaEntity> {
        val result = ArrayList<MediaEntity>()
        var offset = 0
        while (true) {
            val chunk = getChunkByArchiveUuidSync(archiveUuid = archiveUuid, limit = chunkSize, offset = offset)
            result.addAll(chunk)
            if (chunk.size < chunkSize) break
            offset += chunkSize
        }
        return result
    }

    @Query("UPDATE media_archive SET archiveUuid = :newUuid WHERE archiveUuid = '' OR archiveUuid IS NULL")
    fun migrateLegacyArchiveUuid(newUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<MediaEntity>)

    @Delete
    fun delete(entity: MediaEntity)

    @Query("SELECT * FROM media_archive WHERE id = :id LIMIT 1")
    fun getById(id: String): MediaEntity?

    @Query("DELETE FROM media_archive")
    fun deleteAll()

    @Query("DELETE FROM media_archive WHERE archiveUuid = :archiveUuid")
    fun deleteByArchiveUuid(archiveUuid: String)

    @Query("SELECT * FROM media_archive ORDER BY dateModified DESC")
    fun getAllSync(): List<MediaEntity>

    @Query("SELECT * FROM media_archive WHERE archiveUuid = :archiveUuid")
    fun getByArchiveUuidSync(archiveUuid: String): List<MediaEntity>

    /** Update LRU timestamp when a preview is loaded for this item */
    @Query("UPDATE media_archive SET lastAccessed = :timestamp WHERE id = :id")
    fun updateLastAccessed(id: String, timestamp: Long)

    /** Update thumbnailPath and LRU timestamp for an archived item */
    @Query("UPDATE media_archive SET thumbnailPath = :thumbnailPath, lastAccessed = :timestamp WHERE id = :id")
    fun updateThumbnailPath(id: String, thumbnailPath: String, timestamp: Long)

    /** Get items sorted by lastAccessed ASC (oldest first) — used for LRU eviction */
    @Query("SELECT * FROM media_archive WHERE thumbnailPath IS NOT NULL ORDER BY lastAccessed ASC LIMIT :limit")
    fun getOldestByLastAccessed(limit: Int): List<MediaEntity>

    /** Clear thumbnailPath for items whose preview cache was evicted */
    @Query("UPDATE media_archive SET thumbnailPath = NULL WHERE id = :id")
    fun clearThumbnailPath(id: String)

    /** Count items with cached previews */
    @Query("SELECT COUNT(*) FROM media_archive WHERE thumbnailPath IS NOT NULL")
    fun getCachedPreviewCount(): Int

    /** Total archive size in bytes (sum of all archived file sizes) — для расчёта лимита 128 МБ */
    @Query("SELECT COALESCE(SUM(size), 0) FROM media_archive")
    fun getTotalArchiveSize(): Long

    @Query("SELECT COUNT(*) FROM media_archive")
    fun getCount(): Int

    /** Items without a cached preview, ordered by most recent first — for background generation */
    @Query("SELECT * FROM media_archive WHERE archiveUuid = :archiveUuid AND (thumbnailPath IS NULL OR thumbnailPath = '') AND otgUri != '' AND otgUri IS NOT NULL ORDER BY dateModified DESC LIMIT :limit")
    fun getWithoutPreview(archiveUuid: String, limit: Int): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media_archive WHERE archiveUuid = :archiveUuid AND (thumbnailPath IS NULL OR thumbnailPath = '') AND otgUri != '' AND otgUri IS NOT NULL")
    fun getWithoutPreviewCount(archiveUuid: String): Int
}
