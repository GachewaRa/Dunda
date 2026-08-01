package com.dunda.app.player

import com.dunda.app.player.QueueManager.NextAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueManagerTest {

    private fun manager(
        songs: List<String>,
        start: Int = 0,
        shuffle: Boolean = false,
        repeat: RepeatMode = RepeatMode.OFF,
        solo: Boolean = false,
        seed: Long = 42L,
    ): QueueManager<String> {
        val qm = QueueManager<String>(Random(seed))
        qm.setShuffle(shuffle)
        qm.setQueue(songs, start)
        qm.repeatMode = repeat
        qm.soloMode = solo
        return qm
    }

    private fun songs(n: Int) = (1..n).map { "song$it" }

    /** Drive natural completions, returning the sequence of songs that played. */
    private fun playThrough(qm: QueueManager<String>, completions: Int): List<String> {
        val played = mutableListOf<String>()
        repeat(completions) {
            when (val a = qm.advanceOnCompletion()) {
                is NextAction.Play -> played.add(a.item)
                NextAction.RepeatCurrent -> played.add(qm.current!!)
                NextAction.Stop -> return played
            }
        }
        return played
    }

    // ---- true shuffle: sampling without replacement ----

    @Test
    fun `shuffle plays every song exactly once per cycle`() {
        val all = songs(20)
        val qm = manager(all, shuffle = true, repeat = RepeatMode.ALL)
        val cycle = listOf(qm.current!!) + playThrough(qm, 19)
        assertEquals("cycle must cover the whole queue", all.toSet(), cycle.toSet())
        assertEquals("no song may repeat within a cycle", 20, cycle.distinct().size)
    }

    @Test
    fun `second shuffle cycle also covers everything with no internal repeats`() {
        val all = songs(10)
        val qm = manager(all, shuffle = true, repeat = RepeatMode.ALL)
        playThrough(qm, 9) // finish first cycle
        val second = playThrough(qm, 10) // wraps into second cycle
        assertEquals(all.toSet(), second.toSet())
        assertEquals(10, second.distinct().size)
    }

    @Test
    fun `no back-to-back repeat across cycle boundary`() {
        // Try several seeds: the last song of cycle 1 must never open cycle 2.
        for (seed in 1L..50L) {
            val qm = manager(songs(5), shuffle = true, repeat = RepeatMode.ALL, seed = seed)
            val firstCycle = listOf(qm.current!!) + playThrough(qm, 4)
            val lastOfCycle = firstCycle.last()
            val firstOfNext = (qm.advanceOnCompletion() as NextAction.Play).item
            assertNotEquals("seed $seed", lastOfCycle, firstOfNext)
        }
    }

    @Test
    fun `shuffle with repeat OFF stops after the cycle is exhausted`() {
        val qm = manager(songs(5), shuffle = true, repeat = RepeatMode.OFF)
        val played = listOf(qm.current!!) + playThrough(qm, 10)
        assertEquals(5, played.size) // stopped at exhaustion, no wrap
        assertEquals(NextAction.Stop, qm.advanceOnCompletion())
    }

    @Test
    fun `crossfade peek matches the committed transition across cycle wrap`() {
        val qm = manager(songs(6), shuffle = true, repeat = RepeatMode.ALL)
        playThrough(qm, 5) // now at last song of cycle
        val peeked = (qm.peekNext() as NextAction.Play).item
        val committed = (qm.advanceOnCompletion() as NextAction.Play).item
        assertEquals("preloaded song must be the one that plays", peeked, committed)
    }

    @Test
    fun `song added mid-cycle plays exactly once in the remainder`() {
        val qm = manager(songs(8), shuffle = true, repeat = RepeatMode.OFF)
        val playedBefore = mutableListOf(qm.current!!)
        repeat(3) { playedBefore.add((qm.advanceOnCompletion() as NextAction.Play).item) }

        qm.addToQueue("newSong")
        val playedAfter = playThrough(qm, 10)

        assertTrue("added song must play", playedAfter.contains("newSong"))
        assertEquals("added song must play exactly once", 1, playedAfter.count { it == "newSong" })
        assertEquals(
            "remainder must finish the cycle: all 9 songs played exactly once total",
            (songs(8) + "newSong").toSet(),
            (playedBefore + playedAfter).toSet()
        )
    }

    @Test
    fun `removing an unplayed song keeps the cycle consistent`() {
        val qm = manager(songs(6), shuffle = true, repeat = RepeatMode.OFF)
        val first = qm.current!!
        // Remove some song that is not current
        val removeIdx = qm.queue.indexOfFirst { it != first }
        val removed = qm.queue[removeIdx]
        assertTrue(qm.removeFromQueue(removeIdx))
        val played = listOf(first) + playThrough(qm, 10)
        assertEquals(5, played.size)
        assertTrue(!played.contains(removed) || played.count { it == removed } == 0)
        assertEquals(songs(6).toSet() - removed, played.toSet())
    }

    // ---- repeat modes ----

    @Test
    fun `repeat ONE repeats the same song indefinitely`() {
        val qm = manager(songs(3), repeat = RepeatMode.ONE)
        repeat(5) {
            assertEquals(NextAction.RepeatCurrent, qm.advanceOnCompletion())
            assertEquals("song1", qm.current)
        }
    }

    @Test
    fun `repeat ONCE plays the song one extra time then advances`() {
        val qm = manager(songs(3), repeat = RepeatMode.ONCE)
        assertEquals(NextAction.RepeatCurrent, qm.advanceOnCompletion())   // extra play
        val next = qm.advanceOnCompletion()
        assertTrue(next is NextAction.Play)
        assertEquals("song2", (next as NextAction.Play).item)
        // Flag must reset for the new song: it too gets one extra play.
        assertEquals(NextAction.RepeatCurrent, qm.advanceOnCompletion())
    }

    @Test
    fun `manual skip clears the ONCE flag`() {
        val qm = manager(songs(3), repeat = RepeatMode.ONCE)
        qm.advanceOnCompletion() // now hasRepeatedOnce = true for song1
        qm.manualNext()          // user skips to song2
        assertEquals("song2", qm.current)
        // song2 must still get its extra play (flag was cleared).
        assertEquals(NextAction.RepeatCurrent, qm.advanceOnCompletion())
    }

    @Test
    fun `repeat ALL wraps to the start in linear mode`() {
        val qm = manager(songs(3), start = 2, repeat = RepeatMode.ALL)
        val a = qm.advanceOnCompletion()
        assertEquals("song1", (a as NextAction.Play).item)
    }

    @Test
    fun `repeat OFF stops at the end of the queue`() {
        val qm = manager(songs(3), start = 2, repeat = RepeatMode.OFF)
        assertEquals(NextAction.Stop, qm.advanceOnCompletion())
        assertEquals("current song is unchanged after stop", "song3", qm.current)
    }

    // ---- solo mode ----

    @Test
    fun `solo mode never auto-advances regardless of repeat mode`() {
        for (mode in RepeatMode.entries) {
            val qm = manager(songs(3), repeat = mode, solo = true)
            assertEquals("repeat=$mode", NextAction.Stop, qm.peekNext())
            assertEquals("repeat=$mode", NextAction.Stop, qm.advanceOnCompletion())
            assertEquals("song1", qm.current)
        }
    }

    @Test
    fun `solo mode still allows manual navigation`() {
        val qm = manager(songs(3), solo = true)
        val a = qm.manualNext()
        assertEquals("song2", (a as NextAction.Play).item)
    }

    // ---- misc ----

    @Test
    fun `manual next wraps at the end even with repeat OFF`() {
        val qm = manager(songs(3), start = 2)
        assertEquals("song1", (qm.manualNext() as NextAction.Play).item)
    }

    @Test
    fun `manual previous at queue start returns Stop`() {
        val qm = manager(songs(3), start = 0)
        assertEquals(NextAction.Stop, qm.manualPrevious())
    }

    @Test
    fun `jumpTo moves the shuffle cursor so playback continues from there`() {
        val qm = manager(songs(5), shuffle = true, repeat = RepeatMode.OFF)
        assertTrue(qm.jumpTo(3))
        assertEquals("song4", qm.current)
        // Should still terminate (no infinite cycle corruption).
        playThrough(qm, 10)
        assertEquals(NextAction.Stop, qm.advanceOnCompletion())
    }

    @Test
    fun `snapshot and restore preserve cycle position`() {
        val qm = manager(songs(6), shuffle = true, repeat = RepeatMode.ALL)
        playThrough(qm, 2)
        val expectedRemainder = mutableListOf<String>()
        val probe = QueueManager<String>(Random(7))
        probe.restore(qm.queue, qm.snapshot())
        // Both managers must agree on the rest of the cycle.
        val fromOriginal = playThrough(qm, 3)
        val fromRestored = playThrough(probe, 3)
        assertEquals(fromOriginal, fromRestored)
        assertEquals(expectedRemainder.size + 3, fromRestored.size)
    }

    @Test
    fun `single song queue with repeat ALL keeps replaying`() {
        val qm = manager(songs(1), repeat = RepeatMode.ALL)
        val a = qm.advanceOnCompletion()
        assertEquals("song1", (a as NextAction.Play).item)
    }
}
