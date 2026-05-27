package com.github.damontecres.stashapp.folders.sync

import android.util.Log
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.api.CountScenesQuery
import com.github.damontecres.stashapp.api.FindScenesQuery
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.type.CriterionModifier
import com.github.damontecres.stashapp.api.type.FindFilterType
import com.github.damontecres.stashapp.api.type.SceneFilterType
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.api.type.TimestampCriterionInput
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import com.github.damontecres.stashapp.folders.data.FolderSyncState
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Background service that scans a Stash library and populates the local Room cache so the
 * Folders destination can browse by directory tree.
 *
 * Lifecycle:
 *  - First run on a server: full scan, paginated by `updated_at ASC` so partial progress is
 *    recoverable (`FolderSyncState.scanProgressDone` is the resume cursor).
 *  - Subsequent runs: delta query (`updated_at > maxUpdatedAt`) just picks up new/edited
 *    scenes. The composite PK on `folder_scenes` (`serverUrl`, `sceneId`) makes the @Upsert
 *    idempotent across re-runs.
 *  - "Force resync": [forceResync] wipes the cache for this server and runs a fresh full
 *    scan.
 *
 * The indexer is single-server per instance — pass a fresh `LibraryIndexer` when the user
 * switches servers. The caller is responsible for cancelling/awaiting a sync before
 * starting one against a different server (see "scan starts at the same time as a server
 * switch" in the docs/plans).
 */
class LibraryIndexer(
    private val server: StashServer,
    private val dao: FolderDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Bookkeeping snapshot the UI can subscribe to so it can show a sync chip.
     *
     * `done` / `total` are scene counts; `total` is `null` while we are still waiting on
     * the page-zero count call.
     */
    sealed class SyncProgress {
        data object Idle : SyncProgress()

        data class Running(val done: Int, val total: Int?) : SyncProgress()

        data class Failed(val reason: String) : SyncProgress()

        data object Completed : SyncProgress()
    }

    sealed class SyncResult {
        data object Completed : SyncResult()

        data class Failed(val reason: String) : SyncResult()
    }

    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    /**
     * Run a delta sync (or resume a full scan if one has not completed yet).
     *
     * Reads `FolderSyncState` to decide:
     *  - no state yet, or `scanComplete = false`: continue/start a full scan from the page
     *    cursor implied by `scanProgressDone`.
     *  - `scanComplete = true`: query only scenes with `updated_at >= maxUpdatedAt` (a small
     *    one-millisecond overlap is harmless thanks to @Upsert idempotency).
     */
    suspend fun runDeltaSync(): SyncResult =
        withContext(ioDispatcher) {
            try {
                val state = dao.getSyncState(server.url)
                if (state == null || !state.scanComplete) {
                    val startPage = ((state?.scanProgressDone ?: 0) / PAGE_SIZE) + 1
                    fullScan(startPage = startPage)
                } else {
                    deltaScan(maxUpdatedAtEpochMs = state.lastSyncAtEpochMs ?: dao.maxUpdatedAt(server.url) ?: 0L)
                }
            } catch (t: Throwable) {
                val reason = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "Sync failed for ${server.url}: $reason", t)
                _progress.value = SyncProgress.Failed(reason)
                SyncResult.Failed(reason)
            }
        }

    /**
     * Wipe the cache for this server and re-run a full scan.
     */
    suspend fun forceResync(): SyncResult =
        withContext(ioDispatcher) {
            try {
                dao.clearForServer(server.url)
            } catch (t: Throwable) {
                val reason = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "Force resync clear failed for ${server.url}: $reason", t)
                _progress.value = SyncProgress.Failed(reason)
                return@withContext SyncResult.Failed(reason)
            }
            runDeltaSync()
        }

    // -- Internals ------------------------------------------------------------------------

    private suspend fun fullScan(startPage: Int): SyncResult {
        // Up-front total via the dedicated CountScenesQuery. FindScenes.graphql in this
        // codebase intentionally does NOT select `count`, so we run the cheap count query
        // once before paginating. If it fails we fall back to a null total — the chip
        // simply hides the denominator until the scan completes.
        val total = runCatching { fetchTotalCount() }.getOrNull()
        _progress.value = SyncProgress.Running(done = (startPage - 1) * PAGE_SIZE, total = total)

        var page = startPage.coerceAtLeast(1)
        var totalDone = (page - 1) * PAGE_SIZE
        var maxUpdatedSeen = 0L
        var seenAny = false

        while (true) {
            val scenes = fetchPage(page = page, sinceEpochMs = null)
            if (scenes.isEmpty()) break

            val (folderScenes, latestEpoch) = persistBatch(scenes)
            seenAny = seenAny || folderScenes.isNotEmpty()
            if (latestEpoch > maxUpdatedSeen) maxUpdatedSeen = latestEpoch

            totalDone += scenes.size

            // Progress checkpoint: persist scanProgressDone after every page so a crash
            // mid-scan resumes correctly.
            dao.upsertSyncState(
                FolderSyncState(
                    serverUrl = server.url,
                    lastSyncAtEpochMs = null,
                    scanComplete = false,
                    scanProgressTotal = total,
                    scanProgressDone = totalDone,
                ),
            )
            _progress.value = SyncProgress.Running(done = totalDone, total = total)

            if (scenes.size < PAGE_SIZE) break
            page += 1
        }

        // Recompute every folder's counts now that all scenes are persisted. For a full
        // scan this is the correct moment because we know nothing else is being added.
        rebuildAllFolderCounts()

        dao.upsertSyncState(
            FolderSyncState(
                serverUrl = server.url,
                lastSyncAtEpochMs = if (seenAny) maxUpdatedSeen else System.currentTimeMillis(),
                scanComplete = true,
                scanProgressTotal = totalDone,
                scanProgressDone = totalDone,
            ),
        )
        _progress.value = SyncProgress.Completed
        return SyncResult.Completed
    }

    private suspend fun deltaScan(maxUpdatedAtEpochMs: Long): SyncResult {
        _progress.value = SyncProgress.Running(done = 0, total = null)

        var page = 1
        var totalDone = 0
        var maxUpdatedSeen = maxUpdatedAtEpochMs

        while (true) {
            val scenes = fetchPage(page = page, sinceEpochMs = maxUpdatedAtEpochMs)
            if (scenes.isEmpty()) break

            val (_, latestEpoch) = persistBatch(scenes)
            if (latestEpoch > maxUpdatedSeen) maxUpdatedSeen = latestEpoch
            totalDone += scenes.size

            _progress.value = SyncProgress.Running(done = totalDone, total = null)
            // No mid-delta checkpoint: delta scans are short, and the next run can simply
            // re-issue the same filter — the @Upsert keeps it idempotent.

            if (scenes.size < PAGE_SIZE) break
            page += 1
        }

        // Approximation: recompute counts for *all* folders rather than diffing affected
        // ancestors. A delta is small, and a full re-aggregate against `folder_scenes`
        // is correct-by-construction and avoids the bookkeeping needed to handle moves
        // (a scene whose `parentPath` changed must decrement its old ancestors). Worth
        // revisiting if libraries grow large enough that the re-aggregate becomes a
        // bottleneck, but for the v1 sizes we expect this is cheap.
        rebuildAllFolderCounts()

        dao.upsertSyncState(
            FolderSyncState(
                serverUrl = server.url,
                lastSyncAtEpochMs = maxUpdatedSeen,
                scanComplete = true,
                scanProgressTotal = (dao.countScenes(server.url)).coerceAtLeast(0),
                scanProgressDone = totalDone,
            ),
        )
        _progress.value = SyncProgress.Completed
        return SyncResult.Completed
    }

    /**
     * Fetch a single page of [SlimSceneData] from the server.
     *
     * The find filter sorts by `updated_at ASC` so partial progress is recoverable: if we
     * crash on page N, page N still re-fetches the same window on resume. `sceneFilter`
     * adds the delta cut-off when present.
     */
    private suspend fun fetchPage(
        page: Int,
        sinceEpochMs: Long?,
    ): List<SlimSceneData> {
        val findFilter =
            FindFilterType(
                per_page = Optional.present(PAGE_SIZE),
                page = Optional.present(page),
                sort = Optional.present(SORT_FIELD),
                direction = Optional.present(SortDirectionEnum.ASC),
            )
        val sceneFilter =
            if (sinceEpochMs != null && sinceEpochMs > 0L) {
                SceneFilterType(
                    updated_at =
                        Optional.present(
                            TimestampCriterionInput(
                                value = formatEpochAsIso(sinceEpochMs),
                                modifier = CriterionModifier.GREATER_THAN,
                            ),
                        ),
                )
            } else {
                null
            }
        val query =
            FindScenesQuery(
                filter = findFilter,
                scene_filter = sceneFilter,
                ids = null,
            )
        val response = server.apolloClient.query(query).execute()
        if (response.hasErrors()) {
            val message = response.errors?.joinToString("; ") { it.message } ?: "GraphQL error"
            throw IllegalStateException(message)
        }
        val data = response.data ?: return emptyList()
        return data.findScenes.scenes.map { it.slimSceneData }
    }

    /**
     * One-shot count of every scene on this server. Issued at the start of a full scan so
     * the progress chip can show a denominator.
     */
    private suspend fun fetchTotalCount(): Int {
        val response = server.apolloClient.query(CountScenesQuery(filter = null, scene_filter = null, ids = null)).execute()
        if (response.hasErrors()) {
            val message = response.errors?.joinToString("; ") { it.message } ?: "GraphQL error"
            throw IllegalStateException(message)
        }
        return response.data?.findScenes?.count ?: 0
    }

    /**
     * Map [SlimSceneData] → [FolderScene] and persist. Returns the persisted rows and the
     * max `updatedAtEpochMs` in this batch so the caller can advance the delta cursor.
     *
     * Folder hierarchy is *not* maintained here — counts are recomputed in
     * [rebuildAllFolderCounts] once a scan finishes.
     */
    private suspend fun persistBatch(scenes: List<SlimSceneData>): Pair<List<FolderScene>, Long> {
        if (scenes.isEmpty()) return emptyList<FolderScene>() to 0L

        var maxUpdated = 0L
        val rows =
            scenes.mapNotNull { scene ->
                val rawPath = scene.files.firstOrNull()?.videoFile?.path ?: return@mapNotNull null
                val normalizedPath = normalizeFilePath(rawPath)
                if (normalizedPath.isBlank()) return@mapNotNull null
                val parentPath = parentPathOf(normalizedPath)
                val updatedEpoch = parseUpdatedAtEpochMs(scene.updated_at)
                if (updatedEpoch > maxUpdated) maxUpdated = updatedEpoch

                val tagIds = scene.tags.map { it.slimTagData.id }

                FolderScene(
                    serverUrl = server.url,
                    sceneId = scene.id,
                    path = normalizedPath,
                    parentPath = parentPath,
                    title = scene.title,
                    durationSeconds = scene.files.firstOrNull()?.videoFile?.duration,
                    rating100 = scene.rating100,
                    organized = scene.organized,
                    screenshotUrl = scene.paths.screenshot,
                    previewUrl = scene.paths.preview,
                    tagIdsJson = tagIdsToJson(tagIds),
                    updatedAtEpochMs = updatedEpoch,
                )
            }

        if (rows.isNotEmpty()) {
            dao.upsertScenes(rows)
        }
        return rows to maxUpdated
    }

    /**
     * Recompute every folder's `directCount` / `recursiveCount` from scratch by walking
     * `folder_scenes` once and aggregating into the folder tree.
     *
     * Done after every scan (full and delta — see comment in [deltaScan]).
     */
    private suspend fun rebuildAllFolderCounts() {
        val directCounts = HashMap<String, Int>()
        val recursiveCounts = HashMap<String, Int>()
        val thumbnailCandidates = HashMap<String, ThumbnailCandidate>()

        // Read every cached scene once, then aggregate counts and thumbnail candidates
        // in memory. This moves the expensive recursive thumbnail lookup out of the
        // folder-browsing query path and into the background sync path.
        val allScenes = collectScenes()

        for (scene in allScenes) {
            val parent = scene.parentPath
            directCounts.merge(parent, 1) { a, b -> a + b }
            // Walk ancestors. parent always ends in "/". Root is "/".
            var ancestor = parent
            val candidate = ThumbnailCandidate.from(scene)
            while (true) {
                recursiveCounts.merge(ancestor, 1) { a, b -> a + b }
                if (candidate != null) {
                    val current = thumbnailCandidates[ancestor]
                    if (current == null || candidate.isBetterThan(current)) {
                        thumbnailCandidates[ancestor] = candidate
                    }
                }
                if (ancestor == "/") break
                ancestor = parentFolderOf(ancestor)
            }
        }

        // Materialise every folder we touched.
        val allFolderPaths = (directCounts.keys + recursiveCounts.keys).toMutableSet()
        // Ensure ancestors of every directCount folder are present even if they have a
        // recursiveCount of 0 (shouldn't happen, but defensive).
        for (p in directCounts.keys.toList()) {
            var ancestor = p
            while (ancestor != "/") {
                ancestor = parentFolderOf(ancestor)
                allFolderPaths += ancestor
            }
        }

        val rows =
            allFolderPaths.map { path ->
                FolderNode(
                    serverUrl = server.url,
                    path = path,
                    name = folderName(path),
                    parentPath = if (path == "/") "" else parentFolderOf(path),
                    recursiveCount = recursiveCounts[path] ?: 0,
                    directCount = directCounts[path] ?: 0,
                    thumbnailUrl = thumbnailCandidates[path]?.url,
                )
            }
        // Drop the old folder rows for this server before upserting fresh ones so
        // directories that lost all their scenes don't linger with stale counts. The
        // delete + upsert pair is *not* atomic, but a transient empty folder table is
        // harmless — the UI's `observeChildren` flow will re-emit once the upsert lands.
        // For a strictly atomic rebuild we'd add an @Transaction method on FolderDao, but
        // that's a follow-up.
        dao.deleteFoldersForServer(server.url)
        if (rows.isNotEmpty()) {
            dao.upsertFolders(rows)
        }
    }

    private suspend fun collectScenes(): List<FolderScene> = dao.allScenesForServer(server.url)

    // -- Path helpers ---------------------------------------------------------------------
    //
    // Thin instance-method shims so existing call sites stay readable. The
    // real logic lives in the companion object so it can be unit-tested
    // without standing up the rest of the indexer.

    internal fun normalizeFilePath(raw: String): String = Companion.normalizeFilePath(raw)

    internal fun parentPathOf(normalizedPath: String): String = Companion.parentPathOf(normalizedPath)

    internal fun parentFolderOf(folderPath: String): String = Companion.parentFolderOf(folderPath)

    internal fun folderName(folderPath: String): String = Companion.folderName(folderPath)

    // -- Time helpers ---------------------------------------------------------------------

    private fun parseUpdatedAtEpochMs(updatedAt: Any?): Long {
        val raw = updatedAt?.toString() ?: return 0L
        if (raw.isBlank()) return 0L
        return try {
            // Stash's updated_at is an ISO 8601 string with offset (e.g.
            // "2025-01-01T12:00:00Z" or "...-04:00"). Match what the rest of the app does
            // in views/Formatting.kt: parse as ZonedDateTime, then convert to epoch ms.
            ZonedDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatEpochAsIso(epochMs: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC),
        )

    // -- JSON helpers ---------------------------------------------------------------------

    private fun tagIdsToJson(ids: List<String>): String {
        if (ids.isEmpty()) return "[]"
        return ids.joinToString(prefix = "[", postfix = "]", separator = ",") { id ->
            "\"${id.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
    }

    companion object {
        private const val TAG = "LibraryIndexer"
        private const val PAGE_SIZE = 1000
        private const val SORT_FIELD = "updated_at"
        private val SLASH_RUN_REGEX = Regex("/+")

        /**
         * Normalize a Stash-reported file path: backslashes → forward slashes,
         * collapse runs of `/` to a single `/`, strip surrounding whitespace,
         * and **ensure a leading slash** so the canonical absolute path
         * (e.g. `/mnt/movies/Foo/scene.mp4`) is returned regardless of whether
         * the source string included one.
         *
         * The leading-slash guarantee is what keeps the stored file [path]
         * and the canonical [parentPathOf] output (always `/`-prefixed) in
         * lockstep. Stash returns some paths
         * without a leading `/` (e.g. `prv/docs/foo.mp4`), and if we faithfully
         * stored those they'd never match a prefix derived from the folders
         * table — which is how the bug manifested before this fix.
         */
        internal fun normalizeFilePath(raw: String): String {
            if (raw.isBlank()) return ""
            val swapped = raw.trim().replace('\\', '/')
            // Collapse repeated slashes (preserving a single leading `/` if present).
            val collapsed = swapped.replace(SLASH_RUN_REGEX, "/")
            if (collapsed.isEmpty()) return ""
            return if (collapsed.startsWith("/")) collapsed else "/$collapsed"
        }

        /**
         * Derive the canonical parent folder of a normalized file path. Always
         * trailing-slash terminated; root is `"/"`.
         */
        internal fun parentPathOf(normalizedPath: String): String {
            if (normalizedPath.isBlank()) return "/"
            val lastSlash = normalizedPath.lastIndexOf('/')
            if (lastSlash < 0) return "/"
            if (lastSlash == 0) return "/" // path was "/foo"
            val parent = normalizedPath.substring(0, lastSlash)
            // Ensure leading + trailing slash. `parent` already has a leading
            // `/` if the input did (which it always should after
            // `normalizeFilePath`); we keep the prepend as belt-and-braces.
            val withLeading = if (parent.startsWith("/")) parent else "/$parent"
            return "$withLeading/"
        }

        /**
         * Parent folder of a folder. `parentFolderOf("/a/b/")` is `"/a/"`,
         * `parentFolderOf("/a/")` is `"/"`, and `parentFolderOf("/")` is `"/"`.
         */
        internal fun parentFolderOf(folderPath: String): String {
            if (folderPath == "/" || folderPath.isBlank()) return "/"
            val trimmed = folderPath.trimEnd('/')
            val lastSlash = trimmed.lastIndexOf('/')
            if (lastSlash <= 0) return "/"
            return trimmed.substring(0, lastSlash) + "/"
        }

        /**
         * Display name of a folder. Root is `""`.
         */
        internal fun folderName(folderPath: String): String {
            if (folderPath == "/" || folderPath.isBlank()) return ""
            val trimmed = folderPath.trimEnd('/')
            val lastSlash = trimmed.lastIndexOf('/')
            return if (lastSlash < 0) trimmed else trimmed.substring(lastSlash + 1)
        }

        internal fun representativeThumbnailFor(
            folderPath: String,
            scenes: List<FolderScene>,
        ): String? {
            var best: ThumbnailCandidate? = null
            for (scene in scenes) {
                if (!scene.path.startsWith(folderPath)) continue
                val candidate = ThumbnailCandidate.from(scene) ?: continue
                if (best == null || candidate.isBetterThan(best)) {
                    best = candidate
                }
            }
            return best?.url
        }
    }

    private data class ThumbnailCandidate(
        val url: String,
        val organized: Boolean,
        val numericSceneId: Long,
    ) {
        fun isBetterThan(other: ThumbnailCandidate): Boolean =
            when {
                organized != other.organized -> organized
                numericSceneId != other.numericSceneId -> numericSceneId < other.numericSceneId
                else -> false
            }

        companion object {
            fun from(scene: FolderScene): ThumbnailCandidate? {
                val url = scene.screenshotUrl?.takeIf { it.isNotBlank() } ?: return null
                return ThumbnailCandidate(
                    url = url,
                    organized = scene.organized,
                    numericSceneId = scene.sceneId.toLongOrNull() ?: 0L,
                )
            }
        }
    }
}
