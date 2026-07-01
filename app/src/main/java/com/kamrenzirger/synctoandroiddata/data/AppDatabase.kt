package com.kamrenzirger.synctoandroiddata.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities = [SyncEntry::class, DirectoryPair::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncEntryDao(): SyncEntryDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS directory_pairs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncEntryId INTEGER NOT NULL,
                        internalPath TEXT NOT NULL,
                        externalPath TEXT NOT NULL,
                        FOREIGN KEY(syncEntryId) REFERENCES sync_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    INSERT INTO directory_pairs (syncEntryId, internalPath, externalPath)
                    SELECT id, internalPath, externalPath FROM sync_entries
                """)
                db.execSQL("""
                    CREATE TABLE sync_entries_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        appName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("""
                    INSERT INTO sync_entries_new (id, appName, packageName, isEnabled)
                    SELECT id, appName, packageName, isEnabled FROM sync_entries
                """)
                db.execSQL("DROP TABLE sync_entries")
                db.execSQL("ALTER TABLE sync_entries_new RENAME TO sync_entries")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_entries ADD COLUMN mirrorDeletions INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sync_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
