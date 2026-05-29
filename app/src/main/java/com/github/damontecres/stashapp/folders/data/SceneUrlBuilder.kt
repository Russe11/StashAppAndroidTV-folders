package com.github.damontecres.stashapp.folders.data

/**
 * Rebuilds scene media URLs (screenshot / preview) at render time from the **stable** parts
 * the Folders cache stores — the scene id and the scene's `updated_at` — plus the **current**
 * server root.
 *
 * Why not cache the absolute URL? Caching `paths.screenshot`/`paths.preview` (which embed the
 * server origin) broke two ways:
 *  - **stale-after-move:** if the server URL changes (or a scene's `updated_at` cache-buster
 *    advances), the cached absolute URL points at the old origin / old cover.
 *  - **multi-server:** the same row could carry another server's origin.
 *
 * Storing only the id + update time and rebuilding here keeps every cached row server-agnostic;
 * the API key is layered on at load time by the Glide/Coil header path, not embedded here.
 *
 * The URL shapes mirror the server's `SceneURLBuilder`
 * (`Stash/internal/api/urlbuilders/scene.go`):
 *  - screenshot: `{root}/scene/{id}/screenshot?t={updatedAtUnixSeconds}`
 *  - preview:    `{root}/scene/{id}/preview`
 */
object SceneUrlBuilder {
    /**
     * Screenshot (still cover) URL, or `null` when [sceneId] is blank. The `?t=` cache-buster
     * is the scene's `updated_at` in **whole seconds** (matching the server's
     * `UpdatedAt.Unix()`), derived from the stored epoch-millis so a cover regenerated on the
     * server invalidates the client cache.
     */
    fun screenshotUrl(
        serverUrl: String,
        sceneId: String,
        updatedAtEpochMs: Long,
    ): String? {
        if (sceneId.isBlank()) return null
        val t = updatedAtEpochMs / 1000L
        return "${root(serverUrl)}/scene/$sceneId/screenshot?t=$t"
    }

    /**
     * Animated preview URL, or `null` when [sceneId] is blank.
     */
    fun previewUrl(
        serverUrl: String,
        sceneId: String,
    ): String? {
        if (sceneId.isBlank()) return null
        return "${root(serverUrl)}/scene/$sceneId/preview"
    }

    /**
     * The server origin with the `/graphql` endpoint stripped and any trailing slash removed,
     * so concatenating `"/scene/..."` never produces a double slash.
     *
     * Pure string handling (no Android `Uri`) so it is unit-testable and matches the server's
     * `BaseURL` (the GraphQL root sans `/graphql`). The stored `serverUrl` is the user-entered
     * server URL — it may or may not include the `/graphql` suffix.
     */
    internal fun root(serverUrl: String): String {
        var root = serverUrl.trim().trimEnd('/')
        if (root.endsWith("/graphql")) {
            root = root.removeSuffix("/graphql").trimEnd('/')
        }
        return root
    }
}
