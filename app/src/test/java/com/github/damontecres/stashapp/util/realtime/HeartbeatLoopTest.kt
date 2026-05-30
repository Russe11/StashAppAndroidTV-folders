package com.github.damontecres.stashapp.util.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the pure [HeartbeatLoop]: it registers immediately, re-registers every interval, treats
 * the first-tick failure specially (reports + exits so the caller can retry connect) and survives
 * later transient tick failures. The clock ([delayFn]) is injected so timing is deterministic.
 */
class HeartbeatLoopTest {
    @Test
    fun registersImmediatelyThenEveryInterval() =
        runBlocking {
            val ticks = AtomicInteger(0)
            val loop =
                HeartbeatLoop(
                    intervalMillis = 20_000,
                    tick = {
                        ticks.incrementAndGet()
                        true
                    },
                    // Immediate "delay" so the loop spins through several intervals quickly.
                    delayFn = { yield() },
                )
            val scope = CoroutineScope(coroutineContext + Job())
            val job = scope.launch { loop.run() }
            // Let it run: one immediate registration + several heartbeats.
            repeat(50) { yield() }
            job.cancel()

            assertTrue("registered immediately + heartbeated repeatedly", ticks.get() >= 3)
        }

    @Test
    fun firstTickFailure_reportsAndExits() =
        runBlocking {
            val ticks = AtomicInteger(0)
            var firstFailureCalled = false
            val loop =
                HeartbeatLoop(
                    intervalMillis = 20_000,
                    tick = {
                        ticks.incrementAndGet()
                        throw RuntimeException("server down on connect")
                    },
                    delayFn = { yield() },
                    onFirstTickFailed = { firstFailureCalled = true },
                )
            // run() returns (does not loop forever) on a first-tick failure.
            loop.run()

            assertEquals("only the initial registration was attempted", 1, ticks.get())
            assertTrue("first-tick failure surfaced to the caller", firstFailureCalled)
        }

    @Test
    fun firstTickReturningFalse_isTreatedAsFailure() =
        runBlocking {
            var firstFailureCalled = false
            val loop =
                HeartbeatLoop(
                    tick = { false },
                    delayFn = { yield() },
                    onFirstTickFailed = { firstFailureCalled = true },
                )
            loop.run()
            assertTrue("false first tick ⇒ reported + exited", firstFailureCalled)
        }

    @Test
    fun laterTickFailures_areSurvived() =
        runBlocking {
            val ticks = AtomicInteger(0)
            val loop =
                HeartbeatLoop(
                    intervalMillis = 20_000,
                    tick = {
                        // First tick OK (we come online), later ticks throw — loop must keep going.
                        if (ticks.incrementAndGet() >= 2) throw RuntimeException("transient blip")
                        true
                    },
                    delayFn = { yield() },
                )
            val scope = CoroutineScope(coroutineContext + Job())
            val job = scope.launch { loop.run() }
            repeat(50) { yield() }
            job.cancel()

            // It kept attempting heartbeats despite repeated post-connect failures.
            assertTrue("survived transient heartbeat failures", ticks.get() >= 3)
        }
}
