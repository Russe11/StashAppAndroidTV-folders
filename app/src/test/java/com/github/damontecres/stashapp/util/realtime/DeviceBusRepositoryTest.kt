package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the [DeviceBusRepository] orchestration, driven entirely with fakes (no Apollo, no
 * network): the double gate (capability AND opt-in), presence-store wiring (snapshot seed +
 * live events → onlineDevices), incoming-command surfacing, the graceful unregister on stop, and
 * the presence reconnect loop. The injected `delayFn`/flows make timing deterministic.
 */
class DeviceBusRepositoryTest {
    private val registration =
        DeviceRegistration(
            id = "self",
            name = "Test TV",
            kind = DeviceKind.TV,
            capabilities = listOf(DeviceCapability.PLAY, DeviceCapability.CONTROL),
        )

    private fun device(id: String) =
        DeviceSnapshot(id, "dev-$id", DeviceKind.TV, listOf(DeviceCapability.PLAY), true, "t", null)

    @Test
    fun gate_capabilityOff_neverRegistersOrSubscribes() =
        runBlocking {
            val registered = AtomicInteger(0)
            val presenceSubs = AtomicInteger(0)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { false }, // server does NOT advertise deviceBus
                    optInGate = { true },
                    register = { registered.incrementAndGet() },
                    fetchDevices = { emptyList() },
                    presenceFlow = {
                        presenceSubs.incrementAndGet()
                        flowOf()
                    },
                    commandFlow = { flowOf() },
                )
            repo.start()
            repeat(20) { yield() }

            assertFalse("never active when capability off", repo.isActive)
            assertEquals(0, registered.get())
            assertEquals(0, presenceSubs.get())
            repo.stop()
        }

    @Test
    fun gate_optInOff_neverRegistersOrSubscribes() =
        runBlocking {
            val registered = AtomicInteger(0)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { true },
                    optInGate = { false }, // user has NOT opted in (default)
                    register = { registered.incrementAndGet() },
                    fetchDevices = { emptyList() },
                    presenceFlow = { flowOf() },
                    commandFlow = { flowOf() },
                )
            repo.start()
            repeat(20) { yield() }

            assertFalse("invisible until opt-in", repo.isActive)
            assertEquals("never registers when opted out", 0, registered.get())
            repo.stop()
        }

    @Test
    fun bothGatesOn_registersSeedsAndAppliesPresence() =
        runBlocking {
            val registered = AtomicInteger(0)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { true },
                    optInGate = { true },
                    register = { registered.incrementAndGet() },
                    // Snapshot seed includes self (must be filtered) + one other device.
                    fetchDevices = { listOf(device("self"), device("a")) },
                    presenceFlow = {
                        flow {
                            // A new device comes online, then 'a' goes offline.
                            emit(DevicePresenceEvent(device("b"), PresenceChange.ONLINE))
                            emit(DevicePresenceEvent(device("a"), PresenceChange.OFFLINE))
                        }
                    },
                    commandFlow = { flowOf() },
                    delayFn = { yield() },
                )
            repo.start()
            repeat(50) { yield() }

            assertTrue("active with both gates on", repo.isActive)
            assertTrue("registered at least once (heartbeat)", registered.get() >= 1)
            // self filtered out; a removed by OFFLINE; b added by ONLINE.
            assertEquals(listOf("b"), repo.onlineDevices.value.map { it.id })
            repo.stop()
        }

    @Test
    fun incomingCommands_areSurfaced() =
        runBlocking {
            val received = mutableListOf<DeviceCommand>()
            val scope = CoroutineScope(coroutineContext + Job())
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = scope,
                    capabilityGate = { true },
                    optInGate = { true },
                    register = { },
                    fetchDevices = { emptyList() },
                    presenceFlow = { flowOf() },
                    commandFlow = { id ->
                        // The target subscribes with its OWN id.
                        assertEquals("self", id)
                        flowOf(
                            DeviceCommand("", DeviceCommandType.PAUSE, null, emptyList(), null, null),
                        )
                    },
                    delayFn = { yield() },
                )
            val collector = scope.launch { repo.incomingCommands.collect { received.add(it) } }
            repo.start()
            repeat(50) { yield() }

            assertTrue("a command was surfaced to the player", received.any { it.type == DeviceCommandType.PAUSE })
            collector.cancel()
            repo.stop()
        }

    @Test
    fun stop_unregistersForGracefulOffline() =
        runBlocking {
            val unregistered = AtomicBoolean(false)
            val unregisteredId = arrayOfNulls<String>(1)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { true },
                    optInGate = { true },
                    register = { },
                    unregister = { id ->
                        unregisteredId[0] = id
                        unregistered.set(true)
                    },
                    fetchDevices = { emptyList() },
                    presenceFlow = { flowOf() },
                    commandFlow = { flowOf() },
                    delayFn = { yield() },
                )
            repo.start()
            repeat(10) { yield() }
            repo.stop()
            repeat(20) { yield() }

            assertTrue("stop() unregisters → OFFLINE", unregistered.get())
            assertEquals("self", unregisteredId[0])
            assertTrue("list cleared on stop", repo.onlineDevices.value.isEmpty())
        }

    @Test
    fun presence_reconnectsAfterError() =
        runBlocking {
            val attempts = AtomicInteger(0)
            val sawError = AtomicBoolean(false)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { true },
                    optInGate = { true },
                    register = { },
                    fetchDevices = { emptyList() },
                    presenceFlow = {
                        when (attempts.getAndIncrement()) {
                            0 ->
                                flow {
                                    sawError.set(true)
                                    throw RuntimeException("ws dropped")
                                }
                            else -> flowOf(DevicePresenceEvent(device("late"), PresenceChange.ONLINE))
                        }
                    },
                    commandFlow = { flowOf() },
                    delayFn = { yield() },
                )
            val job = launch { repo.runPresence() }
            repeat(200) { yield() }
            job.cancel()

            assertTrue("presence stream errored and was handled", sawError.get())
            assertTrue("presence reconnected", attempts.get() >= 2)
            assertEquals("post-reconnect device applied", listOf("late"), repo.onlineDevices.value.map { it.id })
        }

    @Test
    fun reportPlayback_throttlesButFiresOnChange() =
        runBlocking {
            val reports = mutableListOf<PlaybackStateUpdate>()
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { true },
                    optInGate = { true },
                    register = { },
                    fetchDevices = { emptyList() },
                    presenceFlow = { flowOf() },
                    commandFlow = { flowOf() },
                    reportState = { reports.add(it) },
                    delayFn = { yield() },
                )
            repo.start()
            repeat(10) { yield() }

            // First report fires.
            repo.reportPlayback("1", 0.0, paused = false, nowMillis = 0)
            // 500ms later, tiny advance ⇒ throttled (no report).
            repo.reportPlayback("1", 0.5, paused = false, nowMillis = 500)
            // Pause ⇒ immediate report.
            repo.reportPlayback("1", 0.5, paused = true, nowMillis = 600)
            repeat(10) { yield() }

            assertEquals("first + pause reported, the throttled tick dropped", 2, reports.size)
            assertEquals("self", reports.first().deviceId)
            assertTrue("the second report is the pause", reports[1].paused)
            repo.stop()
        }

    @Test
    fun reportPlayback_noopWhenInactive() =
        runBlocking {
            val reports = AtomicInteger(0)
            val repo =
                DeviceBusRepository(
                    server = fakeServer(),
                    registration = { registration },
                    scope = CoroutineScope(coroutineContext + Job()),
                    capabilityGate = { false }, // not active
                    optInGate = { true },
                    register = { },
                    fetchDevices = { emptyList() },
                    presenceFlow = { flowOf() },
                    commandFlow = { flowOf() },
                    reportState = { reports.incrementAndGet() },
                )
            repo.start() // gated off ⇒ inactive
            repo.reportPlayback("1", 0.0, paused = false, nowMillis = 0)

            assertEquals("no playback report while presence inactive", 0, reports.get())
        }

    // A StashServer is a plain data class; url/apiKey are enough here — every network seam
    // (register/fetchDevices/presenceFlow/commandFlow/reportState) and both gates are injected, so
    // serverPreferences is never touched.
    private fun fakeServer() = StashServer(url = "http://test/graphql", apiKey = null)
}
