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
import com.google.android.stardroid.activities.util.FullscreenControlsManager
import com.google.android.stardroid.education.ObjectInfoTapHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GestureInterpreter].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GestureInterpreterTest {

    @Mock
    private lateinit var mockFullscreenControlsManager: FullscreenControlsManager

    @Mock
    private lateinit var mockMapMover: MapMover

    @Mock
    private lateinit var mockObjectInfoTapHandler: ObjectInfoTapHandler

    @Mock
    private lateinit var mockScreenDimensionsProvider: GestureInterpreter.ScreenDimensionsProvider

    private lateinit var gestureInterpreter: GestureInterpreter

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(mockScreenDimensionsProvider.screenWidth).thenReturn(1080)
        whenever(mockScreenDimensionsProvider.screenHeight).thenReturn(1920)
    }

    private fun createGestureInterpreter(
        tapHandler: ObjectInfoTapHandler? = null,
        screenProvider: GestureInterpreter.ScreenDimensionsProvider? = null
    ): GestureInterpreter {
        return GestureInterpreter(
            mockFullscreenControlsManager,
            mockMapMover,
            tapHandler,
            screenProvider
        )
    }

    @Test
    fun onDown_returnsTrue() {
        gestureInterpreter = createGestureInterpreter()
        val event = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f)

        val result = gestureInterpreter.onDown(event)

        assertThat(result).isTrue()
    }

    @Test
    fun onFling_returnsTrue() {
        gestureInterpreter = createGestureInterpreter()
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        val result = gestureInterpreter.onFling(null, event, 500f, 300f)

        assertThat(result).isTrue()
    }

    @Test
    fun onSingleTapUp_noHandler_togglesControls() {
        gestureInterpreter = createGestureInterpreter(null, null)
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        gestureInterpreter.onSingleTapUp(event)

        verify(mockFullscreenControlsManager).toggleControls()
    }

    @Test
    fun onSingleTapUp_noScreenProvider_togglesControls() {
        gestureInterpreter = createGestureInterpreter(mockObjectInfoTapHandler, null)
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        gestureInterpreter.onSingleTapUp(event)

        verify(mockFullscreenControlsManager).toggleControls()
        verify(mockObjectInfoTapHandler, never()).handleTap(any(), any(), any(), any())
    }

    @Test
    fun onSingleTapUp_withHandler_objectFound_doesNotToggle() {
        gestureInterpreter = createGestureInterpreter(mockObjectInfoTapHandler, mockScreenDimensionsProvider)
        whenever(mockObjectInfoTapHandler.handleTap(eq(100f), eq(200f), eq(1080), eq(1920)))
            .thenReturn(true)
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        val result = gestureInterpreter.onSingleTapUp(event)

        assertThat(result).isTrue()
        verify(mockObjectInfoTapHandler).handleTap(100f, 200f, 1080, 1920)
        verify(mockFullscreenControlsManager, never()).toggleControls()
    }

    @Test
    fun onSingleTapUp_withHandler_noObjectFound_togglesControls() {
        gestureInterpreter = createGestureInterpreter(mockObjectInfoTapHandler, mockScreenDimensionsProvider)
        whenever(mockObjectInfoTapHandler.handleTap(any(), any(), any(), any()))
            .thenReturn(false)
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        gestureInterpreter.onSingleTapUp(event)

        verify(mockObjectInfoTapHandler).handleTap(100f, 200f, 1080, 1920)
        verify(mockFullscreenControlsManager).toggleControls()
    }

    @Test
    fun onDoubleTap_returnsFalse() {
        gestureInterpreter = createGestureInterpreter()
        val event = createMotionEvent(MotionEvent.ACTION_DOWN, 100f, 200f)

        val result = gestureInterpreter.onDoubleTap(event)

        assertThat(result).isFalse()
    }

    @Test
    fun onSingleTapConfirmed_returnsFalse() {
        gestureInterpreter = createGestureInterpreter()
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        val result = gestureInterpreter.onSingleTapConfirmed(event)

        assertThat(result).isFalse()
    }

    @Test
    fun onSingleTapUp_returnsTrue() {
        gestureInterpreter = createGestureInterpreter()
        val event = createMotionEvent(MotionEvent.ACTION_UP, 100f, 200f)

        val result = gestureInterpreter.onSingleTapUp(event)

        assertThat(result).isTrue()
    }

    @Test
    fun onSingleTapUp_passesCorrectCoordinatesToHandler() {
        gestureInterpreter = createGestureInterpreter(mockObjectInfoTapHandler, mockScreenDimensionsProvider)
        whenever(mockObjectInfoTapHandler.handleTap(any(), any(), any(), any()))
            .thenReturn(false)
        val event = createMotionEvent(MotionEvent.ACTION_UP, 540f, 960f)

        gestureInterpreter.onSingleTapUp(event)

        verify(mockObjectInfoTapHandler).handleTap(540f, 960f, 1080, 1920)
    }

    private fun createMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(
            0L,
            0L,
            action,
            x,
            y,
            0
        )
    }
}
