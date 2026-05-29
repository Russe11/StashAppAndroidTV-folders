package com.github.damontecres.stashapp.util

import androidx.biometric.BiometricManager

/**
 * Pure, headless-testable logic for the biometric app-lock that sits alongside the existing
 * PIN access-gate.
 *
 * The PIN gate (see [com.github.damontecres.stashapp.PinFragment] and
 * [com.github.damontecres.stashapp.RootActivity]) is the source of truth for *whether* the app
 * is locked. Biometrics are an opt-in *unlock method* layered on top of it: when the user has
 * set an app PIN and enabled "Require biometric unlock", the gate first offers
 * [androidx.biometric.BiometricPrompt] (allowing the device-credential fallback so it works on
 * TVs without a fingerprint sensor). The PIN field stays available as the alternative.
 *
 * None of this logic logs, records, or otherwise persists the biometric result — the only state
 * it consumes is the user's preferences and the platform's capability report.
 */
object BiometricLock {
    /**
     * Authenticators requested from [BiometricManager] / `BiometricPrompt`.
     *
     * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` deliberately allows the device PIN/pattern/password
     * fallback. This is required for Android TV and other devices that have no biometric sensor:
     * the user can still satisfy the prompt with their device credential.
     */
    const val ALLOWED_AUTHENTICATORS: Int =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether biometric / device-credential auth can be used on this device right now.
     *
     * Maps a raw [BiometricManager.canAuthenticate] status to a simple boolean. Anything other
     * than [BiometricManager.BIOMETRIC_SUCCESS] (no hardware, hardware unavailable, none enrolled,
     * unsupported, status unknown) means biometrics can't be presented, so the gate must fall back
     * to the PIN alone.
     */
    fun isBiometricAvailable(canAuthenticateStatus: Int): Boolean = canAuthenticateStatus == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Decide what the access-gate should do given the current state.
     *
     * @param pinSet whether an app PIN is configured (the gate only shows at all when this is true)
     * @param biometricEnabled the "Require biometric unlock" toggle
     * @param biometricAvailable result of [isBiometricAvailable] for this device
     */
    fun decide(
        pinSet: Boolean,
        biometricEnabled: Boolean,
        biometricAvailable: Boolean,
    ): GateAction =
        when {
            // No PIN configured -> the gate never engages; biometrics are meaningless without it.
            !pinSet -> GateAction.NoGate
            // Opted in and the device can actually present a prompt (biometric or device credential).
            biometricEnabled && biometricAvailable -> GateAction.PromptBiometric
            // PIN gate only: either the user didn't opt in, or biometrics are unavailable so we
            // safely fall back to the PIN the user already set.
            else -> GateAction.PinOnly
        }

    /**
     * Convenience: should the gate present [androidx.biometric.BiometricPrompt]?
     *
     * Equivalent to [decide] returning [GateAction.PromptBiometric].
     */
    fun shouldPromptBiometric(
        pinSet: Boolean,
        biometricEnabled: Boolean,
        biometricAvailable: Boolean,
    ): Boolean = decide(pinSet, biometricEnabled, biometricAvailable) == GateAction.PromptBiometric

    /**
     * The action the access-gate should take.
     */
    enum class GateAction {
        /** No app PIN set: skip the gate entirely and go straight to the app. */
        NoGate,

        /** Show the PIN entry only (no biometric prompt). */
        PinOnly,

        /** Present BiometricPrompt; the PIN entry remains as the fallback. */
        PromptBiometric,
    }
}
