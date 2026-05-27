package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable

/**
 * One row in the global New feed. Rows are intentionally mixed: folders use
 * the newest direct scene in that folder, scenes use their own update time.
 */
@Immutable
data class NewItemRow(
    val serverUrl: String,
    val itemType: String,
    val itemId: String,
    val path: String,
    val parentPath: String,
    val title: String?,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val updatedAtEpochMs: Long,
    val directCount: Int,
) {
    val isFolder: Boolean
        get() = itemType == TYPE_FOLDER

    val isScene: Boolean
        get() = itemType == TYPE_SCENE

    companion object {
        const val TYPE_FOLDER = "folder"
        const val TYPE_SCENE = "scene"
    }
}
