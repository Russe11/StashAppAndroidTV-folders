package com.github.damontecres.stashapp.folders.sync

import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.folders.ui.LibraryIndexerBridge
import com.github.damontecres.stashapp.folders.ui.SyncProgressUiState
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-server orchestration for [LibraryIndexer]: holds one indexer at a time
 * (re-created on server switch), registers the UI bridge, and offers a
 * `kickOff()` entry point the app calls on launch or when a server is picked.
 *
 * v1 design: only one indexer alive at a time. Switching servers cancels the
 * in-flight sync, replaces the indexer, and starts a fresh delta sync for the
 * new server. Multi-server concurrent sync would be over-engineering for now;
 * the FolderDao schema is server-keyed so it'd be safe to add later.
 *
 * StashApplication.onCreate calls [install] once with a way to look up the
 * FolderDao + current server. Everything else flows from `currentServerChanged`.
 */
object LibraryIndexerHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var daoProvider: (() -> FolderDao)? = null
    private val mutex = Mutex()

    @Volatile
    private var current: Holder? = null

    private val emptyFlow = MutableStateFlow<SyncProgressUiState>(SyncProgressUiState.Idle).asStateFlow()

    private data class Holder(
        val server: StashServer,
        val indexer: LibraryIndexer,
        val runJob: Job? = null,
    )

    /**
     * One-shot setup. Pass a thunk so we don't capture the database before
     * [com.github.damontecres.stashapp.StashApplication.setupDB] has run.
     */
    fun install(daoProvider: () -> FolderDao) {
        this.daoProvider = daoProvider
        LibraryIndexerBridge.register(
            observe = { url -> observeForServer(url) },
            forceResync = { url -> forceResyncForServer(url) },
        )
    }

    /**
     * Switch to syncing [server]. Cancels any in-flight sync against a previous
     * server. Starts a delta sync immediately. Safe to call repeatedly with the
     * same server — duplicate calls are coalesced.
     */
    fun currentServerChanged(server: StashServer) {
        scope.launch {
            mutex.withLock {
                val existing = current
                if (existing?.server?.url == server.url) return@withLock
                existing?.runJob?.cancel()
                val dao = daoProvider?.invoke() ?: return@withLock
                val indexer = LibraryIndexer(server, dao)
                val job = scope.launch { indexer.runDeltaSync() }
                current = Holder(server, indexer, job)
            }
        }
    }

    private fun observeForServer(serverUrl: String): Flow<SyncProgressUiState> {
        val holder = current ?: return emptyFlow
        if (holder.server.url != serverUrl) return emptyFlow
        return holder.indexer.progress.map { it.toUiState() }
    }

    private suspend fun forceResyncForServer(serverUrl: String) {
        val holder = current ?: return
        if (holder.server.url != serverUrl) return
        // Cancel any in-flight delta and run forceResync sequentially so the
        // cache wipe + fresh scan don't race.
        mutex.withLock {
            holder.runJob?.cancel()
            val job = scope.launch { holder.indexer.forceResync() }
            current = holder.copy(runJob = job)
        }
    }

    private fun LibraryIndexer.SyncProgress.toUiState(): SyncProgressUiState =
        when (this) {
            is LibraryIndexer.SyncProgress.Idle -> SyncProgressUiState.Idle
            is LibraryIndexer.SyncProgress.Running ->
                SyncProgressUiState.Running(
                    done = done,
                    total = total ?: 0,
                )
            is LibraryIndexer.SyncProgress.Completed -> SyncProgressUiState.Completed
            is LibraryIndexer.SyncProgress.Failed -> SyncProgressUiState.Failed(reason)
        }
}
