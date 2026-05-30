package com.github.damontecres.stashapp.util.realtime

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.stashapp.util.SecurePreferences

/**
 * Remembers TOFU control decisions per controller (design §9 — "remembered per controller").
 *
 * The remembered map is `controllerKey -> Boolean` (true = allowed, false = blocked); a missing key
 * means "never decided" ⇒ [ControllerVerdict.ASK]. Decisions persist in [SecurePreferences]
 * (EncryptedSharedPreferences) — invariant #3: control-trust state lives in the encrypted store, not
 * plaintext prefs. This is *trust* state, not ephemeral presence, so it is allowed to persist
 * locally (it is never sent to the server and carries no titles).
 *
 * The storage is abstracted behind [Backing] so the gate logic is unit-testable with an in-memory
 * fake; the production constructor binds it to the encrypted store.
 */
class ControllerAuthorizationStore(
    private val backing: Backing,
) {
    /** A minimal key/value seam (so tests don't need Android's SharedPreferences). */
    interface Backing {
        fun getBoolean(key: String): Boolean?

        fun putBoolean(
            key: String,
            value: Boolean,
        )

        fun remove(key: String)

        fun clear()
    }

    /** The current verdict for a controller (normalizes a blank id to the unidentified sentinel). */
    fun verdict(fromDeviceId: String): ControllerVerdict {
        val key = ControllerAuthorization.keyFor(fromDeviceId)
        return ControllerAuthorization.verdict(backing.getBoolean(prefKey(key)))
    }

    /** Remember the user's decision for a controller (true = allow, false = block). */
    fun remember(
        fromDeviceId: String,
        allowed: Boolean,
    ) {
        val key = ControllerAuthorization.keyFor(fromDeviceId)
        backing.putBoolean(prefKey(key), allowed)
    }

    /** Forget a single controller's decision (it reverts to ASK on next contact). */
    fun forget(fromDeviceId: String) {
        val key = ControllerAuthorization.keyFor(fromDeviceId)
        backing.remove(prefKey(key))
    }

    /** Forget every remembered controller decision (e.g. on a privacy reset). */
    fun forgetAll() = backing.clear()

    private fun prefKey(controllerKey: String): String = "$PREFIX$controllerKey"

    companion object {
        private const val PREFIX = "deviceBus_controller_authz_"

        /**
         * The production store, backed by the encrypted shared-prefs file. Only keys under [PREFIX]
         * are touched, so it coexists with [DeviceIdentity] in the same encrypted store.
         */
        fun secure(context: Context): ControllerAuthorizationStore {
            val prefs = SecurePreferences.get(context.applicationContext)
            return ControllerAuthorizationStore(
                object : Backing {
                    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null

                    override fun putBoolean(
                        key: String,
                        value: Boolean,
                    ) = prefs.edit(true) { putBoolean(key, value) }

                    override fun remove(key: String) = prefs.edit(true) { remove(key) }

                    override fun clear() {
                        // Only remove our own keys; never wipe the co-located device identity / API key.
                        val ours = prefs.all.keys.filter { it.startsWith(PREFIX) }
                        prefs.edit(true) { ours.forEach { remove(it) } }
                    }
                },
            )
        }
    }
}
