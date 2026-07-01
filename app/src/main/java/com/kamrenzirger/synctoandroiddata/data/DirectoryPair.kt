package com.kamrenzirger.synctoandroiddata.data
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
@Entity(
    tableName = "directory_pairs",
    foreignKeys = [
        ForeignKey(
            entity = SyncEntry::class,
            parentColumns = ["id"],
            childColumns = ["syncEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DirectoryPair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncEntryId: Long,
    val internalPath: String,
    val externalPath: String
)
