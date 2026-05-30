package com.github.damontecres.stashapp.util.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces a stream of [EntityChange] events into debounced [LiveRefreshSignal]s.
 *
 * The server publishes one `entityChanged` event per affected entity, so a single user action
 * (a bulk tag, a scan finishing) produces a burst. Refreshing per-event would thrash the UI and
 * the Room cache; instead we accumulate events and emit exactly one coalesced signal once the
 * stream has been quiet for [windowMillis]. The coalescing itself ([LiveRefreshSignal.from]) is
 * pure; this class only adds the trailing time-window.
 *
 * The [delayFn] is injectable so tests can drive the window deterministically (advance a fake
 * clock) without real waits and without depending on `kotlinx-coroutines-test`.
 *
 * @param windowMillis quiet period after the last event before a signal is emitted.
 * @param delayFn suspends for the given millis; defaults to [kotlinx.coroutines.delay].
 */
class EntityChangeDebouncer(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    init {
        require(windowMillis > 0) { "windowMillis must be > 0" }
    }

    /**
     * Transform a cold/hot [upstream] of [EntityChange] into a flow of coalesced signals.
     *
     * Trailing-debounce algorithm: the collector appends each event to a shared buffer and bumps
     * a monotonic `seq`; for each event a timer coroutine (in a dedicated [timersScope]) waits
     * [windowMillis] and — if `seq` hasn't advanced since it started waiting — drains the buffer
     * into one [LiveRefreshSignal] and offers it to the downstream channel. The timers run
     * concurrently with collection, so the collector keeps draining upstream *during* the wait
     * and a burst correctly restarts the window. A window that coalesces to nothing (e.g. only
     * GalleryChapter changes) is dropped rather than emitted.
     *
     * When upstream completes, the buffer is flushed once and the timers scope is **cancelled**
     * (rather than awaited): any still-parked timers are now redundant, and awaiting them would
     * hang the flow forever if the window never elapses. The debounce window only delays a
     * trailing flush; the completion flush already covers it.
     *
     * [channelFlow] is used (rather than a plain `flow`) precisely because the signal is
     * produced from child coroutines — `send` on its [kotlinx.coroutines.channels.SendChannel]
     * is concurrency-safe, unlike `FlowCollector.emit`.
     */
    fun debounce(upstream: Flow<EntityChange>): Flow<LiveRefreshSignal> =
        channelFlow {
            val pending = ArrayList<EntityChange>()
            val lock = Mutex()
            var seq = 0L

            // Timers run under a child Job of the channelFlow producer scope so they're tied to
            // its lifecycle (cancelled if the consumer cancels) but can be cancelled independently
            // when upstream completes. `this` here is the ProducerScope.
            val timersScope = CoroutineScope(this.coroutineContext + Job(this.coroutineContext.job))

            try {
                upstream.collect { change ->
                    val capturedSeq =
                        lock.withLock {
                            pending.add(change)
                            ++seq
                        }
                    // Restart the quiet timer for this event. A stale timer (a newer event bumped
                    // seq while it waited) no-ops when it wakes.
                    timersScope.launch {
                        delayFn(windowMillis)
                        val signal =
                            lock.withLock {
                                if (seq != capturedSeq) return@withLock null
                                LiveRefreshSignal.from(pending).also { pending.clear() }
                            }
                        if (signal != null && !signal.isEmpty) send(signal)
                    }
                }

                // Upstream completed: flush whatever's left so nothing is silently dropped.
                val tail =
                    lock.withLock {
                        if (pending.isEmpty()) null else LiveRefreshSignal.from(pending).also { pending.clear() }
                    }
                if (tail != null && !tail.isEmpty) send(tail)
            } finally {
                // Redundant once upstream is done (and required so the flow can actually close):
                // drop any still-parked timers instead of awaiting their windows.
                timersScope.cancel()
            }
        }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 500L
    }
}
