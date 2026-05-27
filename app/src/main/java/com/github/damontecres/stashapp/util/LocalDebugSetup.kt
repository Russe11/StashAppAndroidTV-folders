package com.github.damontecres.stashapp.util

import com.github.damontecres.stashapp.BuildConfig

data class LocalDebugServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

object LocalDebugSetup {
    val credentials: LocalDebugServerCredentials? =
        credentialsFrom(
            autoSetup = BuildConfig.DEBUG_LOCAL_SERVER_AUTO_SETUP,
            serverUrl = BuildConfig.DEBUG_STASH_URL,
            username = BuildConfig.DEBUG_STASH_USERNAME,
            password = BuildConfig.DEBUG_STASH_PASSWORD,
        )

    val disablePin: Boolean =
        credentials != null && BuildConfig.DEBUG_LOCAL_SERVER_NO_PIN

    internal fun credentialsFrom(
        autoSetup: Boolean,
        serverUrl: String,
        username: String,
        password: String,
    ): LocalDebugServerCredentials? {
        if (!autoSetup) return null
        val cleanServerUrl = serverUrl.trim()
        val cleanUsername = username.trim()
        if (cleanServerUrl.isBlank() || cleanUsername.isBlank() || password.isBlank()) return null
        return LocalDebugServerCredentials(
            serverUrl = cleanServerUrl,
            username = cleanUsername,
            password = password,
        )
    }
}
