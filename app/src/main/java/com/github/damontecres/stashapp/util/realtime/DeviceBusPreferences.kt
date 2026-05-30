package com.github.damontecres.stashapp.util.realtime

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * The opt-in toggle that gates all deviceBus presence/control behaviour for this device.
 *
 * **Default OFF** (invariant: privacy-first, opt-in presence — design §9). Until the user opts in,
 * this device does *not* register, does *not* heartbeat, does *not* report playback, and does NOT
 * appear in any other device's online list — it is invisible and uncontrollable. The flag is a
 * plain (non-secret) user preference in the default store; the device identity (UUID) lives in the
 * encrypted store ([DeviceIdentity]).
 *
 * This is a *user* gate; it is ANDed with the *capability* gate
 * ([com.github.damontecres.stashapp.util.ServerCapabilities.supportsDeviceBus]) — both must hold
 * before presence activates. A change is broadcast on [optInChanges] so the host can start/stop
 * presence reactively when the user flips the toggle.
 */
object DeviceBusPreferences {
    /** Default-store key for the opt-in flag. Default value is `false` (OFF). */
    const val PREF_PRESENCE_OPT_IN = "deviceBus_presence_opt_in"

    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    /** True if the user has opted into presence on this device. Default OFF. */
    fun isOptedIn(context: Context): Boolean =
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .getBoolean(PREF_PRESENCE_OPT_IN, false)

    /** Set the opt-in flag and notify [optInChanges] subscribers. */
    fun setOptedIn(
        context: Context,
        optedIn: Boolean,
    ) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PREF_PRESENCE_OPT_IN, optedIn)
        }
        synchronized(listeners) { listeners.toList() }.forEach { it(optedIn) }
    }

    /**
     * Register a callback invoked (off the prefs thread) whenever the opt-in flag changes via
     * [setOptedIn]. Returns an unsubscribe lambda.
     */
    fun optInChanges(listener: (Boolean) -> Unit): () -> Unit {
        synchronized(listeners) { listeners.add(listener) }
        return { synchronized(listeners) { listeners.remove(listener) } }
    }
}
