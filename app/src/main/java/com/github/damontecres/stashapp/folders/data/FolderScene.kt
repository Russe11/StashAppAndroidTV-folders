package com.github.damontecres.stashapp.folders.data

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A cached Stash scene, indexed by file path so the Folders destination can browse
 * by directory structure rather than by Studio/Tag/etc.
 *
 * `parentPath` is the canonical directory containing the scene's file, with a leading
 * and trailing slash (e.g. `/Movies/Foo/`). It is derived during sync from `files[0].path`
 * by trimming the basename and ensuring exactly one leading + trailing slash.
 *
 * `tagIdsJson` stores the scene's tag IDs as a JSON array of strings (e.g. `["1","42"]`).
 * The folder DAO's tag filter does a cheap `LIKE '%' || tagId || '%'` match; this is a
 * deliberate v1 trade-off (no separate scene_tags join table) per the Folders plan.
 *
 * `serverUrl` is part of every row so multiple Stash servers can coexist in the cache
 * without cross-contamination.
 */
@Entity(
    tableName = "folder_scenes",
    // Composite key: a single sceneId is unique within one server, not across
    // servers. Two servers can both have scene "42" and they must not overwrite
    // each other.
    primaryKeys = ["serverUrl", "sceneId"],
    indices = [
        Index(value = ["serverUrl", "parentPath"]),
        // Speeds up server-scoped path scans such as the sync rebuild's
        // `WHERE serverUrl = ? ORDER BY path` query shape.
        Index(value = ["serverUrl", "path"]),
        // The New feed sorts scenes by recency within a server; this lets the
        // scene arm of the feed's UNION walk the index instead of sorting the
        // whole scene table.
        Index(value = ["serverUrl", "updatedAtEpochMs"]),
    ],
)
// @Immutable lets Compose skip recomposition of any composable that takes a
// FolderScene as a parameter when the *reference* is unchanged. We mutate via
// data-class `copy`, never in place, so the contract holds.
@Immutable
data class FolderScene(
    val serverUrl: String,
    val sceneId: String,
    val path: String,
    val parentPath: String,
    val title: String?,
    val durationSeconds: Double?,
    val rating100: Int?,
    val organized: Boolean,
    val screenshotUrl: String?,
    val previewUrl: String?,
    @ColumnInfo(defaultValue = "[]")
    val tagIdsJson: String = "[]",
    val updatedAtEpochMs: Long,
)
