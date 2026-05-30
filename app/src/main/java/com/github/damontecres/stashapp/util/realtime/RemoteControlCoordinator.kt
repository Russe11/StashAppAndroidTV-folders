package com.github.damontecres.stashapp.util.realtime

import android.util.Log

/**
 * The TARGET-side glue (R-C): turns one incoming [DeviceCommand] into a player action, gated by TOFU.
 *
 * Flow per command:
 *  1. Map the command to a [PlayerAction] ([DeviceCommandMapper]); a malformed/partial command maps
 *     to null and is dropped.
 *  2. Consult the per-controller TOFU verdict ([ControllerAuthorizationStore]):
 *     - ALLOWED → dispatch the action immediately.
 *     - BLOCKED → drop silently.
 *     - ASK     → surface a [ControllerConfirmationRequest] to the UI (via [requestConfirmation]) and
 *       hold the action; if the user later allows the controller, the held action is dispatched.
 *  3. Dispatch via [RemoteCommandDispatcher].
 *
 * Pure orchestration (no Android/Media3 types beyond logging) so the gate behaviour is unit-testable.
 * [resolveControllerName] lets the UI show a friendly name; [dispatcher] and [authStore] are injected.
 */
class RemoteControlCoordinator(
    private val authStore: ControllerAuthorizationStore,
    private val dispatcher: RemoteCommandDispatcher,
    /** Best-effort display name for a controller id (e.g. from the online-devices list). */
    private val resolveControllerName: (fromDeviceId: String) -> String,
    /** Surface a TOFU prompt to the UI; the UI calls [onConfirmationResult] with the decision. */
    private val requestConfirmation: (ControllerConfirmationRequest) -> Unit,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    /** Actions held awaiting a TOFU decision, keyed by controller key. Most-recent wins per key. */
    private val pending = mutableMapOf<String, PlayerAction>()

    /**
     * Handle one incoming command. Returns the [ControllerVerdict] applied (useful for tests/logging).
     * Synchronized so a burst of commands from the same new controller doesn't race the pending map.
     */
    @Synchronized
    fun onCommand(command: DeviceCommand): ControllerVerdict {
        val action = DeviceCommandMapper.toAction(command)
        if (action == null) {
            log("dropping unmappable command ${command.type}")
            return ControllerVerdict.BLOCKED
        }
        val key = ControllerAuthorization.keyFor(command.fromDeviceId)
        return when (val verdict = authStore.verdict(command.fromDeviceId)) {
            ControllerVerdict.ALLOWED -> {
                dispatcher.dispatch(action)
                verdict
            }

            ControllerVerdict.BLOCKED -> {
                log("ignoring command from blocked controller $key")
                verdict
            }

            ControllerVerdict.ASK -> {
                // Hold the latest action for this controller and ask the user once.
                pending[key] = action
                requestConfirmation(
                    ControllerConfirmationRequest(
                        controllerKey = key,
                        controllerName = resolveControllerName(command.fromDeviceId),
                    ),
                )
                verdict
            }
        }
    }

    /**
     * The user's answer to a TOFU prompt for [controllerKey]. Remembers the decision; if [allowed],
     * dispatches the action that was held while we asked. Idempotent per key.
     */
    @Synchronized
    fun onConfirmationResult(
        controllerKey: String,
        allowed: Boolean,
    ) {
        authStore.remember(controllerKey, allowed)
        val held = pending.remove(controllerKey)
        if (allowed && held != null) {
            log("controller $controllerKey allowed; dispatching held ${held::class.simpleName}")
            dispatcher.dispatch(held)
        } else if (!allowed) {
            log("controller $controllerKey blocked by user")
        }
    }

    /** Drop any held action for a controller without recording a decision (e.g. prompt dismissed). */
    @Synchronized
    fun discardPending(controllerKey: String) {
        pending.remove(controllerKey)
    }

    companion object {
        private const val TAG = "RemoteControlCoordinator"
    }
}
