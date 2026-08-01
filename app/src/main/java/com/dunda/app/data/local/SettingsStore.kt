package com.dunda.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dunda.app.data.model.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User settings persisted with DataStore so they survive app restarts. */
class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_CROSSFADE_MS = longPreferencesKey("crossfade_ms")
        private val KEY_LIBRARY_SORT = stringPreferencesKey("library_sort_mode")
        const val DEFAULT_CROSSFADE_MS = 10_000L
    }

    val crossfadeMs: Flow<Long> = context.dataStore.data
        .map { it[KEY_CROSSFADE_MS] ?: DEFAULT_CROSSFADE_MS }

    val librarySortMode: Flow<SortMode> = context.dataStore.data
        .map { SortMode.fromName(it[KEY_LIBRARY_SORT]) }

    suspend fun setCrossfadeMs(value: Long) {
        context.dataStore.edit { it[KEY_CROSSFADE_MS] = value }
    }

    suspend fun setLibrarySortMode(mode: SortMode) {
        context.dataStore.edit { it[KEY_LIBRARY_SORT] = mode.name }
    }
}
