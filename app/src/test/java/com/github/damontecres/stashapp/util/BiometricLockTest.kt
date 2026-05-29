package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.util.BiometricLock.GateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless unit tests for [BiometricLock], the pure lock-state / should-prompt logic that the
 * app-entry gate ([com.github.damontecres.stashapp.PinFragment]) uses to decide between showing
 * the PIN only and offering a [androidx.biometric.BiometricPrompt].
 *
 * The `BiometricPrompt` UI and the real [androidx.biometric.BiometricManager.canAuthenticate]
 * call require an Android device/instrumented environment and cannot be exercised headlessly;
 * those are covered by on-device verification. These tests pin the decision table and the
 * availability mapping.
 *
 * `canAuthenticate` status literals used below mirror
 * `androidx.biometric.BiometricManager`: BIOMETRIC_SUCCESS=0, BIOMETRIC_ERROR_HW_UNAVAILABLE=1,
 * BIOMETRIC_ERROR_NONE_ENROLLED=11, BIOMETRIC_ERROR_NO_HARDWARE=12. Authenticator flags:
 * BIOMETRIC_STRONG=0x000F, DEVICE_CREDENTIAL=0x8000.
 */
class BiometricLockTest {
    @Test
    fun allowedAuthenticatorsIncludeStrongBiometricAndDeviceCredentialFallback() {
        // BIOMETRIC_STRONG (0x000F) or DEVICE_CREDENTIAL (0x8000) — the device-credential bit is
        // what lets TVs without a fingerprint sensor satisfy the prompt with a device PIN.
        assertEquals(0x000F or 0x8000, BiometricLock.ALLOWED_AUTHENTICATORS)
        assertTrue("DEVICE_CREDENTIAL fallback must be allowed", BiometricLock.ALLOWED_AUTHENTICATORS and 0x8000 != 0)
        assertTrue("BIOMETRIC_STRONG must be allowed", BiometricLock.ALLOWED_AUTHENTICATORS and 0x000F != 0)
    }

    @Test
    fun isBiometricAvailableOnlyForSuccessStatus() {
        assertTrue(BiometricLock.isBiometricAvailable(0)) // BIOMETRIC_SUCCESS
        assertFalse(BiometricLock.isBiometricAvailable(1)) // ERROR_HW_UNAVAILABLE
        assertFalse(BiometricLock.isBiometricAvailable(11)) // ERROR_NONE_ENROLLED
        assertFalse(BiometricLock.isBiometricAvailable(12)) // ERROR_NO_HARDWARE
        assertFalse(BiometricLock.isBiometricAvailable(-1)) // ERROR_UNSUPPORTED / status unknown
    }

    @Test
    fun noGateWhenPinNotSet() {
        // Without an app PIN the gate never engages, regardless of the biometric toggle/availability.
        assertEquals(GateAction.NoGate, BiometricLock.decide(pinSet = false, biometricEnabled = false, biometricAvailable = false))
        assertEquals(GateAction.NoGate, BiometricLock.decide(pinSet = false, biometricEnabled = true, biometricAvailable = true))
        assertFalse(BiometricLock.shouldPromptBiometric(pinSet = false, biometricEnabled = true, biometricAvailable = true))
    }

    @Test
    fun promptsBiometricWhenPinSetEnabledAndAvailable() {
        assertEquals(
            GateAction.PromptBiometric,
            BiometricLock.decide(pinSet = true, biometricEnabled = true, biometricAvailable = true),
        )
        assertTrue(BiometricLock.shouldPromptBiometric(pinSet = true, biometricEnabled = true, biometricAvailable = true))
    }

    @Test
    fun pinOnlyWhenBiometricDisabled() {
        assertEquals(
            GateAction.PinOnly,
            BiometricLock.decide(pinSet = true, biometricEnabled = false, biometricAvailable = true),
        )
        assertFalse(BiometricLock.shouldPromptBiometric(pinSet = true, biometricEnabled = false, biometricAvailable = true))
    }

    @Test
    fun pinOnlyFallbackWhenBiometricEnabledButUnavailable() {
        // Opted in, but the device can't present a prompt (no sensor + no credential, none enrolled,
        // etc.) — the PIN the user already set is the safe fallback so they are never locked out.
        assertEquals(
            GateAction.PinOnly,
            BiometricLock.decide(pinSet = true, biometricEnabled = true, biometricAvailable = false),
        )
        assertFalse(BiometricLock.shouldPromptBiometric(pinSet = true, biometricEnabled = true, biometricAvailable = false))
    }
}
