package com.github.damontecres.stashapp.util.realtime

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide owner of the TARGET-side remote-control machinery (R-C): the currently-bound player,
 * the TOFU [RemoteControlCoordinator], and the stream of pending confirmation prompts for the UI.
 * Mirrors [DeviceBusHost]/[LiveRefreshHost] as a foreground singleton.
 *
 * The live player binds itself via [bindPlayer] when its screen starts and [unbindPlayer]s on
 * dispose. Incoming [DeviceCommand]s (fed by [DeviceBusHost] from the `deviceCommands` subscription)
 * go through [onCommand], which maps + TOFU-gates + dispatches. A first command from an unknown
 * controller surfaces a [ControllerConfirmationRequest] on [confirmations]; a host UI collects that,
 * prompts "Allow <controller> to control this device?", and answers via [resolveConfirmation].
 *
 * Everything here only runs while the user is opted in (the command subscription itself only runs
 * then — see [DeviceBusRepository]); this host adds no behaviour when no commands arrive.
 */
object RemoteControlHost {
    private const val TAG = "RemoteControlHost"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var boundPlayer: PlayerController? = null

    /** Opens the player on a scene (set by the app — navigation). Null until installed. */
    @Volatile
    private var sceneLauncher: ((sceneId: String, startSeconds: Double?) -> Unit)? = null

    /** Resolves a controller id to a friendly name (set by the app from the online-devices list). */
    @Volatile
    private var controllerNameResolver: ((String) -> String)? = null

    private val _confirmations =
        MutableSharedFlow<ControllerConfirmationRequest>(
            replay = 0,
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Pending TOFU prompts for the UI to show. */
    val confirmations: SharedFlow<ControllerConfirmationRequest> = _confirmations.asSharedFlow()

    @Volatile
    private var coordinator: RemoteControlCoordinator? = null

    /**
     * One-shot setup. Wires the app context (for the encrypted TOFU store), the navigation-based
     * scene launcher, and the controller-name resolver. Safe to call again to update the lambdas.
     */
    @Synchronized
    fun install(
        context: Context,
        sceneLauncher: (sceneId: String, startSeconds: Double?) -> Unit,
        controllerNameResolver: (String) -> String = { ControllerAuthorization.UNIDENTIFIED },
    ) {
        this.appContext = context.applicationContext
        this.sceneLauncher = sceneLauncher
        this.controllerNameResolver = controllerNameResolver
        this.coordinator = buildCoordinator(context.applicationContext)
    }

    /** Bind the live player so transport actions can drive it. Called when the player screen starts. */
    fun bindPlayer(player: PlayerController) {
        boundPlayer = player
    }

    /** Unbind the live player (called on player dispose). A later transport command becomes a no-op. */
    fun unbindPlayer(player: PlayerController) {
        // Only clear if it's still the one we hold (avoid a stale unbind clobbering a newer player).
        if (boundPlayer === player) boundPlayer = null
    }

    /**
     * Feed one incoming command through the TOFU gate + dispatcher. No-op (logs) if not installed.
     * Called by [DeviceBusHost] for every `deviceCommands` frame while opted in.
     */
    fun onCommand(command: DeviceCommand) {
        val c = coordinator ?: run {
            Log.w(TAG, "remote-control not installed; dropping ${command.type}")
            return
        }
        c.onCommand(command)
    }

    /** The user's answer to a TOFU prompt (true = allow this controller, false = block). */
    fun resolveConfirmation(
        controllerKey: String,
        allowed: Boolean,
    ) {
        coordinator?.onConfirmationResult(controllerKey, allowed)
    }

    /** Dismiss a prompt without deciding (the held action is discarded; controller stays "ASK"). */
    fun dismissConfirmation(controllerKey: String) {
        coordinator?.discardPending(controllerKey)
    }

    private fun buildCoordinator(context: Context): RemoteControlCoordinator =
        RemoteControlCoordinator(
            authStore = ControllerAuthorizationStore.secure(context),
            dispatcher =
                RemoteCommandDispatcher(
                    playerProvider = { boundPlayer },
                    launchScene = { sceneId, startSeconds ->
                        sceneLauncher?.invoke(sceneId, startSeconds)
                            ?: Log.w(TAG, "no scene launcher; cannot PLAY $sceneId")
                    },
                ),
            resolveControllerName = { id -> controllerNameResolver?.invoke(id) ?: id },
            requestConfirmation = { req -> _confirmations.tryEmit(req) },
        )
}
