package com.github.damontecres.stashapp.folders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.folders.data.NewItemRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class NewViewModel : ViewModel() {
    private val dao = StashApplication.getDatabase().folderDao()
    private val serverUrl = MutableStateFlow("")
    private var retainedState = NewPageRetainedState()
    private val browseHistory = MutableStateFlow(retainedState.browseHistory)
    val currentFolderPath: StateFlow<String?> =
        browseHistory
            .map { it.currentFolderPath }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    fun bindServer(serverUrl: String) {
        if (this.serverUrl.value != serverUrl) {
            this.serverUrl.value = serverUrl
            retainedState = retainedStatesByServer[serverUrl] ?: NewPageRetainedState()
            browseHistory.value = retainedState.browseHistory
        }
    }

    fun enterFolder(path: String) {
        retainedState = retainedState.enterFolder(path)
        browseHistory.value = retainedState.browseHistory
        persistRetainedState()
    }

    fun goBackInFolder(): Boolean {
        val result = retainedState.goBack()
        retainedState = result.state
        browseHistory.value = retainedState.browseHistory
        persistRetainedState()
        return result.moved
    }

    fun rememberFocus(
        browseKey: String,
        focusedRowIndex: Int,
    ) {
        retainedState = retainedState.rememberFocus(browseKey, focusedRowIndex)
        persistRetainedState()
    }

    fun restoreFocus(browseKey: String): Int = retainedState.restoreFocus(browseKey)

    private fun persistRetainedState() {
        val server = serverUrl.value
        if (server.isNotBlank()) {
            retainedStatesByServer[server] = retainedState
        }
    }

    val itemsFlow: Flow<PagingData<NewItemRow>> =
        serverUrl
            .flatMapLatest { server ->
                browseHistory.flatMapLatest { history ->
                    if (server.isBlank()) {
                        flowOf(PagingData.empty())
                    } else {
                        Pager(
                            config = NEW_FEED_PAGING_CONFIG,
                        ) {
                            val folderPath = history.currentFolderPath
                            if (folderPath == null) {
                                dao.pagingNewestItems(server)
                            } else {
                                dao.pagingNewFolderItems(serverUrl = server, parentPath = folderPath)
                            }
                        }.flow
                    }
                }
            }.cachedIn(viewModelScope)

    companion object {
        private val retainedStatesByServer = mutableMapOf<String, NewPageRetainedState>()
    }
}

internal const val NEW_GLOBAL_BROWSE_KEY = "__new_global__"

internal data class NewPageRetainedState(
    val browseHistory: NewBrowseHistory = NewBrowseHistory(),
    val focusedRowsByBrowseKey: Map<String, Int> = mapOf(NEW_GLOBAL_BROWSE_KEY to 0),
) {
    val currentFolderPath: String?
        get() = browseHistory.currentFolderPath

    fun enterFolder(path: String): NewPageRetainedState = copy(browseHistory = browseHistory.enterFolder(path))

    fun goBack(): NewPageRetainedBackResult {
        val result = browseHistory.goBack()
        return NewPageRetainedBackResult(
            state = copy(browseHistory = result.history),
            moved = result.moved,
        )
    }

    fun rememberFocus(
        browseKey: String,
        focusedRowIndex: Int,
    ): NewPageRetainedState =
        copy(
            focusedRowsByBrowseKey =
                focusedRowsByBrowseKey + (browseKey to focusedRowIndex.coerceAtLeast(0)),
        )

    fun restoreFocus(browseKey: String): Int = focusedRowsByBrowseKey[browseKey]?.coerceAtLeast(0) ?: 0
}

internal data class NewPageRetainedBackResult(
    val state: NewPageRetainedState,
    val moved: Boolean,
)

internal data class NewBrowseHistory(
    val folderStack: List<String> = emptyList(),
) {
    val currentFolderPath: String?
        get() = folderStack.lastOrNull()

    fun enterFolder(path: String): NewBrowseHistory = copy(folderStack = folderStack + path)

    fun goBack(): NewBrowseBackResult =
        if (folderStack.isEmpty()) {
            NewBrowseBackResult(history = this, moved = false)
        } else {
            NewBrowseBackResult(history = copy(folderStack = folderStack.dropLast(1)), moved = true)
        }
}

internal data class NewBrowseBackResult(
    val history: NewBrowseHistory,
    val moved: Boolean,
)

internal val NEW_FEED_PAGING_CONFIG =
    PagingConfig(
        pageSize = 60,
        initialLoadSize = 60,
        prefetchDistance = 10,
        enablePlaceholders = false,
    )

internal fun clampNewFeedFocus(
    focusedIndex: Int,
    itemCount: Int,
): Int = if (itemCount <= 0) 0 else focusedIndex.coerceIn(0, itemCount - 1)
