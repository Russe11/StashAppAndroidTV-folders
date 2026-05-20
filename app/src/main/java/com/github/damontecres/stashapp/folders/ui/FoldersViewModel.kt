package com.github.damontecres.stashapp.folders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the column-stack + selected-folder state for the Folders destination.
 *
 * The column stack is the breadcrumb of "currently focused folder" at each depth:
 * stack[0] is the root listing (parentPath = "/"), stack[1] = children of the folder
 * focused inside stack[0], and so on. The right-pane scene grid renders scenes
 * recursively under `selectedPath` (the deepest folder the user has activated).
 *
 * State is intentionally not persisted across process death; the UI is cheap enough
 * to rebuild from scratch on cold start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel : ViewModel() {
    private val dao = StashApplication.getDatabase().folderDao()

    private val _serverUrl = MutableStateFlow("")

    /**
     * Stack of parent paths for each visible column. stack[0] is always the root.
     * Stack length is always >= 1.
     */
    private val _columnStack = MutableStateFlow(listOf(ROOT_PARENT))
    val columnStack: StateFlow<List<String>> = _columnStack.asStateFlow()

    /**
     * The folder whose scenes are showing in the right pane. Null until the user
     * activates a folder (D-pad center). Empty/blank means "show every scene under
     * the server", but for v1 we leave the grid empty until something is picked.
     */
    private val _selectedPath = MutableStateFlow<String?>(null)
    val selectedPath: StateFlow<String?> = _selectedPath.asStateFlow()

    fun bindServer(serverUrl: String) {
        if (_serverUrl.value != serverUrl) {
            _serverUrl.value = serverUrl
            // New server: reset navigation so we don't carry over stale paths.
            _columnStack.value = listOf(ROOT_PARENT)
            _selectedPath.value = null
        }
    }

    /**
     * Observe the list of children for a given column (by parent path).
     *
     * Must be reactive to [_serverUrl] — the first composition runs before
     * [bindServer] fires from `LaunchedEffect`, so a one-shot snapshot would
     * latch onto a blank server URL and never recover.
     */
    fun observeColumn(parentPath: String): Flow<List<FolderNode>> =
        _serverUrl
            .flatMapLatest { server ->
                if (server.isBlank()) flowOf(emptyList()) else dao.observeChildren(server, parentPath)
            }.distinctUntilChanged()

    /**
     * Drill into [folder]: push a new column showing its children. No-op if the
     * folder is already the parent of the rightmost column (the user pressed Right
     * twice without moving focus).
     */
    fun pushColumn(folder: FolderNode) {
        val current = _columnStack.value
        if (current.lastOrNull() == folder.path) return
        _columnStack.value = current + folder.path
    }

    /**
     * Pop the rightmost column. No-op if only the root is left — the host shell
     * handles "back from root" by navigating out of the destination entirely.
     */
    fun popColumn(): Boolean {
        val current = _columnStack.value
        if (current.size <= 1) return false
        _columnStack.value = current.dropLast(1)
        return true
    }

    /** Activate [folder]: scenes under this path now drive the right pane. */
    fun selectFolder(folder: FolderNode) {
        _selectedPath.value = folder.path
    }

    /**
     * Paged scenes recursively under [selectedPath]. Re-keyed by both the server
     * URL and the path so flipping between folders restarts the page stream.
     */
    val scenesFlow: Flow<PagingData<FolderScene>> =
        _serverUrl
            .flatMapLatest { server ->
                _selectedPath.flatMapLatest { path ->
                    if (server.isBlank() || path.isNullOrBlank()) {
                        flowOf(PagingData.empty())
                    } else {
                        Pager(
                            config =
                                PagingConfig(
                                    pageSize = PAGE_SIZE,
                                    enablePlaceholders = false,
                                ),
                        ) {
                            dao.observeScenesIn(server, path, tagIdFilter = null)
                        }.flow
                    }
                }
            }.cachedIn(viewModelScope)

    /**
     * Sync progress, kept hot so the top-bar chip reacts immediately. Reads from
     * [com.github.damontecres.stashapp.folders.sync.LibraryIndexer]'s singleton when
     * available; falls back to `Idle` while the indexer module isn't loaded so the
     * UI still renders on cold-start.
     */
    val syncProgress: StateFlow<SyncProgressUiState> =
        _serverUrl
            .flatMapLatest { server ->
                if (server.isBlank()) {
                    flowOf(SyncProgressUiState.Idle)
                } else {
                    LibraryIndexerBridge.observe(server)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SyncProgressUiState.Idle,
            )

    companion object {
        const val ROOT_PARENT = "/"
        private const val PAGE_SIZE = 60
    }
}
