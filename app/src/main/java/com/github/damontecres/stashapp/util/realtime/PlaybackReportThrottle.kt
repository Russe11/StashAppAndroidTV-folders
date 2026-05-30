package com.github.damontecres.stashapp.util.realtime

import kotlin.math.abs

/**
 * Pure debounce gate for playback-state reports (design §5: `updatePlaybackState` is "debounced
 * ~2s by the reporting device"). The player can tick position many times a second; we only want to
 * push one `updatePlaybackState` mutation roughly every [minIntervalMillis], EXCEPT that a
 * *meaningful* state change (play→pause, scene change, a seek) should report immediately so other
 * devices' views stay live.
 *
 * Pure + clock-injected (caller passes `nowMillis`) so the throttle is fully unit-testable without
 * a real clock or coroutines. The repository feeds the player's position into [shouldReport] and
 * only fires the mutation when it returns true.
 *
 * @param minIntervalMillis the minimum gap between position-only reports (~2s).
 * @param seekThresholdSeconds a position jump larger than this (with no time gap) is treated as a
 *   seek and reported immediately.
 */
class PlaybackReportThrottle(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val seekThresholdSeconds: Double = DEFAULT_SEEK_THRESHOLD_SECONDS,
) {
    private var lastReportedMillis: Long = Long.MIN_VALUE
    private var lastSceneId: String? = null
    private var lastPaused: Boolean? = null
    private var lastPositionSeconds: Double = 0.0

    /**
     * Decide whether to report the given playback state now. Returns true (and records it as the
     * new baseline) when:
     *  - it's the first report, or
     *  - the scene changed, or the paused flag flipped, or
     *  - the position jumped by more than [seekThresholdSeconds] (a seek), or
     *  - at least [minIntervalMillis] has elapsed since the last report (steady position drip).
     *
     * Otherwise returns false and changes nothing.
     */
    fun shouldReport(
        nowMillis: Long,
        sceneId: String?,
        positionSeconds: Double,
        paused: Boolean,
    ): Boolean {
        val first = lastPaused == null
        val sceneChanged = sceneId != lastSceneId
        val pausedFlipped = paused != lastPaused
        val seeked =
            !first && abs(positionSeconds - lastPositionSeconds) > seekThresholdSeconds
        val intervalElapsed = nowMillis - lastReportedMillis >= minIntervalMillis

        val report = first || sceneChanged || pausedFlipped || seeked || intervalElapsed
        if (report) {
            lastReportedMillis = nowMillis
            lastSceneId = sceneId
            lastPaused = paused
            lastPositionSeconds = positionSeconds
        }
        return report
    }

    /** Reset the baseline (e.g. on stop / opt-out) so the next report fires immediately. */
    fun reset() {
        lastReportedMillis = Long.MIN_VALUE
        lastSceneId = null
        lastPaused = null
        lastPositionSeconds = 0.0
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MILLIS = 2_000L
        const val DEFAULT_SEEK_THRESHOLD_SECONDS = 3.0
    }
}
