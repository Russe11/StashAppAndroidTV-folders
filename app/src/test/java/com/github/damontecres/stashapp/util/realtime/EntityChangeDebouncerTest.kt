package com.github.damontecres.stashapp.util.realtime

import com.github.damontecres.stashapp.data.DataType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EntityChangeDebouncer] — that a burst of events coalesces into exactly one
 * [LiveRefreshSignal], that a gap produces separate signals, and that windows with nothing
 * actionable are dropped.
 *
 * Timing is driven by an injectable `delayFn` gated on the test, so the trailing-debounce
 * behaviour is deterministic without real waits. The upstream is a buffered [Channel] (not a
 * `MutableSharedFlow`) so events sent before the debouncer subscribes are not lost.
 */
class EntityChangeDebouncerTest {
    /**
     * A controllable delay: each timer call parks on its own gate; the test releases the
     * *oldest-still-waiting* gates to "fire" those windows. This lets the test decide exactly
     * when (and how many) debounce windows elapse.
     */
    private class ManualDelay {
        private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

        val delayFn: suspend (Long) -> Unit = {
            val gate = CompletableDeferred<Unit>()
            synchronized(waiters) { waiters.addLast(gate) }
            gate.await()
        }

        /** How many timer waits are currently parked. */
        fun parkedCount(): Int = synchronized(waiters) { waiters.size }

        /** Fire the [count] oldest parked timers (default: all currently parked). */
        fun fire(count: Int = Int.MAX_VALUE) {
            val toFire = synchronized(waiters) { (0 until minOf(count, waiters.size)).map { waiters.removeFirst() } }
            toFire.forEach { it.complete(Unit) }
        }
    }

    /** Let other coroutines run; generous to absorb any scheduling slack on a busy CI box. */
    private suspend fun pump() = repeat(50) { yield() }

    @Test
    fun burst_coalesces_into_one_signal() =
        runBlocking {
            val manual = ManualDelay()
            val debouncer = EntityChangeDebouncer(windowMillis = 500, delayFn = manual.delayFn)
            val upstream = Channel<EntityChange>(Channel.UNLIMITED)
            val received = mutableListOf<LiveRefreshSignal>()

            val collector =
                launch {
                    debouncer.debounce(upstream.consumeAsFlow()).collect { received.add(it) }
                }

            // Emit a burst of three scene changes; each schedules a timer (buffered, so order +
            // delivery are guaranteed regardless of subscription timing).
            upstream.send(EntityChange("Scene", "1", EntityOperation.CREATE))
            upstream.send(EntityChange("Scene", "2", EntityOperation.UPDATE))
            upstream.send(EntityChange("Scene", "3", EntityOperation.DESTROY))
            pump()
            assertEquals("all three events scheduled a timer", 3, manual.parkedCount())

            // Fire all timers. Only the last (seq matches) flushes; the earlier two no-op.
            manual.fire()
            pump()

            assertEquals("a burst yields exactly one coalesced signal", 1, received.size)
            assertEquals(setOf(DataType.SCENE), received[0].changedTypes)
            assertEquals(setOf("3"), received[0].deletedSceneIds)

            collector.cancel()
        }

    @Test
    fun separated_bursts_produce_separate_signals() =
        runBlocking {
            val manual = ManualDelay()
            val debouncer = EntityChangeDebouncer(windowMillis = 500, delayFn = manual.delayFn)
            val upstream = Channel<EntityChange>(Channel.UNLIMITED)
            val received = mutableListOf<LiveRefreshSignal>()

            val collector =
                launch {
                    debouncer.debounce(upstream.consumeAsFlow()).collect { received.add(it) }
                }

            // First burst: one tag change, then fire its window.
            upstream.send(EntityChange("Tag", "10", EntityOperation.UPDATE))
            pump()
            manual.fire()
            pump()
            assertEquals(1, received.size)
            assertEquals(setOf(DataType.TAG), received[0].changedTypes)

            // Second burst, strictly after the first window fired: a performer change.
            upstream.send(EntityChange("Performer", "20", EntityOperation.CREATE))
            pump()
            manual.fire()
            pump()
            assertEquals(2, received.size)
            assertEquals(setOf(DataType.PERFORMER), received[1].changedTypes)

            collector.cancel()
        }

    @Test
    fun window_with_only_unknown_kinds_emits_nothing() =
        runBlocking {
            val manual = ManualDelay()
            val debouncer = EntityChangeDebouncer(windowMillis = 500, delayFn = manual.delayFn)
            val upstream = Channel<EntityChange>(Channel.UNLIMITED)
            val received = mutableListOf<LiveRefreshSignal>()

            val collector =
                launch {
                    debouncer.debounce(upstream.consumeAsFlow()).collect { received.add(it) }
                }

            upstream.send(EntityChange("GalleryChapter", "x", EntityOperation.DESTROY))
            pump()
            manual.fire()
            pump()

            assertTrue("nothing actionable ⇒ no signal", received.isEmpty())
            collector.cancel()
        }

    @Test
    fun upstream_completion_flushes_remaining_buffer() =
        runBlocking {
            // delayFn parks forever (never fires), so the only flush path is upstream completion.
            // The debouncer must cancel its parked timers on completion rather than await them.
            val debouncer =
                EntityChangeDebouncer(
                    windowMillis = 500,
                    delayFn = { CompletableDeferred<Unit>().await() },
                )
            val signals =
                debouncer
                    .debounce(
                        flowOf(
                            EntityChange("Scene", "1", EntityOperation.CREATE),
                            EntityChange("Scene", "2", EntityOperation.DESTROY),
                        ),
                    ).toList()

            assertEquals(1, signals.size)
            assertEquals(setOf(DataType.SCENE), signals[0].changedTypes)
            assertEquals(setOf("2"), signals[0].deletedSceneIds)
        }
}
