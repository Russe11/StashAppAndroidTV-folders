package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the reconnect backoff schedule used by the live-refresh reconnect loop. */
class ReconnectBackoffTest {
    @Test
    fun grows_exponentially_from_base() {
        val backoff = ReconnectBackoff(baseDelayMillis = 1_000, maxDelayMillis = 100_000, factor = 2.0)
        assertEquals(1_000L, backoff.nextDelayMillis()) // attempt 0
        assertEquals(2_000L, backoff.nextDelayMillis()) // attempt 1
        assertEquals(4_000L, backoff.nextDelayMillis()) // attempt 2
        assertEquals(8_000L, backoff.nextDelayMillis()) // attempt 3
    }

    @Test
    fun clamps_at_max() {
        val backoff = ReconnectBackoff(baseDelayMillis = 1_000, maxDelayMillis = 5_000, factor = 2.0)
        assertEquals(1_000L, backoff.nextDelayMillis())
        assertEquals(2_000L, backoff.nextDelayMillis())
        assertEquals(4_000L, backoff.nextDelayMillis())
        // 8_000 would exceed max → clamps.
        assertEquals(5_000L, backoff.nextDelayMillis())
        assertEquals(5_000L, backoff.nextDelayMillis())
    }

    @Test
    fun reset_returns_to_base() {
        val backoff = ReconnectBackoff(baseDelayMillis = 1_000, maxDelayMillis = 100_000, factor = 2.0)
        backoff.nextDelayMillis()
        backoff.nextDelayMillis()
        assertEquals(2, backoff.attemptCount)

        backoff.reset()
        assertEquals(0, backoff.attemptCount)
        assertEquals(1_000L, backoff.nextDelayMillis())
    }

    @Test
    fun attemptCount_tracks_failures() {
        val backoff = ReconnectBackoff()
        assertEquals(0, backoff.attemptCount)
        backoff.nextDelayMillis()
        assertEquals(1, backoff.attemptCount)
        backoff.nextDelayMillis()
        assertEquals(2, backoff.attemptCount)
    }

    @Test
    fun delayForAttempt_is_pure_and_matches_sequence() {
        val backoff = ReconnectBackoff(baseDelayMillis = 1_000, maxDelayMillis = 30_000, factor = 2.0)
        assertEquals(1_000L, backoff.delayForAttempt(0))
        assertEquals(2_000L, backoff.delayForAttempt(1))
        assertEquals(4_000L, backoff.delayForAttempt(2))
        // Cap reached well before attempt 20; never explodes.
        assertEquals(30_000L, backoff.delayForAttempt(20))
        // delayForAttempt does not mutate the live counter.
        assertEquals(0, backoff.attemptCount)
    }

    @Test
    fun rejects_invalid_construction() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectBackoff(baseDelayMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectBackoff(baseDelayMillis = 1_000, maxDelayMillis = 500)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReconnectBackoff(factor = 0.5)
        }
    }

    @Test
    fun defaults_are_sane() {
        val backoff = ReconnectBackoff()
        val first = backoff.nextDelayMillis()
        assertEquals(ReconnectBackoff.DEFAULT_BASE_MILLIS, first)
        // Many failures stay bounded by the default max.
        repeat(50) { backoff.nextDelayMillis() }
        assertTrue(backoff.nextDelayMillis() <= ReconnectBackoff.DEFAULT_MAX_MILLIS)
    }
}
