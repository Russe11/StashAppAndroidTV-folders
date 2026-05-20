package com.github.damontecres.stashapp

import com.github.damontecres.stashapp.ui.components.prefs.StashPreference
import com.github.damontecres.stashapp.util.AppUpgradeHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpgradeHandlerTest {
    @Test
    fun migrateDefaultUpdateUrlMovesLegacyDefaultToFork() {
        assertEquals(
            StashPreference.DEFAULT_UPDATE_URL,
            AppUpgradeHandler.migrateDefaultUpdateUrl(StashPreference.LEGACY_DEFAULT_UPDATE_URL),
        )
    }

    @Test
    fun migrateDefaultUpdateUrlPreservesCustomUrl() {
        val customUrl = "https://example.com/custom/releases/latest"

        assertEquals(customUrl, AppUpgradeHandler.migrateDefaultUpdateUrl(customUrl))
    }
}
