package com.dunda.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var songDao: SongDao
    private lateinit var playEventDao: PlayEventDao

    private fun song(id: Long, title: String = "song$id") = SongEntity(
        id = id, title = title, artist = "artist", album = "album",
        duration = 180_000, uri = "content://media/$id", albumArtUri = null,
        dateAdded = id,
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = db.songDao()
        playEventDao = db.playEventDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    // ---- library cache sync ----

    @Test
    fun `sync preserves favourites across rescans`() = runTest {
        songDao.sync(listOf(song(1), song(2)))
        songDao.setFavourite(1, true)

        songDao.sync(listOf(song(1), song(2), song(3)))

        val all = songDao.getAllPresentOnce().associateBy { it.id }
        assertTrue("favourite must survive rescan", all[1]!!.isFavourite)
        assertFalse(all[2]!!.isFavourite)
        assertEquals(3, all.size)
    }

    @Test
    fun `songs missing from a scan are marked absent, not deleted`() = runTest {
        songDao.sync(listOf(song(1), song(2)))
        songDao.setFavourite(2, true)

        songDao.sync(listOf(song(1))) // song 2 disappeared (e.g. SD card removed)

        assertEquals(listOf(1L), songDao.getAllPresentOnce().map { it.id })
        val gone = songDao.getById(2)!!
        assertFalse(gone.isPresent)
        assertTrue("user data survives disappearance", gone.isFavourite)

        songDao.sync(listOf(song(1), song(2))) // song 2 came back
        val restored = songDao.getById(2)!!
        assertTrue(restored.isPresent)
        assertTrue("favourite restored with the song", restored.isFavourite)
    }

    // ---- play statistics ----

    @Test
    fun `statsInRange includes never-played songs with count zero`() = runTest {
        songDao.sync(listOf(song(1), song(2)))
        playEventDao.insert(PlayEvent(songId = 1, playedAt = 500))

        val stats = playEventDao.statsInRange(0, Long.MAX_VALUE).first()
            .associateBy { it.song.id }
        assertEquals(2, stats.size)
        assertEquals(1, stats[1]!!.playCount)
        assertEquals("least-played honesty: zero-play songs appear", 0, stats[2]!!.playCount)
        assertEquals(null, stats[2]!!.lastPlayedAt)
    }

    @Test
    fun `statsInRange filters by period`() = runTest {
        songDao.sync(listOf(song(1)))
        playEventDao.insert(PlayEvent(songId = 1, playedAt = 100))   // outside
        playEventDao.insert(PlayEvent(songId = 1, playedAt = 1_000)) // inside
        playEventDao.insert(PlayEvent(songId = 1, playedAt = 2_000)) // inside
        playEventDao.insert(PlayEvent(songId = 1, playedAt = 9_000)) // outside

        val stats = playEventDao.statsInRange(1_000, 5_000).first()
        assertEquals(2, stats.single().playCount)
        assertEquals(2_000L, stats.single().lastPlayedAt)
    }

    @Test
    fun `absent songs are excluded from stats`() = runTest {
        songDao.sync(listOf(song(1), song(2)))
        playEventDao.insert(PlayEvent(songId = 2, playedAt = 500))
        songDao.sync(listOf(song(1))) // song 2 gone

        val stats = playEventDao.statsInRange(0, Long.MAX_VALUE).first()
        assertEquals(listOf(1L), stats.map { it.song.id })
    }

    @Test
    fun `playCountsInRange aggregates per song`() = runTest {
        songDao.sync(listOf(song(1), song(2)))
        repeat(3) { playEventDao.insert(PlayEvent(songId = 1, playedAt = 1_000L + it)) }
        playEventDao.insert(PlayEvent(songId = 2, playedAt = 1_000))

        val counts = playEventDao.playCountsInRange(0, Long.MAX_VALUE).first()
            .associate { it.songId to it.playCount }
        assertEquals(3, counts[1])
        assertEquals(1, counts[2])
    }

    // ---- queue state ----

    @Test
    fun `queue state round-trips`() = runTest {
        val dao = db.queueStateDao()
        dao.save(
            com.dunda.app.data.model.QueueState(
                queueIds = "3,1,2", shuffleOrder = "1,0,2", shuffleCursor = 1,
                currentIndex = 0, positionMs = 4_200, shuffleEnabled = true,
                repeatMode = "ONCE", soloMode = true,
            )
        )
        val loaded = dao.get()!!
        assertEquals("3,1,2", loaded.queueIds)
        assertEquals(1, loaded.shuffleCursor)
        assertTrue(loaded.shuffleEnabled)
        assertEquals("ONCE", loaded.repeatMode)
        assertTrue(loaded.soloMode)
    }
}
