package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.api.DeviceCommandsSubscription
import com.github.damontecres.stashapp.api.DevicePresenceSubscription
import com.github.damontecres.stashapp.api.DevicesQuery
import com.github.damontecres.stashapp.api.fragment.DeviceFields

/**
 * Adapts Apollo-generated deviceBus types into the Apollo-free domain models ([DeviceSnapshot],
 * [DevicePresenceEvent], [DeviceCommand]). Keeping this boundary thin means the presence-store,
 * heartbeat, and command logic stay pure and unit-testable without Apollo on the classpath.
 *
 * All enum mapping goes through the domain `fromServer` helpers (via the generated enum's
 * `rawValue`) so an unknown future enum value degrades sensibly instead of crashing — invariant:
 * additive NG, tolerate version skew.
 */
object DeviceBusMapper {
    /** Adapt the shared `DeviceFields` fragment into a [DeviceSnapshot] (the wire carries no titles). */
    fun device(d: DeviceFields): DeviceSnapshot =
        DeviceSnapshot(
            id = d.id,
            name = d.name,
            kind = DeviceKind.fromServer(d.kind.rawValue),
            capabilities = d.capabilities.mapNotNull { DeviceCapability.fromServer(it.rawValue) },
            online = d.online,
            // `Time` is mapped to `Any` (a String on the wire); keep it opaque.
            lastSeen = d.lastSeen.toString(),
            playback = d.playback?.let { p ->
                PlaybackSnapshot(
                    sceneId = p.sceneId,
                    positionSeconds = p.positionSeconds,
                    paused = p.paused,
                    updatedAt = p.updatedAt.toString(),
                )
            },
        )

    /** Adapt one `devices` query row. */
    fun device(d: DevicesQuery.Device): DeviceSnapshot = device(d.deviceFields)

    /** Adapt a `devicePresence` subscription frame into a [DevicePresenceEvent]. */
    fun presenceEvent(e: DevicePresenceSubscription.DevicePresence): DevicePresenceEvent =
        DevicePresenceEvent(
            device = device(e.device.deviceFields),
            kind = PresenceChange.fromServer(e.kind.rawValue),
        )

    /**
     * Adapt a `deviceCommands` subscription frame into a [DeviceCommand], or null if the command
     * type is unknown to this client (ignored rather than acted on incorrectly).
     */
    fun command(c: DeviceCommandsSubscription.DeviceCommands): DeviceCommand? {
        val type = DeviceCommandType.fromServer(c.type.rawValue) ?: return null
        return DeviceCommand(
            fromDeviceId = c.fromDeviceId,
            type = type,
            sceneId = c.sceneId,
            sceneIds = c.sceneIds.orEmpty(),
            startSeconds = c.startSeconds,
            seekSeconds = c.seekSeconds,
        )
    }
}
