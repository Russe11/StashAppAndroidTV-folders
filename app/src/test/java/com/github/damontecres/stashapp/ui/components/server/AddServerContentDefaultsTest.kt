package com.github.damontecres.stashapp.ui.components.server

import org.junit.Assert.assertTrue
import org.junit.Test

class AddServerContentDefaultsTest {
    @Test
    fun freshAddServerUsesUsernameByDefault() {
        assertTrue(USE_USERNAME_BY_DEFAULT)
    }
}
