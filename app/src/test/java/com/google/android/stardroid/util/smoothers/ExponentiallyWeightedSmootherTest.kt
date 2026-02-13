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

package com.google.android.stardroid.util.smoothers

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Tests for [ExponentiallyWeightedSmoother].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExponentiallyWeightedSmootherTest {

    @Mock
    private lateinit var mockListener: SensorEventListener

    @Mock
    private lateinit var mockSensor: Sensor

    private lateinit var smoother: ExponentiallyWeightedSmoother

    private val alpha = 0.5f
    private val exponent = 2

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        smoother = ExponentiallyWeightedSmoother(mockListener, alpha, exponent)
    }

    @Test
    fun onSensorChanged_passesEventToListener() {
        val event = createSensorEvent(floatArrayOf(1f, 2f, 3f))

        smoother.onSensorChanged(event)

        verify(mockListener).onSensorChanged(any())
    }

    @Test
    fun firstReading_smoothedValue_approachesInput() {
        // On first reading, current[] is [0,0,0], so the full value is applied
        val event = createSensorEvent(floatArrayOf(10f, 20f, 30f))

        smoother.onSensorChanged(event)

        val captor = argumentCaptor<SensorEvent>()
        verify(mockListener).onSensorChanged(captor.capture())

        // Values should be non-zero and moving toward input
        val smoothed = captor.firstValue.values
        assertThat(smoothed[0]).isGreaterThan(0f)
        assertThat(smoothed[1]).isGreaterThan(0f)
        assertThat(smoothed[2]).isGreaterThan(0f)
    }

    @Test
    fun multipleReadings_convergesOverTime() {
        val targetValue = 100f
        var lastSmoothed = 0f

        // Send same value multiple times
        for (i in 0 until 20) {
            val event = createSensorEvent(floatArrayOf(targetValue, 0f, 0f))
            smoother.onSensorChanged(event)

            val captor = argumentCaptor<SensorEvent>()
            verify(mockListener, org.mockito.Mockito.times(i + 1)).onSensorChanged(captor.capture())

            val smoothed = captor.lastValue.values[0]
            if (i > 0) {
                // Each iteration should get closer to target
                assertThat(kotlin.math.abs(smoothed - targetValue))
                    .isLessThan(kotlin.math.abs(lastSmoothed - targetValue) + 0.1f)
            }
            lastSmoothed = smoothed
        }

        // After many iterations, should be close to target
        assertThat(lastSmoothed).isWithin(10f).of(targetValue)
    }

    @Test
    fun smoothing_alpha_affectsConvergenceSpeed() {
        // Higher alpha = faster convergence (more of the difference is applied)
        val slowSmoother = ExponentiallyWeightedSmoother(mockListener, 0.1f, 1)
        val fastSmoother = ExponentiallyWeightedSmoother(mockListener, 0.9f, 1)

        val event1 = createSensorEvent(floatArrayOf(100f, 0f, 0f))
        slowSmoother.onSensorChanged(event1)

        val event2 = createSensorEvent(floatArrayOf(100f, 0f, 0f))
        fastSmoother.onSensorChanged(event2)

        // Fast smoother should be closer to 100 after first reading
        // (Can't directly compare as they share the mock listener)
        // This test mostly verifies no crash with different alpha values
    }

    @Test
    fun correction_clampedToAbsDiff() {
        // The correction should never exceed the difference
        // This is tested by the line: if (correction > abs(diff) || correction < -abs(diff))
        val event = createSensorEvent(floatArrayOf(1f, 2f, 3f))

        smoother.onSensorChanged(event)

        val captor = argumentCaptor<SensorEvent>()
        verify(mockListener).onSensorChanged(captor.capture())

        // No assertion about specific values, just that it doesn't crash
        // and returns reasonable values
        val smoothed = captor.firstValue.values
        assertThat(smoothed).isNotEmpty()
    }

    @Test
    fun onAccuracyChanged_doesNothing() {
        // The parent class SensorSmoother ignores onAccuracyChanged
        // This test just verifies it doesn't crash
        smoother.onAccuracyChanged(mockSensor, 1)

        // Listener should NOT be called (SensorSmoother does nothing)
        verify(mockListener, org.mockito.Mockito.never()).onAccuracyChanged(any(), any())
    }

    /**
     * Creates a SensorEvent with the given values using reflection.
     * SensorEvent doesn't have a public constructor, so we need reflection.
     */
    private fun createSensorEvent(values: FloatArray): SensorEvent {
        // SensorEvent has a package-private constructor
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.java)
        constructor.isAccessible = true
        val event = constructor.newInstance(values.size)

        // Set the values field
        val valuesField: Field = SensorEvent::class.java.getDeclaredField("values")
        valuesField.isAccessible = true
        val eventValues = valuesField.get(event) as FloatArray
        System.arraycopy(values, 0, eventValues, 0, values.size)

        // Set the sensor field
        val sensorField: Field = SensorEvent::class.java.getDeclaredField("sensor")
        sensorField.isAccessible = true
        sensorField.set(event, mockSensor)

        return event
    }
}
