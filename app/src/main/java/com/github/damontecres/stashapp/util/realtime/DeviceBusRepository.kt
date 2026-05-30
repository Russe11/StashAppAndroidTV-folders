package com.github.damontecres.stashapp.util.realtime

import android.util.Log
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.SubscriptionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The NG deviceBus presence (+ command) layer for one server (design §6 — "an Apollo
 * subscription-backed DeviceBusRepository").
 *
 * Responsibilities for this device (an Android-TV: kind=TV, capabilities=[PLAY, CONTROL], primarily
 * a TARGET):
 *  - **Register + heartbeat** ([HeartbeatLoop]) so the device is ONLINE and stays within the TTL.
 *  - **Presence** — subscribe to `devicePresence`, fold events into a [DevicePresenceStore], and
 *    expose the online-devices list as observable [onlineDevices] state (seeded by the `devices`
 *    query on connect).
 *  - **Commands** — subscribe to `deviceCommands(deviceId)` and surface incoming [DeviceCommand]s
 *    on [incomingCommands] for the player (R-C).
 *  - **Playback** — [reportPlayback] pushes debounced `updatePlaybackState` while playing.
 *  - **Graceful exit** — [stop]/[shutdown] unregisters (emits OFFLINE).
 *
 * **Double-gated** (privacy-first, invariant #1/#2): [start] no-ops unless the server advertises
 * `deviceBus` AND the user has opted in. Until both hold this device never registers, never
 * heartbeats, and is invisible/uncontrollable.
 *
 * Resilience mirrors [LiveRefreshRepository]: the presence subscription is wrapped in an
 * exponential-[ReconnectBackoff] reconnect loop; on each (re)connect the `devices` query reseeds
 * the store so a missed-while-disconnected change is reconciled.
 *
 * Collaborators are injected so the orchestration is unit-testable without Apollo/network:
 * [capabilityGate]/[optInGate] decide activation, [registration] supplies identity,
 * [fetchDevices]/[presenceFlow]/[commandFlow]/[register]/[unregister]/[reportState] are the I/O
 * seams, and [delayFn] drives backoff.
 */
class DeviceBusRepository(
    private val server: StashServer,
    private val registration: () -> DeviceRegistration,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val capabilityGate: () -> Boolean = { server.serverPreferences.capabilities.supportsDeviceBus },
    private val optInGate: () -> Boolean = { false },
    private val engineProvider: () -> DeviceBusEngine = { DeviceBusEngine(server) },
    private val fetchDevices: suspend () -> List<DeviceSnapshot> = { engineProvider().devices() },
    private val presenceFlow: () -> Flow<DevicePresenceEvent> = { SubscriptionEngine(server).devicePresence() },
    private val commandFlow: (String) -> Flow<DeviceCommand> = { id -> SubscriptionEngine(server).deviceCommands(id) },
    private val register: suspend (DeviceRegistration) -> Unit = { engineProvider().registerDevice(it) },
    private val unregister: suspend (String) -> Unit = { engineProvider().unregisterDevice(it) },
    private val reportState: suspend (PlaybackStateUpdate) -> Unit = { engineProvider().updatePlaybackState(it) },
    private val sendCommandFn: suspend (OutgoingDeviceCommand) -> Boolean = { engineProvider().sendDeviceCommand(it) },
    private val heartbeatIntervalMillis: Long = HeartbeatLoop.DEFAULT_INTERVAL_MILLIS,
    private val backoff: ReconnectBackoff = ReconnectBackoff(),
    private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val deviceId: String get() = registration().id

    private val store = DevicePresenceStore(selfDeviceId = deviceId)

    private val _onlineDevices = MutableStateFlow<List<DeviceSnapshot>>(emptyList())

    /** The current online devices (excludes self), updated live from presence events. */
    val onlineDevices: StateFlow<List<DeviceSnapshot>> = _onlineDevices.asStateFlow()

    private val _incomingCommands =
        MutableSharedFlow<DeviceCommand>(replay = 0, extraBufferCapacity = 16)

    /** Commands targeted at this device (the player obeys them — R-C). */
    val incomingCommands: SharedFlow<DeviceCommand> = _incomingCommands.asSharedFlow()

    private val throttle = PlaybackReportThrottle()

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var presenceJob: Job? = null

    @Volatile
    private var commandJob: Job? = null

    @Volatile
    private var active: Boolean = false

    /** True while presence is active (registered + subscribed). */
    val isActive: Boolean get() = active

    /**
     * Start presence: register + heartbeat, subscribe to presence + commands, seed the
     * online-devices list. Idempotent. **No-ops** (and logs why) unless BOTH the `deviceBus`
     * capability is advertised AND the user has opted in — so an un-opted-in device is invisible
     * and a non-deviceBus server is a cheap no-op.
     */
    @Synchronized
    fun start() {
        if (active) return
        if (!capabilityGate()) {
            Log.i(TAG, "deviceBus capability absent for ${server.url}; presence disabled")
            return
        }
        if (!optInGate()) {
            Log.i(TAG, "presence opt-in OFF for ${server.url}; device stays invisible")
            return
        }
        active = true
        Log.i(TAG, "deviceBus presence starting for ${server.url} as ${registration().id}")
        heartbeatJob = scope.launch { runHeartbeat() }
        presenceJob = scope.launch { runPresence() }
        commandJob = scope.launch { runCommands() }
    }

    /**
     * Stop presence (opt-out / background / server switch). Best-effort `unregisterDevice` so other
     * devices see OFFLINE immediately rather than waiting for the TTL. Safe to call when stopped.
     */
    @Synchronized
    fun stop() {
        if (!active && heartbeatJob == null) return
        active = false
        heartbeatJob?.cancel()
        presenceJob?.cancel()
        commandJob?.cancel()
        heartbeatJob = null
        presenceJob = null
        commandJob = null
        store.clear()
        _onlineDevices.value = emptyList()
        throttle.reset()
        // Graceful OFFLINE on a separate launch (the cancelled jobs above can't run suspend work).
        val id = deviceId
        scope.launch {
            try {
                unregister(id)
            } catch (t: Throwable) {
                Log.w(TAG, "unregisterDevice failed for ${server.url}: ${t.message}")
            }
        }
    }

    /**
     * Report this device's live playback while it's a target playing a scene. Debounced ~2s by
     * [PlaybackReportThrottle] — meaningful changes (scene change, play/pause flip, seek) report
     * immediately, steady position drips at the interval. No-op when presence isn't active.
     *
     * @param nowMillis the current monotonic clock (caller supplies so the throttle stays pure).
     */
    suspend fun reportPlayback(
        sceneId: String?,
        positionSeconds: Double,
        paused: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!active) return
        if (!throttle.shouldReport(nowMillis, sceneId, positionSeconds, paused)) return
        try {
            reportState(
                PlaybackStateUpdate(
                    deviceId = deviceId,
                    sceneId = sceneId,
                    positionSeconds = positionSeconds,
                    paused = paused,
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "updatePlaybackState failed for ${server.url}: ${t.message}")
        }
    }

    /**
     * Send a remote command from this device (acting as a CONTROLLER — R-C) to a target device. The
     * server relays it; returns true only if a live target subscriber received it. No-op returning
     * false when presence isn't active (this device hasn't opted in / registered).
     */
    suspend fun sendCommand(command: OutgoingDeviceCommand): Boolean {
        if (!active) return false
        return try {
            sendCommandFn(command)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "sendDeviceCommand failed for ${server.url}: ${t.message}")
            false
        }
    }

    // -- internal loops (non-private so unit tests can drive them with fakes) --

    internal suspend fun runHeartbeat() {
        HeartbeatLoop(
            intervalMillis = heartbeatIntervalMillis,
            tick = {
                register(registration())
                true
            },
            delayFn = delayFn,
            onFirstTickFailed = { t ->
                Log.w(TAG, "initial registerDevice failed for ${server.url}: ${t?.message}")
            },
        ).run()
    }

    internal suspend fun runPresence() {
        while (currentCoroutineContext().isActive) {
            try {
                // Reseed from the authoritative snapshot on each (re)connect so anything missed
                // while disconnected is reconciled.
                _onlineDevices.value = store.replaceAll(fetchDevices())
                backoff.reset()
                presenceFlow().collect { event ->
                    _onlineDevices.value = store.apply(event)
                }
                Log.d(TAG, "devicePresence stream completed for ${server.url}; will reconnect")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "devicePresence stream error for ${server.url}: ${t.message}")
            }
            if (!currentCoroutineContext().isActive) break
            val wait = backoff.nextDelayMillis()
            delayFn(wait)
        }
    }

    internal suspend fun runCommands() {
        val id = deviceId
        while (currentCoroutineContext().isActive) {
            try {
                commandFlow(id).collect { command ->
                    Log.i(TAG, "deviceCommand ${command.type} for ${server.url}")
                    _incomingCommands.emit(command)
                }
                Log.d(TAG, "deviceCommands stream completed for ${server.url}; will reconnect")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "deviceCommands stream error for ${server.url}: ${t.message}")
            }
            if (!currentCoroutineContext().isActive) break
            delayFn(backoff.delayForAttempt(1))
        }
    }

    companion object {
        private const val TAG = "DeviceBusRepository"
    }
}
