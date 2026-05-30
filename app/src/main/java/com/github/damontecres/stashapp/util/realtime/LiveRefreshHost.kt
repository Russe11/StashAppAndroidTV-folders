package com.github.damontecres.stashapp.util.realtime

import android.util.Log
import com.github.damontecres.stashapp.folders.data.FolderDao
import com.github.damontecres.stashapp.util.StashServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Process-wide owner of the single live [LiveRefreshRepository] (one server, one WS).
 *
 * Mirrors `LibraryIndexerHost`: [install] wires a [FolderDao] provider once at startup; the host
 * then starts/stops the repository as the active server and the app's foreground state change.
 * The WS only runs while the app is foreground (no point holding a socket in the background — the
 * next foreground delta sync reconciles anything missed). On a server switch the old repository
 * is stopped and a fresh one is started for the new server.
 *
 * The repository's per-server gating means [start] is a cheap no-op on a server that doesn't
 * advertise `entityChanged`, so this host can be wired unconditionally.
 *
 * [refreshSignals] re-exposes whichever repository is active so UI list holders can observe one
 * stable flow across server switches. The later deviceBus presence/control phase plugs into the
 * same host (it owns the WS lifecycle this phase establishes).
 */
object LiveRefreshHost {
    private const val TAG = "LiveRefreshHost"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var daoProvider: (() -> FolderDao)? = null

    @Volatile
    private var current: LiveRefreshRepository? = null

    @Volatile
    private var bridgeJob: Job? = null

    @Volatile
    private var currentServerUrl: String? = null

    @Volatile
    private var foreground: Boolean = false

    private val _refreshSignals =
        MutableSharedFlow<LiveRefreshSignal>(replay = 0, extraBufferCapacity = 16)

    /** Coalesced live-refresh signals from whichever repository is currently active. */
    val refreshSignals: SharedFlow<LiveRefreshSignal> = _refreshSignals.asSharedFlow()

    /** One-shot setup. Pass a thunk so the database isn't captured before it's built. */
    @Synchronized
    fun install(daoProvider: () -> FolderDao) {
        this.daoProvider = daoProvider
    }

    /** Called when the active server changes (incl. first selection). Restarts the WS. */
    @Synchronized
    fun currentServerChanged(server: StashServer) {
        if (currentServerUrl == server.url && current != null) return
        stopCurrent()
        currentServerUrl = server.url
        if (foreground) startFor(server)
    }

    /** App came to the foreground: (re)start live-refresh for the active server. */
    @Synchronized
    fun onForeground() {
        foreground = true
        val server = StashServer.getCurrentStashServer() ?: return
        currentServerUrl = server.url
        if (current == null) startFor(server)
    }

    /** App went to the background: drop the WS to save battery/sockets. */
    @Synchronized
    fun onBackground() {
        foreground = false
        stopCurrent()
    }

    private fun startFor(server: StashServer) {
        val dao = daoProvider?.invoke() ?: run {
            Log.w(TAG, "live-refresh not installed; skipping start for ${server.url}")
            return
        }
        val repo = LiveRefreshRepository.create(server, dao)
        current = repo
        repo.start()
        // Bridge the repository's per-server signals onto the host's stable, server-independent
        // flow so UI consumers collect one flow across server switches.
        bridgeJob =
            scope.launch {
                repo.refreshSignals.collect { _refreshSignals.emit(it) }
            }
        Log.i(TAG, "live-refresh started for ${server.url}")
    }

    private fun stopCurrent() {
        bridgeJob?.cancel()
        bridgeJob = null
        current?.stop()
        current = null
    }
}
