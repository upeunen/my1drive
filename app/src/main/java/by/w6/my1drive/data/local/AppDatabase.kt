package by.w6.my1drive.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaEntity::class, ArchiveEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun archiveDao(): ArchiveDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2: added originalRelativePath column */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_archive ADD COLUMN originalRelativePath TEXT")
            }
        }

        /**
         * v2 → v3:
         * - Add archiveId, folderPath, lastAccessed to media_archive
         * - Create archive_info table
         * - Migrate existing thumbnailPath entries from filesDir/thumbnails/ to
         *   filesDir/my1drive_previews/ (rename handled at runtime by PreviewCacheManager)
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extend media_archive
                db.execSQL("ALTER TABLE media_archive ADD COLUMN archiveId TEXT")
                db.execSQL("ALTER TABLE media_archive ADD COLUMN folderPath TEXT")
                db.execSQL("ALTER TABLE media_archive ADD COLUMN lastAccessed INTEGER NOT NULL DEFAULT 0")

                // Create archive identity table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS archive_info (
                        id TEXT NOT NULL PRIMARY KEY,
                        otgUri TEXT NOT NULL,
                        label TEXT NOT NULL,
                        firstSeen INTEGER NOT NULL,
                        totalFiles INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my1drive.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
