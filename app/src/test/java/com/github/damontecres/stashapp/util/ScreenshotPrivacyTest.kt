package com.github.damontecres.stashapp.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless unit tests for [ScreenshotPrivacy], the pure decision behind whether
 * [com.github.damontecres.stashapp.RootActivity] applies
 * [android.view.WindowManager.LayoutParams.FLAG_SECURE].
 *
 * The actual `FLAG_SECURE` effect (blocked screenshots / recording / casting and the blanked
 * recents thumbnail) needs a real device or instrumented environment and is covered by on-device
 * verification. These tests pin the privacy-first default and the explicit opt-out.
 */
class ScreenshotPrivacyTest {
    @Test
    fun securesWindowByDefaultWhenNoPreferenceStored() {
        // Fresh install / preference never set -> privacy-first ON for an adult-media viewer.
        assertTrue(ScreenshotPrivacy.shouldSecureWindow(null))
    }

    @Test
    fun defaultEnabledIsTrue() {
        assertTrue(ScreenshotPrivacy.DEFAULT_ENABLED)
        // The null (unset) case must agree with the published default.
        assertEqualsDefault(ScreenshotPrivacy.shouldSecureWindow(null))
    }

    @Test
    fun securesWindowWhenExplicitlyEnabled() {
        assertTrue(ScreenshotPrivacy.shouldSecureWindow(true))
    }

    @Test
    fun doesNotSecureWindowWhenExplicitlyDisabled() {
        // The only way the window is left unsecured is an explicit user opt-out.
        assertFalse(ScreenshotPrivacy.shouldSecureWindow(false))
    }

    private fun assertEqualsDefault(actual: Boolean) {
        assertTrue(actual == ScreenshotPrivacy.DEFAULT_ENABLED)
    }
}
