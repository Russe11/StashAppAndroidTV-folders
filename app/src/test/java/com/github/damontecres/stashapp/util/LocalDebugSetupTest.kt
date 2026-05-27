package com.github.damontecres.stashapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDebugSetupTest {
    @Test
    fun credentialsFrom_ignoresValuesWhenAutoSetupIsDisabled() {
        val credentials =
            LocalDebugSetup.credentialsFrom(
                autoSetup = false,
                serverUrl = "http://localhost:9999",
                username = "debug",
                password = "pw",
            )

        assertNull(credentials)
    }

    @Test
    fun credentialsFrom_requiresCompleteCredentials() {
        assertNull(
            LocalDebugSetup.credentialsFrom(
                autoSetup = true,
                serverUrl = "http://localhost:9999",
                username = "debug",
                password = "",
            ),
        )
    }

    @Test
    fun credentialsFrom_trimsUrlAndUsernameButKeepsPasswordVerbatim() {
        val credentials =
            LocalDebugSetup.credentialsFrom(
                autoSetup = true,
                serverUrl = " http://localhost:9999 ",
                username = " debug ",
                password = " pw ",
            )

        assertEquals(
            LocalDebugServerCredentials(
                serverUrl = "http://localhost:9999",
                username = "debug",
                password = " pw ",
            ),
            credentials,
        )
    }
}
