package com.dunda.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.SongEntity
import kotlinx.coroutines.flow.Flow

data class SongPlayStats(
    @Embedded val song: SongEntity,
    val playCount: Int,
    val lastPlayedAt: Long?,
)

data class PlayCountRow(
    val songId: Long,
    val playCount: Int,
)

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent)

    /**
     * Play statistics for every present song within [from, to].
     * LEFT JOIN so never-played songs appear with playCount 0 — this is what
     * makes "least played" honest. Sort ascending/descending in the caller
     * (Room can't parameterize ORDER BY direction).
     */
    @Query(
        """
        SELECT s.*, COUNT(p.id) AS playCount, MAX(p.playedAt) AS lastPlayedAt
        FROM songs s
        LEFT JOIN play_events p
            ON p.songId = s.id AND p.playedAt BETWEEN :from AND :to
        WHERE s.isPresent = 1
        GROUP BY s.id
        """
    )
    fun statsInRange(from: Long, to: Long): Flow<List<SongPlayStats>>

    /** All-time play count per song that has at least one play. */
    @Query(
        """
        SELECT songId, COUNT(id) AS playCount
        FROM play_events
        WHERE playedAt BETWEEN :from AND :to
        GROUP BY songId
        """
    )
    fun playCountsInRange(from: Long, to: Long): Flow<List<PlayCountRow>>

    @Query("SELECT COUNT(id) FROM play_events WHERE songId = :songId")
    suspend fun totalPlays(songId: Long): Int
}
