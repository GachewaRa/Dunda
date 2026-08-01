package com.dunda.app.player

import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE, ONCE }

/**
 * Pure queue/advance logic: true shuffle (sampling without replacement via a
 * random permutation), repeat modes, and solo mode. No Android dependencies so
 * every guarantee here is unit-tested on the JVM.
 *
 * Shuffle invariants:
 *  - While a cycle is in progress, no song plays twice before every song in the
 *    queue has played once (the cycle IS a permutation).
 *  - When a cycle is exhausted and playback wraps (repeat ALL or manual skip),
 *    a fresh permutation is generated whose first song differs from the song
 *    just played (queue size permitting), so no back-to-back repeat.
 *  - Songs added mid-cycle land at a random spot in the *unplayed* remainder.
 *
 * Advance policy on natural song end (see docs/FEATURES.md §2):
 *  solo overrides everything → stop; ONE → same song forever; ONCE → same song
 *  one extra time, then advance; ALL wraps at the end; OFF stops at the end.
 */
class QueueManager<T>(private val random: Random = Random.Default) {

    sealed interface NextAction<out T> {
        data class Play<T>(val item: T, val index: Int) : NextAction<T>
        data object RepeatCurrent : NextAction<Nothing>
        data object Stop : NextAction<Nothing>
    }

    private val items = mutableListOf<T>()
    private var order = mutableListOf<Int>()   // permutation of item indices (shuffle on)
    private var cursor = -1                    // position within order
    var currentIndex = -1
        private set

    var shuffleEnabled = false
        private set
    var repeatMode = RepeatMode.OFF
    var soloMode = false
    var hasRepeatedOnce = false
        private set

    val queue: List<T> get() = items.toList()
    val size: Int get() = items.size
    val current: T? get() = items.getOrNull(currentIndex)

    fun setQueue(newItems: List<T>, startIndex: Int) {
        items.clear()
        items.addAll(newItems)
        currentIndex = startIndex.coerceIn(-1, items.size - 1)
        hasRepeatedOnce = false
        if (shuffleEnabled) newCycle(pinCurrentFirst = true) else clearCycle()
    }

    fun setShuffle(enabled: Boolean) {
        if (shuffleEnabled == enabled) return
        shuffleEnabled = enabled
        if (enabled && items.isNotEmpty()) newCycle(pinCurrentFirst = true) else clearCycle()
    }

    fun toggleShuffle() = setShuffle(!shuffleEnabled)

    /** User tapped a specific queue position: play it and continue from there. */
    fun jumpTo(index: Int): Boolean {
        if (index !in items.indices) return false
        pending = null
        currentIndex = index
        hasRepeatedOnce = false
        if (shuffleEnabled) {
            val pos = order.indexOf(index)
            cursor = if (pos >= 0) pos else 0
        }
        return true
    }

    /**
     * What plays after the current song ends naturally. Does not mutate state —
     * safe to call for crossfade preloading. RepeatCurrent means "this same
     * song again from the start" (crossfade into itself).
     */
    fun peekNext(): NextAction<T> {
        if (current == null) return NextAction.Stop
        if (soloMode) return NextAction.Stop
        return when (repeatMode) {
            RepeatMode.ONE -> NextAction.RepeatCurrent
            RepeatMode.ONCE ->
                if (!hasRepeatedOnce) NextAction.RepeatCurrent else nextInOrder(wrap = false)
            RepeatMode.ALL -> nextInOrder(wrap = true)
            RepeatMode.OFF -> nextInOrder(wrap = false)
        }
    }

    /** Apply the natural-end transition and return what happens. */
    fun advanceOnCompletion(): NextAction<T> {
        val action = peekNext()
        when (action) {
            is NextAction.RepeatCurrent -> {
                if (repeatMode == RepeatMode.ONCE) hasRepeatedOnce = true
            }
            is NextAction.Play -> {
                commitMove(action.index)
            }
            NextAction.Stop -> { /* nothing to update */ }
        }
        return action
    }

    /** Manual skip: always moves (wraps at the end), clears the ONCE flag. */
    fun manualNext(): NextAction<T> {
        if (items.isEmpty()) return NextAction.Stop
        val action = nextInOrder(wrap = true)
        if (action is NextAction.Play) commitMove(action.index)
        return action
    }

    /**
     * Manual previous. Returns Stop when there is no previous song (caller
     * typically restarts the current one).
     */
    fun manualPrevious(): NextAction<T> {
        if (items.isEmpty()) return NextAction.Stop
        hasRepeatedOnce = false
        return if (shuffleEnabled) {
            if (cursor > 0) {
                cursor--
                currentIndex = order[cursor]
                NextAction.Play(items[currentIndex], currentIndex)
            } else NextAction.Stop
        } else {
            if (currentIndex > 0) {
                currentIndex--
                NextAction.Play(items[currentIndex], currentIndex)
            } else NextAction.Stop
        }
    }

    /** Append to the queue; under shuffle it joins the unplayed remainder. */
    fun addToQueue(item: T) {
        pending = null
        items.add(item)
        val newIndex = items.size - 1
        if (shuffleEnabled) {
            if (order.isEmpty()) {
                newCycle(pinCurrentFirst = true)
            } else {
                val insertAt = random.nextInt(cursor + 1, order.size + 1)
                order.add(insertAt, newIndex)
            }
        }
    }

    /**
     * Remove a queue position. Returns false for invalid index or an attempt
     * to remove the currently playing song (callers should skip first).
     */
    fun removeFromQueue(index: Int): Boolean {
        if (index !in items.indices || index == currentIndex) return false
        pending = null
        items.removeAt(index)
        if (currentIndex > index) currentIndex--
        if (shuffleEnabled) {
            val pos = order.indexOf(index)
            if (pos >= 0) {
                order.removeAt(pos)
                if (pos <= cursor) cursor--
            }
            for (i in order.indices) if (order[i] > index) order[i] = order[i] - 1
        }
        return true
    }

    // ---- persistence support ----

    data class Snapshot(
        val order: List<Int>,
        val cursor: Int,
        val currentIndex: Int,
        val shuffleEnabled: Boolean,
        val repeatMode: RepeatMode,
        val soloMode: Boolean,
    )

    fun snapshot() = Snapshot(order.toList(), cursor, currentIndex, shuffleEnabled, repeatMode, soloMode)

    fun restore(newItems: List<T>, s: Snapshot) {
        items.clear()
        items.addAll(newItems)
        shuffleEnabled = s.shuffleEnabled
        repeatMode = s.repeatMode
        soloMode = s.soloMode
        currentIndex = s.currentIndex.coerceIn(-1, items.size - 1)
        order = s.order.filter { it in items.indices }.toMutableList()
        cursor = s.cursor.coerceIn(-1, order.size - 1)
        hasRepeatedOnce = false
        if (shuffleEnabled && order.isEmpty() && items.isNotEmpty()) newCycle(pinCurrentFirst = true)
    }

    // ---- internals ----

    private fun nextInOrder(wrap: Boolean): NextAction<T> {
        if (items.isEmpty()) return NextAction.Stop
        if (items.size == 1) {
            return if (wrap) NextAction.Play(items[0], 0) else NextAction.Stop
        }
        return if (shuffleEnabled) {
            if (cursor + 1 < order.size) {
                val idx = order[cursor + 1]
                NextAction.Play(items[idx], idx)
            } else if (wrap) {
                // Cycle exhausted: peek the first song of the next cycle.
                val idx = pendingCycle().first()
                NextAction.Play(items[idx], idx)
            } else NextAction.Stop
        } else {
            if (currentIndex + 1 < items.size) {
                NextAction.Play(items[currentIndex + 1], currentIndex + 1)
            } else if (wrap) {
                NextAction.Play(items[0], 0)
            } else NextAction.Stop
        }
    }

    private fun commitMove(targetIndex: Int) {
        hasRepeatedOnce = false
        if (shuffleEnabled) {
            if (cursor + 1 < order.size && order[cursor + 1] == targetIndex) {
                cursor++
            } else {
                // Wrapping into a fresh cycle (possibly pre-generated by peek).
                val cycle = pendingCycle()
                order = cycle
                pending = null
                cursor = 0
            }
            currentIndex = order[cursor]
        } else {
            currentIndex = targetIndex
        }
    }

    /**
     * The next shuffle cycle, generated once and cached so that what peekNext
     * reported (e.g. already crossfading into it) is exactly what commitMove
     * lands on. First song of the new cycle != last song played.
     */
    private var pending: MutableList<Int>? = null

    private fun pendingCycle(): MutableList<Int> {
        pending?.let { return it }
        val cycle = items.indices.toMutableList().apply { shuffle(random) }
        if (cycle.size > 1 && cycle.first() == currentIndex) {
            val swapWith = 1 + random.nextInt(cycle.size - 1)
            cycle[0] = cycle[swapWith].also { cycle[swapWith] = cycle[0] }
        }
        pending = cycle
        return cycle
    }

    private fun newCycle(pinCurrentFirst: Boolean) {
        pending = null
        order = items.indices.toMutableList().apply { shuffle(random) }
        if (pinCurrentFirst && currentIndex >= 0) {
            order.remove(currentIndex)
            order.add(0, currentIndex)
        }
        cursor = if (currentIndex >= 0) 0 else -1
    }

    private fun clearCycle() {
        pending = null
        order = mutableListOf()
        cursor = -1
    }
}
