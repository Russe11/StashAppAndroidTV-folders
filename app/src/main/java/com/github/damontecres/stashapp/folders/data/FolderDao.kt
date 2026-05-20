package com.github.damontecres.stashapp.folders.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data-access for the Folders destination.
 *
 * Three tables are involved:
 *  - `folder_scenes`  — cached scenes, indexed by file path
 *  - `folders`        — materialised directory tree
 *  - `folder_sync_state` — per-server delta-sync bookkeeping
 *
 * All queries are scoped by `serverUrl` so multiple Stash servers can share the cache
 * without cross-contamination.
 */
@Dao
interface FolderDao {
    @Upsert
    suspend fun upsertScenes(scenes: List<FolderScene>)

    @Upsert
    suspend fun upsertFolders(folders: List<FolderNode>)

    @Upsert
    suspend fun upsertSyncState(state: FolderSyncState)

    /**
     * Wipe everything for a single server. Used by "Force resync" so the next sync
     * starts from scratch.
     */
    @Query("DELETE FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun deleteScenesForServer(serverUrl: String)

    @Query("DELETE FROM folders WHERE serverUrl = :serverUrl")
    suspend fun deleteFoldersForServer(serverUrl: String)

    @Query("DELETE FROM folder_sync_state WHERE serverUrl = :serverUrl")
    suspend fun deleteSyncStateForServer(serverUrl: String)

    /**
     * Convenience wrapper that clears all three tables for a server in one call.
     * Marked `@Transaction` so a partial wipe can't leave the cache in a half-cleared
     * state if anything throws midway.
     */
    @androidx.room.Transaction
    suspend fun clearForServer(serverUrl: String) {
        deleteScenesForServer(serverUrl)
        deleteFoldersForServer(serverUrl)
        deleteSyncStateForServer(serverUrl)
    }

    /**
     * Children of [parentPath] (canonical, with trailing slash), sorted by name
     * case-insensitively so the Folders browser shows a stable order regardless of
     * filesystem casing.
     */
    @Query(
        "SELECT * FROM folders " +
            "WHERE serverUrl = :serverUrl AND parentPath = :parentPath " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    fun observeChildren(
        serverUrl: String,
        parentPath: String,
    ): Flow<List<FolderNode>>

    /**
     * Scenes anywhere under [pathPrefix] (canonical, trailing slash). Pass the path
     * itself as the prefix to get both that folder's direct scenes and its
     * descendants' scenes.
     *
     * When [tagIdFilter] is null or blank, no tag filtering is applied. When given,
     * we match against the JSON tag-id array with a cheap `LIKE`; this is the
     * documented v1 trade-off (no scene_tags join table) per the Folders plan.
     */
    @Query(
        "SELECT * FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl " +
            "AND (path LIKE :pathPrefix || '%') " +
            "AND (:tagIdFilter IS NULL OR :tagIdFilter = '' " +
            "     OR tagIdsJson LIKE '%\"' || :tagIdFilter || '\"%') " +
            "ORDER BY path COLLATE NOCASE ASC",
    )
    fun observeScenesIn(
        serverUrl: String,
        pathPrefix: String,
        tagIdFilter: String?,
    ): PagingSource<Int, FolderScene>

    @Query("SELECT * FROM folder_sync_state WHERE serverUrl = :serverUrl LIMIT 1")
    suspend fun getSyncState(serverUrl: String): FolderSyncState?

    @Query("SELECT MAX(updatedAtEpochMs) FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun maxUpdatedAt(serverUrl: String): Long?

    @Query("SELECT COUNT(*) FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun countScenes(serverUrl: String): Int

    /**
     * Every scene's `parentPath` for a server, as a flat list (one entry per scene; a
     * folder with N scenes appears N times). Used by [com.github.damontecres.stashapp.folders.sync.LibraryIndexer]
     * after a scan to recompute folder counts in a single pass.
     *
     * Scoped by `serverUrl` so a multi-server cache stays isolated. Returns the raw column
     * — callers are responsible for any deduplication/aggregation.
     */
    @Query("SELECT parentPath FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun allParentPathsForServer(serverUrl: String): List<String>
}
