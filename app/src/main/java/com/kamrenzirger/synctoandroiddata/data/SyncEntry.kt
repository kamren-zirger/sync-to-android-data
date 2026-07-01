package com.kamrenzirger.synctoandroiddata.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "sync_entries")
data class SyncEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val isEnabled: Boolean = true,
    val mirrorDeletions: Boolean = false
)
