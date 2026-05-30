package com.github.damontecres.stashapp.util.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the pure routing in [RemoteCommandDispatcher] (R-C TARGET): PLAY reuses a live player when
 * it's already on the requested scene (resume + seek) but launches fresh otherwise; transport
 * actions require a live player and are dropped (false) when none is bound.
 */
class RemoteCommandDispatcherTest {
    private class FakePlayer(
        override var currentSceneId: String?,
        override var isPlaying: Boolean = false,
    ) : PlayerController {
        val calls = mutableListOf<String>()

        override fun pause() {
            calls.add("pause")
        }

        override fun resume() {
            calls.add("resume")
        }

        override fun seekTo(seconds: Double) {
            calls.add("seekTo($seconds)")
        }

        override fun stop() {
            calls.add("stop")
        }

        override fun enqueue(sceneIds: List<String>) {
            calls.add("enqueue($sceneIds)")
        }

        override fun next() {
            calls.add("next")
        }

        override fun previous() {
            calls.add("previous")
        }

        override fun playScene(
            sceneId: String,
            startSeconds: Double?,
        ) {
            calls.add("playScene($sceneId,$startSeconds)")
        }
    }

    private fun dispatcher(
        player: PlayerController?,
        launched: MutableList<Pair<String, Double?>> = mutableListOf(),
    ) = RemoteCommandDispatcher(
        playerProvider = { player },
        launchScene = { id, start -> launched.add(id to start) },
    )

    @Test
    fun play_sameSceneLoaded_resumesAndSeeksOnLivePlayer() {
        val player = FakePlayer(currentSceneId = "42")
        val launched = mutableListOf<Pair<String, Double?>>()
        val handled = dispatcher(player, launched).dispatch(PlayerAction.Play("42", startSeconds = 30.0))

        assertTrue(handled)
        assertEquals(listOf("seekTo(30.0)", "resume"), player.calls)
        assertTrue("must not re-launch when already on the scene", launched.isEmpty())
    }

    @Test
    fun play_differentScene_launchesFresh() {
        val player = FakePlayer(currentSceneId = "42")
        val launched = mutableListOf<Pair<String, Double?>>()
        val handled = dispatcher(player, launched).dispatch(PlayerAction.Play("99", startSeconds = 5.0))

        assertTrue(handled)
        assertEquals(listOf("99" to 5.0), launched)
        assertTrue("must not touch the live player for a different scene", player.calls.isEmpty())
    }

    @Test
    fun play_noPlayer_launchesFresh() {
        val launched = mutableListOf<Pair<String, Double?>>()
        val handled = dispatcher(player = null, launched = launched).dispatch(PlayerAction.Play("7", startSeconds = null))

        assertTrue(handled)
        assertEquals(listOf("7" to null), launched)
    }

    @Test
    fun transportActions_driveLivePlayer() {
        val player = FakePlayer(currentSceneId = "42")
        val d = dispatcher(player)

        assertTrue(d.dispatch(PlayerAction.Pause))
        assertTrue(d.dispatch(PlayerAction.Resume))
        assertTrue(d.dispatch(PlayerAction.Seek(120.0)))
        assertTrue(d.dispatch(PlayerAction.Stop))
        assertTrue(d.dispatch(PlayerAction.Enqueue(listOf("1", "2"))))
        assertTrue(d.dispatch(PlayerAction.Next))
        assertTrue(d.dispatch(PlayerAction.Previous))

        assertEquals(
            listOf("pause", "resume", "seekTo(120.0)", "stop", "enqueue([1, 2])", "next", "previous"),
            player.calls,
        )
    }

    @Test
    fun transportActions_withNoPlayer_areDropped() {
        val d = dispatcher(player = null)
        assertFalse(d.dispatch(PlayerAction.Pause))
        assertFalse(d.dispatch(PlayerAction.Resume))
        assertFalse(d.dispatch(PlayerAction.Seek(1.0)))
        assertFalse(d.dispatch(PlayerAction.Stop))
        assertFalse(d.dispatch(PlayerAction.Enqueue(listOf("1"))))
        assertFalse(d.dispatch(PlayerAction.Next))
        assertFalse(d.dispatch(PlayerAction.Previous))
    }
}
