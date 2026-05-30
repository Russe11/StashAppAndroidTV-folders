package com.github.damontecres.stashapp.util.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The pure presence-heartbeat control loop, extracted from any Apollo/coroutine-scheduler
 * specifics so the register-then-heartbeat-then-unregister logic is unit-testable with fakes.
 *
 * Contract (design §5 + §6): on connect call [tick] once immediately (the initial `registerDevice`,
 * which emits ONLINE), then call it again every [intervalMillis] (~20s) to refresh `lastSeen`
 * before the server's ~45s TTL evicts the device. The loop runs until its coroutine is cancelled
 * (opt-out / background / server switch); a single tick failure is logged-and-survived (transient
 * server blip) rather than tearing the loop down, because the next heartbeat self-heals — but the
 * *very first* registration failure is surfaced (via [onFirstTickFailed]) so the caller can decide
 * to back off and retry the whole connect.
 *
 * Deliberately interval-only (no jitter): a single-user LAN client refreshing one server has no
 * thundering-herd concern, and a fixed interval keeps the tick→`lastSeen` timing testable.
 *
 * @param intervalMillis the heartbeat period (~20s; must be < the server TTL of ~45s).
 * @param tick performs one `registerDevice` heartbeat; returns true on success.
 * @param delayFn suspends for the given millis (injectable so tests drive the clock).
 * @param onFirstTickFailed invoked if the very first (connect) tick fails, before the loop exits.
 */
class HeartbeatLoop(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val tick: suspend () -> Boolean,
    private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val onFirstTickFailed: suspend (Throwable?) -> Unit = {},
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be > 0" }
    }

    /**
     * Run the heartbeat loop: register immediately, then re-register every [intervalMillis] until
     * cancelled. If the *first* tick fails (throws or returns false) the loop reports it via
     * [onFirstTickFailed] and returns so the caller can retry connect; subsequent tick failures are
     * swallowed (the next heartbeat reconciles). Returns normally only on the first-tick-failure
     * path; otherwise it runs until cancelled.
     */
    suspend fun run() {
        // Initial registration (ONLINE). A failure here means we never came online — bubble it up.
        val firstOk =
            try {
                tick()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onFirstTickFailed(t)
                return
            }
        if (!firstOk) {
            onFirstTickFailed(null)
            return
        }

        while (currentCoroutineContext().isActive) {
            delayFn(intervalMillis)
            if (!currentCoroutineContext().isActive) break
            try {
                tick()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Transient: the next heartbeat (or a reconnect) reconciles. Don't tear down.
            }
        }
    }

    companion object {
        /** ~20s heartbeat, comfortably inside the server's ~45s presence TTL. */
        const val DEFAULT_INTERVAL_MILLIS = 20_000L
    }
}
