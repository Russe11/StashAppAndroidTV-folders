package com.github.damontecres.stashapp.util.realtime

import android.content.Context
import android.util.Log
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the single live [DeviceBusRepository] (one server, one presence session),
 * mirroring [LiveRefreshHost].
 *
 * [install] wires the app [Context] once at startup; the host then starts/stops presence as the
 * active server, the foreground state, AND the opt-in toggle change. Presence only runs while
 * foreground AND opted-in AND the server advertises `deviceBus` (the repository enforces the latter
 * two gates; the host enforces foreground). On a server switch or opt-out the old session is
 * stopped (which unregisters → OFFLINE) and, if still eligible, a fresh one is started.
 *
 * [onlineDevices] re-exposes whichever repository is active so the online-devices UI observes one
 * stable flow across server switches; it is empty whenever presence is inactive.
 */
object DeviceBusHost {
    private const val TAG = "DeviceBusHost"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var appContext: Context? = null

    @Volatile
    private var current: DeviceBusRepository? = null

    @Volatile
    private var bridgeJob: Job? = null

    @Volatile
    private var currentServerUrl: String? = null

    @Volatile
    private var foreground: Boolean = false

    private val _onlineDevices = MutableStateFlow<List<DeviceSnapshot>>(emptyList())

    /** The online devices from the active presence session (empty when inactive). */
    val onlineDevices: StateFlow<List<DeviceSnapshot>> = _onlineDevices.asStateFlow()

    /** True while a presence session is running (registered + subscribed). */
    val isActive: Boolean get() = current?.isActive == true

    /** One-shot setup. Registers an opt-in listener so flipping the toggle starts/stops presence. */
    @Synchronized
    fun install(context: Context) {
        this.appContext = context.applicationContext
        DeviceBusPreferences.optInChanges { optedIn ->
            // React to the user flipping the presence toggle.
            if (optedIn) {
                onForeground() // re-evaluate: if foreground + capable, this starts presence.
            } else {
                stopCurrent() // opt-out: tear down + unregister (OFFLINE).
            }
        }
    }

    /** Called when the active server changes (incl. first selection). Restarts presence. */
    @Synchronized
    fun currentServerChanged(server: StashServer) {
        if (currentServerUrl == server.url && current != null) return
        stopCurrent()
        currentServerUrl = server.url
        if (foreground) startFor(server)
    }

    /** App came to the foreground: (re)start presence for the active server (if eligible). */
    @Synchronized
    fun onForeground() {
        foreground = true
        val server = StashServer.getCurrentStashServer() ?: return
        currentServerUrl = server.url
        if (current == null) startFor(server)
    }

    /** App went to the background: stop presence (unregisters → OFFLINE; reconnects next foreground). */
    @Synchronized
    fun onBackground() {
        foreground = false
        stopCurrent()
    }

    /**
     * Report this device's live playback to the active presence session (debounced inside the
     * repository). No-op when presence is inactive. Called from the player.
     */
    fun reportPlayback(
        sceneId: String?,
        positionSeconds: Double,
        paused: Boolean,
    ) {
        val repo = current ?: return
        scope.launch {
            repo.reportPlayback(sceneId, positionSeconds, paused)
        }
    }

    private fun startFor(server: StashServer) {
        val ctx = appContext ?: run {
            Log.w(TAG, "deviceBus not installed; skipping start for ${server.url}")
            return
        }
        // Cheap pre-check so we don't generate a device UUID for a server that can't use it or a
        // user who hasn't opted in. The repository re-checks both gates authoritatively.
        if (!DeviceBusPreferences.isOptedIn(ctx)) {
            Log.i(TAG, "presence opt-in OFF; not starting for ${server.url}")
            return
        }
        val repo =
            DeviceBusRepository(
                server = server,
                registration = {
                    DeviceRegistration(
                        id = DeviceIdentity.deviceId(ctx),
                        name = DeviceIdentity.deviceName(ctx),
                        // This device's role (design §7): Android-TV is a TV that can both play and
                        // control.
                        kind = DeviceKind.TV,
                        capabilities = listOf(DeviceCapability.PLAY, DeviceCapability.CONTROL),
                    )
                },
                optInGate = { DeviceBusPreferences.isOptedIn(ctx) },
            )
        current = repo
        repo.start()
        bridgeJob =
            scope.launch {
                repo.onlineDevices.collect { _onlineDevices.value = it }
            }
        Log.i(TAG, "deviceBus presence started for ${server.url}")
    }

    private fun stopCurrent() {
        bridgeJob?.cancel()
        bridgeJob = null
        current?.stop()
        current = null
        _onlineDevices.value = emptyList()
    }
}
