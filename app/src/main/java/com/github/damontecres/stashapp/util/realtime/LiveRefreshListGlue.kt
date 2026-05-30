package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Glue between [LiveRefreshHost.refreshSignals] and a UI list ViewModel (R1 UI invalidation,
 * design §8). A list shows exactly one [DataType]; when a live-refresh signal reports that type as
 * changed, the list must re-fetch (invalidate its pager) so the on-screen content reflects the
 * server change. Everything destructive/IO lives in the caller's [invalidate] lambda — this object
 * only decides *whether* to fire and owns the lifecycle-safe collection, so it's unit-testable
 * headless with a plain flow + a fake invalidate callback.
 */
object LiveRefreshListGlue {
    /**
     * Should a list showing [dataType] refresh in response to [signal]? True iff the signal reports
     * that data type as changed in this window. Pure — no I/O, no clock.
     */
    fun shouldRefresh(
        signal: LiveRefreshSignal,
        dataType: DataType,
    ): Boolean = signal.changedTypes.contains(dataType)

    /**
     * Collect [signals] on [scope] and call [invalidate] once per signal that touches the list's
     * current [DataType]. Returns the collection [Job] so the caller can cancel early if needed; it
     * is also tied to [scope], so a `viewModelScope` collection is torn down automatically when the
     * ViewModel is cleared (no leak). [currentDataType] is read per-signal so a list that swaps
     * filters/types keeps matching against its *current* type, and a not-yet-initialized list
     * (null type) never invalidates.
     */
    fun collectInto(
        scope: CoroutineScope,
        signals: Flow<LiveRefreshSignal>,
        currentDataType: () -> DataType?,
        invalidate: () -> Unit,
    ): Job =
        scope.launch {
            signals.collect { signal ->
                val type = currentDataType() ?: return@collect
                if (shouldRefresh(signal, type)) {
                    invalidate()
                }
            }
        }
}
