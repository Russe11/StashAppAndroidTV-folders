package com.github.damontecres.stashapp.util.realtime

import android.util.Log
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.SubscriptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Live-refresh over the NG `entityChanged` subscription (design §8 R1).
 *
 * Subscribes to the server's metadata-only change feed, coalesces bursts with
 * [EntityChangeDebouncer], and on each quiet window applies the change to the local cache
 * ([LiveRefreshActions]) and republishes a [LiveRefreshSignal] on [refreshSignals] so visible
 * lists can invalidate. The whole feature is **capability-gated**: [start] no-ops unless the
 * server advertises `entityChanged`, so on an older NG server the app silently keeps using its
 * existing poll/delta-sync path (graceful degrade — invariant #1).
 *
 * Resilience: the subscription is wrapped in a reconnect loop with exponential
 * [ReconnectBackoff]; a WS drop or transient error waits, then re-subscribes (and the next
 * delta sync reconciles anything missed while disconnected). Cancelling the scope (or [stop])
 * tears everything down cleanly.
 *
 * This class is the entry point the later presence/control phase (deviceBus) builds on — it owns
 * the single live WS subscription lifecycle for a server and the reconnect/backoff machinery.
 *
 * Most collaborators are injected so the orchestration is unit-testable without Apollo/Room:
 * [subscribe] yields the raw event flow, [actions] receives coalesced signals, and [delayFn]
 * drives the backoff clock.
 */
class LiveRefreshRepository(
    private val server: StashServer,
    private val actions: LiveRefreshActions,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val capabilityGate: () -> Boolean = { server.serverPreferences.capabilities.supportsEntityChanged },
    private val subscribe: () -> Flow<EntityChange> = { SubscriptionEngine(server).entityChanges() },
    private val debouncer: EntityChangeDebouncer = EntityChangeDebouncer(),
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val _refreshSignals =
        MutableSharedFlow<LiveRefreshSignal>(replay = 0, extraBufferCapacity = 16)

    /**
     * The stream of coalesced refresh signals, after cache effects have been applied. UI list
     * holders collect this to invalidate/refetch the on-screen page for the changed
     * [com.github.damontecres.stashapp.data.DataType]s. Hot + shared; late subscribers see only
     * future signals.
     */
    val refreshSignals: SharedFlow<LiveRefreshSignal> = _refreshSignals.asSharedFlow()

    @Volatile
    private var job: Job? = null

    /**
     * Start the live-refresh loop. Idempotent: a second call while running is a no-op. No-ops
     * entirely (and logs why) when the server doesn't advertise the `entityChanged` capability,
     * leaving the existing poll/sync path in charge.
     */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        if (!capabilityGate()) {
            Log.i(TAG, "entityChanged capability absent for ${server.url}; live-refresh disabled (poll/sync remains)")
            return
        }
        job = scope.launch { runLoop() }
    }

    /** Stop the loop and drop the live subscription. Safe to call when not started. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * The reconnect loop. Runs until its coroutine is cancelled: (re)subscribe, feed events
     * through the debouncer, apply + republish each coalesced signal; on a normal completion or
     * an error, back off and reconnect. [reset][ReconnectBackoff.reset]s the backoff once a
     * connection has actually delivered at least one event (proof the subscription is healthy)
     * so a flapping connection still backs off but a long-lived one starts fresh after a drop.
     *
     * Internal (not private) so the unit test can drive it directly with fakes.
     */
    internal suspend fun runLoop() {
        // Loop until *this* coroutine is cancelled (via stop() cancelling the job, or its scope
        // being torn down). Using the running coroutine's own context — not the launching scope —
        // makes the loop directly drivable in a test without coupling to scope lifecycle.
        while (currentCoroutineContext().isActive) {
            try {
                // Reset the backoff as soon as an event proves the subscription is live, so a
                // long-lived connection that later drops restarts from the base delay while a
                // connection that flaps before delivering anything keeps backing off.
                debouncer.debounce(tapHealthy(subscribe()) { backoff.reset() }).collect { signal ->
                    handleSignal(signal)
                }
                // Upstream completed without error (server closed the WS): fall through to backoff.
                Log.d(TAG, "entityChanged stream completed for ${server.url}; will reconnect")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "entityChanged stream error for ${server.url}: ${t.message}", t)
            }
            if (!currentCoroutineContext().isActive) break
            val wait = backoff.nextDelayMillis()
            Log.d(TAG, "reconnecting entityChanged for ${server.url} in ${wait}ms (attempt ${backoff.attemptCount})")
            delayFn(wait)
        }
    }

    private suspend fun handleSignal(signal: LiveRefreshSignal) {
        if (signal.isEmpty) return
        Log.i(TAG, "live-refresh signal for ${server.url}: types=${signal.changedTypes} deleted=${signal.deletedSceneIds.size}")
        actions.apply(signal)
        _refreshSignals.emit(signal)
    }

    /** Wrap [source] so the first item runs [onFirst] (proof the WS is delivering events). */
    private fun tapHealthy(
        source: Flow<EntityChange>,
        onFirst: () -> Unit,
    ): Flow<EntityChange> =
        kotlinx.coroutines.flow.flow {
            var first = true
            source.collect {
                if (first) {
                    first = false
                    onFirst()
                }
                emit(it)
            }
        }

    companion object {
        private const val TAG = "LiveRefreshRepository"

        /**
         * Build the production repository for [server] wiring the real Apollo subscription and
         * Room-cache effects. Caller owns [start]/[stop] (and the [scope] lifecycle if it passes
         * one). The default scope is a fresh IO supervisor.
         */
        fun create(
            server: StashServer,
            dao: FolderDao,
        ): LiveRefreshRepository =
            LiveRefreshRepository(
                server = server,
                actions = DefaultLiveRefreshActions(server, dao),
            )
    }
}
