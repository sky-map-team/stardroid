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

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.ControllerGroup
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MapMover].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MapMoverTest {

    @Mock
    private lateinit var mockModel: AstronomerModel

    @Mock
    private lateinit var mockControllerGroup: ControllerGroup

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    private lateinit var mapMover: MapMover

    private val screenHeight = 1920
    private val fieldOfView = 45f

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        val displayMetrics = DisplayMetrics().apply {
            heightPixels = screenHeight
        }
        whenever(mockContext.resources).thenReturn(mockResources)
        whenever(mockResources.displayMetrics).thenReturn(displayMetrics)
        whenever(mockModel.fieldOfView).thenReturn(fieldOfView)

        mapMover = MapMover(mockModel, mockControllerGroup, mockContext)
    }

    @Test
    fun onDrag_convertsPixelsToRadians() {
        val xPixels = 100f
        val yPixels = 50f

        mapMover.onDrag(xPixels, yPixels)

        // Expected formula: pixels * fieldOfView / (screenHeight * RADIANS_TO_DEGREES)
        val expectedRadiansPerPixel = fieldOfView / (screenHeight * RADIANS_TO_DEGREES)
        val expectedX = -xPixels * expectedRadiansPerPixel
        val expectedY = -yPixels * expectedRadiansPerPixel

        val captorY = argumentCaptor<Float>()
        val captorX = argumentCaptor<Float>()
        verify(mockControllerGroup).changeUpDown(captorY.capture())
        verify(mockControllerGroup).changeRightLeft(captorX.capture())

        assertThat(captorY.firstValue).isWithin(0.0001f).of(expectedY)
        assertThat(captorX.firstValue).isWithin(0.0001f).of(expectedX)
    }

    @Test
    fun onDrag_negatesYForUpDown() {
        // Positive Y pixels (dragging down) should result in negative up/down change
        mapMover.onDrag(0f, 100f)

        val captor = argumentCaptor<Float>()
        verify(mockControllerGroup).changeUpDown(captor.capture())
        assertThat(captor.firstValue).isLessThan(0f)
    }

    @Test
    fun onDrag_negatesXForRightLeft() {
        // Positive X pixels (dragging right) should result in negative right/left change
        mapMover.onDrag(100f, 0f)

        val captor = argumentCaptor<Float>()
        verify(mockControllerGroup).changeRightLeft(captor.capture())
        assertThat(captor.firstValue).isLessThan(0f)
    }

    @Test
    fun onRotate_negatesDegrees() {
        mapMover.onRotate(30f)

        verify(mockControllerGroup).rotate(-30f)
    }

    @Test
    fun onRotate_negativeAngle() {
        mapMover.onRotate(-45f)

        verify(mockControllerGroup).rotate(45f)
    }

    @Test
    fun onStretch_invertsRatio() {
        // When stretching (zooming out with fingers), ratio > 1
        // The view field should shrink, so we use 1/ratio
        mapMover.onStretch(2.0f)

        verify(mockControllerGroup).zoomBy(0.5f)
    }

    @Test
    fun onStretch_ratioLessThan1() {
        // When pinching (zooming in), ratio < 1
        // The view field should grow
        mapMover.onStretch(0.5f)

        verify(mockControllerGroup).zoomBy(2.0f)
    }

    @Test
    fun onDrag_returnsTrue() {
        val result = mapMover.onDrag(100f, 100f)
        assertThat(result).isTrue()
    }

    @Test
    fun onRotate_returnsTrue() {
        val result = mapMover.onRotate(45f)
        assertThat(result).isTrue()
    }

    @Test
    fun onStretch_returnsTrue() {
        val result = mapMover.onStretch(1.5f)
        assertThat(result).isTrue()
    }

    @Test
    fun onDrag_usesCurrentFieldOfView() {
        // First drag with fieldOfView = 45
        mapMover.onDrag(100f, 0f)

        val captor1 = argumentCaptor<Float>()
        verify(mockControllerGroup).changeRightLeft(captor1.capture())
        val firstValue = captor1.firstValue

        // Change field of view
        whenever(mockModel.fieldOfView).thenReturn(90f)
        mapMover.onDrag(100f, 0f)

        val captor2 = argumentCaptor<Float>()
        // Called twice now
        verify(mockControllerGroup, org.mockito.Mockito.times(2)).changeRightLeft(captor2.capture())
        val secondValue = captor2.allValues[1]

        // With double the field of view, the radians per pixel should double
        assertThat(secondValue).isWithin(0.0001f).of(firstValue * 2)
    }
}
