package com.kamrenzirger.synctoandroiddata.data
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao
interface SyncEntryDao {
    @Transaction
    @Query("SELECT * FROM sync_entries")
    fun getAllSyncEntriesWithPairs(): Flow<List<SyncEntryWithPairs>>
    @Transaction
    @Query("SELECT * FROM sync_entries WHERE packageName = :packageName")
    fun getSyncEntriesWithPairsForPackage(packageName: String): List<SyncEntryWithPairs>
    @Transaction
    @Query("SELECT * FROM sync_entries WHERE id = :entryId")
    fun getSyncEntryWithPairsById(entryId: Long): Flow<SyncEntryWithPairs?>
    @Insert
    fun insertSyncEntry(entry: SyncEntry): Long
    @Update
    fun updateSyncEntry(entry: SyncEntry)
    @Delete
    fun deleteSyncEntry(entry: SyncEntry)
    @Insert
    fun insertDirectoryPair(pair: DirectoryPair)
    @Insert
    fun insertDirectoryPairs(pairs: List<DirectoryPair>)
    @Query("DELETE FROM directory_pairs WHERE syncEntryId = :syncEntryId")
    fun deletePairsForEntry(syncEntryId: Long)
    @Transaction
    fun insertSyncEntryWithPairs(entry: SyncEntry, pairs: List<DirectoryPair>) {
        val entryId = insertSyncEntry(entry)
        val pairsWithId = pairs.map { it.copy(syncEntryId = entryId) }
        insertDirectoryPairs(pairsWithId)
    }
    @Transaction
    fun updateSyncEntryWithPairs(entry: SyncEntry, pairs: List<DirectoryPair>) {
        updateSyncEntry(entry)
        deletePairsForEntry(entry.id)
        val pairsWithId = pairs.map { it.copy(syncEntryId = entry.id) }
        insertDirectoryPairs(pairsWithId)
    }
}
