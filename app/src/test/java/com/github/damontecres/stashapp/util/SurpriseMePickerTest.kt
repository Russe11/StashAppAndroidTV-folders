package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurpriseMePickerTest {
    @Test
    fun pick_emptyCandidates_returnsNull() {
        assertNull(SurpriseMePicker.pick(emptyList()))
    }

    @Test
    fun pick_defaultsToHead_ofServerShuffledList() {
        // The server already shuffled the list; the picker just takes the head by default.
        val candidates = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals("a", SurpriseMePicker.pick(candidates)!!.id)
    }

    @Test
    fun pick_honorsExplicitIndex() {
        val candidates = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals("b", SurpriseMePicker.pick(candidates, index = 1)!!.id)
        assertEquals("c", SurpriseMePicker.pick(candidates, index = 2)!!.id)
    }

    @Test
    fun pick_wrapsOutOfRangeAndNegativeIndices() {
        // A caller-supplied RNG result (or stale index) is wrapped into range, never crashes.
        val candidates = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals("a", SurpriseMePicker.pick(candidates, index = 3)!!.id)
        assertEquals("b", SurpriseMePicker.pick(candidates, index = 4)!!.id)
        assertEquals("c", SurpriseMePicker.pick(candidates, index = -1)!!.id)
    }

    @Test
    fun pick_prefersUnwatched_whenAvailable() {
        // Head is watched; bias skips it and lands on the first unwatched scene.
        val candidates =
            listOf(
                scene("watched", playCount = 3),
                scene("fresh", playCount = 0),
                scene("alsoFresh", playCount = null),
            )
        assertEquals("fresh", SurpriseMePicker.pick(candidates)!!.id)
    }

    @Test
    fun pick_preferUnwatched_fallsBackWhenEverythingWatched() {
        // A fully-watched library must still surface something (never dead-end).
        val candidates =
            listOf(
                scene("a", playCount = 1),
                scene("b", playCount = 5),
            )
        assertEquals("a", SurpriseMePicker.pick(candidates)!!.id)
    }

    @Test
    fun pick_preferUnwatchedFalse_keepsServerOrderIncludingWatched() {
        val candidates =
            listOf(
                scene("watched", playCount = 3),
                scene("fresh", playCount = 0),
            )
        assertEquals(
            "watched",
            SurpriseMePicker.pick(candidates, preferUnwatched = false)!!.id,
        )
    }

    @Test
    fun pick_indexAppliesToFilteredPool_notRawCandidates() {
        // With prefer-unwatched, the index addresses the *filtered* pool, so index 1 picks the
        // second unwatched scene, skipping the watched ones entirely.
        val candidates =
            listOf(
                scene("w1", playCount = 2),
                scene("u1", playCount = 0),
                scene("w2", playCount = 9),
                scene("u2", playCount = 0),
            )
        assertEquals("u2", SurpriseMePicker.pick(candidates, index = 1)!!.id)
    }

    private fun scene(
        id: String,
        playCount: Int? = null,
    ): SlimSceneData =
        SlimSceneData(
            id = id,
            title = "Scene $id",
            code = null,
            details = null,
            director = null,
            urls = emptyList(),
            date = null,
            rating100 = null,
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
            tags = emptyList(),
            performers = emptyList(),
        )
}
