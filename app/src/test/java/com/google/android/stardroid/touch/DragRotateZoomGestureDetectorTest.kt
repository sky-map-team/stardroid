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

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/**
 * Tests for [DragRotateZoomGestureDetector].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DragRotateZoomGestureDetectorTest {

    @Mock
    private lateinit var mockListener: DragRotateZoomGestureDetector.DragRotateZoomGestureDetectorListener

    private lateinit var detector: DragRotateZoomGestureDetector

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(mockListener.onDrag(any(), any())).thenReturn(true)
        whenever(mockListener.onStretch(any())).thenReturn(true)
        whenever(mockListener.onRotate(any())).thenReturn(true)
        detector = DragRotateZoomGestureDetector(mockListener)
    }

    @Test
    fun actionDown_returnsTrue() {
        val event = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f)
        val result = detector.onTouchEvent(event)
        assertThat(result).isTrue()
    }

    @Test
    fun actionMove_afterDown_callsOnDrag() {
        // First, send ACTION_DOWN to enter DRAGGING state
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))

        // Then, send ACTION_MOVE
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_MOVE, 150f, 250f))

        // Verify onDrag was called with the delta
        verify(mockListener).onDrag(50f, 50f)
    }

    @Test
    fun actionMove_multipleMoves_tracksDelta() {
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_MOVE, 150f, 250f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_MOVE, 160f, 260f))

        // The second move should be relative to the previous position (150, 250)
        // First call was (50, 50), second call was (10, 10)
        val inOrder = org.mockito.Mockito.inOrder(mockListener)
        inOrder.verify(mockListener).onDrag(50f, 50f)
        inOrder.verify(mockListener).onDrag(10f, 10f)
    }

    @Test
    fun actionUp_afterDrag_resetsState() {
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_MOVE, 150f, 250f))
        val result = detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_UP, 150f, 250f))

        assertThat(result).isTrue()
    }

    @Test
    fun actionPointerDown_transitionsToDragging2() {
        // Start single-finger drag
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))

        // Add second finger
        val event = createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            100f, 200f, 200f, 300f
        )
        val result = detector.onTouchEvent(event)

        assertThat(result).isTrue()
    }

    @Test
    fun twoFingerMove_callsOnDragWithAverage() {
        // Start single-finger drag
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))

        // Add second finger at (200, 300)
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            100f, 200f, 200f, 300f
        ))

        // Move both fingers: finger1 to (110, 210), finger2 to (220, 330)
        // Delta finger1: (10, 10), Delta finger2: (20, 30)
        // Average: (15, 20)
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_MOVE,
            110f, 210f, 220f, 330f
        ))

        verify(mockListener).onDrag(15f, 20f)
    }

    @Test
    fun twoFingerPinchZoom_callsOnStretch() {
        // Start with fingers 100 pixels apart
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 0f, 0f))
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            0f, 0f, 100f, 0f
        ))

        // Move fingers to be 200 pixels apart (zoom in 2x)
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_MOVE,
            0f, 0f, 200f, 0f
        ))

        val captor = argumentCaptor<Float>()
        verify(mockListener).onStretch(captor.capture())
        // Ratio should be 2.0 (200/100)
        assertThat(captor.firstValue).isWithin(0.01f).of(2.0f)
    }

    @Test
    fun twoFingerPinchZoomIn_callsOnStretchWithRatioLessThan1() {
        // Start with fingers 200 pixels apart
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 0f, 0f))
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            0f, 0f, 200f, 0f
        ))

        // Move fingers to be 100 pixels apart (zoom out, ratio 0.5)
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_MOVE,
            0f, 0f, 100f, 0f
        ))

        val captor = argumentCaptor<Float>()
        verify(mockListener).onStretch(captor.capture())
        assertThat(captor.firstValue).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun twoFingerRotation_callsOnRotate() {
        // Start with horizontal line: (0,0) to (100, 0)
        // Note: atan2(x, y) is used (not standard atan2(y, x)), so this gives angle π/2
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 0f, 0f))
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            0f, 0f, 100f, 0f
        ))

        // Rotate to diagonal: (0,0) to (70.7, 70.7)
        // With atan2(x, y): atan2(70.7, 70.7) = π/4 = 45 degrees
        // Delta = 45 - 90 = -45 degrees
        val diag = (100.0 / sqrt(2.0)).toFloat()
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_MOVE,
            0f, 0f, diag, diag
        ))

        val captor = argumentCaptor<Float>()
        verify(mockListener).onRotate(captor.capture())
        // Should be approximately -45 degrees (rotated clockwise)
        assertThat(captor.firstValue).isWithin(1f).of(-45f)
    }

    @Test
    fun actionPointerUp_transitionsToReady() {
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            100f, 200f, 200f, 300f
        ))

        val result = detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            100f, 200f, 200f, 300f
        ))

        assertThat(result).isTrue()
    }

    @Test
    fun unexpectedPointerCount_inDragging2_returnsFalse() {
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))
        detector.onTouchEvent(createTwoFingerEvent(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            100f, 200f, 200f, 300f
        ))

        // Create a malformed event with only 1 pointer when we expect 2
        val badEvent = createMotionEvent(MotionEvent.ACTION_MOVE, 100f, 200f)
        val result = detector.onTouchEvent(badEvent)

        assertThat(result).isFalse()
    }

    @Test
    fun actionDown_whenInReadyState_startsNewDrag() {
        // Complete a drag cycle
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f))

        // Start new drag
        val result = detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_DOWN, 50f, 60f))

        assertThat(result).isTrue()
    }

    // Helper methods to create MotionEvents

    private fun createMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(
            0L,  // downTime
            0L,  // eventTime
            action,
            x,
            y,
            0    // metaState
        )
    }

    private fun createTwoFingerEvent(
        action: Int,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0 },
            MotionEvent.PointerProperties().apply { id = 1 }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = x1; y = y1 },
            MotionEvent.PointerCoords().apply { x = x2; y = y2 }
        )
        return MotionEvent.obtain(
            0L,           // downTime
            0L,           // eventTime
            action,
            2,            // pointerCount
            properties,
            coords,
            0,            // metaState
            0,            // buttonState
            1f,           // xPrecision
            1f,           // yPrecision
            0,            // deviceId
            0,            // edgeFlags
            0,            // source
            0             // flags
        )
    }
}
