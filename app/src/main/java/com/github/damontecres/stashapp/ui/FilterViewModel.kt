package com.github.damontecres.stashapp.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Query
import com.github.damontecres.stashapp.api.fragment.StashData
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.suppliers.DataSupplierFactory
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.suppliers.StashPagingSource
import com.github.damontecres.stashapp.util.AlphabetSearchUtils
import com.github.damontecres.stashapp.util.ComposePager
import com.github.damontecres.stashapp.util.LoggingCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.realtime.LiveRefreshHost
import com.github.damontecres.stashapp.util.realtime.LiveRefreshListGlue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FilterViewModel : ViewModel() {
    private var server: StashServer? = null
    val pager = MutableLiveData<ComposePager<StashData>>()

    val currentFilter: FilterArgs? get() = pager.value?.filter
    val dataType: DataType? get() = currentFilter?.dataType

    private var job: Job? = null

    // The columns last used to build the pager, so a live-refresh rebuild matches the on-screen grid.
    private var columns: Int = 1

    init {
        // R1 live-refresh UI invalidation: when the server reports this list's DataType changed
        // (entityChanged WS), rebuild the pager so the grid reflects the change. Lifecycle-safe —
        // collected in viewModelScope, so it's cancelled when the ViewModel is cleared (no leak).
        // On servers that don't advertise entityChanged the host's flow simply never emits.
        LiveRefreshListGlue.collectInto(
            scope = viewModelScope,
            signals = LiveRefreshHost.refreshSignals,
            currentDataType = { dataType },
            invalidate = { reload() },
        )
    }

    fun setFilter(
        server: StashServer,
        filterArgs: FilterArgs,
        columns: Int,
    ) {
        if (pager.value?.filter != filterArgs || server != this.server) {
            job?.cancel()
            Log.d("FilterPageViewModel", "filterArgs=$filterArgs, columns=$columns")
            this.server = server
            this.columns = columns
            val dataSupplierFactory = DataSupplierFactory(server.version)
            val dataSupplier =
                dataSupplierFactory.create<Query.Data, StashData, Query.Data>(filterArgs)
            val pagingSource =
                StashPagingSource(QueryEngine(server), dataSupplier) { _, _, item -> item }
            val pager =
                ComposePager(filterArgs, pagingSource, viewModelScope, pageSize = columns * 10)
            job =
                viewModelScope.launch(LoggingCoroutineExceptionHandler(server, viewModelScope)) {
                    pager.init()
                    this@FilterViewModel.pager.value = pager
                }
        }
    }

    /**
     * Rebuild the pager for the current server+filter, re-fetching from the server. Used by
     * live-refresh: [setFilter] short-circuits when the filter is unchanged, so this builds a fresh
     * [ComposePager] directly (a fresh pager re-runs the count + page queries, surfacing the change).
     * No-ops until a filter has been set.
     */
    private fun reload() {
        val server = this.server ?: return
        val filterArgs = pager.value?.filter ?: return
        job?.cancel()
        Log.d("FilterPageViewModel", "live-refresh reload filterArgs=$filterArgs")
        val dataSupplierFactory = DataSupplierFactory(server.version)
        val dataSupplier =
            dataSupplierFactory.create<Query.Data, StashData, Query.Data>(filterArgs)
        val pagingSource =
            StashPagingSource(QueryEngine(server), dataSupplier) { _, _, item -> item }
        val pager =
            ComposePager(filterArgs, pagingSource, viewModelScope, pageSize = columns * 10)
        job =
            viewModelScope.launch(LoggingCoroutineExceptionHandler(server, viewModelScope)) {
                pager.init()
                this@FilterViewModel.pager.value = pager
            }
    }

    suspend fun findLetterPosition(letter: Char): Int {
        val server = this.server!!
        val filter = this.pager.value!!.filter

        val dataSupplierFactory = DataSupplierFactory(server.version)
        val letterPosition =
            AlphabetSearchUtils.findPosition(
                letter,
                filter,
                QueryEngine(server),
                dataSupplierFactory,
            )
        val jumpPosition =
            if (filter.sortAndDirection.direction == SortDirectionEnum.DESC) {
                // Reverse if sorting descending
                pager.value!!.size - letterPosition - 1
            } else {
                letterPosition
            }
        return jumpPosition
    }
}
