package com.github.damontecres.stashapp.util.realtime

/**
 * A presentation-ready row for the online-devices list: the device, plus a locally-resolved
 * playback line (the wire never carries the title, so [playbackLine] is built from a separately
 * resolved scene name). Pure data — the formatting is in [from] so it's unit-testable.
 */
data class OnlineDeviceRow(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val online: Boolean,
    val capabilities: List<DeviceCapability>,
    /** A one-line "what's playing" summary, or null when the device isn't reporting playback. */
    val playbackLine: String?,
) {
    companion object {
        /**
         * Build a display row from a [device] snapshot and a locally [resolvedSceneName] (null if it
         * couldn't be resolved or the device isn't playing). The playback line is built only from
         * client-side data: the scene *name* the client resolved itself, plus the numeric position
         * the server reported — never a server-supplied title.
         */
        fun from(
            device: DeviceSnapshot,
            resolvedSceneName: String?,
        ): OnlineDeviceRow =
            OnlineDeviceRow(
                id = device.id,
                name = device.name,
                kind = device.kind,
                online = device.online,
                capabilities = device.capabilities,
                playbackLine = playbackLine(device.playback, resolvedSceneName),
            )

        /**
         * Format the "what's playing" line, or null when there's no live scene. Examples:
         *  - playing a known scene:  "Playing My Scene · 12:30"
         *  - paused a known scene:   "Paused My Scene · 12:30"
         *  - scene id unresolved:    "Playing Scene #42 · 12:30"
         *  - reported but no scene:  null (idle/stopped)
         */
        fun playbackLine(
            playback: PlaybackSnapshot?,
            resolvedSceneName: String?,
        ): String? {
            val pb = playback ?: return null
            val sceneId = pb.sceneId ?: return null
            val name = resolvedSceneName?.takeIf { it.isNotBlank() } ?: "Scene #$sceneId"
            val verb = if (pb.paused) "Paused" else "Playing"
            return "$verb $name · ${formatPosition(pb.positionSeconds)}"
        }

        /** Format a position in seconds as m:ss or h:mm:ss. Negative/NaN clamps to 0:00. */
        fun formatPosition(seconds: Double): String {
            val total = if (seconds.isFinite() && seconds > 0) seconds.toLong() else 0L
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) {
                "%d:%02d:%02d".format(h, m, s)
            } else {
                "%d:%02d".format(m, s)
            }
        }
    }
}
