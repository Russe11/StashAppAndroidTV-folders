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
                newItemRow(itemType = NewItemRow.TYPE_SCENE, itemId = "scene-new", path = "/Movies/new.mp4", title = "Newest", thumbnailUrl = "new.jpg"),
                newItemRow(itemType = NewItemRow.TYPE_SCENE, itemId = "scene-old", path = "/Movies/old.mp4", title = "", thumbnailUrl = "old.jpg"),
            )

        val scenes = rows.toHomeNewestScenes()

        assertEquals(listOf("scene-new", "scene-old"), scenes.map { it.id })
        assertEquals("Newest", scenes[0].title)
        assertEquals("old", scenes[1].title)
        assertEquals("new.jpg", scenes[0].paths.screenshot)
        assertEquals("old.jpg", scenes[1].paths.screenshot)
    }

    private fun newItemRow(
        itemType: String,
        itemId: String,
        path: String,
        title: String?,
        thumbnailUrl: String? = null,
    ): NewItemRow =
        NewItemRow(
            serverUrl = "https://stash.example.test",
            itemType = itemType,
            itemId = itemId,
            path = path,
            parentPath = "/Movies/",
            title = title,
            thumbnailUrl = thumbnailUrl,
            previewUrl = "preview.mp4",
            updatedAtEpochMs = 100,
            directCount = 0,
        )
}
