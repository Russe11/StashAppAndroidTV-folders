package com.github.damontecres.stashapp.folders.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI-facing view of [com.github.damontecres.stashapp.folders.sync.LibraryIndexer]'s
 * progress. The indexer module owns the canonical [SyncProgress] type; we mirror
 * the cases the UI cares about here so the UI compiles even before that module
 * lands.
 */
sealed interface SyncProgressUiState {
    data object Idle : SyncProgressUiState

    data class Running(
        val done: Int,
        val total: Int,
    ) : SyncProgressUiState

    data object Completed : SyncProgressUiState

    data class Failed(
        val message: String?,
    ) : SyncProgressUiState
}

/**
 * Indirection so the Folders UI doesn't hard-import the parallel-developed sync
 * module. The sync agent registers a real provider on app startup; until it does,
 * the UI sees [SyncProgressUiState.Idle] and the "Force resync" button is a no-op.
 *
 * Wiring expectation (lives in `folders/sync/LibraryIndexer.kt`):
 * ```
 * LibraryIndexerBridge.register(
 *     observe = { server -> indexer.progress.map { it.toUiState() } },
 *     forceResync = { server -> indexer.forceResync(server) },
 * )
 * ```
 */
object LibraryIndexerBridge {
    private val idleFlow = MutableStateFlow<SyncProgressUiState>(SyncProgressUiState.Idle).asStateFlow()

    @Volatile
    private var observer: ((String) -> Flow<SyncProgressUiState>)? = null

    @Volatile
    private var resyncer: (suspend (String) -> Unit)? = null

    fun register(
        observe: (String) -> Flow<SyncProgressUiState>,
        forceResync: suspend (String) -> Unit,
    ) {
        observer = observe
        resyncer = forceResync
    }

    fun observe(serverUrl: String): Flow<SyncProgressUiState> = observer?.invoke(serverUrl) ?: idleFlow

    suspend fun forceResync(serverUrl: String) {
        resyncer?.invoke(serverUrl)
    }
}
