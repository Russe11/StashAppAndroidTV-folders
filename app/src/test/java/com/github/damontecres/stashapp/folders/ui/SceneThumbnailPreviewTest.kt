package com.github.damontecres.stashapp.folders.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneThumbnailPreviewTest {
    @Test
    fun shouldPlaySceneThumbnailPreview_requiresSelectionPreferenceAndPreviewUrl() {
        assertTrue(
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = true,
                playVideoPreviews = true,
                previewUrl = "https://example.test/preview.mp4",
            ),
        )

        assertFalse(
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = false,
                playVideoPreviews = true,
                previewUrl = "https://example.test/preview.mp4",
            ),
        )
        assertFalse(
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = true,
                playVideoPreviews = false,
                previewUrl = "https://example.test/preview.mp4",
            ),
        )
        assertFalse(
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = true,
                playVideoPreviews = true,
                previewUrl = " ",
            ),
        )
        assertFalse(
            shouldPlaySceneThumbnailPreview(
                selectedAfterDelay = true,
                playVideoPreviews = true,
                previewUrl = null,
            ),
        )
    }
}
