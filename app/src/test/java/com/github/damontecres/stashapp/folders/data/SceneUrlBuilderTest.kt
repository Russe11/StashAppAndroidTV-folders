package com.github.damontecres.stashapp.folders.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SceneUrlBuilder] — the render-time URL rebuild that replaced caching absolute
 * media URLs (fixes stale-URL-after-move + multi-server breakage).
 *
 * The URL shapes mirror the server's `SceneURLBuilder`
 * (`Stash/internal/api/urlbuilders/scene.go`):
 *  - screenshot: `{root}/scene/{id}/screenshot?t={updatedAtUnixSeconds}`
 *  - preview:    `{root}/scene/{id}/preview`
 */
class SceneUrlBuilderTest {
    @Test
    fun screenshotUrl_buildsServerRootedUrlWithSecondsCacheBuster() {
        // 1_700_000_500_000 ms -> 1_700_000_500 s (whole-second truncation, matching the
        // server's UpdatedAt.Unix()).
        assertEquals(
            "https://stash.example.test/scene/42/screenshot?t=1700000500",
            SceneUrlBuilder.screenshotUrl("https://stash.example.test", "42", 1_700_000_500_999L),
        )
    }

    @Test
    fun previewUrl_buildsServerRootedUrl() {
        assertEquals(
            "https://stash.example.test/scene/42/preview",
            SceneUrlBuilder.previewUrl("https://stash.example.test", "42"),
        )
    }

    @Test
    fun stripsTrailingSlashAndGraphqlSuffix() {
        // The stored serverUrl may carry a trailing slash and/or the /graphql endpoint; the
        // root must drop both so concatenation never doubles a slash or hits /graphql/scene/...
        assertEquals(
            "https://stash.example.test/scene/7/preview",
            SceneUrlBuilder.previewUrl("https://stash.example.test/", "7"),
        )
        assertEquals(
            "https://stash.example.test/scene/7/preview",
            SceneUrlBuilder.previewUrl("https://stash.example.test/graphql", "7"),
        )
        assertEquals(
            "https://stash.example.test/scene/7/preview",
            SceneUrlBuilder.previewUrl("https://stash.example.test/graphql/", "7"),
        )
    }

    @Test
    fun nullForBlankSceneId() {
        assertNull(SceneUrlBuilder.screenshotUrl("https://stash.example.test", "", 0))
        assertNull(SceneUrlBuilder.previewUrl("https://stash.example.test", ""))
    }

    @Test
    fun rebuildsAgainstCurrentRoot_soAServerMoveNeverYieldsAStaleOrigin() {
        // The whole point of storing the id (not the URL): the same scene rebuilds against
        // whatever server root is current, never a baked-in old origin.
        val old = SceneUrlBuilder.screenshotUrl("http://old.local:9999", "5", 1_000)
        val new = SceneUrlBuilder.screenshotUrl("https://new.example.test", "5", 1_000)
        assertEquals("http://old.local:9999/scene/5/screenshot?t=1", old)
        assertEquals("https://new.example.test/scene/5/screenshot?t=1", new)
    }
}
