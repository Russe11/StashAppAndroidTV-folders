package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the persistence-backed TOFU store ([ControllerAuthorizationStore]) over an in-memory backing
 * (no Android). Verifies: unseen ⇒ ASK, remembered allow/block ⇒ ALLOWED/BLOCKED, blank controller
 * id normalizes to a single unidentified key (since the server currently sends `fromDeviceId=""`),
 * and forget/forgetAll reset.
 */
class ControllerAuthorizationStoreTest {
    /** A trivial in-memory [ControllerAuthorizationStore.Backing]. */
    private class FakeBacking : ControllerAuthorizationStore.Backing {
        val map = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String): Boolean? = map[key]

        override fun putBoolean(
            key: String,
            value: Boolean,
        ) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }

        override fun clear() = map.clear()
    }

    private fun store(): Pair<ControllerAuthorizationStore, FakeBacking> {
        val backing = FakeBacking()
        return ControllerAuthorizationStore(backing) to backing
    }

    @Test
    fun unseenController_asks() {
        val (s, _) = store()
        assertEquals(ControllerVerdict.ASK, s.verdict("controllerA"))
    }

    @Test
    fun rememberedAllow_isAllowed() {
        val (s, _) = store()
        s.remember("controllerA", true)
        assertEquals(ControllerVerdict.ALLOWED, s.verdict("controllerA"))
    }

    @Test
    fun rememberedBlock_isBlocked() {
        val (s, _) = store()
        s.remember("controllerA", false)
        assertEquals(ControllerVerdict.BLOCKED, s.verdict("controllerA"))
    }

    @Test
    fun blankControllerId_collapsesToUnidentifiedKey() {
        val (s, backing) = store()
        // Server currently sends fromDeviceId="" — both an empty string and the sentinel resolve to
        // the same remembered decision.
        s.remember("", true)
        assertEquals(ControllerVerdict.ALLOWED, s.verdict("   "))
        assertEquals(ControllerVerdict.ALLOWED, s.verdict(ControllerAuthorization.UNIDENTIFIED))
        // Exactly one key was written, keyed by the unidentified sentinel.
        assertEquals(1, backing.map.size)
    }

    @Test
    fun decisionsAreKeyedPerController() {
        val (s, _) = store()
        s.remember("a", true)
        s.remember("b", false)
        assertEquals(ControllerVerdict.ALLOWED, s.verdict("a"))
        assertEquals(ControllerVerdict.BLOCKED, s.verdict("b"))
        assertEquals(ControllerVerdict.ASK, s.verdict("c"))
    }

    @Test
    fun forget_revertsToAsk() {
        val (s, _) = store()
        s.remember("a", true)
        s.forget("a")
        assertEquals(ControllerVerdict.ASK, s.verdict("a"))
    }

    @Test
    fun forgetAll_clearsEverything() {
        val (s, _) = store()
        s.remember("a", true)
        s.remember("b", false)
        s.forgetAll()
        assertEquals(ControllerVerdict.ASK, s.verdict("a"))
        assertEquals(ControllerVerdict.ASK, s.verdict("b"))
    }
}
