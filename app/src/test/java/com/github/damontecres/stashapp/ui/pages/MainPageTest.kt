package com.github.damontecres.stashapp.ui.pages

import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.VideoFile
import com.github.damontecres.stashapp.folders.data.NewItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageTest {
    @Test
    fun isEffectivelyFinished_keepsPartiallyWatched_dropsFinishedOrUnstarted() {
        // No resume position -> not in Continue Watching at all.
        assertFalse(isEffectivelyFinished(scene(resume = null, duration = 100.0)))
        // Early in the scene -> keep.
        assertFalse(isEffectivelyFinished(scene(resume = 10.0, duration = 100.0)))
        // Halfway -> keep.
        assertFalse(isEffectivelyFinished(scene(resume = 50.0, duration = 100.0)))
        // Past 95% -> finished, drop.
        assertTrue(isEffectivelyFinished(scene(resume = 96.0, duration = 100.0)))
        // Within the 30s end-margin on a long scene (3600s) -> finished even though < 95%.
        assertTrue(isEffectivelyFinished(scene(resume = 3580.0, duration = 3600.0)))
        // A resume of 0 is treated as finished/unstarted noise.
        assertTrue(isEffectivelyFinished(scene(resume = 0.0, duration = 100.0)))
    }

    @Test
    fun isEffectivelyFinished_unknownDuration_keepsScene() {
        // Can't decide "finished" without a duration, so a resumed scene stays visible.
        assertFalse(isEffectivelyFinished(scene(resume = 42.0, duration = null)))
    }

    @Test
    fun homeNewestVideos_filtersFoldersAndKeepsNewestFeedOrder() {
        val rows =
            listOf(
                newItemRow(itemType = NewItemRow.TYPE_FOLDER, itemId = "/Movies/", path = "/Movies/", title = "Movies"),
                newItemRow(itemType = NewItemRow.TYPE_SCENE, itemId = "scene-new", path = "/Movies/new.mp4", title = "Newest", thumbnailSceneId = "scene-new"),
                newItemRow(itemType = NewItemRow.TYPE_SCENE, itemId = "scene-old", path = "/Movies/old.mp4", title = "", thumbnailSceneId = "scene-old"),
            )

        val scenes = rows.toHomeNewestScenes()

        assertEquals(listOf("scene-new", "scene-old"), scenes.map { it.id })
        assertEquals("Newest", scenes[0].title)
        assertEquals("old", scenes[1].title)
        // Screenshot is rebuilt at render time from serverUrl + sceneId + updated_at (whole
        // seconds), not read from a stored absolute URL.
        assertEquals(
            "https://stash.example.test/scene/scene-new/screenshot?t=0",
            scenes[0].paths.screenshot,
        )
        assertEquals(
            "https://stash.example.test/scene/scene-old/screenshot?t=0",
            scenes[1].paths.screenshot,
        )
    }

    private fun scene(
        resume: Double?,
        duration: Double?,
    ): SlimSceneData =
        SlimSceneData(
            id = "scene",
            title = "Scene",
            code = null,
            details = null,
            director = null,
            urls = emptyList(),
            date = null,
            rating100 = null,
            play_count = null,
            play_duration = null,
            o_counter = null,
            organized = false,
            resume_time = resume,
            created_at = null,
            updated_at = null,
            files =
                if (duration != null) {
                    listOf(
                        SlimSceneData.File(
                            __typename = "VideoFile",
                            videoFile =
                                VideoFile(
                                    id = "file",
                                    path = "/scene.mp4",
                                    size = 0,
                                    mod_time = 0,
                                    duration = duration,
                                    video_codec = "h264",
                                    audio_codec = "aac",
                                    format = "mp4",
                                    width = 1920,
                                    height = 1080,
                                    frame_rate = 30.0,
                                    bit_rate = 1000,
                                    __typename = "VideoFile",
                                ),
                        ),
                    )
                } else {
                    emptyList()
                },
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

    private fun newItemRow(
        itemType: String,
        itemId: String,
        path: String,
        title: String?,
        thumbnailSceneId: String? = null,
    ): NewItemRow =
        NewItemRow(
            serverUrl = "https://stash.example.test",
            itemType = itemType,
            itemId = itemId,
            path = path,
            parentPath = "/Movies/",
            title = title,
            thumbnailSceneId = thumbnailSceneId,
            updatedAtEpochMs = 100,
            directCount = 0,
        )
}
