package com.github.damontecres.stashapp.ui.components

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Lightweight persistence for the user's most-recent search queries.
 *
 * Stored in the default [android.content.SharedPreferences] under a single delimited string so
 * that insertion order (most-recent first) is preserved. This intentionally avoids the proto /
 * DataStore schema and only adds a new string key.
 */
object SearchHistory {
    /** SharedPreferences key holding the delimited, ordered list of recent queries. */
    const val PREF_KEY = "search_history"

    /** Maximum number of distinct recent queries to retain. */
    const val MAX_ENTRIES = 8

    // A control character (Unit Separator, 0x1F) used as the delimiter; it will not appear in a
    // query a user types on a keyboard.
    private val DELIMITER = Char(0x1F).toString()

    /**
     * Returns the recent queries, most-recent first, capped at [MAX_ENTRIES].
     */
    fun get(context: Context): List<String> {
        val raw =
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(PREF_KEY, null)
                ?: return emptyList()
        return raw
            .split(DELIMITER)
            .filter { it.isNotBlank() }
            .take(MAX_ENTRIES)
    }

    /**
     * Records a submitted [query]. Blank queries are ignored. The query is de-duplicated
     * (case-insensitively) and moved to the front, and the list is capped at [MAX_ENTRIES].
     */
    fun add(
        context: Context,
        query: String,
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val existing = get(context)
        val deduped =
            buildList {
                add(trimmed)
                existing.forEach { prior ->
                    if (!prior.equals(trimmed, ignoreCase = true)) {
                        add(prior)
                    }
                }
            }.take(MAX_ENTRIES)

        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_KEY, deduped.joinToString(DELIMITER))
            .apply()
    }

    /** Clears all recorded search history. */
    fun clear(context: Context) {
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .remove(PREF_KEY)
            .apply()
    }
}
