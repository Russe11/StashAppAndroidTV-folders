package com.github.damontecres.stashapp.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.damontecres.stashapp.util.realtime.PlayerController

/**
 * Media3-backed [PlayerController] (R-C TARGET): binds an incoming remote [PlayerAction] to the live
 * Media3 [Player]. Pure adapter — the routing/gating lives upstream
 * ([com.github.damontecres.stashapp.util.realtime.RemoteCommandDispatcher] /
 * [com.github.damontecres.stashapp.util.realtime.RemoteControlCoordinator]); this just calls the
 * player.
 *
 * All calls assume they run on the player's thread; the binding site posts them to the main thread.
 *
 * @param currentSceneIdProvider how to read the id of the scene currently loaded (the player only
 *   knows MediaItems, so the screen supplies this from its own state).
 * @param enqueueScenes appends scenes to the queue (building MediaItems needs server/stream context
 *   the screen owns, so it is injected rather than done here).
 * @param leavePlayer backs out of the player screen on STOP (navigation, also owned by the screen).
 */
class Media3RemoteController(
    private val player: Player,
    private val currentSceneIdProvider: () -> String?,
    private val enqueueScenes: (List<String>) -> Unit,
    private val leavePlayer: () -> Unit,
) : PlayerController {
    override val currentSceneId: String?
        get() = currentSceneIdProvider()

    override val isPlaying: Boolean
        get() = player.isPlaying

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seekTo(seconds: Double) {
        val ms = (seconds * 1000).toLong().coerceAtLeast(0L)
        player.seekTo(ms)
    }

    override fun stop() {
        player.pause()
        leavePlayer()
    }

    override fun enqueue(sceneIds: List<String>) {
        enqueueScenes(sceneIds)
    }

    override fun next() {
        if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
    }

    override fun previous() {
        if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) player.seekToPrevious()
    }

    override fun playScene(
        sceneId: String,
        startSeconds: Double?,
    ) {
        // A fresh "play this scene" on an already-open player: the dispatcher only routes here when
        // the requested scene matches the loaded one, so this is effectively a resume+seek. A
        // different scene goes through the navigation launcher instead (see RemoteControlHost).
        startSeconds?.let { seekTo(it) }
        player.play()
    }

    /** Build a [MediaItem]-free description for logging/tests if needed. */
    @Suppress("unused")
    private fun current(): MediaItem? = player.currentMediaItem
}
