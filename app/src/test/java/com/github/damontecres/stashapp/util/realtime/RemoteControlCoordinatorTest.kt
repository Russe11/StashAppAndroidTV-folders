package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the TARGET-side TOFU gate ([RemoteControlCoordinator]) end-to-end with fakes (design §10 —
 * the TOFU gate + command→action dispatch). Verifies:
 *  - an allowed controller's command is dispatched immediately,
 *  - a blocked controller's command is silently dropped,
 *  - the FIRST command from an unknown controller prompts (and is held, not dispatched),
 *  - allowing after the prompt dispatches the held command and remembers the decision,
 *  - blocking after the prompt drops it and remembers the block,
 *  - an unmappable command is dropped before ever reaching TOFU.
 */
class RemoteControlCoordinatorTest {
    private class FakeBacking : ControllerAuthorizationStore.Backing {
        val map = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String): Boolean? = map[key]

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        override fun clear() = map.clear()
    }

    private class Harness {
        val backing = FakeBacking()
        val authStore = ControllerAuthorizationStore(backing)
        val dispatched = mutableListOf<PlayerAction>()
        val prompts = mutableListOf<ControllerConfirmationRequest>()

        // A dispatcher with no live player whose launchScene records a Play action so we can assert.
        val dispatcher =
            RemoteCommandDispatcher(
                playerProvider = { null },
                launchScene = { sceneId, start -> dispatched.add(PlayerAction.Play(sceneId, start)) },
            )

        val coordinator =
            RemoteControlCoordinator(
                authStore = authStore,
                dispatcher = dispatcher,
                resolveControllerName = { "Controller $it" },
                requestConfirmation = { prompts.add(it) },
                log = {},
            )
    }

    private fun playCommand(
        from: String,
        sceneId: String = "42",
    ) = DeviceCommand(
        fromDeviceId = from,
        type = DeviceCommandType.PLAY,
        sceneId = sceneId,
        sceneIds = emptyList(),
        startSeconds = null,
        seekSeconds = null,
    )

    @Test
    fun allowedController_dispatchesImmediately() {
        val h = Harness()
        h.authStore.remember("ctrlA", true)

        val verdict = h.coordinator.onCommand(playCommand("ctrlA"))

        assertEquals(ControllerVerdict.ALLOWED, verdict)
        assertEquals(listOf<PlayerAction>(PlayerAction.Play("42", null)), h.dispatched)
        assertTrue(h.prompts.isEmpty())
    }

    @Test
    fun blockedController_isDroppedSilently() {
        val h = Harness()
        h.authStore.remember("ctrlA", false)

        val verdict = h.coordinator.onCommand(playCommand("ctrlA"))

        assertEquals(ControllerVerdict.BLOCKED, verdict)
        assertTrue(h.dispatched.isEmpty())
        assertTrue(h.prompts.isEmpty())
    }

    @Test
    fun firstCommandFromUnknown_promptsAndHolds() {
        val h = Harness()

        val verdict = h.coordinator.onCommand(playCommand("ctrlNew"))

        assertEquals(ControllerVerdict.ASK, verdict)
        // Prompted with the resolved name, nothing dispatched yet (held).
        assertEquals(1, h.prompts.size)
        assertEquals("Controller ctrlNew", h.prompts.single().controllerName)
        assertEquals("ctrlNew", h.prompts.single().controllerKey)
        assertTrue(h.dispatched.isEmpty())
    }

    @Test
    fun allowingAfterPrompt_dispatchesHeldCommandAndRemembers() {
        val h = Harness()
        h.coordinator.onCommand(playCommand("ctrlNew", sceneId = "99"))

        h.coordinator.onConfirmationResult("ctrlNew", allowed = true)

        // The held PLAY now fires, and the decision is remembered for next time.
        assertEquals(listOf<PlayerAction>(PlayerAction.Play("99", null)), h.dispatched)
        assertEquals(ControllerVerdict.ALLOWED, h.authStore.verdict("ctrlNew"))

        // A subsequent command from the same controller dispatches without another prompt.
        h.coordinator.onCommand(playCommand("ctrlNew", sceneId = "100"))
        assertEquals(2, h.dispatched.size)
        assertEquals(1, h.prompts.size)
    }

    @Test
    fun blockingAfterPrompt_dropsHeldCommandAndRemembers() {
        val h = Harness()
        h.coordinator.onCommand(playCommand("ctrlNew"))

        h.coordinator.onConfirmationResult("ctrlNew", allowed = false)

        assertTrue(h.dispatched.isEmpty())
        assertEquals(ControllerVerdict.BLOCKED, h.authStore.verdict("ctrlNew"))

        // A subsequent command is now silently dropped (no new prompt).
        h.coordinator.onCommand(playCommand("ctrlNew"))
        assertTrue(h.dispatched.isEmpty())
        assertEquals(1, h.prompts.size)
    }

    @Test
    fun unmappableCommand_isDroppedBeforeTofu() {
        val h = Harness()
        // PLAY with no sceneId can't be mapped — dropped, no prompt even from an unknown controller.
        val cmd =
            DeviceCommand(
                fromDeviceId = "ctrlNew",
                type = DeviceCommandType.PLAY,
                sceneId = null,
                sceneIds = emptyList(),
                startSeconds = null,
                seekSeconds = null,
            )
        val verdict = h.coordinator.onCommand(cmd)
        assertEquals(ControllerVerdict.BLOCKED, verdict)
        assertTrue(h.prompts.isEmpty())
        assertTrue(h.dispatched.isEmpty())
    }

    @Test
    fun discardPending_dropsHeldWithoutDeciding() {
        val h = Harness()
        h.coordinator.onCommand(playCommand("ctrlNew"))
        h.coordinator.discardPending("ctrlNew")
        // Controller stays ASK; allowing later has nothing held to dispatch.
        assertEquals(ControllerVerdict.ASK, h.authStore.verdict("ctrlNew"))
        h.coordinator.onConfirmationResult("ctrlNew", allowed = true)
        assertTrue(h.dispatched.isEmpty())
    }
}
