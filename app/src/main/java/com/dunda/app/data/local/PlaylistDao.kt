package com.dunda.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.PlaylistSong
import kotlinx.coroutines.flow.Flow

data class PlaylistSongCount(val playlistId: Long, val songCount: Int)

@Dao
interface PlaylistDao {

    @Query("SELECT playlistId, COUNT(songId) AS songCount FROM playlist_songs GROUP BY playlistId")
    fun getPlaylistSongCounts(): Flow<List<PlaylistSongCount>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    /** Delete a playlist AND its song rows — a bare @Delete leaves orphans. */
    @Transaction
    suspend fun deletePlaylistWithSongs(playlist: Playlist) {
        deletePlaylistSongs(playlist.id)
        deletePlaylist(playlist)
    }

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Query("UPDATE playlists SET sortMode = :mode WHERE id = :playlistId")
    suspend fun setSortMode(playlistId: Long, mode: String)

    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val position = getNextPosition(playlistId)
        insertPlaylistSong(PlaylistSong(playlistId, songId, position))
    }

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        var position = getNextPosition(playlistId)
        for (songId in songIds) {
            insertPlaylistSong(PlaylistSong(playlistId, songId, position++))
        }
    }
}
