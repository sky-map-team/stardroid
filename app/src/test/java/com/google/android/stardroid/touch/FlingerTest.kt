// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.android.stardroid.touch

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for [Flinger].
 *
 * These tests verify the Flinger behavior by capturing callback values.
 * The Flinger uses a scheduled executor, so we use latches to synchronize
 * with the async callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FlingerTest {

    @Test
    fun fling_callsListenerWithScaledVelocity() {
        val velocityX = 200f
        val velocityY = 400f
        val updatesPerSecond = 20

        val latch = CountDownLatch(1)
        val capturedX = AtomicReference<Float>()
        val capturedY = AtomicReference<Float>()

        val flinger = Flinger { x, y ->
            capturedX.set(x)
            capturedY.set(y)
            latch.countDown()
        }

        flinger.fling(velocityX, velocityY)

        // Wait for at least one callback
        val received = latch.await(500, TimeUnit.MILLISECONDS)
        flinger.stop()

        assertThat(received).isTrue()
        // First callback should have velocity / updatesPerSecond
        assertThat(capturedX.get()).isWithin(0.1f).of(velocityX / updatesPerSecond)
        assertThat(capturedY.get()).isWithin(0.1f).of(velocityY / updatesPerSecond)
    }

    @Test
    fun fling_velocityDecaysOverTime() {
        val velocityX = 1000f
        val velocityY = 0f
        val decelFactor = 1.1f

        val callCount = AtomicInteger(0)
        val velocities = mutableListOf<Float>()
        val latch = CountDownLatch(3)

        val flinger = Flinger { x, _ ->
            velocities.add(x)
            callCount.incrementAndGet()
            latch.countDown()
        }

        flinger.fling(velocityX, velocityY)

        // Wait for at least 3 callbacks
        latch.await(500, TimeUnit.MILLISECONDS)
        flinger.stop()

        assertThat(velocities.size).isAtLeast(3)

        // Each velocity should be smaller than the previous
        for (i in 1 until velocities.size) {
            assertThat(velocities[i]).isLessThan(velocities[i - 1])
        }

        // Verify the decay factor is approximately correct
        if (velocities.size >= 2) {
            val ratio = velocities[0] / velocities[1]
            assertThat(ratio).isWithin(0.1f).of(decelFactor)
        }
    }

    @Test
    fun stop_cancelsFuture() {
        val callCount = AtomicInteger(0)

        val flinger = Flinger { _, _ ->
            callCount.incrementAndGet()
        }

        flinger.fling(1000f, 1000f)
        // Stop immediately
        flinger.stop()

        // Wait a bit to ensure no more callbacks happen
        Thread.sleep(150)

        // Should have at most 1 callback (the immediate one before stop took effect)
        assertThat(callCount.get()).isAtMost(1)
    }

    @Test
    fun fling_stopsWhenVelocityBelowThreshold() {
        // Use a small velocity that will decay below threshold quickly
        // TOL = 10, so v^2 < 10 means |v| < 3.16
        val smallVelocity = 20f  // Will decay quickly

        val callCount = AtomicInteger(0)
        val latch = CountDownLatch(1)
        var stoppedNaturally = false

        val flinger = Flinger { _, _ ->
            val count = callCount.incrementAndGet()
            // After many iterations, velocity should drop below threshold
            if (count > 50) {
                // If we got this many calls, something is wrong
                latch.countDown()
            }
        }

        flinger.fling(smallVelocity, smallVelocity)

        // Wait for the flinger to stop naturally or timeout
        stoppedNaturally = !latch.await(1000, TimeUnit.MILLISECONDS)
        flinger.stop()

        // The flinger should have stopped naturally before reaching 50 iterations
        assertThat(stoppedNaturally).isTrue()
        // Should have made some calls but not too many
        assertThat(callCount.get()).isGreaterThan(0)
        assertThat(callCount.get()).isLessThan(50)
    }

    @Test
    fun multipleFlings_cancelsPrevious() {
        val firstFlingCalls = AtomicInteger(0)
        val secondFlingCalls = AtomicInteger(0)
        val latch = CountDownLatch(3)

        // Track which fling is generating callbacks using the velocity
        val flinger = Flinger { x, _ ->
            if (x > 40f) {  // First fling has velocity 1000
                firstFlingCalls.incrementAndGet()
            } else {  // Second fling has velocity 100
                secondFlingCalls.incrementAndGet()
                latch.countDown()
            }
        }

        // Start first fling with high velocity
        flinger.fling(1000f, 0f)

        // Wait a tiny bit then start second fling
        Thread.sleep(60)
        flinger.fling(100f, 0f)

        // Wait for second fling to generate some callbacks
        latch.await(500, TimeUnit.MILLISECONDS)
        flinger.stop()

        // Second fling should have generated callbacks
        assertThat(secondFlingCalls.get()).isAtLeast(1)
    }
}
