package com.github.damontecres.stashapp.util.realtime

import android.util.Log
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.folders.sync.LibraryIndexerHost
import com.github.damontecres.stashapp.util.StashServer

/**
 * The side-effects a [LiveRefreshSignal] triggers, behind an interface so the
 * [LiveRefreshRepository] orchestration loop (subscribe → debounce → apply → reconnect) can be
 * unit-tested with a fake that just records what it was asked to do — no Room, no Apollo.
 */
interface LiveRefreshActions {
    /**
     * Apply one coalesced refresh: prune any destroyed scene ids from the local cache and kick
     * a delta sync to pick up creates/updates. Runs off the main thread (the repository calls it
     * from an IO context).
     */
    suspend fun apply(signal: LiveRefreshSignal)
}

/**
 * Production [LiveRefreshActions]: prunes destroyed scenes straight from the Room folder cache
 * (the ids come for free in the `entityChanged` payload, so no `deletedSince` round-trip is
 * needed) and asks [LibraryIndexerHost] to run a delta sync for creates/updates, which refetches
 * the new/edited scenes and rebuilds folder counts.
 */
class DefaultLiveRefreshActions(
    private val server: StashServer,
    private val dao: FolderDao,
) : LiveRefreshActions {
    override suspend fun apply(signal: LiveRefreshSignal) {
        if (signal.deletedSceneIds.isNotEmpty()) {
            signal.deletedSceneIds
                .toList()
                .chunked(SQLITE_PARAM_LIMIT)
                .forEach { chunk -> dao.deleteScenesByIds(server.url, chunk) }
            Log.i(TAG, "live-refresh pruned ${signal.deletedSceneIds.size} scene(s) for ${server.url}")
        }
        // Any non-delete change (or a create/update for any cached type) means the cache may be
        // stale: run a delta sync. It is internally idempotent and gated, and the host skips it
        // when one is already running.
        LibraryIndexerHost.requestDeltaSync(server)
    }

    companion object {
        private const val TAG = "LiveRefreshActions"

        // SQLite caps host parameters at 999 per statement; chunk id lists below this (mirrors
        // LibraryIndexer.SQLITE_PARAM_LIMIT).
        private const val SQLITE_PARAM_LIMIT = 900
    }
}
