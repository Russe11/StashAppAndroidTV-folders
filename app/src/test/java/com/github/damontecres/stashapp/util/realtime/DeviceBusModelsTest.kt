package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure enum-mapping tests for the deviceBus domain models. These stand in for the
 * "subscription-decode" coverage the design §10 asks for: every server enum value the
 * `devicePresence`/`deviceCommands` payloads can carry maps to the right domain enum, and unknown
 * values degrade sensibly (additive-NG tolerance) rather than crashing.
 */
class DeviceBusModelsTest {
    @Test
    fun deviceKind_mapsKnownAndUnknown() {
        assertEquals(DeviceKind.TV, DeviceKind.fromServer("TV"))
        assertEquals(DeviceKind.DESKTOP, DeviceKind.fromServer("desktop"))
        assertEquals(DeviceKind.MOBILE, DeviceKind.fromServer(" Mobile "))
        assertEquals(DeviceKind.OTHER, DeviceKind.fromServer("OTHER"))
        // Unknown / future value ⇒ OTHER (don't crash).
        assertEquals(DeviceKind.OTHER, DeviceKind.fromServer("WATCH"))
    }

    @Test
    fun deviceCapability_mapsKnownAndDropsUnknown() {
        assertEquals(DeviceCapability.PLAY, DeviceCapability.fromServer("PLAY"))
        assertEquals(DeviceCapability.CONTROL, DeviceCapability.fromServer("control"))
        // Unknown capability ⇒ null (dropped from the list).
        assertNull(DeviceCapability.fromServer("RECORD"))
    }

    @Test
    fun presenceChange_mapsAllAndUnknownToPlayback() {
        assertEquals(PresenceChange.ONLINE, PresenceChange.fromServer("ONLINE"))
        assertEquals(PresenceChange.OFFLINE, PresenceChange.fromServer("offline"))
        assertEquals(PresenceChange.PLAYBACK, PresenceChange.fromServer("PLAYBACK"))
        // Unknown ⇒ PLAYBACK (treat as a refresh signal, the safe default).
        assertEquals(PresenceChange.PLAYBACK, PresenceChange.fromServer("MUTED"))
    }

    @Test
    fun deviceCommandType_mapsFullSetAndDropsUnknown() {
        // The full v1 command set (design §12.5).
        assertEquals(DeviceCommandType.PLAY, DeviceCommandType.fromServer("PLAY"))
        assertEquals(DeviceCommandType.PAUSE, DeviceCommandType.fromServer("PAUSE"))
        assertEquals(DeviceCommandType.RESUME, DeviceCommandType.fromServer("RESUME"))
        assertEquals(DeviceCommandType.SEEK, DeviceCommandType.fromServer("SEEK"))
        assertEquals(DeviceCommandType.STOP, DeviceCommandType.fromServer("STOP"))
        assertEquals(DeviceCommandType.ENQUEUE, DeviceCommandType.fromServer("ENQUEUE"))
        assertEquals(DeviceCommandType.NEXT, DeviceCommandType.fromServer("NEXT"))
        assertEquals(DeviceCommandType.PREV, DeviceCommandType.fromServer("PREV"))
        // Unknown command ⇒ null (ignored, not mis-acted on).
        assertNull(DeviceCommandType.fromServer("VOLUME"))
    }

    @Test
    fun deviceSnapshot_roleHelpers() {
        val tv =
            DeviceSnapshot(
                id = "1",
                name = "Living Room TV",
                kind = DeviceKind.TV,
                capabilities = listOf(DeviceCapability.PLAY, DeviceCapability.CONTROL),
                online = true,
                lastSeen = "2026-05-29T00:00:00Z",
                playback = null,
            )
        assertEquals(true, tv.isPlayTarget)
        assertEquals(true, tv.isController)

        val phone = tv.copy(kind = DeviceKind.MOBILE, capabilities = listOf(DeviceCapability.CONTROL))
        assertEquals(false, phone.isPlayTarget)
        assertEquals(true, phone.isController)
    }
}
