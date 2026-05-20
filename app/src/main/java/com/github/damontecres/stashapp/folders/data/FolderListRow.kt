package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/**
 * A row in the left-pane subfolder list: the folder itself plus a representative
 * thumbnail URL chosen from the scenes recursively under it. The thumbnail is
 * computed at query time by [FolderDao.observeChildren] so adding it does not
 * require a schema migration or indexer changes.
 *
 * `thumbnailUrl` is null when no scene under the folder has a `screenshotUrl`,
 * or when the folder is empty.
 */
@Immutable
data class FolderListRow(
    @Embedded val node: FolderNode,
    val thumbnailUrl: String?,
)
