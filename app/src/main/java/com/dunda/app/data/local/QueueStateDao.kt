package com.dunda.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dunda.app.data.model.QueueState

@Dao
interface QueueStateDao {

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun get(): QueueState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: QueueState)

    @Query("DELETE FROM queue_state")
    suspend fun clear()
}
