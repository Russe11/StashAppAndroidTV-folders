package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure command→player-action mapping tests (design §10 — "command→player-action mapping (pure)").
 * Covers the full v1 command set and the malformed-command cases that must be dropped (mapped to
 * null) rather than turned into a surprise player action.
 */
class DeviceCommandMapperTest {
    private fun cmd(
        type: DeviceCommandType,
        sceneId: String? = null,
        sceneIds: List<String> = emptyList(),
        startSeconds: Double? = null,
        seekSeconds: Double? = null,
    ) = DeviceCommand(
        fromDeviceId = "",
        type = type,
        sceneId = sceneId,
        sceneIds = sceneIds,
        startSeconds = startSeconds,
        seekSeconds = seekSeconds,
    )

    @Test
    fun play_withScene_mapsToPlayWithStart() {
        val action = DeviceCommandMapper.toAction(cmd(DeviceCommandType.PLAY, sceneId = "42", startSeconds = 12.5))
        assertEquals(PlayerAction.Play(sceneId = "42", startSeconds = 12.5), action)
    }

    @Test
    fun play_withoutStart_mapsWithNullStart() {
        val action = DeviceCommandMapper.toAction(cmd(DeviceCommandType.PLAY, sceneId = "7"))
        assertEquals(PlayerAction.Play(sceneId = "7", startSeconds = null), action)
    }

    @Test
    fun play_withoutScene_isDropped() {
        // A PLAY with no sceneId can't be satisfied — must be ignored, not crash.
        assertNull(DeviceCommandMapper.toAction(cmd(DeviceCommandType.PLAY, sceneId = null)))
        assertNull(DeviceCommandMapper.toAction(cmd(DeviceCommandType.PLAY, sceneId = "  ")))
    }

    @Test
    fun pause_resume_stop_mapToObjects() {
        assertEquals(PlayerAction.Pause, DeviceCommandMapper.toAction(cmd(DeviceCommandType.PAUSE)))
        assertEquals(PlayerAction.Resume, DeviceCommandMapper.toAction(cmd(DeviceCommandType.RESUME)))
        assertEquals(PlayerAction.Stop, DeviceCommandMapper.toAction(cmd(DeviceCommandType.STOP)))
    }

    @Test
    fun seek_withSeconds_mapsToSeek() {
        assertEquals(
            PlayerAction.Seek(seekSeconds = 90.0),
            DeviceCommandMapper.toAction(cmd(DeviceCommandType.SEEK, seekSeconds = 90.0)),
        )
        // Seek to 0 is valid (rewind to start).
        assertEquals(
            PlayerAction.Seek(seekSeconds = 0.0),
            DeviceCommandMapper.toAction(cmd(DeviceCommandType.SEEK, seekSeconds = 0.0)),
        )
    }

    @Test
    fun seek_withoutSeconds_isDropped() {
        assertNull(DeviceCommandMapper.toAction(cmd(DeviceCommandType.SEEK, seekSeconds = null)))
    }

    @Test
    fun enqueue_withScenes_mapsToEnqueueFilteringBlanks() {
        val action = DeviceCommandMapper.toAction(cmd(DeviceCommandType.ENQUEUE, sceneIds = listOf("1", " ", "2")))
        assertEquals(PlayerAction.Enqueue(sceneIds = listOf("1", "2")), action)
    }

    @Test
    fun enqueue_withNoScenes_isDropped() {
        assertNull(DeviceCommandMapper.toAction(cmd(DeviceCommandType.ENQUEUE, sceneIds = emptyList())))
        assertNull(DeviceCommandMapper.toAction(cmd(DeviceCommandType.ENQUEUE, sceneIds = listOf(" ", ""))))
    }

    @Test
    fun nextAndPrev_mapToObjects() {
        assertEquals(PlayerAction.Next, DeviceCommandMapper.toAction(cmd(DeviceCommandType.NEXT)))
        assertEquals(PlayerAction.Previous, DeviceCommandMapper.toAction(cmd(DeviceCommandType.PREV)))
    }

    @Test
    fun everyCommandType_mapsOrDropsDeterministically() {
        // Sanity: with all fields present every type yields a non-null action (no command type is
        // unhandled / falls through to a default).
        for (type in DeviceCommandType.entries) {
            val action =
                DeviceCommandMapper.toAction(
                    cmd(type, sceneId = "1", sceneIds = listOf("2", "3"), startSeconds = 1.0, seekSeconds = 5.0),
                )
            assertTrue("type $type should map when all fields present", action != null)
        }
    }
}
