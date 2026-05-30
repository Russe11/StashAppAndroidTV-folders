package com.github.damontecres.stashapp.util.realtime

/**
 * The trust-on-first-use (TOFU) decision for one incoming command's controller (design §9 / open
 * decision #3): a target confirms the FIRST command from a new controller, then remembers the
 * decision per controller.
 *
 * Pure, persistence-free logic so the gate is unit-testable. The store ([ControllerAuthorizationStore])
 * supplies the remembered state; this enum is the per-command verdict.
 */
enum class ControllerVerdict {
    /** This controller is confirmed allowed — execute the command silently. */
    ALLOWED,

    /** This controller is explicitly blocked — drop the command silently. */
    BLOCKED,

    /** First contact (or a previously-deferred prompt) — ask the user before doing anything. */
    ASK,
}

/**
 * The remembered authorization state for one controller id. `null` (no entry) means "never seen" ⇒
 * [ControllerVerdict.ASK]. The decision is keyed by the controller's device id.
 *
 * NOTE (server contract): `DeviceCommandEvent.fromDeviceId` is currently always the empty string on
 * the wire (the server has no controller-id field yet — see SERVER_SCHEMA behavior notes). An empty
 * id is normalized to [UNIDENTIFIED] so TOFU still gates: an unidentified controller is treated as a
 * single anonymous controller that the user must confirm once. When the server adds the field this
 * keying becomes per-real-controller automatically with no client change.
 */
object ControllerAuthorization {
    /** Sentinel key for a controller the server didn't identify (empty `fromDeviceId`). */
    const val UNIDENTIFIED = "__unidentified__"

    /** Normalize a raw `fromDeviceId` to a stable key (blank ⇒ [UNIDENTIFIED]). */
    fun keyFor(fromDeviceId: String): String = fromDeviceId.trim().ifBlank { UNIDENTIFIED }

    /**
     * The verdict for a controller given its remembered [allowed] flag:
     *  - `null`  → never decided → [ControllerVerdict.ASK]
     *  - `true`  → [ControllerVerdict.ALLOWED]
     *  - `false` → [ControllerVerdict.BLOCKED]
     */
    fun verdict(allowed: Boolean?): ControllerVerdict =
        when (allowed) {
            null -> ControllerVerdict.ASK
            true -> ControllerVerdict.ALLOWED
            false -> ControllerVerdict.BLOCKED
        }
}

/**
 * A pending TOFU prompt surfaced to the UI: a command arrived from a controller the user hasn't
 * decided on yet. The UI shows "Allow <controllerName> to control this device?" and calls back with
 * the decision. [controllerKey] is the normalized key the decision is remembered under.
 */
data class ControllerConfirmationRequest(
    val controllerKey: String,
    /** A best-effort display name (the controller's device name if resolvable, else a placeholder). */
    val controllerName: String,
)
