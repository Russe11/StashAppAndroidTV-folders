package com.github.damontecres.stashapp.ui.components.scene

import kotlinx.coroutines.Job
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneDetailsLoadGateTest {
    @Test
    fun start_ignoresDuplicateActiveLoadsAndAllowsRetryAfterCancel() {
        val gate = SceneDetailsLoadGate()
        val firstJob = Job()

        assertNotNull(gate.start { firstJob })
        assertNull(gate.start { Job() })

        gate.cancel()

        val retryJob = Job()
        assertNotNull(gate.start { retryJob })
        assertTrue(firstJob.isCancelled)
    }

    @Test
    fun start_ignoresLoadsAfterSceneHasLoaded() {
        val gate = SceneDetailsLoadGate()

        assertNotNull(gate.start { Job() })
        gate.markLoaded()

        assertNull(gate.start { Job() })
    }
}
