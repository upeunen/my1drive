package by.w6.my1drive.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(archive: ArchiveEntity)

    @Query("SELECT * FROM archives ORDER BY lastConnected DESC")
    fun getAllFlow(): Flow<List<ArchiveEntity>>

    @Query("SELECT * FROM archives WHERE uuid = :uuid")
    fun getById(uuid: String): ArchiveEntity?

    @Query("UPDATE archives SET name = :name WHERE uuid = :uuid")
    fun updateName(uuid: String, name: String)

    @Query("DELETE FROM archives WHERE uuid = :uuid")
    fun delete(uuid: String)

    @Query("DELETE FROM archives")
    fun deleteAll()
}
