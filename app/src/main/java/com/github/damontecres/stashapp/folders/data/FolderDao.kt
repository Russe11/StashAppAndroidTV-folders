package com.github.damontecres.stashapp.folders.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

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
     * Paged children for TV navigation. [includeThumbnails] deliberately gates the
     * materialised thumbnail column so the top of very wide folder trees can skip
     * image loading without paying any recursive scene lookup cost while browsing.
     */
    @Query(
        "SELECT f.serverUrl, f.path, f.name, f.parentPath, f.recursiveCount, f.directCount, " +
            "CASE WHEN :includeThumbnails THEN f.thumbnailUrl ELSE NULL END AS thumbnailUrl, " +
            "(SELECT COUNT(*) FROM folders c WHERE c.serverUrl = f.serverUrl AND c.parentPath = f.path) AS childFolderCount " +
            "FROM folders f " +
            "WHERE f.serverUrl = :serverUrl AND f.parentPath = :parentPath " +
            "ORDER BY f.name COLLATE NOCASE ASC",
    )
    fun pagingChildren(
        serverUrl: String,
        parentPath: String,
        includeThumbnails: Boolean,
    ): PagingSource<Int, FolderListRow>

    /**
     * Scenes directly inside [parentPath] (canonical, trailing slash). Subfolder
     * scenes are intentionally excluded from the right-pane video grid; subfolder
     * rows still expose recursive counts/thumbnails as navigation hints.
     *
     * When [tagIdFilter] is null or blank, no tag filtering is applied. When given,
     * we match against the JSON tag-id array with a cheap `LIKE`; this is the
     * documented v1 trade-off (no scene_tags join table) per the Folders plan.
     */
    @Query(
        "SELECT * FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl " +
            "AND parentPath = :parentPath " +
            "AND (:tagIdFilter IS NULL OR :tagIdFilter = '' " +
            "     OR tagIdsJson LIKE '%\"' || :tagIdFilter || '\"%') " +
            "ORDER BY path COLLATE NOCASE ASC",
    )
    fun pagingScenesInFolder(
        serverUrl: String,
        parentPath: String,
        tagIdFilter: String?,
    ): PagingSource<Int, FolderScene>

    @Query(
        "SELECT * FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl " +
            "AND parentPath = :parentPath " +
            "AND (:tagIdFilter IS NULL OR :tagIdFilter = '' " +
            "     OR tagIdsJson LIKE '%\"' || :tagIdFilter || '\"%') " +
            "ORDER BY " +
            "CASE WHEN :sort = 'Longest' THEN durationSeconds END DESC, " +
            "CASE WHEN :sort = 'Newest' THEN updatedAtEpochMs END DESC, " +
            "updatedAtEpochMs DESC, path COLLATE NOCASE ASC",
    )
    fun pagingScenesInFolderSorted(
        serverUrl: String,
        parentPath: String,
        tagIdFilter: String?,
        sort: String,
    ): PagingSource<Int, FolderScene>

    /**
     * Global mixed feed for the New destination. Scene rows use their own
     * `updatedAtEpochMs`; folder rows use the newest scene directly inside the
     * folder, not scenes in subfolders.
     */
    @Query(
        "SELECT serverUrl, 'scene' AS itemType, sceneId AS itemId, path, parentPath, title, " +
            "screenshotUrl AS thumbnailUrl, previewUrl, updatedAtEpochMs, 0 AS directCount " +
            "FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl " +
            "UNION ALL " +
            "SELECT f.serverUrl, 'folder' AS itemType, f.path AS itemId, f.path, f.parentPath, f.name AS title, " +
            "(SELECT s.screenshotUrl FROM folder_scenes s " +
            " WHERE s.serverUrl = f.serverUrl AND s.parentPath = f.path " +
            " ORDER BY s.updatedAtEpochMs DESC, s.path COLLATE NOCASE ASC LIMIT 1) AS thumbnailUrl, " +
            "NULL AS previewUrl, " +
            "(SELECT s.updatedAtEpochMs FROM folder_scenes s " +
            " WHERE s.serverUrl = f.serverUrl AND s.parentPath = f.path " +
            " ORDER BY s.updatedAtEpochMs DESC, s.path COLLATE NOCASE ASC LIMIT 1) AS updatedAtEpochMs, " +
            "f.directCount " +
            "FROM folders f " +
            "WHERE f.serverUrl = :serverUrl AND f.directCount > 0 " +
            "ORDER BY updatedAtEpochMs DESC, itemType ASC, path COLLATE NOCASE ASC",
    )
    fun pagingNewestItems(serverUrl: String): PagingSource<Int, NewItemRow>

    @Query(
        "SELECT serverUrl, 'scene' AS itemType, sceneId AS itemId, path, parentPath, title, " +
            "screenshotUrl AS thumbnailUrl, previewUrl, updatedAtEpochMs, 0 AS directCount " +
            "FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl " +
            "ORDER BY updatedAtEpochMs DESC, path COLLATE NOCASE ASC " +
            "LIMIT :limit",
    )
    suspend fun newestSceneItems(
        serverUrl: String,
        limit: Int,
    ): List<NewItemRow>

    /**
     * Immediate mixed contents for the New page's in-pane folder browser.
     * Child folders are listed first by name, followed by direct scene files by
     * display title/path; nested scene files are only shown after entering their
     * folder.
     */
    @Query(
        "SELECT * FROM (" +
            "SELECT f.serverUrl, 'folder' AS itemType, f.path AS itemId, f.path, f.parentPath, f.name AS title, " +
            "f.thumbnailUrl AS thumbnailUrl, NULL AS previewUrl, " +
            "COALESCE((SELECT MAX(s.updatedAtEpochMs) FROM folder_scenes s " +
            " WHERE s.serverUrl = f.serverUrl AND s.parentPath = f.path), 0) AS updatedAtEpochMs, " +
            "f.directCount " +
            "FROM folders f " +
            "WHERE f.serverUrl = :serverUrl AND f.parentPath = :parentPath " +
            "UNION ALL " +
            "SELECT serverUrl, 'scene' AS itemType, sceneId AS itemId, path, parentPath, title, " +
            "screenshotUrl AS thumbnailUrl, previewUrl, updatedAtEpochMs, 0 AS directCount " +
            "FROM folder_scenes " +
            "WHERE serverUrl = :serverUrl AND parentPath = :parentPath" +
            ") " +
            "ORDER BY itemType ASC, path COLLATE NOCASE ASC",
    )
    fun pagingNewFolderItems(
        serverUrl: String,
        parentPath: String,
    ): PagingSource<Int, NewItemRow>

    @Query("SELECT * FROM folder_sync_state WHERE serverUrl = :serverUrl LIMIT 1")
    suspend fun getSyncState(serverUrl: String): FolderSyncState?

    @Query("SELECT MAX(updatedAtEpochMs) FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun maxUpdatedAt(serverUrl: String): Long?

    @Query("SELECT COUNT(*) FROM folder_scenes WHERE serverUrl = :serverUrl")
    suspend fun countScenes(serverUrl: String): Int

    @Query("SELECT * FROM folder_scenes WHERE serverUrl = :serverUrl ORDER BY path COLLATE NOCASE ASC")
    suspend fun allScenesForServer(serverUrl: String): List<FolderScene>
}
