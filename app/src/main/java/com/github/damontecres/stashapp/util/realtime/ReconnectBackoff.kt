package com.github.damontecres.stashapp.util.realtime

import kotlin.math.min
import kotlin.math.pow

/**
 * Pure, deterministic exponential backoff for the subscription reconnect loop.
 *
 * The repository drives this on every WS drop: a successful (re)connect calls [reset]; a drop
 * calls [nextDelayMillis] to learn how long to wait before retrying. Delays grow
 * `base * factor^attempt` and are clamped to [maxDelayMillis], so a server that stays down
 * backs off to a steady retry cadence instead of hammering it.
 *
 * Deliberately jitter-free so the mapping (attempt → delay) is unit-testable; the small extra
 * value of randomised jitter (thundering-herd avoidance) is irrelevant for a single-user LAN
 * client reconnecting to one server.
 */
class ReconnectBackoff(
    private val baseDelayMillis: Long = DEFAULT_BASE_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_MILLIS,
    private val factor: Double = DEFAULT_FACTOR,
) {
    init {
        require(baseDelayMillis > 0) { "baseDelayMillis must be > 0" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must be >= baseDelayMillis" }
        require(factor >= 1.0) { "factor must be >= 1.0" }
    }

    private var attempt = 0

    /** The number of failed attempts since the last [reset] (0 immediately after a reset). */
    val attemptCount: Int get() = attempt

    /**
     * The delay to wait before the next reconnect attempt, then advance the attempt counter.
     * The first call after a [reset] returns [baseDelayMillis]; each subsequent call multiplies
     * by [factor], clamped at [maxDelayMillis].
     */
    fun nextDelayMillis(): Long {
        val raw = baseDelayMillis.toDouble() * factor.pow(attempt)
        val clamped = min(raw, maxDelayMillis.toDouble())
        if (attempt < Int.MAX_VALUE) attempt++
        return clamped.toLong()
    }

    /** The delay for [attempt] without mutating state — exposed for tests / introspection. */
    fun delayForAttempt(attempt: Int): Long {
        require(attempt >= 0) { "attempt must be >= 0" }
        val raw = baseDelayMillis.toDouble() * factor.pow(attempt)
        return min(raw, maxDelayMillis.toDouble()).toLong()
    }

    /** Call after a successful (re)connect so the next drop starts from [baseDelayMillis]. */
    fun reset() {
        attempt = 0
    }

    companion object {
        const val DEFAULT_BASE_MILLIS = 1_000L
        const val DEFAULT_MAX_MILLIS = 30_000L
        const val DEFAULT_FACTOR = 2.0
    }
}
