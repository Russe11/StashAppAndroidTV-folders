package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure [PlaybackReportThrottle] (the ~2s playback-report debounce, design §5):
 * first report fires, steady ticks are throttled to the interval, and meaningful changes (scene
 * change / pause flip / seek) report immediately regardless of the interval.
 */
class PlaybackReportThrottleTest {
    @Test
    fun firstReportAlwaysFires() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000)
        assertTrue(t.shouldReport(nowMillis = 0, sceneId = "1", positionSeconds = 0.0, paused = false))
    }

    @Test
    fun steadyPositionThrottledToInterval() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000)
        assertTrue(t.shouldReport(0, "1", 0.0, false)) // first
        // 500ms later, same scene, tiny position advance ⇒ throttled.
        assertFalse(t.shouldReport(500, "1", 0.5, false))
        assertFalse(t.shouldReport(1_000, "1", 1.0, false))
        // 2s after the last report ⇒ allowed (steady drip).
        assertTrue(t.shouldReport(2_000, "1", 2.0, false))
    }

    @Test
    fun sceneChangeReportsImmediately() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000)
        assertTrue(t.shouldReport(0, "1", 10.0, false))
        // Different scene 100ms later ⇒ immediate (not throttled).
        assertTrue(t.shouldReport(100, "2", 0.0, false))
    }

    @Test
    fun pauseFlipReportsImmediately() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000)
        assertTrue(t.shouldReport(0, "1", 10.0, false))
        // Pause 100ms later ⇒ immediate.
        assertTrue(t.shouldReport(100, "1", 10.0, true))
        // Resume 50ms later ⇒ immediate.
        assertTrue(t.shouldReport(150, "1", 10.0, false))
    }

    @Test
    fun seekReportsImmediately() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000, seekThresholdSeconds = 3.0)
        assertTrue(t.shouldReport(0, "1", 10.0, false))
        // Jump +30s within 100ms ⇒ a seek ⇒ immediate.
        assertTrue(t.shouldReport(100, "1", 40.0, false))
        // A sub-threshold 1s advance shortly after ⇒ throttled.
        assertFalse(t.shouldReport(200, "1", 41.0, false))
    }

    @Test
    fun resetMakesNextReportFire() {
        val t = PlaybackReportThrottle(minIntervalMillis = 2_000)
        assertTrue(t.shouldReport(0, "1", 0.0, false))
        assertFalse(t.shouldReport(100, "1", 0.1, false))
        t.reset()
        assertTrue("after reset the next report fires", t.shouldReport(200, "1", 0.2, false))
    }
}
