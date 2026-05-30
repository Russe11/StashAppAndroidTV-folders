package com.github.damontecres.stashapp.util.realtime

/**
 * Pure reducer that maintains the "online devices" map from a snapshot ([replaceAll], from the
 * `devices` query) plus a live stream of [DevicePresenceEvent]s (from the `devicePresence`
 * subscription). No coroutines, no I/O, no clock — it's a deterministic state machine so the
 * online-devices logic is unit-testable in isolation; the repository wraps it with the actual
 * flows and exposes the result as observable UI state.
 *
 * Reduction rules (design §5 presence semantics):
 *  - [PresenceChange.ONLINE] / [PresenceChange.PLAYBACK]: upsert the device (latest wins).
 *  - [PresenceChange.OFFLINE]: remove the device (it was evicted / unregistered).
 *  - A device's own [DeviceSnapshot.id] is excluded from the visible list (you don't list
 *    yourself) so the UI shows *other* devices; pass it via [selfDeviceId].
 *
 * Iteration order is insertion order ([LinkedHashMap]) so the UI list is stable across updates
 * rather than reshuffling on every event.
 */
class DevicePresenceStore(
    private val selfDeviceId: String? = null,
) {
    private val byId = LinkedHashMap<String, DeviceSnapshot>()

    /**
     * Replace the entire set from a fresh `devices` snapshot (e.g. on (re)connect). Self is
     * filtered out. Returns the new visible list.
     */
    fun replaceAll(devices: Collection<DeviceSnapshot>): List<DeviceSnapshot> {
        byId.clear()
        for (d in devices) {
            if (d.id == selfDeviceId) continue
            byId[d.id] = d
        }
        return snapshot()
    }

    /**
     * Apply one presence event and return the new visible list. An event about *this* device is
     * ignored (you don't appear in your own list). OFFLINE removes; ONLINE/PLAYBACK upsert.
     */
    fun apply(event: DevicePresenceEvent): List<DeviceSnapshot> {
        val device = event.device
        if (device.id != selfDeviceId) {
            when (event.kind) {
                PresenceChange.OFFLINE -> byId.remove(device.id)
                PresenceChange.ONLINE, PresenceChange.PLAYBACK -> byId[device.id] = device
            }
        }
        return snapshot()
    }

    /** The current visible online-devices list (excludes self), in stable insertion order. */
    fun snapshot(): List<DeviceSnapshot> = byId.values.toList()

    /** Drop all devices (e.g. on opt-out / server switch / disconnect). */
    fun clear() {
        byId.clear()
    }
}
