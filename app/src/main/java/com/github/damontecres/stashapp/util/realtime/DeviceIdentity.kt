package com.github.damontecres.stashapp.util.realtime

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.github.damontecres.stashapp.util.SecurePreferences

/**
 * This device's stable identity on the presence bus: a client-generated UUID (design §6 "Device
 * identity") plus a user-editable display name.
 *
 * The UUID is generated once on first opt-in and persisted in [SecurePreferences]
 * (EncryptedSharedPreferences) — invariant #3: secrets/device-identity in secure storage, never
 * plaintext prefs. It is *not* a secret per se, but co-locating it with the API key keeps a single
 * encrypted store and avoids leaking a cross-server-stable identifier in a plaintext backup-able
 * file. The id is server-independent (the same device is the same device on every server).
 *
 * The display [name] defaults to the host/device name ([Build.MODEL]) and is user-editable; the
 * edited value is persisted alongside the id.
 */
object DeviceIdentity {
    private const val PREF_DEVICE_UUID = "deviceBus_device_uuid"
    private const val PREF_DEVICE_NAME = "deviceBus_device_name"

    /**
     * The stable UUID for this install, generating + persisting one on first call. Thread-safe via
     * the underlying prefs; the double-check keeps a concurrent first-call from generating two.
     */
    @Synchronized
    fun deviceId(context: Context): String {
        val prefs = SecurePreferences.get(context)
        prefs.getString(PREF_DEVICE_UUID, null)?.let { return it }
        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit(true) { putString(PREF_DEVICE_UUID, generated) }
        return generated
    }

    /** The default device name when the user hasn't set one: the host/device model name. */
    fun defaultName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android TV"

    /** The current display name: the user-set value, or [defaultName] if unset. */
    fun deviceName(context: Context): String {
        val stored = SecurePreferences.get(context).getString(PREF_DEVICE_NAME, null)
        return stored?.takeIf { it.isNotBlank() } ?: defaultName()
    }

    /**
     * Persist a user-edited device name. A blank value clears the override (falls back to
     * [defaultName]).
     */
    fun setDeviceName(
        context: Context,
        name: String,
    ) {
        val trimmed = name.trim()
        SecurePreferences.get(context).edit(true) {
            if (trimmed.isBlank()) {
                remove(PREF_DEVICE_NAME)
            } else {
                putString(PREF_DEVICE_NAME, trimmed)
            }
        }
    }
}
