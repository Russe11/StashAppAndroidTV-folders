package com.github.damontecres.stashapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneQuickActionsTest {
    @Test
    fun ratingLadder_isWholeStarsPlusClear_topItemIsFiveStars() {
        // Most-common "I liked it" first so it's a single D-pad press on a remote;
        // explicit clear (0) is the tail.
        assertEquals(listOf(100, 80, 60, 40, 20, 0), SceneQuickActions.QUICK_RATING_LADDER)
    }

    @Test
    fun rating100ToStars_roundsToNearestWholeStar() {
        assertEquals(0, SceneQuickActions.rating100ToStars(0))
        assertEquals(1, SceneQuickActions.rating100ToStars(20))
        assertEquals(3, SceneQuickActions.rating100ToStars(60))
        assertEquals(5, SceneQuickActions.rating100ToStars(100))
        // Off-grid values (e.g. half-star ratings written elsewhere) round sensibly.
        assertEquals(1, SceneQuickActions.rating100ToStars(15))
        assertEquals(2, SceneQuickActions.rating100ToStars(45))
    }

    @Test
    fun rating100ToStars_clampsOutOfRange() {
        assertEquals(0, SceneQuickActions.rating100ToStars(-50))
        assertEquals(5, SceneQuickActions.rating100ToStars(500))
    }

    @Test
    fun starsToRating100_mapsAndClamps() {
        assertEquals(0, SceneQuickActions.starsToRating100(0))
        assertEquals(60, SceneQuickActions.starsToRating100(3))
        assertEquals(100, SceneQuickActions.starsToRating100(5))
        assertEquals(100, SceneQuickActions.starsToRating100(9))
        assertEquals(0, SceneQuickActions.starsToRating100(-2))
    }

    @Test
    fun isRatingSelected_treatsNullAsZero() {
        assertTrue(SceneQuickActions.isRatingSelected(0, null))
        assertFalse(SceneQuickActions.isRatingSelected(20, null))
        assertTrue(SceneQuickActions.isRatingSelected(80, 80))
        assertFalse(SceneQuickActions.isRatingSelected(80, 60))
    }

    @Test
    fun overrides_fallBackToServerValuesWhenNoEditRecorded() {
        val overrides = SceneCurationOverrides()
        assertFalse(overrides.hasOverride("1"))
        assertTrue(overrides.effectiveOrganized("1", serverValue = true))
        assertEquals(40, overrides.effectiveRating100("1", serverValue = 40))
        assertEquals(2, overrides.effectiveOCounter("1", serverValue = 2))
        // A null server o-counter stays null when un-overridden.
        assertNull(overrides.effectiveOCounter("1", serverValue = null))
    }

    @Test
    fun overrides_applyOptimisticEdits_perScene() {
        val overrides =
            SceneCurationOverrides()
                .withOrganized("1", true)
                .withRating100("1", 80)
                .withOCounter("1", 5)

        assertTrue(overrides.hasOverride("1"))
        // Override wins over the (stale) server value.
        assertFalse(overrides.effectiveOrganized("1", serverValue = false).let { !it })
        assertEquals(80, overrides.effectiveRating100("1", serverValue = 20))
        assertEquals(5, overrides.effectiveOCounter("1", serverValue = 1))

        // A different scene is untouched.
        assertFalse(overrides.hasOverride("2"))
        assertEquals(20, overrides.effectiveRating100("2", serverValue = 20))
    }

    @Test
    fun overrides_canClearRatingOptimistically() {
        // Clearing a rating records a null override, which must win over a non-null server value.
        val overrides = SceneCurationOverrides().withRating100("1", null)
        assertTrue(overrides.hasOverride("1"))
        assertNull(overrides.effectiveRating100("1", serverValue = 80))
    }

    @Test
    fun overrides_areImmutable_originalUnaffected() {
        val original = SceneCurationOverrides()
        val updated = original.withOrganized("1", true)
        assertFalse(original.hasOverride("1"))
        assertTrue(updated.hasOverride("1"))
    }

    @Test
    fun overrides_latestEditWins() {
        val overrides =
            SceneCurationOverrides()
                .withRating100("1", 20)
                .withRating100("1", 100)
        assertEquals(100, overrides.effectiveRating100("1", serverValue = 0))
    }
}
