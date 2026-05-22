package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/**
 * A row in the left-pane subfolder list: the folder itself plus a representative
 * thumbnail URL chosen from the scenes recursively under it. The thumbnail is
 * materialised into [FolderNode] during sync, then depth-gated by the query so
 * the top of very wide trees can stay cheap to browse.
 *
 * `thumbnailUrl` is null when no scene under the folder has a `screenshotUrl`,
 * when the folder is empty, or when thumbnails are disabled for the current depth.
 */
@Immutable
data class FolderListRow(
    @Embedded val node: FolderNode,
) {
    val thumbnailUrl: String?
        get() = node.thumbnailUrl
}
