package com.github.damontecres.stashapp.ui.pages

import com.github.damontecres.stashapp.folders.data.NewItemRow
import org.junit.Assert.assertEquals
import org.junit.Test

class MainPageTest {
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
