# Dunda — Feature Manifest

This is the authoritative list of what Dunda must do, its current status, and the
design for each feature. Update this file whenever a feature is specified, changed,
or lands in code.

**App identity:** a local-files music player for Android whose differentiators are
crossfade playback, *correct* shuffle, rich repeat/advance control, and play-history
statistics.

## Status manifest

| # | Feature | Status | Code / design section |
|---|---------|--------|----------------------|
| 1 | Play / pause / seek | ✅ Done | `MusicService`, `CrossfadePlayer` |
| 2 | Next / previous (smart previous: restart if >3s in) | ✅ Done | `MusicService.skipNext/skipPrevious` |
| 3 | Crossfade between songs (adjustable duration) | ✅ Done (not persisted) | `CrossfadePlayer`; [§ Settings persistence](#9-settings-persistence) |
| 4 | Local library scan (MediaStore) | ✅ Done | `MediaScanner` |
| 5 | Custom playlists (create, add/remove songs, ordering) | ✅ Done | `PlaylistDao`, `Playlist` entities |
| 6 | Background playback + media notification | ✅ Done | `MusicService` (MediaSessionService) |
| 7 | True shuffle — no repeats until queue exhausted | ✅ Done | `player/QueueManager.kt`; [§ True shuffle](#1-true-shuffle) |
| 8 | Repeat modes: off / all / one-indefinite / **one-then-advance** | ✅ Done | `player/QueueManager.kt`; [§ Repeat & advance policy](#2-repeat--advance-policy) |
| 9 | Solo mode — play only the selected song, never auto-advance | ✅ Done | `player/QueueManager.kt`; [§ Repeat & advance policy](#2-repeat--advance-policy) |
| 10 | Favourites | ✅ Done | `SongDao`, virtual playlist in `PlaylistDetailScreen` |
| 11 | Play logging — count + timestamp of every play | ✅ Done | `player/PlayTracker.kt`, `PlayEventDao`; [§ Play logging](#5-play-logging) |
| 12 | Most/least played, filterable by period (month, year, …) | ✅ Done | `ui/screens/StatsScreen.kt`; [§ Statistics](#6-statistics) |
| 13 | Playlist sorting incl. by play count (asc/desc) | ✅ Done | `data/model/SortMode.kt`; [§ Playlist sorting](#7-playlist-sorting) |
| 14 | Library cache in Room (foundation for 10–13) | ✅ Done | `data/model/SongEntity.kt`, `SongDao.sync`; [§ Library cache](#3-library-cache-foundational) |
| 15 | Settings persistence (crossfade, shuffle, repeat mode) | ✅ Done | `data/local/SettingsStore.kt`, `queue_state`; [§ Settings persistence](#9-settings-persistence) |

| 16 | Lock screen / notification controls + headphone buttons (incl. favourite & repeat buttons) | ✅ Done | [§ MediaSession integration](#10-mediasession-integration) |
| 17 | Audio focus: pause on call, duck for beeps, pause on unplug | ✅ Done | `MusicService` focus handling; [§ Audio focus](#11-audio-focus) |
| 18 | Draggable playhead with time labels in mini player | ✅ Done | `ui/components/MiniPlayer.kt` |
| 19 | Search (title / artist / album) | ✅ Done | `ui/screens/HomeScreen.kt` |
| 20 | Smart playlists: Favourites, Recently Added, Most Played | ✅ Done | `PlaylistDetailScreen` virtual ids -1/-2/-3 |
| 21 | Playlist management: create (FAB no longer obscured), delete w/ confirmation + cascade, remove song from playlist | ✅ Done | `PlaylistScreen`, `PlaylistDao.deletePlaylistWithSongs` |
| 22 | Multi-select on Home (long-press): add many songs to a playlist / new playlist at once | ✅ Done | `HomeScreen` selection mode, `PlaylistDao.addSongsToPlaylist` |
| 23 | Metadata overrides: customTitle/customArtist columns (Room v3), survive rescans, "Edit info" dialog per song | ✅ Done | `SongEntity`, `MusicRepository.setSongInfo`, `HomeScreen` |
| 24 | Artist statistics: most/least-listened artists with period filters | ✅ Done | `StatsScreen` Artists mode |
| 25 | One-time library cleanup: 1,254 songs assigned artist+title, 243 title-cleaned (of 1,578); junk files skipped. Applied 2026-08-09 via adb into overrides | ✅ Done | scripts in session scratchpad; data lives in `songs.customTitle/customArtist` |
| 26 | Now Playing screen: album art (Coil), song details incl. play count, seek bar, full controls (shuffle/repeat/solo/favourite). Opens on song tap and mini-player tap | ✅ Done | `ui/screens/NowPlayingScreen.kt`, route `now_playing` |
| 27 | Artists browsing: Artists tab (song/play counts per artist), artist detail page with shuffle-play; artist name in Now Playing is clickable | ✅ Done | `ArtistsScreen.kt`, `ArtistDetailScreen.kt`, routes `artists`, `artist/{name}` |
| 28 | Crossfade handover: incoming song becomes "current" at fade START (UI/lock screen/seek/next all follow it) | ✅ Done | `CrossfadePlayer.onCrossfadeStarted`, `displayPlayer()` |
| 29 | Library filters: minimum song length (default 1:50) + hide voice notes/recordings by file signature; both in Settings, auto-rescan on change; excluded files keep history (isPresent=0) | ✅ Done | `MediaScanner`, `SettingsStore`, `SettingsScreen` |

> Statuses reflect code + JVM unit tests (`app/src/test/`). On-device verification
> (crossfade feel, notification controls) is tracked separately by the user.

Legend: ✅ Done · 🟡 Partial · 🔲 Planned

---

## Design

### 1. True shuffle

**Requirement.** Shuffle must never replay a song while others in the queue haven't
played yet. Most players get this wrong by picking a random song each time
(sampling *with* replacement); Dunda must sample *without* replacement.

**Approach.** A random permutation of the queue **is** sampling without replacement:
shuffle the indices once (Fisher-Yates), then walk the permutation in order. Every
song plays exactly once per cycle by construction — no played-set bookkeeping needed
within a cycle.

`MusicService.generateShuffledIndices()` already does this (current song pinned
first). What's missing:

- **Cycle exhaustion.** Currently `getNextIndex()` returns `-1` at the end of the
  permutation and playback stops. Correct behaviour: when the cycle is exhausted
  and repeat-all is on (or "reshuffle on exhaust" — see Repeat), generate a *new*
  permutation, with the constraint that its first song ≠ the last song just played
  (no accidental back-to-back repeat across cycle boundaries).
- **Mid-cycle queue changes.** Songs added to the queue while shuffling must be
  inserted at random positions in the *unplayed remainder* of the permutation, never
  the played prefix. Removed songs are dropped from the permutation.
- **Persistence.** The permutation and current position within it must survive
  service death/restart (store in `queue_state` — see [§ Playback state persistence](#8-playback-state-persistence)),
  so that reopening the app doesn't reset the cycle and cause early repeats.

**Non-goal.** History-weighted randomness across sessions ("play what I haven't
heard in weeks first") is a possible later enhancement, not part of this spec.

### 2. Repeat & advance policy

**Requirement.** Beyond the usual repeat modes, two additions:

1. **Repeat once** — the current song plays one extra time, then the queue advances
   normally.
2. **Solo mode** — the song plays and then playback simply stops; the app never
   auto-advances. For sessions where the user wants to hand-pick every song.

**Approach.** Model everything that answers "what happens when the current song
ends?" as one `AdvancePolicy`, owned by `MusicService`:

```kotlin
enum class RepeatMode { OFF, ALL, ONE, ONCE }   // ONCE = one extra play, then advance
```

plus an orthogonal `soloMode: Boolean`. Resolution order when a song ends:

| State | On song end |
|-------|-------------|
| `soloMode = true` | Stop. Do nothing until the user picks a song. (Overrides everything.) |
| `RepeatMode.ONE` | Restart the same song, indefinitely. |
| `RepeatMode.ONCE`, not yet repeated | Restart the same song, set `hasRepeatedOnce = true`. |
| `RepeatMode.ONCE`, already repeated | Clear the flag, advance. |
| `RepeatMode.ALL` | Advance; at end of queue/shuffle-cycle wrap around (reshuffling if shuffle is on). |
| `RepeatMode.OFF` | Advance; stop at end of queue. |

`hasRepeatedOnce` resets on every song change and every manual skip.

**Why solo is a toggle, not a 5th repeat state:** solo answers a different question
("should the app choose the next song at all?") and the user will want to flip it
on/off without losing their repeat preference. UI: repeat button cycles
OFF → ALL → ONE → ONCE (distinct icons/badges); solo mode is its own toggle in the
player (icon: e.g. a "1"-in-circle or hand icon) and is clearly indicated when active.

**Crossfade interaction (important):** crossfade pre-loads the *next* song into the
inactive player near the end of the current one. Under `ONE`, `ONCE` (first play),
and solo mode, the "next" item is the same song or nothing — `queueNextForCrossfade()`
must consult the advance policy: crossfade into the same song from its start
(ONE/ONCE), or suppress crossfade entirely and let the song end cleanly (solo).

### 3. Library cache (foundational)

**Requirement (derived).** Features 10–13 need to attach data to songs (favourite
flag, play counts) and query/sort across the whole library — including songs with
*zero* plays ("least played"). MediaStore can't be joined against Room, so we
maintain a Room-side cache of the library:

```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,       // MediaStore ID
    val title: String, val artist: String, val album: String,
    val duration: Long, val uri: String, val albumArtUri: String?,
    val dateAdded: Long,
    val isFavourite: Boolean = false,
    val isPresent: Boolean = true,  // false when missing from last scan
)
```

`MediaScanner` upserts into this table on every scan (app start + manual refresh).
Songs that disappear from the device are marked `isPresent = false` rather than
deleted, so their play history and favourite status survive re-appearing (e.g. SD
card remount). All library reads in the app move from MediaStore-direct to this
table; MediaStore becomes ingest-only.

This makes every statistic and sort a plain SQL query and is the single migration
that unlocks features 10–13.

### 4. Favourites

**Requirement.** Mark/unmark any song as a favourite; view favourites as a
collection.

**Approach.** `isFavourite` flag on `SongEntity` (see above). Toggling is one DAO
update. "Favourites" is surfaced as a built-in virtual playlist (query
`WHERE isFavourite = 1`), not a row in the `playlists` table — it can't be deleted
or renamed, but supports the same sorting options as real playlists. Heart toggle
appears in the player screen, mini-player long-press menu, and song list items.

### 5. Play logging

**Requirement.** Every play of every song is logged — both a count and *when* —
enabling most/least-played views over arbitrary periods.

**Approach.** Append-only event log; counts are always derived by aggregation,
never stored as a mutable counter (a counter can't answer "most played this month").

```kotlin
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,       // → songs.id
    val playedAt: Long,     // epoch millis, recorded when the play qualifies
)
```

**What counts as a play (spec):** a play is logged once per playback of a song when
**cumulative listened time reaches 30 seconds, or 50% of the song's duration,
whichever is smaller** (the 50% clause covers very short tracks). Skipping within
the song doesn't reset the accumulated time; restarting the song (repeat ONE/ONCE
cycles included) starts a *new* potential play — so a song looped 10 times logs 10
plays, which is exactly what the statistics should reflect. Tracking lives in
`MusicService`, which is the only component that knows true playback progress
(crossfade means two players are involved; the accumulator follows the active one).

Indexes: `(songId)`, `(playedAt)`.

### 6. Statistics

**Requirement.** See most played and least played songs, filterable by period —
this month, this year, all time, etc.

**Approach.** One query pattern serves everything:

```sql
SELECT s.*, COUNT(p.id) AS playCount
FROM songs s
LEFT JOIN play_events p
    ON p.songId = s.id AND p.playedAt BETWEEN :from AND :to
WHERE s.isPresent = 1
GROUP BY s.id
ORDER BY playCount DESC   -- ASC for least played
```

`LEFT JOIN` is what makes "least played" honest: never-played songs appear with
count 0 instead of vanishing.

UI: a **Stats** screen with period filter chips (This week · This month · This year ·
All time · Custom range) and a Most/Least toggle. Rows show rank, song, play count,
and last-played date. Any row plays / enqueues like a normal list item. (Later,
cheap extensions from the same event log: listening time per period, "on this day",
per-artist stats.)

### 7. Playlist sorting

**Requirement.** Inside a playlist, sort by play count (most→least or least→most)
among other orderings.

**Approach.** Persist the choice per playlist — a `sortMode` column on `playlists`
(and a global one for the library/Home list):

```kotlin
enum class SortMode {
    CUSTOM,            // manual order via playlist_songs.position (default; playlists only)
    TITLE_ASC, TITLE_DESC,
    ARTIST_ASC,
    DATE_ADDED_DESC, DATE_ADDED_ASC,
    PLAY_COUNT_DESC,   // most played first
    PLAY_COUNT_ASC,    // least played first
    DURATION_ASC, DURATION_DESC,
}
```

Play-count sorts reuse the § Statistics aggregate (all-time window by default),
joined against the playlist's songs. Manual reordering remains available only in
`CUSTOM`; switching to another sort never destroys the stored custom positions.
Sort is chosen from a menu in the playlist header. **Playback follows the displayed
order** — the visible list is the queue order handed to `MusicService`.

### 8. Playback state persistence

**Requirement (derived).** Shuffle-cycle progress, repeat mode, solo mode, queue,
and position should survive process death so the no-repeat guarantee and the user's
context aren't reset every launch.

**Approach.** A small `queue_state` Room table (single row): serialized queue song
IDs, shuffled index order, current index, position ms, repeat mode, solo flag.
Written on meaningful transitions (song change, mode change, pause), restored in
`MusicService.onCreate`.

### 9. Settings persistence

**Requirement (derived).** Crossfade duration currently resets every launch.

**Approach.** Jetpack DataStore (`Preferences`) for user settings: crossfade
duration, default sort modes, and any future toggles. ViewModels read as Flow;
service reads once at start and observes changes.

### 10. MediaSession integration

The audible ExoPlayer changes on every crossfade, so `MusicService` calls
`MediaSession.setPlayer()` with a `ForwardingPlayer` wrapper around the new
active player on each swap. The wrapper adds SEEK_TO_NEXT/PREVIOUS commands
(a single-item ExoPlayer doesn't advertise them — the queue lives in
`QueueManager`) and routes play/pause/next/previous through the service so the
advance policy and audio focus always apply. Custom session buttons (favourite
toggle, repeat cycle) are exposed via `setCustomLayout` + `onCustomCommand`;
their icons update reactively from the favourites flow and repeat mode. This is
what makes lock screen, notification, and headphone/Bluetooth buttons work.

### 11. Audio focus

The service owns one `AudioFocusRequest` (USAGE_MEDIA / CONTENT_TYPE_MUSIC —
focus can't be delegated to ExoPlayer's built-in handling because the two
crossfading players would steal focus from each other). Behavior: permanent
loss → pause; transient loss (call) → pause and resume after only if we caused
the pause; duck → volume ×0.3 via `CrossfadePlayer.volumeMultiplier` (applied
on top of crossfade volumes); headphones unplugged → pause. Starting playback
is focus-gated: if the request is denied (mid-call), nothing starts.

---

## Data model after this spec (Room v2)

```
songs           (id PK, title, artist, album, duration, uri, albumArtUri,
                 dateAdded, isFavourite, isPresent)
playlists       (id PK, name, createdAt, sortMode)          -- sortMode added
playlist_songs  (playlistId, songId, position, addedAt)     -- unchanged
play_events     (id PK, songId, playedAt)                   -- new
queue_state     (single row: queue, shuffle order, index, position, modes)  -- new
```

Migration 1→2 adds `songs`, `play_events`, `queue_state`, and the
`playlists.sortMode` column. Existing playlist data is preserved.

## Suggested implementation order

1. **Library cache** (§3) + migration — everything else depends on it.
2. **Repeat & advance policy** (§2) — pure service logic, high user value.
3. **Finish true shuffle** (§1) — reshuffle-on-exhaust, mid-cycle edits, persistence (§8).
4. **Play logging** (§5) — start collecting data early so stats have history.
5. **Favourites** (§4) — small, independent.
6. **Statistics screen** (§6).
7. **Playlist sorting** (§7).
8. **Settings persistence** (§9).
