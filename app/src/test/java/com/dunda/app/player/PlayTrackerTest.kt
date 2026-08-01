package com.dunda.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTrackerTest {

    private class Recorder {
        val plays = mutableListOf<Pair<Long, Long>>()
        var now = 1_000_000L
        val tracker = PlayTracker(
            onQualified = { id, at -> plays.add(id to at) },
            clock = { now },
        )
    }

    @Test
    fun `qualifies at 30s for a long song`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 1, durationMs = 240_000)
        r.tracker.onProgress(29_000)
        assertEquals(0, r.plays.size)
        r.tracker.onProgress(1_000)
        assertEquals(1, r.plays.size)
        assertEquals(1L, r.plays[0].first)
        assertEquals(1_000_000L, r.plays[0].second)
    }

    @Test
    fun `qualifies at half duration for a short song`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 2, durationMs = 40_000) // threshold = 20s
        r.tracker.onProgress(19_000)
        assertEquals(0, r.plays.size)
        r.tracker.onProgress(1_000)
        assertEquals(1, r.plays.size)
    }

    @Test
    fun `does not double-count within one instance`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 3, durationMs = 100_000)
        r.tracker.onProgress(60_000)
        r.tracker.onProgress(60_000)
        assertEquals(1, r.plays.size)
    }

    @Test
    fun `each restart is a fresh instance - looped song logs one play per loop`() {
        val r = Recorder()
        repeat(3) {
            r.tracker.startInstance(songId = 4, durationMs = 90_000)
            r.tracker.onProgress(35_000)
        }
        assertEquals(3, r.plays.size)
    }

    @Test
    fun `restarting before qualification discards partial progress`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 5, durationMs = 200_000)
        r.tracker.onProgress(20_000) // not qualified yet
        r.tracker.startInstance(songId = 6, durationMs = 200_000)
        r.tracker.onProgress(20_000) // fresh accumulator: still not qualified
        assertEquals(0, r.plays.size)
        r.tracker.onProgress(10_000)
        assertEquals(listOf(6L), r.plays.map { it.first })
    }

    @Test
    fun `unknown duration falls back to the 30s cap`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 7, durationMs = 0)
        r.tracker.onProgress(29_500)
        assertEquals(0, r.plays.size)
        r.tracker.onProgress(500)
        assertEquals(1, r.plays.size)
    }

    @Test
    fun `negative or zero deltas are ignored`() {
        val r = Recorder()
        r.tracker.startInstance(songId = 8, durationMs = 100_000)
        r.tracker.onProgress(0)
        r.tracker.onProgress(-5_000)
        r.tracker.onProgress(29_999)
        assertEquals(0, r.plays.size)
    }
}
