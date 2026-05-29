package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.api.fragment.SlimSceneData

/**
 * Pure, deterministic "pick one" logic behind the home-screen "Surprise Me" action.
 *
 * The *randomness* of a Surprise Me pick comes from the server: callers fetch a small page of
 * scenes sorted by `random` (Stash's standard `random_<seed>` sort), so every fetch already
 * returns a freshly shuffled candidate list. This object does NOT call any RNG itself — given a
 * candidate list and an index it is a pure transform, which is exactly what makes the couch
 * discovery flow unit-testable and stable across runs.
 *
 * On top of that it applies one optional, equally-pure bias: prefer scenes the user has *not*
 * already watched (`play_count == 0`). If every candidate has been played (a small or
 * fully-watched library) it falls back to the full shuffled list rather than returning nothing,
 * so Surprise Me never dead-ends.
 *
 * Note: a finer "not *recently* watched" bias would key off `last_played_at`, but that field is
 * not part of the [SlimSceneData] fragment (adding it means regenerating the Apollo schema, which
 * is out of scope here), so we bias on the available `play_count` instead.
 */
object SurpriseMePicker {
    /**
     * From a server-shuffled [candidates] list, choose the candidate at [index] after optionally
     * dropping already-watched scenes.
     *
     * Because [candidates] is already in random order (server `random` sort), picking a fixed
     * [index] — the head by default — yields a uniformly random scene while keeping this function
     * pure. The single live RNG call (if any) belongs at the call site / in the server seed, not
     * here.
     *
     * @param candidates scenes fetched with a random sort, most-random-first
     * @param index which candidate to take from the (filtered) list; defaults to the head
     * @param preferUnwatched when true, scenes with a non-zero `play_count` are skipped if doing
     *   so still leaves a non-empty pool (so a watched-heavy library still surfaces something)
     * @return the chosen scene, or null if [candidates] is empty
     */
    fun pick(
        candidates: List<SlimSceneData>,
        index: Int = 0,
        preferUnwatched: Boolean = true,
    ): SlimSceneData? {
        if (candidates.isEmpty()) return null

        val pool =
            if (preferUnwatched) {
                val unwatched = candidates.filter { (it.play_count ?: 0) <= 0 }
                // Never dead-end: if everything was already watched, fall back to the full list.
                unwatched.ifEmpty { candidates }
            } else {
                candidates
            }

        // Wrap the index so a caller-supplied RNG result (or a stale index) is always in range.
        val safeIndex = ((index % pool.size) + pool.size) % pool.size
        return pool[safeIndex]
    }
}
