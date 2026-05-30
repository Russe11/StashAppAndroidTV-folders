package com.github.damontecres.stashapp.util.realtime

/**
 * Apollo-free domain models for the NG deviceBus presence/command surface (design §5).
 *
 * Deliberately decoupled from the Apollo-generated `DeviceData` / subscription types so the
 * presence-store, heartbeat, and command logic is pure and unit-testable without standing up
 * Apollo. Repositories adapt the generated types into these on receipt.
 *
 * Privacy: payloads carry IDs only — never titles or paths (the wire payload has none either).
 * Scene names are resolved locally by the UI from [PlaybackSnapshot.sceneId].
 */

/** Device class on the presence bus. Mirrors the server `DeviceKind` enum. */
enum class DeviceKind {
    TV,
    DESKTOP,
    MOBILE,
    OTHER,
    ;

    companion object {
        /** Map a server `DeviceKind` enum name (case-insensitive); unknown ⇒ [OTHER]. */
        fun fromServer(raw: String): DeviceKind =
            when (raw.trim().uppercase()) {
                "TV" -> TV
                "DESKTOP" -> DESKTOP
                "MOBILE" -> MOBILE
                else -> OTHER
            }
    }
}

/** What a device can do. Mirrors the server `DeviceCapability` enum. */
enum class DeviceCapability {
    /** Can be a playback target (obeys deviceCommands in its player). */
    PLAY,

    /** Can drive other devices (send deviceCommands). */
    CONTROL,
    ;

    companion object {
        /** Map a server `DeviceCapability` name (case-insensitive); unknown ⇒ null (dropped). */
        fun fromServer(raw: String): DeviceCapability? =
            when (raw.trim().uppercase()) {
                "PLAY" -> PLAY
                "CONTROL" -> CONTROL
                else -> null
            }
    }
}

/** The kind of presence change a [DevicePresenceEvent] reports. Mirrors `PresenceChange`. */
enum class PresenceChange {
    ONLINE,
    OFFLINE,
    PLAYBACK,
    ;

    companion object {
        /** Map a server `PresenceChange` name (case-insensitive); unknown ⇒ [PLAYBACK] (refresh). */
        fun fromServer(raw: String): PresenceChange =
            when (raw.trim().uppercase()) {
                "ONLINE" -> ONLINE
                "OFFLINE" -> OFFLINE
                else -> PLAYBACK
            }
    }
}

/** Remote-control verbs a controller can send a target. Mirrors `DeviceCommandType`. */
enum class DeviceCommandType {
    PLAY,
    PAUSE,
    RESUME,
    SEEK,
    STOP,
    ENQUEUE,
    NEXT,
    PREV,
    ;

    companion object {
        /** Map a server `DeviceCommandType` name (case-insensitive); unknown ⇒ null (ignored). */
        fun fromServer(raw: String): DeviceCommandType? =
            when (raw.trim().uppercase()) {
                "PLAY" -> PLAY
                "PAUSE" -> PAUSE
                "RESUME" -> RESUME
                "SEEK" -> SEEK
                "STOP" -> STOP
                "ENQUEUE" -> ENQUEUE
                "NEXT" -> NEXT
                "PREV" -> PREV
                else -> null
            }
    }
}

/**
 * A device on the presence bus. Pure snapshot — the wire payload carries no titles, so a UI showing
 * "what's playing" resolves the scene name locally from [playback]`.sceneId`.
 *
 * @param lastSeen the raw server RFC3339 string (opaque to the client; never parsed for logic — the
 *   server's [online] flag is authoritative for TTL).
 */
data class DeviceSnapshot(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val capabilities: List<DeviceCapability>,
    val online: Boolean,
    val lastSeen: String,
    val playback: PlaybackSnapshot?,
) {
    /** True if this device can be a playback target (a "send-to" candidate). */
    val isPlayTarget: Boolean get() = DeviceCapability.PLAY in capabilities

    /** True if this device can drive others (a controller). */
    val isController: Boolean get() = DeviceCapability.CONTROL in capabilities
}

/** A device's live playback signal. IDs not titles; resolve [sceneId] locally for display. */
data class PlaybackSnapshot(
    /** The scene the device is playing, by id (null when stopped/idle). */
    val sceneId: String?,
    val positionSeconds: Double,
    val paused: Boolean,
    /** Raw server RFC3339 string; opaque (used only for display "last updated"). */
    val updatedAt: String,
)

/** A presence/playback change for one device, decoded from the `devicePresence` subscription. */
data class DevicePresenceEvent(
    val device: DeviceSnapshot,
    val kind: PresenceChange,
)

/**
 * A command relayed to this target device, decoded from the `deviceCommands` subscription. The
 * player (R-C) maps this to an action.
 *
 * NOTE: [fromDeviceId] is currently always the empty string on the wire (the server has no
 * controller-id field yet) — do not rely on it for TOFU until the server adds it.
 */
data class DeviceCommand(
    val fromDeviceId: String,
    val type: DeviceCommandType,
    val sceneId: String?,
    val sceneIds: List<String>,
    val startSeconds: Double?,
    val seekSeconds: Double?,
)
