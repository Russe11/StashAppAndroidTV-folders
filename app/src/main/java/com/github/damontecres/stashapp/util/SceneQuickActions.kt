package com.github.damontecres.stashapp.util

/**
 * Pure, side-effect-free logic backing the scene-card quick-curation actions
 * (organized toggle, quick rating, o-counter). Kept free of Android/Compose
 * dependencies so it can be unit tested directly.
 *
 * Stash scenes do **not** have a server-side "favorite" flag (only performers,
 * studios, and tags do); the closest scene-level flag is [organized], so the
 * "quick favorite"-style action is implemented as an organized toggle.
 */
object SceneQuickActions {
    /**
     * The 0-100 rating values offered by the quick-rating picker, one per whole
     * star plus an explicit "clear" (0). Returned newest-intent-first (5 stars
     * down to clear) so the most common "I liked it" action is the top item and
     * reachable with a single D-pad press on a remote.
     */
    val QUICK_RATING_LADDER: List<Int> = listOf(100, 80, 60, 40, 20, 0)

    /** Converts a 0-100 rating into a whole-star count (0..5), rounding to nearest. */
    fun rating100ToStars(rating100: Int): Int = (rating100.coerceIn(0, 100) + 10) / 20

    /** Converts a whole-star count (0..5) into the canonical 0-100 rating value. */
    fun starsToRating100(stars: Int): Int = stars.coerceIn(0, 5) * 20

    /**
     * Whether a quick-rating ladder entry should render as the currently-applied
     * rating, given the scene's effective rating (after optimistic overrides).
     */
    fun isRatingSelected(
        ladderValue: Int,
        currentRating100: Int?,
    ): Boolean = (currentRating100 ?: 0) == ladderValue
}

/**
 * An in-memory, optimistic view of curation edits applied from scene cards before
 * the paged list source refetches. Card display reads through [effectiveOrganized],
 * [effectiveRating100], and [effectiveOCounter] so a long-press edit is reflected
 * immediately even though the underlying pager still holds the pre-edit value.
 *
 * Immutable + pure: every mutator returns a new instance, making the reducer
 * trivially unit-testable.
 */
data class SceneCurationOverrides(
    private val organized: Map<String, Boolean> = emptyMap(),
    private val rating100: Map<String, Int?> = emptyMap(),
    private val oCounter: Map<String, Int> = emptyMap(),
) {
    fun withOrganized(
        sceneId: String,
        value: Boolean,
    ): SceneCurationOverrides = copy(organized = organized + (sceneId to value))

    fun withRating100(
        sceneId: String,
        value: Int?,
    ): SceneCurationOverrides = copy(rating100 = rating100 + (sceneId to value))

    fun withOCounter(
        sceneId: String,
        value: Int,
    ): SceneCurationOverrides = copy(oCounter = oCounter + (sceneId to value))

    /** True iff any override has been recorded for this scene. */
    fun hasOverride(sceneId: String): Boolean =
        organized.containsKey(sceneId) ||
            rating100.containsKey(sceneId) ||
            oCounter.containsKey(sceneId)

    fun effectiveOrganized(
        sceneId: String,
        serverValue: Boolean,
    ): Boolean = organized[sceneId] ?: serverValue

    fun effectiveRating100(
        sceneId: String,
        serverValue: Int?,
    ): Int? = if (rating100.containsKey(sceneId)) rating100[sceneId] else serverValue

    fun effectiveOCounter(
        sceneId: String,
        serverValue: Int?,
    ): Int? = if (oCounter.containsKey(sceneId)) oCounter[sceneId] else serverValue
}
