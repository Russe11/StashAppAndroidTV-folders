package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure [DevicePresenceStore] reducer that backs the online-devices list: snapshot
 * seeding, ONLINE/PLAYBACK upsert, OFFLINE removal, self-exclusion, and stable ordering.
 */
class DevicePresenceStoreTest {
    private fun device(
        id: String,
        name: String = "dev-$id",
        playbackSceneId: String? = null,
    ) = DeviceSnapshot(
        id = id,
        name = name,
        kind = DeviceKind.TV,
        capabilities = listOf(DeviceCapability.PLAY),
        online = true,
        lastSeen = "t",
        playback = playbackSceneId?.let { PlaybackSnapshot(it, 0.0, false, "t") },
    )

    private fun event(
        device: DeviceSnapshot,
        kind: PresenceChange,
    ) = DevicePresenceEvent(device, kind)

    @Test
    fun replaceAll_seedsAndExcludesSelf() {
        val store = DevicePresenceStore(selfDeviceId = "self")
        val list = store.replaceAll(listOf(device("self"), device("a"), device("b")))
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun online_upserts_offline_removes() {
        val store = DevicePresenceStore(selfDeviceId = "self")
        store.replaceAll(listOf(device("a")))

        var list = store.apply(event(device("b"), PresenceChange.ONLINE))
        assertEquals(listOf("a", "b"), list.map { it.id })

        list = store.apply(event(device("a"), PresenceChange.OFFLINE))
        assertEquals(listOf("b"), list.map { it.id })
    }

    @Test
    fun playback_event_updatesExistingDeviceInPlace() {
        val store = DevicePresenceStore()
        store.replaceAll(listOf(device("a"), device("b")))

        val list =
            store.apply(event(device("a", playbackSceneId = "42"), PresenceChange.PLAYBACK))
        // Order is preserved (a stays first) and its playback is now populated.
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals("42", list.first { it.id == "a" }.playback?.sceneId)
    }

    @Test
    fun selfEvents_areIgnored() {
        val store = DevicePresenceStore(selfDeviceId = "self")
        val list = store.apply(event(device("self"), PresenceChange.ONLINE))
        assertEquals(emptyList<String>(), list.map { it.id })
    }

    @Test
    fun ordering_isInsertionStableAcrossUpserts() {
        val store = DevicePresenceStore()
        store.apply(event(device("a"), PresenceChange.ONLINE))
        store.apply(event(device("b"), PresenceChange.ONLINE))
        store.apply(event(device("c"), PresenceChange.ONLINE))
        // Re-upsert 'a' (e.g. a playback event) — it should NOT jump to the end.
        val list = store.apply(event(device("a", playbackSceneId = "1"), PresenceChange.PLAYBACK))
        assertEquals(listOf("a", "b", "c"), list.map { it.id })
    }

    @Test
    fun clear_emptiesTheList() {
        val store = DevicePresenceStore()
        store.replaceAll(listOf(device("a"), device("b")))
        store.clear()
        assertEquals(emptyList<String>(), store.snapshot().map { it.id })
    }
}
