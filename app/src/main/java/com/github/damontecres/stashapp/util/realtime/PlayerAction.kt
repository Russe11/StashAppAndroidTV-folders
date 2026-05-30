package com.github.damontecres.stashapp.util.realtime

/**
 * A concrete, player-ready action decoded from an incoming [DeviceCommand] (R-C — this Android-TV
 * acting as a remote-control TARGET).
 *
 * Pure data with no Apollo/Media3 dependency so the command→action mapping ([DeviceCommandMapper])
 * is exhaustively unit-testable. The dispatcher ([RemoteControlTarget]) turns one of these into a
 * Media3 `Player` call (or a navigation for a fresh [Play]).
 *
 * Privacy: every payload is IDs + numbers — never a title. The target resolves nothing from the
 * wire; it just loads the scene id it was told to.
 */
sealed interface PlayerAction {
    /**
     * Load [sceneId] and begin playback, optionally seeking to [startSeconds] first. This is the
     * only action that may need to *leave* the current screen and open the player (the others assume
     * a player is already up); the dispatcher routes it through navigation when nothing is playing.
     */
    data class Play(
        val sceneId: String,
        val startSeconds: Double?,
    ) : PlayerAction

    /** Pause the current playback (no-op if already paused). */
    data object Pause : PlayerAction

    /** Resume the current playback (no-op if already playing). */
    data object Resume : PlayerAction

    /** Seek the current playback to [seekSeconds] from the start. */
    data class Seek(
        val seekSeconds: Double,
    ) : PlayerAction

    /** Stop playback and leave the player. */
    data object Stop : PlayerAction

    /** Append [sceneIds] to the play queue (does not interrupt the current item). */
    data class Enqueue(
        val sceneIds: List<String>,
    ) : PlayerAction

    /** Advance to the next item in the queue. */
    data object Next : PlayerAction

    /** Go back to the previous item in the queue. */
    data object Previous : PlayerAction
}
