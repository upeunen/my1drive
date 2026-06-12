package by.w6.my1drive.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(entity: ArchiveEntity)

    @Query("SELECT * FROM archive_info WHERE id = :id LIMIT 1")
    fun getById(id: String): ArchiveEntity?

    @Query("SELECT * FROM archive_info ORDER BY firstSeen ASC")
    fun getAllFlow(): Flow<List<ArchiveEntity>>

    @Query("SELECT * FROM archive_info ORDER BY firstSeen ASC")
    fun getAllSync(): List<ArchiveEntity>

    @Query("UPDATE archive_info SET totalFiles = :count WHERE id = :id")
    fun updateTotalFiles(id: String, count: Int)

    @Query("UPDATE archive_info SET label = :label WHERE id = :id")
    fun updateLabel(id: String, label: String)

    @Query("SELECT COUNT(*) FROM archive_info")
    fun getCount(): Int
}
