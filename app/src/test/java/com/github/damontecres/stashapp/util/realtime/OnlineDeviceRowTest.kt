package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the pure [OnlineDeviceRow] formatting — especially that the "what's playing" line is
 * built ONLY from the locally-resolved scene name (never a wire title) and that an unresolved scene
 * falls back to a neutral "Scene #<id>" rather than leaking nothing/crashing.
 */
class OnlineDeviceRowTest {
    private fun device(playback: PlaybackSnapshot?) =
        DeviceSnapshot(
            id = "tv1",
            name = "Living Room TV",
            kind = DeviceKind.TV,
            capabilities = listOf(DeviceCapability.PLAY, DeviceCapability.CONTROL),
            online = true,
            lastSeen = "t",
            playback = playback,
        )

    @Test
    fun playingKnownScene_usesResolvedName() {
        val row =
            OnlineDeviceRow.from(
                device = device(PlaybackSnapshot(sceneId = "42", positionSeconds = 750.0, paused = false, updatedAt = "t")),
                resolvedSceneName = "My Scene",
            )
        assertEquals("Playing My Scene · 12:30", row.playbackLine)
    }

    @Test
    fun pausedKnownScene_usesPausedVerb() {
        val row =
            OnlineDeviceRow.from(
                device = device(PlaybackSnapshot("42", 5.0, paused = true, updatedAt = "t")),
                resolvedSceneName = "My Scene",
            )
        assertEquals("Paused My Scene · 0:05", row.playbackLine)
    }

    @Test
    fun unresolvedScene_fallsBackToSceneId() {
        // The wire carries only the id; if the client can't resolve the title locally we show a
        // neutral placeholder rather than a server-supplied name (there is none).
        val row =
            OnlineDeviceRow.from(
                device = device(PlaybackSnapshot("42", 0.0, paused = false, updatedAt = "t")),
                resolvedSceneName = null,
            )
        assertEquals("Playing Scene #42 · 0:00", row.playbackLine)
    }

    @Test
    fun noPlayback_hasNoPlaybackLine() {
        val row = OnlineDeviceRow.from(device(null), resolvedSceneName = null)
        assertNull(row.playbackLine)
    }

    @Test
    fun playbackWithoutScene_isTreatedAsIdle() {
        // A PlaybackState present but sceneId null (stopped) ⇒ no line.
        val row =
            OnlineDeviceRow.from(
                device = device(PlaybackSnapshot(sceneId = null, positionSeconds = 0.0, paused = true, updatedAt = "t")),
                resolvedSceneName = null,
            )
        assertNull(row.playbackLine)
    }

    @Test
    fun formatPosition_handlesHoursAndBadInput() {
        assertEquals("0:00", OnlineDeviceRow.formatPosition(0.0))
        assertEquals("0:09", OnlineDeviceRow.formatPosition(9.0))
        assertEquals("1:05", OnlineDeviceRow.formatPosition(65.0))
        assertEquals("1:01:05", OnlineDeviceRow.formatPosition(3665.0))
        assertEquals("0:00", OnlineDeviceRow.formatPosition(-5.0))
        assertEquals("0:00", OnlineDeviceRow.formatPosition(Double.NaN))
    }

    @Test
    fun row_carriesDeviceMetadata() {
        val row = OnlineDeviceRow.from(device(null), null)
        assertEquals("tv1", row.id)
        assertEquals(DeviceKind.TV, row.kind)
        assertEquals(listOf(DeviceCapability.PLAY, DeviceCapability.CONTROL), row.capabilities)
    }
}
