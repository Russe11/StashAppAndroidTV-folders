package com.github.damontecres.stashapp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.stashapp.SettingsFragment
import com.github.damontecres.stashapp.StashApplication
import com.github.damontecres.stashapp.StashExoPlayer
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a server
 */
data class StashServer(
    val url: String,
    val apiKey: String?,
) {

    /**
     * The server side preferences
     *
     * Note: needs to populated via [updateServerPrefs]!
     */
    val serverPreferences by lazy { ServerPreferences(this) }

    /**
     * The server's version
     *
     * Depends on [serverPreferences] which depends on [updateServerPrefs]!
     */
    val version: Version
        get() {
            return serverPreferences.serverVersion
        }

    val okHttpClient by lazy { StashClient.createOkHttpClient(this) }
    val apolloClient by lazy { StashClient.createApolloClient(this) }
    val streamingOkHttpClient by lazy { okHttpClient.newBuilder().cache(null).build() }

    /**
     * Query the server for preferences
     */
    suspend fun updateServerPrefs(): ServerPreferences {
        val queryEngine = QueryEngine(this)
        val result = queryEngine.getServerConfiguration()
        serverPreferences.updatePreferences(result)
        // NG capability handshake: probe + persist so NG-gated paths (e.g. the deletedSince
        // deletion feed) can be enabled at connect rather than re-probed on every operation.
        // A probe failure must not fail the whole connect — the persisted default is
        // ServerCapabilities.UPSTREAM (NG paths off), which is the safe fallback.
        try {
            serverPreferences.updateServerCapabilities(queryEngine.getServerCapabilities())
        } catch (t: Throwable) {
            android.util.Log.w("StashServer", "serverCapabilities probe failed for $url: ${t.message}", t)
        }
        return serverPreferences
    }

    override fun toString(): String = "StashServer(url=$url, apiKey?=${apiKey.isNotNullOrBlank()})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StashServer

        if (url != other.url) return false
        if (apiKey != other.apiKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (apiKey?.hashCode() ?: 0)
        return result
    }

    companion object {
        private const val SERVER_PREF_PREFIX = "server_"
        private const val SERVER_APIKEY_PREF_PREFIX = "apikey_"

        private val servers = ConcurrentHashMap<String, StashServer>()

        /**
         * Trim the API key and treat blank as absent. Done once here so every consumer
         * (request headers, ExoPlayer direct-play, Glide image loads) gets the same
         * whitespace-free value and a key pasted with a trailing newline no longer 401s.
         */
        fun normalizeApiKey(apiKey: String?): String? = apiKey?.trim()?.ifBlank { null }

        /**
         * The encrypted-at-rest store holding the active server URL/key and every
         * `apikey_<url>` entry. The full-access key must never live in plaintext prefs.
         */
        private fun secure(context: Context): SharedPreferences = SecurePreferences.get(context)

        /**
         * Read a secret, migrating it out of the legacy plaintext default-prefs entry
         * on first access, then clearing the plaintext copy. Returns the secure value.
         */
        private fun readMigratingSecret(
            context: Context,
            key: String,
        ): String? {
            val securePrefs = secure(context)
            if (securePrefs.contains(key)) {
                return securePrefs.getString(key, null)
            }
            val plain = PreferenceManager.getDefaultSharedPreferences(context)
            if (plain.contains(key)) {
                val value = plain.all[key]?.toString()
                securePrefs.edit(true) { putString(key, value) }
                plain.edit(true) { remove(key) }
                return value
            }
            return null
        }

        /**
         * Migrate every known secret key (active URL/key + each `server_<url>` /
         * `apikey_<url>` pair) from plaintext default prefs into the encrypted store,
         * then strip them from plaintext. Idempotent; safe to call on every launch.
         */
        fun migratePlaintextSecrets(context: Context) {
            val plain = PreferenceManager.getDefaultSharedPreferences(context)
            val secretKeys =
                plain.all.keys.filter {
                    it == SettingsFragment.PREF_STASH_URL ||
                        it == SettingsFragment.PREF_STASH_API_KEY ||
                        it.startsWith(SERVER_PREF_PREFIX) ||
                        it.startsWith(SERVER_APIKEY_PREF_PREFIX)
                }
            // Don't touch (and thus eagerly initialize) the encrypted store when there is
            // nothing left to migrate — the common case after the one-time migration.
            if (secretKeys.isEmpty()) return
            val securePrefs = secure(context)
            securePrefs.edit(true) {
                secretKeys.forEach { key ->
                    if (!securePrefs.contains(key)) {
                        putString(key, plain.all[key]?.toString())
                    }
                }
            }
            plain.edit(true) {
                secretKeys.forEach { remove(it) }
            }
        }

        /**
         * The active server's API key, read from the encrypted store (migrating from
         * plaintext on first access). Use this anywhere the key is needed outside a
         * [StashServer] instance (e.g. image loading).
         */
        fun getStoredApiKey(context: Context): String? = normalizeApiKey(readMigratingSecret(context, SettingsFragment.PREF_STASH_API_KEY))

        fun getCurrentServerVersion(): Version = ServerPreferences(requireCurrentServer()).serverVersion

        fun requireCurrentServer(): StashServer {
            if (StashApplication.currentServer == null) {
                val server =
                    findConfiguredStashServer(StashApplication.getApplication())
                        ?: throw QueryEngine.StashNotConfiguredException()
                setCurrentStashServer(StashApplication.getApplication(), server)
            }
            return StashApplication.requireCurrentServer()
        }

        fun getCurrentStashServer(): StashServer? = StashApplication.currentServer

        fun findConfiguredStashServer(context: Context): StashServer? {
            val url = readMigratingSecret(context, SettingsFragment.PREF_STASH_URL)
            val apiKey = readMigratingSecret(context, SettingsFragment.PREF_STASH_API_KEY)
            return if (url.isNotNullOrBlank()) {
                servers.getOrPut(url) { StashServer(url, apiKey) }
            } else {
                null
            }
        }

        fun setCurrentStashServer(
            context: Context,
            server: StashServer,
        ) {
            secure(context).edit(true) {
                putString(SettingsFragment.PREF_STASH_URL, server.url)
                putString(SettingsFragment.PREF_STASH_API_KEY, normalizeApiKey(server.apiKey))
            }
            StashExoPlayer.releasePlayer()
            StashApplication.currentServer = server
            com.github.damontecres.stashapp.folders.sync.LibraryIndexerHost.currentServerChanged(server)
        }

        fun removeStashServer(
            context: Context,
            server: StashServer,
        ) {
            val serverKey = SERVER_PREF_PREFIX + server.url
            val apiKeyKey = SERVER_APIKEY_PREF_PREFIX + server.url
            secure(context).edit(true) {
                remove(serverKey)
                remove(apiKeyKey)
            }
            // Drop any stale plaintext copy that may predate the encrypted-store migration.
            PreferenceManager.getDefaultSharedPreferences(context).edit(true) {
                remove(serverKey)
                remove(apiKeyKey)
            }
            server.serverPreferences.preferences.edit(true) {
                clear()
            }
        }

        fun addServer(
            context: Context,
            newServer: StashServer,
        ) {
            val newServerKey = SERVER_PREF_PREFIX + newServer.url
            val newApiKeyKey = SERVER_APIKEY_PREF_PREFIX + newServer.url
            secure(context).edit(true) {
                putString(newServerKey, newServer.url)
                putString(newApiKeyKey, normalizeApiKey(newServer.apiKey))
            }
        }

        fun addAndSwitchServer(
            context: Context,
            newServer: StashServer,
            otherSettings: ((SharedPreferences.Editor) -> Unit)? = null,
        ) {
            val current = findConfiguredStashServer(context)
            val currentServerKey = SERVER_PREF_PREFIX + current?.url
            val currentApiKeyKey =
                SERVER_APIKEY_PREF_PREFIX + current?.url
            val newServerKey = SERVER_PREF_PREFIX + newServer.url
            val newApiKeyKey =
                SERVER_APIKEY_PREF_PREFIX + newServer.url
            secure(context).edit(true) {
                if (current != null) {
                    putString(currentServerKey, current.url)
                    putString(currentApiKeyKey, normalizeApiKey(current.apiKey))
                }
                putString(newServerKey, newServer.url)
                putString(newApiKeyKey, normalizeApiKey(newServer.apiKey))
                putString(SettingsFragment.PREF_STASH_URL, newServer.url)
                putString(SettingsFragment.PREF_STASH_API_KEY, normalizeApiKey(newServer.apiKey))
            }
            // [otherSettings] writes non-secret settings, which live in default prefs.
            if (otherSettings != null) {
                PreferenceManager.getDefaultSharedPreferences(context).edit(true) {
                    otherSettings(this)
                }
            }
            StashExoPlayer.releasePlayer()
        }

        fun getAll(context: Context): List<StashServer> {
            // Migrate any legacy plaintext server entries before enumerating the store.
            migratePlaintextSecrets(context)
            val securePrefs = secure(context)
            val keys =
                securePrefs.all.keys
                    .filter { it.startsWith(SERVER_PREF_PREFIX) }
                    .sorted()
                    .toList()
            return keys
                .map {
                    val url = it.replace(SERVER_PREF_PREFIX, "")
                    val apiKeyKey =
                        it.replace(
                            SERVER_PREF_PREFIX,
                            SERVER_APIKEY_PREF_PREFIX,
                        )
                    val apiKey =
                        securePrefs.all[apiKeyKey]
                            ?.toString()
                            ?.replace(SERVER_APIKEY_PREF_PREFIX, "")
                    StashServer(url, apiKey)
                }.sortedBy { it.url }
        }
    }
}
