package com.github.damontecres.stashapp.ui.components.scene

import com.github.damontecres.stashapp.util.MutationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Wave 3 — covers the delete-scene ViewModel *dispatch* seam.
 *
 * The pure dialog helpers (`deleteConfirmAction`/`deleteGatePin`) are already unit-tested, and the
 * Compose dialog wiring (DeleteDialog -> PinGate.onCorrect -> performDelete) needs an emulator. The
 * remaining headless-testable seam is the dispatch: what [SceneDetailsViewModel.deleteScene] does
 * once the user has confirmed — it must call [MutationEngine.deleteScene] exactly once with the
 * caller's flags and hand the engine's success boolean back to `onDeleted`.
 *
 * [SceneDetailsViewModel] itself can't be constructed in a plain JVM test (its constructor eagerly
 * builds `QueryEngine`/`MutationEngine` from a real `StashServer` and touches `viewModelScope`), so
 * the dispatch body was extracted to the package-internal [dispatchDeleteScene], which the ViewModel
 * delegates to. Testing that function exercises the exact same call shape with a mocked engine.
 *
 * `MutationEngine` is a concrete (final) class; mockito 5's inline mock-maker (the default) handles
 * final classes, so no production `open`/interface change is needed.
 */
class SceneDeleteDispatchTest {
    @Test
    fun deleteScene_invokesEngineOnceWithExactFlags_andForwardsSuccess() =
        runBlocking {
            val engine = mock<MutationEngine>()
            whenever(engine.deleteScene(any(), any(), any())).thenReturn(true)

            var received: Boolean? = null
            val result =
                dispatchDeleteScene(
                    mutationEngine = engine,
                    sceneId = "scene-42",
                    deleteFiles = true,
                    deleteGenerated = true,
                    onDeleted = { received = it },
                )

            // Exactly one call, with the exact flags the user confirmed.
            verify(engine, times(1)).deleteScene(eq("scene-42"), eq(true), eq(true))
            // The onDeleted callback receives the engine's returned boolean.
            assertEquals(true, received)
            assertTrue(result)
        }

    @Test
    fun deleteScene_forwardsFailureFromEngine() =
        runBlocking {
            val engine = mock<MutationEngine>()
            whenever(engine.deleteScene(any(), any(), any())).thenReturn(false)

            var received: Boolean? = null
            val result =
                dispatchDeleteScene(
                    mutationEngine = engine,
                    sceneId = "scene-7",
                    deleteFiles = false,
                    deleteGenerated = false,
                    onDeleted = { received = it },
                )

            verify(engine, times(1)).deleteScene(eq("scene-7"), eq(false), eq(false))
            assertEquals(false, received)
            assertFalse(result)
        }
}
