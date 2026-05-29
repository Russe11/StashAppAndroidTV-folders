package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.SlimTagData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    @Test
    fun buildProfile_talliesTagsAndPerformers_topFirst() {
        val watched =
            listOf(
                scene(id = "1", playCount = 1, tagIds = listOf("a", "b"), performerIds = listOf("p1")),
                scene(id = "2", playCount = 1, tagIds = listOf("a"), performerIds = listOf("p1", "p2")),
                scene(id = "3", playCount = 1, tagIds = listOf("b"), performerIds = listOf("p3")),
            )

        val profile = RecommendationEngine.buildProfile(watched)

        // tag "a" appears in 2 scenes, "b" in 2 — tie broken by ascending id, so "a" before "b".
        assertEquals(listOf("a", "b"), profile.tagIds)
        assertEquals(2.0, profile.tagScores["a"]!!, 0.0001)
        assertEquals(2.0, profile.tagScores["b"]!!, 0.0001)
        // performer p1 appears twice, then p2/p3 once each.
        assertEquals("p1", profile.performerIds.first())
        assertEquals(2.0, profile.performerScores["p1"]!!, 0.0001)
    }

    @Test
    fun buildProfile_weightsRepeatPlays_butCapsTheBonus() {
        val once = RecommendationEngine.buildProfile(listOf(scene("1", playCount = 1, tagIds = listOf("a"))))
        val twice = RecommendationEngine.buildProfile(listOf(scene("1", playCount = 2, tagIds = listOf("a"))))
        val huge = RecommendationEngine.buildProfile(listOf(scene("1", playCount = 100, tagIds = listOf("a"))))

        assertEquals(1.0, once.tagScores["a"]!!, 0.0001)
        assertEquals(1.25, twice.tagScores["a"]!!, 0.0001)
        // Bonus is capped at +1.0 so a single binge can't dominate the profile.
        assertEquals(2.0, huge.tagScores["a"]!!, 0.0001)
    }

    @Test
    fun buildProfile_honorsTopLimits() {
        val watched =
            listOf(
                scene("1", playCount = 5, tagIds = listOf("hot")),
                scene("2", playCount = 1, tagIds = listOf("cold1")),
                scene("3", playCount = 1, tagIds = listOf("cold2")),
            )

        val profile = RecommendationEngine.buildProfile(watched, topTags = 1, topPerformers = 1)

        assertEquals(listOf("hot"), profile.tagIds)
    }

    @Test
    fun buildProfile_emptyWatched_isEmptyProfile() {
        val profile = RecommendationEngine.buildProfile(emptyList())
        assertTrue(profile.isEmpty)
    }

    @Test
    fun rankRecommendations_ordersByAffinityThenRating_andExcludesWatched() {
        val profile =
            RecommendationEngine.buildProfile(
                listOf(scene("seed", playCount = 1, tagIds = listOf("a", "b"), performerIds = listOf("p1"))),
            )

        val candidates =
            listOf(
                // shares only tag "a" -> score 1.0
                scene("low", rating = 90, tagIds = listOf("a")),
                // shares tag "a" + performer "p1" -> score 2.0 (highest affinity)
                scene("high", rating = 10, tagIds = listOf("a"), performerIds = listOf("p1")),
                // shares tag "b" only -> score 1.0, but higher rating than "low" -> ranks above it
                scene("mid", rating = 99, tagIds = listOf("b")),
                // shares nothing -> dropped
                scene("none", rating = 100, tagIds = listOf("z")),
                // already watched -> excluded even though it matches strongly
                scene("seed", rating = 100, tagIds = listOf("a", "b"), performerIds = listOf("p1")),
            )

        val result =
            RecommendationEngine.rankRecommendations(
                candidates = candidates,
                profile = profile,
                excludeSceneIds = setOf("seed"),
                limit = 10,
            )

        // high (affinity 2.0) > mid (affinity 1.0, rating 99) > low (affinity 1.0, rating 90).
        // "none" dropped (no shared affinity), "seed" excluded.
        assertEquals(listOf("high", "mid", "low"), result.map { it.id })
        assertFalse(result.any { it.id == "seed" })
        assertFalse(result.any { it.id == "none" })
    }

    @Test
    fun rankRecommendations_emptyProfile_returnsNothing() {
        val result =
            RecommendationEngine.rankRecommendations(
                candidates = listOf(scene("x", tagIds = listOf("a"))),
                profile = RecommendationEngine.buildProfile(emptyList()),
                excludeSceneIds = emptySet(),
                limit = 10,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun rankRecommendations_honorsLimit() {
        val profile =
            RecommendationEngine.buildProfile(listOf(scene("seed", playCount = 1, tagIds = listOf("a"))))
        val candidates = (1..5).map { scene("c$it", rating = it, tagIds = listOf("a")) }

        val result =
            RecommendationEngine.rankRecommendations(candidates, profile, emptySet(), limit = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun rankRecommendations_isDeterministic_forTiedScenes() {
        val profile =
            RecommendationEngine.buildProfile(listOf(scene("seed", playCount = 1, tagIds = listOf("a"))))
        // All identical affinity + rating; tie-break must be stable ascending id.
        val candidates =
            listOf(
                scene("c3", rating = 50, tagIds = listOf("a")),
                scene("c1", rating = 50, tagIds = listOf("a")),
                scene("c2", rating = 50, tagIds = listOf("a")),
            )

        val result = RecommendationEngine.rankRecommendations(candidates, profile, emptySet(), 10)

        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    private fun scene(
        id: String,
        rating: Int? = null,
        playCount: Int? = null,
        tagIds: List<String> = emptyList(),
        performerIds: List<String> = emptyList(),
    ): SlimSceneData =
        SlimSceneData(
            id = id,
            title = "Scene $id",
            code = null,
            details = null,
            director = null,
            urls = emptyList(),
            date = null,
            rating100 = rating,
            play_count = playCount,
            play_duration = null,
            o_counter = null,
            organized = false,
            resume_time = null,
            created_at = null,
            updated_at = null,
            files = emptyList(),
            paths =
                SlimSceneData.Paths(
                    screenshot = null,
                    preview = null,
                    stream = null,
                    sprite = null,
                    caption = null,
                ),
            scene_markers = emptyList(),
            galleries = emptyList(),
            studio = null,
            groups = emptyList(),
            tags =
                tagIds.map {
                    SlimSceneData.Tag(__typename = "Tag", slimTagData = SlimTagData(id = it, name = "tag-$it"))
                },
            performers =
                performerIds.map {
                    SlimSceneData.Performer(id = it, name = "performer-$it")
                },
        )
}
