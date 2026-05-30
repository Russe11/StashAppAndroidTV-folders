package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Wave 2 tail (R1 UI invalidation) — covers the glue that wires
 * [LiveRefreshHost.refreshSignals] to a UI list ViewModel's pager invalidation.
 *
 * The pure decision ([LiveRefreshListGlue.shouldRefresh]) and the collection wiring
 * ([LiveRefreshListGlue.collectInto]) are tested headless with a plain [MutableSharedFlow] and a
 * fake invalidate callback — no Android, no Apollo. The Compose-level effect (the rebuilt
 * `ComposePager` actually re-rendering the grid) needs an instrumented test and is out of scope here.
 */
class LiveRefreshListGlueTest {
    private fun signalOf(vararg types: DataType) =
        LiveRefreshSignal(changedTypes = types.toSet(), deletedSceneIds = emptySet())

    @Test
    fun shouldRefresh_trueOnlyWhenMatchingTypeChanged() {
        assertTrue(
            LiveRefreshListGlue.shouldRefresh(signalOf(DataType.SCENE), DataType.SCENE),
        )
        assertTrue(
            LiveRefreshListGlue.shouldRefresh(
                signalOf(DataType.TAG, DataType.SCENE),
                DataType.SCENE,
            ),
        )
        assertFalse(
            LiveRefreshListGlue.shouldRefresh(signalOf(DataType.TAG), DataType.SCENE),
        )
        assertFalse(
            LiveRefreshListGlue.shouldRefresh(LiveRefreshSignal.EMPTY, DataType.SCENE),
        )
    }

    @Test
    fun collectInto_firesInvalidateOnMatchingSignal() =
        runBlocking {
            val signals = MutableSharedFlow<LiveRefreshSignal>(extraBufferCapacity = 8)
            val fired = AtomicInteger(0)

            val job: Job =
                LiveRefreshListGlue.collectInto(
                    scope = CoroutineScope(coroutineContext + Job()),
                    signals = signals,
                    currentDataType = { DataType.SCENE },
                    invalidate = { fired.incrementAndGet() },
                )

            // Let the collector subscribe before emitting (hot flow, replay = 0).
            awaitSubscriber(signals)

            signals.emit(signalOf(DataType.TAG)) // non-matching -> no invalidate
            signals.emit(signalOf(DataType.SCENE)) // matching -> invalidate
            signals.emit(signalOf(DataType.SCENE, DataType.PERFORMER)) // matching -> invalidate

            // Drain until both matching signals have fired.
            withTimeout(2_000) {
                while (fired.get() < 2) yield()
            }

            job.cancelAndJoin()
            assertEquals(2, fired.get())
        }

    @Test
    fun collectInto_noInvalidateWhenCurrentTypeNull() =
        runBlocking {
            val signals = MutableSharedFlow<LiveRefreshSignal>(extraBufferCapacity = 8)
            val fired = AtomicInteger(0)

            val job =
                LiveRefreshListGlue.collectInto(
                    scope = CoroutineScope(coroutineContext + Job()),
                    signals = signals,
                    currentDataType = { null }, // list not initialized -> never invalidate
                    invalidate = { fired.incrementAndGet() },
                )
            awaitSubscriber(signals)
            signals.emit(signalOf(DataType.SCENE))
            // Give the collector a chance to run.
            repeat(50) { yield() }
            job.cancelAndJoin()
            assertEquals(0, fired.get())
        }

    @Test
    fun collectInto_cancellingJobStopsCollection() =
        runBlocking {
            val signals = MutableSharedFlow<LiveRefreshSignal>(extraBufferCapacity = 8)
            val fired = AtomicInteger(0)
            val job =
                LiveRefreshListGlue.collectInto(
                    scope = CoroutineScope(coroutineContext + Job()),
                    signals = signals,
                    currentDataType = { DataType.SCENE },
                    invalidate = { fired.incrementAndGet() },
                )
            awaitSubscriber(signals)
            job.cancelAndJoin()
            // After cancellation the collector is gone; emissions are dropped.
            signals.emit(signalOf(DataType.SCENE))
            repeat(50) { yield() }
            assertEquals(0, fired.get())
        }

    private suspend fun awaitSubscriber(flow: MutableSharedFlow<*>) {
        withTimeout(2_000) {
            while (flow.subscriptionCount.value == 0) yield()
        }
    }
}
