package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable

/**
 * One row in the global New feed. Rows are intentionally mixed: folders use
 * the newest direct scene in that folder, scenes use their own update time.
 *
 * The thumbnail/preview are identified by [thumbnailSceneId] (+ [updatedAtEpochMs] as the
 * cache-buster), not by a stored absolute URL: the URL is rebuilt at render time by
 * [SceneUrlBuilder] against the current server root so it survives a server move and never
 * leaks another server's origin. For scene rows [thumbnailSceneId] is the scene itself; for
 * folder rows it is the folder's newest-direct scene.
 */
@Immutable
data class NewItemRow(
    val serverUrl: String,
    val itemType: String,
    val itemId: String,
    val path: String,
    val parentPath: String,
    val title: String?,
    val thumbnailSceneId: String?,
    val updatedAtEpochMs: Long,
    val directCount: Int,
) {
    val isFolder: Boolean
        get() = itemType == TYPE_FOLDER

    val isScene: Boolean
        get() = itemType == TYPE_SCENE

    /** Screenshot URL rebuilt against the current server root, or null when no scene backs it. */
    val thumbnailUrl: String?
        get() = thumbnailSceneId?.let { SceneUrlBuilder.screenshotUrl(serverUrl, it, updatedAtEpochMs) }

    /** Animated preview URL — scene rows only; folder rows never auto-play a preview. */
    val previewUrl: String?
        get() = if (isScene) thumbnailSceneId?.let { SceneUrlBuilder.previewUrl(serverUrl, it) } else null

    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_SCENE = "scene"
    }
}
