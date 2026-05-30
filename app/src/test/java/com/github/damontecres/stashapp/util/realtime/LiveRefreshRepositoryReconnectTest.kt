package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the [LiveRefreshRepository] reconnect loop and capability gate, driven entirely with
 * fakes (no Apollo, no Room): a fake `subscribe` flow, a recording [LiveRefreshActions], and a
 * controllable `delayFn`/`debouncer` so reconnect timing is deterministic.
 */
class LiveRefreshRepositoryReconnectTest {
    private class RecordingActions : LiveRefreshActions {
        val applied = mutableListOf<LiveRefreshSignal>()

        override suspend fun apply(signal: LiveRefreshSignal) {
            applied.add(signal)
        }
    }

    @Test
    fun gate_off_means_no_subscription() =
        runBlocking {
            val actions = RecordingActions()
            val subscribeCalls = AtomicInteger(0)
            val repo =
                LiveRefreshRepository(
                    server = fakeServer(),
                    actions = actions,
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { false }, // server does NOT advertise entityChanged
                    subscribe = {
                        subscribeCalls.incrementAndGet()
                        flowOf()
                    },
                )
            repo.start()
            repeat(20) { yield() }

            assertEquals("gated off ⇒ never subscribes", 0, subscribeCalls.get())
            assertTrue(actions.applied.isEmpty())
            repo.stop()
        }

    @Test
    fun reconnects_after_stream_error_and_keeps_applying_signals() =
        runBlocking {
            val actions = RecordingActions()
            val attempt = AtomicInteger(0)
            val sawError = java.util.concurrent.atomic.AtomicBoolean(false)
            // Window=1ms with an immediate delayFn so each single-event burst flushes promptly.
            val debouncer = EntityChangeDebouncer(windowMillis = 1, delayFn = { /* fire now */ })
            val loopScope = CoroutineScope(this.coroutineContext + Job())

            val repo =
                LiveRefreshRepository(
                    server = fakeServer(),
                    actions = actions,
                    scope = loopScope,
                    capabilityGate = { true },
                    subscribe = {
                        when (attempt.getAndIncrement()) {
                            0 ->
                                // First connection: deliver a scene change, then complete (server
                                // closed the WS). The event reliably flushes before completion.
                                flowOf(EntityChange("Scene", "1", EntityOperation.CREATE))
                            1 ->
                                // Second connection: error out *without* an event to prove the loop
                                // survives a transport error and reconnects again.
                                flow<EntityChange> {
                                    sawError.set(true)
                                    throw RuntimeException("ws dropped")
                                }
                            else ->
                                // Third+ connection: deliver a tag change (post-error reconnect).
                                flowOf(EntityChange("Tag", "9", EntityOperation.UPDATE))
                        }
                    },
                    debouncer = debouncer,
                    // No real backoff wait in the test.
                    delayFn = { yield() },
                )

            val job = launch { repo.runLoop() }
            // Let the loop run through: connect#0 (scene) → #1 (error) → #2+ (tag).
            repeat(400) { yield() }
            job.cancel()

            assertTrue("reconnected several times", attempt.get() >= 3)
            assertTrue("a transport error occurred and was handled", sawError.get())
            // Both the pre-error scene change and the post-error tag change were applied — proving
            // the loop kept delivering across a clean close AND a transport error.
            val allTypes = actions.applied.flatMap { it.changedTypes }.toSet()
            assertTrue("scene change applied", DataType.SCENE in allTypes)
            assertTrue("tag change applied after error+reconnect", DataType.TAG in allTypes)
        }

    @Test
    fun reconnects_after_normal_completion() =
        runBlocking {
            val actions = RecordingActions()
            val attempt = AtomicInteger(0)
            val debouncer = EntityChangeDebouncer(windowMillis = 1, delayFn = { /* fire now */ })
            val loopScope = CoroutineScope(this.coroutineContext + Job())

            val repo =
                LiveRefreshRepository(
                    server = fakeServer(),
                    actions = actions,
                    scope = loopScope,
                    capabilityGate = { true },
                    subscribe = {
                        // Every connection completes immediately with no events (server closed WS);
                        // the loop must keep reconnecting (capped here by cancelling the job).
                        attempt.incrementAndGet()
                        flowOf()
                    },
                    debouncer = debouncer,
                    delayFn = { yield() },
                )

            val job = launch { repo.runLoop() }
            repeat(100) { yield() }
            job.cancel()

            assertTrue("kept reconnecting on normal completion", attempt.get() >= 3)
        }

    // -- helper: a StashServer is a plain data class; url/apiKey are enough for the loop, which
    // never touches the network here (subscribe/actions/gate are all injected). serverPreferences
    // is lazy and unused because capabilityGate is overridden.
    private fun fakeServer() =
        com.github.damontecres.stashapp.util.StashServer(url = "http://test/graphql", apiKey = null)
}
