package com.github.damontecres.stashapp.util.realtime

import android.util.Log
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.api.DevicesQuery
import com.github.damontecres.stashapp.api.RegisterDeviceMutation
import com.github.damontecres.stashapp.api.SendDeviceCommandMutation
import com.github.damontecres.stashapp.api.UnregisterDeviceMutation
import com.github.damontecres.stashapp.api.UpdatePlaybackStateMutation
import com.github.damontecres.stashapp.api.type.DeviceCommandInput
import com.github.damontecres.stashapp.api.type.DeviceInput
import com.github.damontecres.stashapp.api.type.PlaybackStateInput
import com.github.damontecres.stashapp.util.MutationEngine
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.api.type.DeviceCapability as ApiDeviceCapability
import com.github.damontecres.stashapp.api.type.DeviceCommandType as ApiDeviceCommandType
import com.github.damontecres.stashapp.api.type.DeviceKind as ApiDeviceKind

/**
 * Executes the NG deviceBus query + mutations (registerDevice / heartbeat, unregisterDevice,
 * updatePlaybackState, sendDeviceCommand, devices) against a server, adapting Apollo types to the
 * Apollo-free domain models.
 *
 * Callers MUST gate every call on both the `deviceBus` capability and the opt-in toggle (the
 * [DeviceBusRepository] does this) — this engine just runs the op. The deviceBus mutations are
 * presence/control, NOT content edits, so they pass `overrideReadOnly = true` (read-only mode is
 * about not editing library content, not about announcing presence).
 */
class DeviceBusEngine(
    private val server: StashServer,
) {
    private val queryEngine = QueryEngine(server)
    private val mutationEngine = MutationEngine(server)

    /** Online devices (lastSeen within TTL). Drives the online-devices list / send-to picker. */
    suspend fun devices(): List<DeviceSnapshot> {
        val data = queryEngine.executeQuery(DevicesQuery()).data ?: return emptyList()
        return data.devices.map { DeviceBusMapper.device(it) }
    }

    /**
     * Register this device (connect) and on each ~20s heartbeat tick. Upserts on the server,
     * refreshes lastSeen, emits ONLINE on a fresh/recovered device. Returns the server's
     * authoritative view of this device.
     */
    suspend fun registerDevice(registration: DeviceRegistration): DeviceSnapshot {
        val input =
            DeviceInput(
                id = registration.id,
                name = registration.name,
                kind = registration.kind.toApi(),
                capabilities = registration.capabilities.map { it.toApi() },
            )
        val data = mutationEngine.executeMutation(RegisterDeviceMutation(input = input), true).data!!
        return DeviceBusMapper.device(data.registerDevice.deviceFields)
    }

    /** Graceful exit (opt-out / shutdown). Emits OFFLINE; returns true if the device was present. */
    suspend fun unregisterDevice(deviceId: String): Boolean {
        val data = mutationEngine.executeMutation(UnregisterDeviceMutation(deviceId = deviceId), true).data
        return data?.unregisterDevice ?: false
    }

    /**
     * Report live playback (debounced ~2s by the reporting device). Emits PLAYBACK; returns false
     * if the device is not registered. [PlaybackStateUpdate.sceneId] is null when stopped/idle.
     */
    suspend fun updatePlaybackState(update: PlaybackStateUpdate): Boolean {
        val input =
            PlaybackStateInput(
                deviceId = update.deviceId,
                sceneId = Optional.presentIfNotNull(update.sceneId),
                positionSeconds = update.positionSeconds,
                paused = update.paused,
            )
        val data = mutationEngine.executeMutation(UpdatePlaybackStateMutation(input = input), true).data
        return data?.updatePlaybackState ?: false
    }

    /**
     * Send a command from this controller to a target device (relayed by the server). Returns true
     * only if a live target subscriber received it. (Controller role — R-C; the TV is primarily a
     * target but is capability `CONTROL` too.)
     */
    suspend fun sendDeviceCommand(command: OutgoingDeviceCommand): Boolean {
        val input =
            DeviceCommandInput(
                targetDeviceId = command.targetDeviceId,
                type = command.type.toApi(),
                sceneId = Optional.presentIfNotNull(command.sceneId),
                sceneIds = Optional.presentIfNotNull(command.sceneIds.takeIf { it.isNotEmpty() }),
                startSeconds = Optional.presentIfNotNull(command.startSeconds),
                seekSeconds = Optional.presentIfNotNull(command.seekSeconds),
            )
        val data = mutationEngine.executeMutation(SendDeviceCommandMutation(input = input), true).data
        return data?.sendDeviceCommand ?: false
    }

    companion object {
        private const val TAG = "DeviceBusEngine"
    }
}

/** This device's registration payload (id + name + role). */
data class DeviceRegistration(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val capabilities: List<DeviceCapability>,
)

/** A playback-state report for the reporting device. */
data class PlaybackStateUpdate(
    val deviceId: String,
    val sceneId: String?,
    val positionSeconds: Double,
    val paused: Boolean,
)

/** A command this controller is sending to a target. */
data class OutgoingDeviceCommand(
    val targetDeviceId: String,
    val type: DeviceCommandType,
    val sceneId: String? = null,
    val sceneIds: List<String> = emptyList(),
    val startSeconds: Double? = null,
    val seekSeconds: Double? = null,
)

private fun DeviceKind.toApi(): ApiDeviceKind = ApiDeviceKind.safeValueOf(name)

private fun DeviceCapability.toApi(): ApiDeviceCapability = ApiDeviceCapability.safeValueOf(name)

private fun DeviceCommandType.toApi(): ApiDeviceCommandType = ApiDeviceCommandType.safeValueOf(name)
