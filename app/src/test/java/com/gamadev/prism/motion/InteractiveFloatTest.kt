package com.gamadev.prism.motion

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveFloatTest {
    @Test(timeout = 3000)
    fun retargetingPreservesPresentationPositionAndVelocity() {
        val clock = BroadcastFrameClock()
        runBlocking(clock) {
            val position = InteractiveFloat()
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                position.animateTo(1f, Motion.page.spec(), initialVelocity = 0.5f)
            }
            clock.frame(0L)
            clock.frame(30_000_000L)
            val before = position.value
            val velocity = position.velocity
            assertTrue(before in 0f..1f)
            assertTrue(velocity > 0f)

            val reverse = launch(start = CoroutineStart.UNDISPATCHED) {
                position.animateTo(0f, Motion.page.spec())
            }
            assertEquals(before, position.value, 0.0001f)
            assertEquals(velocity, position.velocity, 0.0001f)
            clock.frame(40_000_000L)
            assertTrue(first.isCancelled)

            position.snapTo(0.75f)
            assertEquals(0.75f, position.value, 0f)
            assertFalse(position.isRunning)
            reverse.join()
            assertEquals(0.75f, position.value, 0f)
        }
    }

    @Test
    fun releaseVelocityUsesTheDisplayedOverscrollSlope() {
        val raw = 70f
        val extent = 700f
        val observed = (Motion.rubberBand(raw + 0.05f, extent) - Motion.rubberBand(raw - 0.05f, extent)) / 0.1f
        assertEquals(observed, Motion.rubberBandSlope(raw, extent), 0.0002f)
    }

    private suspend fun BroadcastFrameClock.frame(nanos: Long) {
        while (!hasAwaiters) yield()
        sendFrame(nanos)
        yield()
    }
}
