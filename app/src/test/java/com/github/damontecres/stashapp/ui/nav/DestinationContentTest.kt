package com.github.damontecres.stashapp.ui.nav

import com.github.damontecres.stashapp.navigation.Destination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationContentTest {
    @Test
    fun settingsPinDestinationIsSupportedByComposeContent() {
        assertFalse(isUnsupportedComposeDestination(Destination.SettingsPin))
    }

    @Test
    fun appPinDestinationStillUsesLegacyFragment() {
        assertTrue(isUnsupportedComposeDestination(Destination.Pin))
    }
}
