package com.dunda.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dunda.app.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE isPresent = 1")
    fun getAllPresent(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isPresent = 1")
    suspend fun getAllPresentOnce(): List<SongEntity>

    @Query("SELECT * FROM songs")
    suspend fun getAllOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE isPresent = 1 AND isFavourite = 1")
    fun getFavourites(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET isFavourite = :favourite WHERE id = :songId")
    suspend fun setFavourite(songId: Long, favourite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Query("UPDATE songs SET isPresent = 0")
    suspend fun markAllAbsent()

    /**
     * Replace the library with the latest MediaStore scan while preserving
     * per-song user data (favourite flag, analyzed BPM) on existing rows.
     * Songs missing from the scan stay in the table with isPresent = 0.
     * Done as markAllAbsent + re-insert (present) to avoid IN-clause limits.
     */
    @Transaction
    suspend fun sync(scanned: List<SongEntity>) {
        val existing = getAllOnce().associateBy { it.id }
        val merged = scanned.map { s ->
            val old = existing[s.id]
            if (old != null) s.copy(isFavourite = old.isFavourite, bpm = old.bpm ?: s.bpm)
            else s
        }
        markAllAbsent()
        insertAll(merged)
    }
}
