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

    @Query("UPDATE media_archive SET archiveUuid = :newUuid WHERE archiveUuid = '' OR archiveUuid IS NULL")
    fun migrateLegacyArchiveUuid(newUuid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: MediaEntity)

    @Delete
    fun delete(entity: MediaEntity)

    @Query("SELECT * FROM media_archive WHERE id = :id LIMIT 1")
    fun getById(id: String): MediaEntity?

    @Query("DELETE FROM media_archive")
    fun deleteAll()

    @Query("SELECT * FROM media_archive ORDER BY dateModified DESC")
    fun getAllSync(): List<MediaEntity>

    /** Update LRU timestamp when a preview is loaded for this item */
    @Query("UPDATE media_archive SET lastAccessed = :timestamp WHERE id = :id")
    fun updateLastAccessed(id: String, timestamp: Long)

    /** Get items sorted by lastAccessed ASC (oldest first) вЂ” used for LRU eviction */
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
}



