package com.github.damontecres.stashapp.ui.components.devicebus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.realtime.DeviceBusHost
import com.github.damontecres.stashapp.util.realtime.DeviceBusPreferences
import com.github.damontecres.stashapp.util.realtime.DeviceCommandType
import com.github.damontecres.stashapp.util.realtime.DeviceIdentity
import com.github.damontecres.stashapp.util.realtime.OnlineDeviceRow
import com.github.damontecres.stashapp.util.realtime.OutgoingDeviceCommand
import com.github.damontecres.stashapp.util.realtime.SceneNameResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the "Cross-Device" panel: the opt-in toggle, the editable device name, and the live
 * online-devices list. The list comes from [DeviceBusHost.onlineDevices]; scene IDs on it are
 * resolved to titles **locally** (via [SceneNameResolver]) before display — the wire never carries
 * titles (privacy invariant).
 *
 * The panel is only shown when the server advertises `deviceBus` ([deviceBusSupported]); the caller
 * gates on that. The opt-in toggle itself defaults OFF, so even on a capable server this device
 * stays invisible until the user enables presence here.
 */
class CrossDeviceViewModel : ViewModel() {
    private lateinit var resolver: SceneNameResolver

    private val _optedIn = MutableStateFlow(false)
    val optedIn: StateFlow<Boolean> = _optedIn.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _deviceBusSupported = MutableStateFlow(false)
    val deviceBusSupported: StateFlow<Boolean> = _deviceBusSupported.asStateFlow()

    private val _rows = MutableStateFlow<List<OnlineDeviceRow>>(emptyList())

    /** The presentation-ready online-devices rows (scene names already resolved locally). */
    val rows: StateFlow<List<OnlineDeviceRow>> = _rows.asStateFlow()

    fun init(
        context: Context,
        server: StashServer,
    ) {
        resolver = SceneNameResolver(server)
        _deviceBusSupported.value = server.serverPreferences.capabilities.supportsDeviceBus
        _optedIn.value = DeviceBusPreferences.isOptedIn(context)
        _deviceName.value = DeviceIdentity.deviceName(context)

        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            DeviceBusHost.onlineDevices.collect { devices ->
                _rows.value =
                    devices.map { device ->
                        OnlineDeviceRow.from(
                            device = device,
                            resolvedSceneName = resolver.resolve(device.playback?.sceneId),
                        )
                    }
            }
        }
    }

    /** Flip the opt-in toggle. The [DeviceBusHost] reacts (starts/stops presence) via its listener. */
    fun setOptedIn(
        context: Context,
        optedIn: Boolean,
    ) {
        DeviceBusPreferences.setOptedIn(context, optedIn)
        _optedIn.value = optedIn
    }

    /** Persist a user-edited device name (blank resets to the default host name). */
    fun setDeviceName(
        context: Context,
        name: String,
    ) {
        DeviceIdentity.setDeviceName(context, name)
        _deviceName.value = DeviceIdentity.deviceName(context)
    }

    // -- Controller role (R-C "send to device" / phone-as-remote) --

    private val _selectedTarget = MutableStateFlow<OnlineDeviceRow?>(null)

    /** The target device the user opened a remote for, or null when the remote sheet is closed. */
    val selectedTarget: StateFlow<OnlineDeviceRow?> = _selectedTarget.asStateFlow()

    /** Open the transport remote for [target] (a device that advertises the PLAY capability). */
    fun openRemote(target: OnlineDeviceRow) {
        _selectedTarget.value = target
    }

    /** Close the transport remote. */
    fun closeRemote() {
        _selectedTarget.value = null
    }

    /**
     * Send a transport command to the currently-selected target (CONTROLLER role). Scene IDs only —
     * never titles. No-op if no target is selected. The send goes through the active presence
     * session ([DeviceBusHost]); a relay failure is swallowed (best-effort remote).
     */
    fun sendToSelected(
        type: DeviceCommandType,
        sceneId: String? = null,
        sceneIds: List<String> = emptyList(),
        startSeconds: Double? = null,
        seekSeconds: Double? = null,
    ) {
        val target = _selectedTarget.value ?: return
        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            DeviceBusHost.sendCommand(
                OutgoingDeviceCommand(
                    targetDeviceId = target.id,
                    type = type,
                    sceneId = sceneId,
                    sceneIds = sceneIds,
                    startSeconds = startSeconds,
                    seekSeconds = seekSeconds,
                ),
            )
        }
    }

    /**
     * "Play this scene on the selected target" — the headline send-to-device action. Sends a PLAY
     * command carrying only the scene id (+ optional resume position).
     */
    fun castScene(
        sceneId: String,
        startSeconds: Double? = null,
    ) = sendToSelected(DeviceCommandType.PLAY, sceneId = sceneId, startSeconds = startSeconds)
}
