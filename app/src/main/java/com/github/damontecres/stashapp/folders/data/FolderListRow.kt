package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/**
 * A row in the left-pane subfolder list: the folder itself plus a representative
 * thumbnail chosen from the scenes recursively under it. The representative scene id is
 * materialised into [FolderNode] during sync, then depth-gated by the query (the query nulls
 * out `thumbnailSceneId` when thumbnails are disabled for the current depth) so the top of
 * very wide trees stays cheap to browse.
 *
 * [thumbnailUrl] is rebuilt at render time against the current server root by [SceneUrlBuilder]
 * (so it survives a server move). It is null when no scene under the folder has a screenshot,
 * when the folder is empty, or when thumbnails are disabled for the current depth.
 */
@Immutable
data class FolderListRow(
    @Embedded val node: FolderNode,
    val childFolderCount: Int = 0,
) {
    val thumbnailUrl: String?
        get() =
            node.thumbnailSceneId?.let {
                SceneUrlBuilder.screenshotUrl(node.serverUrl, it, node.thumbnailUpdatedAtEpochMs)
            }
}
