package com.github.damontecres.stashapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the PIN-gate selection for permanent disk deletion (Stream E, task 4). A
 * `delete_file=true` mutation must be gated behind a PIN when one is configured: the
 * read-only-mode PIN is preferred (its purpose is gating destructive actions), with the
 * app PIN as fallback. When neither is set the gate returns null (no PIN step) and the
 * caller falls back to the explicit reveal + confirm flow.
 */
class DeleteGatePinTest {
    @Test
    fun prefersReadOnlyPinWhenBothSet() {
        assertEquals("1234", deleteGatePin(readOnlyPin = "1234", appPin = "9999"))
    }

    @Test
    fun fallsBackToAppPin() {
        assertEquals("9999", deleteGatePin(readOnlyPin = "", appPin = "9999"))
        assertEquals("9999", deleteGatePin(readOnlyPin = null, appPin = "9999"))
        assertEquals("9999", deleteGatePin(readOnlyPin = "   ", appPin = "9999"))
    }

    @Test
    fun nullWhenNoPinConfigured() {
        assertNull(deleteGatePin(readOnlyPin = "", appPin = ""))
        assertNull(deleteGatePin(readOnlyPin = null, appPin = null))
        assertNull(deleteGatePin(readOnlyPin = "  ", appPin = "  "))
    }
}
