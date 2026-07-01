package com.kamrenzirger.synctoandroiddata.data
import androidx.room.Embedded
import androidx.room.Relation
data class SyncEntryWithPairs(
    @Embedded val entry: SyncEntry,
    @Relation(
        parentColumn = "id",
        entityColumn = "syncEntryId"
    )
    val pairs: List<DirectoryPair>
)
