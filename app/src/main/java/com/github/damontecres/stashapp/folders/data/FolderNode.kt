package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
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
 *
 * The representative thumbnail is stored as the **scene id** (+ its `updated_at`), not an
 * absolute URL: `thumbnailSceneId` / `thumbnailUpdatedAtEpochMs` identify the scene whose
 * screenshot represents this folder, and the URL is rebuilt at render time by
 * [SceneUrlBuilder] against the current server root (so it survives a server move and never
 * leaks another server's origin). Materialising the *id* during sync still avoids running one
 * recursive scene lookup per visible child row.
 *
 * `newestDirectUpdatedAtEpochMs` / `newestDirectSceneId` likewise identify the newest scene
 * directly inside this folder for the New feed, with its thumbnail rebuilt at render time.
 */
@Entity(
    tableName = "folders",
    primaryKeys = ["serverUrl", "path"],
    indices = [
        Index(value = ["serverUrl", "parentPath"]),
        // The left-pane query orders by `name COLLATE NOCASE`. A composite
        // index on (serverUrl, name) lets SQLite walk the index in order
        // instead of building a transient sort table per folder change.
        Index(value = ["serverUrl", "name"]),
    ],
)
// See FolderScene for the @Immutable rationale.
@Immutable
data class FolderNode(
    val serverUrl: String,
    val path: String,
    val name: String,
    val parentPath: String,
    val recursiveCount: Int,
    val directCount: Int,
    // Representative thumbnail scene (id + its updated_at), rebuilt to a URL at render time.
    val thumbnailSceneId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val thumbnailUpdatedAtEpochMs: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val newestDirectUpdatedAtEpochMs: Long = 0,
    // Newest-direct scene id (its updated_at is newestDirectUpdatedAtEpochMs), rebuilt at render.
    val newestDirectSceneId: String? = null,
)
