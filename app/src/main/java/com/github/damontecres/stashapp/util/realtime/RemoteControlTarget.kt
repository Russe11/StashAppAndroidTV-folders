package com.github.damontecres.stashapp.util.realtime

/**
 * The seam a live player exposes so a remote [PlayerAction] can drive it (R-C — TARGET role). Kept
 * abstract (no Media3 types) so the dispatch routing in [RemoteCommandDispatcher] is unit-testable
 * with a fake; [Media3PlayerController] is the production binding.
 */
interface PlayerController {
    /** The id of the scene currently loaded in this player, or null if none. */
    val currentSceneId: String?

    /** True if the player is currently playing (vs paused/idle). */
    val isPlaying: Boolean

    /** Pause the current item. */
    fun pause()

    /** Resume the current item. */
    fun resume()

    /** Seek the current item to [seconds] from the start. */
    fun seekTo(seconds: Double)

    /** Stop playback and leave the player (back out of the player screen). */
    fun stop()

    /** Append [sceneIds] to the current play queue. */
    fun enqueue(sceneIds: List<String>)

    /** Advance to the next queued item. */
    fun next()

    /** Go back to the previous queued item. */
    fun previous()

    /**
     * Load [sceneId] fresh and start playing it, seeking to [startSeconds] first if given. Used when
     * the requested scene isn't the one already loaded (a true "play this on the TV" cast).
     */
    fun playScene(
        sceneId: String,
        startSeconds: Double?,
    )
}

/**
 * Routes a resolved [PlayerAction] to the live player, or to a "open the player on this scene"
 * launcher when no player is up yet. Pure routing (no Android types) so every branch is testable.
 *
 * Routing rules:
 *  - [PlayerAction.Play]: if a player is up AND already on the requested scene → just resume/seek on
 *    it; otherwise → [launchScene] (open the player fresh, possibly from no player at all).
 *  - All other actions require a live player; if none is bound they are dropped (logged by the
 *    caller). A remote PAUSE/SEEK/NEXT with nothing playing is a no-op, by design.
 */
class RemoteCommandDispatcher(
    /** Supplies the currently-bound player, or null if no player screen is active. */
    private val playerProvider: () -> PlayerController?,
    /** Opens the player on a scene (navigation) when [PlayerAction.Play] can't reuse a live player. */
    private val launchScene: (sceneId: String, startSeconds: Double?) -> Unit,
) {
    /**
     * Apply [action] to the player. Returns true if it was dispatched (to a live player or the
     * launcher), false if it was dropped because no player was available for an action that needs one.
     */
    fun dispatch(action: PlayerAction): Boolean {
        val player = playerProvider()
        return when (action) {
            is PlayerAction.Play -> {
                if (player != null && player.currentSceneId == action.sceneId) {
                    // Same scene already loaded: resume + optional seek rather than reloading.
                    action.startSeconds?.let { player.seekTo(it) }
                    player.resume()
                    true
                } else {
                    // Different scene (or no player up): open the player on it.
                    launchScene(action.sceneId, action.startSeconds)
                    true
                }
            }

            PlayerAction.Pause -> player?.let { it.pause(); true } ?: false
            PlayerAction.Resume -> player?.let { it.resume(); true } ?: false
            is PlayerAction.Seek -> player?.let { it.seekTo(action.seekSeconds); true } ?: false
            PlayerAction.Stop -> player?.let { it.stop(); true } ?: false
            is PlayerAction.Enqueue -> player?.let { it.enqueue(action.sceneIds); true } ?: false
            PlayerAction.Next -> player?.let { it.next(); true } ?: false
            PlayerAction.Previous -> player?.let { it.previous(); true } ?: false
        }
    }
}
