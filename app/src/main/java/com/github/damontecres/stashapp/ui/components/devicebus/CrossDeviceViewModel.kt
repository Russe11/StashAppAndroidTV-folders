package com.github.damontecres.stashapp.ui.components.devicebus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.realtime.DeviceBusHost
import com.github.damontecres.stashapp.util.realtime.DeviceBusPreferences
import com.github.damontecres.stashapp.util.realtime.DeviceIdentity
import com.github.damontecres.stashapp.util.realtime.OnlineDeviceRow
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
}
