package com.dunda.app.data.model

/**
 * Sort orderings for song lists. CUSTOM (manual positions) applies to playlists
 * only; the library list uses the rest. Persisted by enum name — do not rename
 * constants without a migration.
 */
enum class SortMode {
    CUSTOM,
    TITLE_ASC, TITLE_DESC,
    ARTIST_ASC,
    DATE_ADDED_DESC, DATE_ADDED_ASC,
    PLAY_COUNT_DESC,   // most played first
    PLAY_COUNT_ASC,    // least played first
    DURATION_ASC, DURATION_DESC,
    BPM;

    companion object {
        fun fromName(name: String?): SortMode =
            entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}
