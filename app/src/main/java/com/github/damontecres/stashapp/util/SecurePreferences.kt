package com.github.damontecres.stashapp.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted-at-rest storage for server secrets (API keys, the active server URL,
 * and the per-server `apikey_<url>` entries).
 *
 * Backed by [EncryptedSharedPreferences] using an AES-256 master key held in the
 * Android Keystore, so the full-access Stash API key is never readable as plaintext
 * on-device (e.g. via `adb backup` is already blocked, but also via a rooted file pull
 * of the plaintext shared-prefs XML).
 *
 * If the device's keystore cannot produce/decrypt the master key (which has been seen
 * on a handful of low-end Android-TV boxes and after a keystore reset), the encrypted
 * store is unrecoverable. In that case we delete the corrupt store and recreate it so
 * the app keeps working — the user simply has to re-enter their key — rather than
 * crash-looping on startup.
 */
object SecurePreferences {
    private const val TAG = "SecurePreferences"
    private const val FILE_NAME = "stash_secure_prefs"

    @Volatile
    private var instance: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(context: Context): SharedPreferences =
        try {
            build(context)
        } catch (ex: Exception) {
            // Corrupt/unrecoverable keystore-backed store. Wipe and rebuild so we don't
            // crash-loop; the secrets it held are simply lost (user re-enters them).
            Log.e(TAG, "EncryptedSharedPreferences unavailable; recreating store", ex)
            context.deleteSharedPreferences(FILE_NAME)
            try {
                build(context)
            } catch (ex2: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences still unavailable", ex2)
                throw ex2
            }
        }

    private fun build(context: Context): SharedPreferences {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
