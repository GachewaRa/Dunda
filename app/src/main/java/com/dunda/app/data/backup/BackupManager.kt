package com.dunda.app.data.backup

import android.content.Context
import android.net.Uri
import com.dunda.app.data.local.AppDatabase
import com.dunda.app.data.local.SettingsStore
import com.dunda.app.data.model.PlayEvent
import com.dunda.app.data.model.Playlist
import com.dunda.app.data.model.PlaylistSong
import com.dunda.app.data.model.SongEntity
import com.dunda.app.data.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup = one portable JSON file holding everything a reinstall or new phone
 * can't recover from the files themselves: favourites, metadata overrides,
 * BPM, playlists, the play-event history, and settings.
 *
 * Songs are referenced two ways: by MediaStore id (exact, same device) and by
 * a fingerprint of raw title + duration (survives new phones / rescans where
 * ids change). Import prefers the id when its fingerprint still matches.
 *
 * Import is additive/merging — it never deletes local data: overrides fill in,
 * favourites OR together, playlists replace same-named ones, events dedupe on
 * exact (song, timestamp) pairs.
 */
class BackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val settings = SettingsStore(appContext)

    companion object {
        const val VERSION = 1

        fun fingerprint(rawTitle: String, durationMs: Long): String =
            rawTitle.trim().lowercase() + "|" + durationMs / 1000
    }

    private fun SongEntity.fp() = fingerprint(title, duration)

    suspend fun export(uri: Uri): String = withContext(Dispatchers.IO) {
        val songs = db.songDao().getAllOnce()
        val events = db.playEventDao().getAllOnce()
        val fpById = songs.associate { it.id to it.fp() }

        val songsArr = JSONArray()
        for (s in songs) {
            val interesting = s.customTitle != null || s.customArtist != null ||
                s.isFavourite || s.bpm != null
            if (!interesting) continue
            songsArr.put(JSONObject().apply {
                put("id", s.id)
                put("fp", s.fp())
                s.customTitle?.let { put("t", it) }
                s.customArtist?.let { put("a", it) }
                if (s.isFavourite) put("fav", true)
                s.bpm?.let { put("bpm", it) }
            })
        }

        val playlistsArr = JSONArray()
        for (p in db.playlistDao().getAllPlaylistsOnce()) {
            val members = db.playlistDao().getPlaylistSongsOnce(p.id)
            playlistsArr.put(JSONObject().apply {
                put("name", p.name)
                put("createdAt", p.createdAt)
                put("sortMode", p.sortMode)
                put("songs", JSONArray(members.mapNotNull { fpById[it.songId] }))
            })
        }

        val eventsArr = JSONArray()
        for (e in events) {
            val fp = fpById[e.songId] ?: continue
            eventsArr.put(JSONObject().put("fp", fp).put("at", e.playedAt))
        }

        val root = JSONObject().apply {
            put("app", "dunda")
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("songs", songsArr)
            put("playlists", playlistsArr)
            put("events", eventsArr)
            put("settings", JSONObject().apply {
                put("crossfadeMs", settings.crossfadeMs.first())
                put("minDurationMs", settings.minDurationMs.first())
                put("excludeNonMusic", settings.excludeNonMusic.first())
                put("librarySort", settings.librarySortMode.first().name)
            })
        }

        appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Could not open backup destination")

        settings.setLastBackupAt(System.currentTimeMillis())
        "Backed up ${songsArr.length()} songs' data, ${playlistsArr.length()} playlists, ${eventsArr.length()} plays"
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("Could not read backup file")
        val root = JSONObject(text)
        require(root.optString("app") == "dunda") { "Not a Dunda backup file" }

        val current = db.songDao().getAllOnce()
        val byId = current.associateBy { it.id }
        val byFp = current.groupBy { it.fp() }.mapValues { it.value.first() }

        fun resolve(id: Long, fp: String): Long? {
            byId[id]?.let { if (it.fp() == fp) return it.id }
            return byFp[fp]?.id
        }

        // Per-song user data
        var songsApplied = 0
        var songsUnmatched = 0
        val songsArr = root.optJSONArray("songs") ?: JSONArray()
        for (i in 0 until songsArr.length()) {
            val o = songsArr.getJSONObject(i)
            val target = resolve(o.optLong("id"), o.optString("fp"))
            if (target == null) { songsUnmatched++; continue }
            db.songDao().applyBackupUserData(
                songId = target,
                title = o.optString("t").takeIf { it.isNotEmpty() },
                artist = o.optString("a").takeIf { it.isNotEmpty() },
                favourite = o.optBoolean("fav", false),
                bpm = if (o.has("bpm")) o.getInt("bpm") else null,
            )
            songsApplied++
        }

        // Playlists: replace same-named
        var playlistsApplied = 0
        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlistsArr.length()) {
            val o = playlistsArr.getJSONObject(i)
            val name = o.optString("name")
            if (name.isEmpty()) continue
            db.playlistDao().getPlaylistByName(name)?.let {
                db.playlistDao().deletePlaylistWithSongs(it)
            }
            val newId = db.playlistDao().insertPlaylist(
                Playlist(
                    name = name,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    sortMode = o.optString("sortMode", SortMode.CUSTOM.name),
                )
            )
            val fps = o.optJSONArray("songs") ?: JSONArray()
            var pos = 0
            for (j in 0 until fps.length()) {
                val sid = byFp[fps.getString(j)]?.id ?: continue
                db.playlistDao().insertPlaylistSong(PlaylistSong(newId, sid, pos++))
            }
            playlistsApplied++
        }

        // Play events: dedupe on exact (songId, playedAt)
        val existing = db.playEventDao().getAllOnce()
            .mapTo(HashSet()) { it.songId to it.playedAt }
        var eventsApplied = 0
        val eventsArr = root.optJSONArray("events") ?: JSONArray()
        for (i in 0 until eventsArr.length()) {
            val o = eventsArr.getJSONObject(i)
            val sid = byFp[o.optString("fp")]?.id ?: continue
            val at = o.optLong("at")
            if ((sid to at) in existing) continue
            db.playEventDao().insert(PlayEvent(songId = sid, playedAt = at))
            existing.add(sid to at)
            eventsApplied++
        }

        root.optJSONObject("settings")?.let { s ->
            if (s.has("crossfadeMs")) settings.setCrossfadeMs(s.getLong("crossfadeMs"))
            if (s.has("minDurationMs")) settings.setMinDurationMs(s.getLong("minDurationMs"))
            if (s.has("excludeNonMusic")) settings.setExcludeNonMusic(s.getBoolean("excludeNonMusic"))
            if (s.has("librarySort")) settings.setLibrarySortMode(SortMode.fromName(s.getString("librarySort")))
        }

        buildString {
            append("Restored: $songsApplied songs' data, $playlistsApplied playlists, $eventsApplied plays")
            if (songsUnmatched > 0) append(" ($songsUnmatched songs not found on this device)")
        }
    }
}
