package com.dunda.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.PlaylistSong
import com.dunda.app.data.model.QueueState
import com.dunda.app.data.model.SongEntity

@Database(
    entities = [
        Playlist::class,
        PlaylistSong::class,
        SongEntity::class,
        PlayEvent::class,
        QueueState::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun songDao(): SongDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun queueStateDao(): QueueStateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playlists ADD COLUMN sortMode TEXT NOT NULL DEFAULT 'CUSTOM'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS songs (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        albumArtUri TEXT,
                        dateAdded INTEGER NOT NULL,
                        bpm INTEGER,
                        isFavourite INTEGER NOT NULL,
                        isPresent INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS play_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        songId INTEGER NOT NULL,
                        playedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_songId ON play_events (songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_playedAt ON play_events (playedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS queue_state (
                        id INTEGER NOT NULL PRIMARY KEY,
                        queueIds TEXT NOT NULL,
                        shuffleOrder TEXT NOT NULL,
                        shuffleCursor INTEGER NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        positionMs INTEGER NOT NULL,
                        shuffleEnabled INTEGER NOT NULL,
                        repeatMode TEXT NOT NULL,
                        soloMode INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dunda_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
