package com.github.damontecres.stashapp.folders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.folders.data.FolderListRow
import com.github.damontecres.stashapp.folders.data.FolderNode
import com.github.damontecres.stashapp.folders.data.FolderScene
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the "currently-visible folder" state for the Folders destination.
 *
 * Per [docs/plans/stash-android-tv-folders-fork.md], the browser shows one folder
 * at a time: the left pane lists that folder's immediate subfolders (with a `..`
 * affordance pinned at the top when not at root); the right pane shows only the
 * scenes directly inside it. Selecting a subfolder *replaces* the pane (no
 * column stack, no tree expansion); Back / `..` goes up one level.
 *
 * State is intentionally not persisted across process death; the UI is cheap
 * enough to rebuild from scratch on cold start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel : ViewModel() {
    private val dao = StashApplication.getDatabase().folderDao()

    private val _serverUrl = MutableStateFlow("")
    private val _videoPanePath = MutableStateFlow(ROOT_PARENT)
    private val _videoSort = MutableStateFlow(FolderVideoSort.Newest)
    private var retainedState = FoldersPageRetainedState()

    /**
     * The folder the user is currently viewing. Always canonical (trailing
     * slash); `/` means the synthetic root. Selecting a subfolder sets this to
     * that subfolder's path; `goUp()` walks one level back toward the root.
     */
    private val _currentPath = MutableStateFlow(ROOT_PARENT)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    fun bindServer(
        serverUrl: String,
        initialPath: String? = null,
    ) {
        if (_serverUrl.value != serverUrl) {
            retainedState = retainedStatesByServer[serverUrl] ?: FoldersPageRetainedState()
            _serverUrl.value = serverUrl
            // New server: reset navigation so we don't carry over stale paths.
            val restoredPath = initialPath ?: retainedState.currentPath
            retainedState = retainedState.withCurrentPath(restoredPath)
            _currentPath.value = retainedState.currentPath
            persistRetainedState()
        } else if (initialPath != null && _currentPath.value != initialPath) {
            retainedState = retainedState.withCurrentPath(initialPath)
            _currentPath.value = retainedState.currentPath
            persistRetainedState()
        }
    }

    fun setVideoPanePath(path: String) {
        if (_videoPanePath.value != path) {
            _videoPanePath.value = path
        }
    }

    fun setVideoSort(sort: FolderVideoSort) {
        if (_videoSort.value != sort) {
            _videoSort.value = sort
        }
    }

    fun rememberFolderFocus(
        path: String,
        focusedRowIndex: Int,
    ) {
        retainedState = retainedState.rememberFolderFocus(path, focusedRowIndex)
        persistRetainedState()
    }

    fun restoreFolderFocus(path: String): Int = retainedState.restoreFolderFocus(path)

    fun rememberVideoFocus(
        path: String,
        focusedRowIndex: Int,
    ) {
        retainedState = retainedState.rememberVideoFocus(path, focusedRowIndex)
        persistRetainedState()
    }

    fun restoreVideoFocus(path: String): Int = retainedState.restoreVideoFocus(path)

    internal fun rememberActivePane(pane: FoldersRetainedPane) {
        retainedState = retainedState.rememberActivePane(pane)
        persistRetainedState()
    }

    internal fun restoreActivePane(): FoldersRetainedPane = retainedState.activePane

    private fun persistRetainedState() {
        val server = _serverUrl.value
        if (server.isNotBlank()) {
            retainedStatesByServer[server] = retainedState
        }
    }

    /**
     * Paged immediate children for the left folder pane. Paging is used at every
     * depth; thumbnail loading is still depth-gated by [showFolderThumbnailsForParentPath].
     */
    val childrenFlow: Flow<PagingData<FolderListRow>> =
        _serverUrl
            .flatMapLatest { server ->
                _currentPath.flatMapLatest { path ->
                    if (server.isBlank()) {
                        flowOf(PagingData.empty())
                    } else {
                        Pager(
                            config =
                                PagingConfig(
                                    pageSize = CHILD_PAGE_SIZE,
                                    prefetchDistance = CHILD_PREFETCH_DISTANCE,
                                    enablePlaceholders = true,
                                ),
                        ) {
                            dao.pagingChildren(
                                serverUrl = server,
                                parentPath = path,
                                includeThumbnails = showFolderThumbnailsForParentPath(path),
                            )
                        }.flow
                    }
                }
            }.cachedIn(viewModelScope)

    /** Enter [folder]: the pane re-renders showing its children. */
    fun enterFolder(folder: FolderNode) {
        retainedState = retainedState.withCurrentPath(folder.path)
        _currentPath.value = retainedState.currentPath
        persistRetainedState()
    }

    /**
     * Go up one level. Returns `true` if the path moved, `false` if we were
     * already at the root — the caller (BackHandler / `..` row) can defer to
     * the host shell to exit the destination in the latter case.
     */
    fun goUp(): Boolean {
        val current = _currentPath.value
        val parent = parentOf(current)
        if (parent == current) return false
        retainedState = retainedState.withCurrentPath(parent)
        _currentPath.value = retainedState.currentPath
        persistRetainedState()
        return true
    }

    val videoScenesFlow: Flow<PagingData<FolderScene>> =
        combine(_serverUrl, _videoPanePath, _videoSort) { server, path, sort ->
            Triple(server, path, sort)
        }.distinctUntilChanged()
            .flatMapLatest { (server, path, sort) ->
                if (server.isBlank()) {
                    flowOf(PagingData.empty())
                } else {
                    Pager(
                        config =
                            PagingConfig(
                                pageSize = PAGE_SIZE,
                                enablePlaceholders = false,
                            ),
                    ) {
                        dao.pagingScenesInFolderSorted(
                            serverUrl = server,
                            parentPath = path,
                            tagIdFilter = null,
                            sort = sort.name,
                        )
                    }.flow
                }
            }.cachedIn(viewModelScope)

    /**
     * Sync progress, kept hot so the top-bar chip reacts immediately. Reads
     * from [com.github.damontecres.stashapp.folders.sync.LibraryIndexer]'s
     * singleton when available; falls back to `Idle` while the indexer module
     * isn't loaded so the UI still renders on cold-start.
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
        private val retainedStatesByServer = mutableMapOf<String, FoldersPageRetainedState>()

        const val ROOT_PARENT = "/"
        private const val PAGE_SIZE = 60
        private const val CHILD_PAGE_SIZE = 80
        private const val CHILD_PREFETCH_DISTANCE = 20

        /**
         * Canonical parent of [path]. Paths look like `/Foo/Bar/` with leading
         * and trailing slashes; the root is `/`. `parentOf("/") == "/"` so this
         * is safe to call unconditionally — the caller checks for the fixed
         * point to detect "already at root".
         */
        internal fun parentOf(path: String): String {
            if (path.isEmpty() || path == ROOT_PARENT) return ROOT_PARENT
            val trimmed = path.trimEnd('/')
            val lastSlash = trimmed.lastIndexOf('/')
            return if (lastSlash <= 0) ROOT_PARENT else trimmed.substring(0, lastSlash + 1)
        }
    }
}

internal fun showFolderThumbnailsForParentPath(parentPath: String): Boolean = folderDepth(parentPath) >= 2

private fun folderDepth(path: String): Int {
    if (path.isBlank() || path == FoldersViewModel.ROOT_PARENT) return 0
    return path.trim('/').split('/').count { it.isNotBlank() }
}
