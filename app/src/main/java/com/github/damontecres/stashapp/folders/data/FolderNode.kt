package com.github.damontecres.stashapp.folders.data

import androidx.room.Entity
import androidx.room.Index

/**
 * A directory node in the Folders tree, derived from the set of [FolderScene.parentPath]s
 * during sync. We materialise the tree as rows (rather than computing it on demand from
 * scenes) so the Folders browser can page through children cheaply and show
 * recursive/direct counts without scanning the whole scene table.
 *
 * `path` is canonical: leading and trailing slash (e.g. `/Movies/Foo/`). The root folder
 * has `path = "/"`. `parentPath` is the canonical parent (e.g. `/Movies/`) or empty string
 * for the synthetic root.
 *
 * `recursiveCount` counts scenes anywhere under this folder; `directCount` counts only
 * scenes whose `parentPath` exactly equals this `path`.
 */
@Entity(
    tableName = "folders",
    primaryKeys = ["serverUrl", "path"],
    indices = [
        Index(value = ["parentPath"]),
        Index(value = ["serverUrl", "parentPath"]),
    ],
)
data class FolderNode(
    val serverUrl: String,
    val path: String,
    val name: String,
    val parentPath: String,
    val recursiveCount: Int,
    val directCount: Int,
)
