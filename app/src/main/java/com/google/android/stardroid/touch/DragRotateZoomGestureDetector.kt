// Copyright 2010 Google Inc.
//
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

import android.util.Log
import android.view.MotionEvent
import com.google.android.stardroid.math.MathUtils.atan2
import com.google.android.stardroid.math.MathUtils.sqrt
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.util.MiscUtil.getTag

/**
 * Detects map drags, rotations and pinch zooms.
 *
 * @author John Taylor
 */
class DragRotateZoomGestureDetector(private val listener: DragRotateZoomGestureDetectorListener) {
  /**
   * Listens for the gestures detected by the [DragRotateZoomGestureDetector].
   *
   * @author John Taylor
   */
  interface DragRotateZoomGestureDetectorListener {
    fun onDrag(xPixels: Float, yPixels: Float): Boolean
    fun onStretch(ratio: Float): Boolean
    fun onRotate(degrees: Float): Boolean
  }

  private enum class State {
    READY, DRAGGING, DRAGGING2
  }

  private var currentState = State.READY
  private var last1X = 0f
  private var last1Y = 0f
  private var last2X = 0f
  private var last2Y = 0f
  fun onTouchEvent(ev: MotionEvent): Boolean {
    // The state changes are as follows.
    // READY -> DRAGGING -> DRAGGING2 -> READY
    //
    // ACTION_DOWN: READY->DRAGGING
    //   last position = current position
    //
    // ACTION_MOVE: no state change
    //   calculate move = current position - last position
    //   last position = current position
    //
    // ACTION_UP: DRAGGING->READY
    //   last position = null
    // ...or...from DRAGGING
    //
    // ACTION_POINTER_DOWN: DRAGGING->DRAGGING2
    //   we're in multitouch mode
    //   last position1 = current position1
    //   last position2 = current position2
    //
    // ACTION_MOVE:
    //   calculate move
    //   last position1 = current position1
    //   last position2 = current position2
    val actionCode = ev.action and MotionEvent.ACTION_MASK
    // Log.d(TAG, "Action: " + actionCode + ", current state " + currentState);
    if (actionCode == MotionEvent.ACTION_DOWN || currentState == State.READY) {
      currentState = State.DRAGGING
      last1X = ev.x
      last1Y = ev.y
      // Log.d(TAG, "Down.  Store last position " + last1X + ", " + last1Y);
      return true
    }
    if (actionCode == MotionEvent.ACTION_MOVE && currentState == State.DRAGGING) {
      // Log.d(TAG, "Move");
      val current1X = ev.x
      val current1Y = ev.y
      // Log.d(TAG, "Move.  Last position " + last1X + ", " + last1Y +
      //    "Current position " + current1X + ", " + current1Y);
      listener.onDrag(current1X - last1X, current1Y - last1Y)
      last1X = current1X
      last1Y = current1Y
      return true
    }
    if (actionCode == MotionEvent.ACTION_MOVE && currentState == State.DRAGGING2) {
      // Log.d(TAG, "Move with two fingers");
      val pointerCount = ev.pointerCount
      if (pointerCount != 2) {
        Log.w(TAG, "Expected exactly two pointers but got $pointerCount")
        return false
      }
      val current1X = ev.getX(0)
      val current1Y = ev.getY(0)
      val current2X = ev.getX(1)
      val current2Y = ev.getY(1)
      // Log.d(TAG, "Old Point 1: " + lastPointer1X + ", " + lastPointer1Y);
      // Log.d(TAG, "Old Point 2: " + lastPointer2X + ", " + lastPointer2Y);
      // Log.d(TAG, "New Point 1: " + current1X + ", " + current1Y);
      // Log.d(TAG, "New Point 2: " + current2X + ", " + current2Y);
      val distanceMovedX1 = current1X - last1X
      val distanceMovedY1 = current1Y - last1Y
      val distanceMovedX2 = current2X - last2X
      val distanceMovedY2 = current2Y - last2Y

      // Log.d(TAG, "Point 1 moved by: " + distanceMovedX1 + ", " + distanceMovedY1);
      // Log.d(TAG, "Point 2 moved by: " + distanceMovedX2 + ", " + distanceMovedY2);

      // Dragging map by the mean of the points
      listener.onDrag(
        (distanceMovedX1 + distanceMovedX2) / 2,
        (distanceMovedY1 + distanceMovedY2) / 2
      )

      // These are the vectors between the two points.
      val vectorLastX = last1X - last2X
      val vectorLastY = last1Y - last2Y
      val vectorCurrentX = current1X - current2X
      val vectorCurrentY = current1Y - current2Y

      // Log.d(TAG, "Previous vector: " + vectorBeforeX + ", " + vectorBeforeY);
      // Log.d(TAG, "Current vector: " + vectorCurrentX + ", " + vectorCurrentY);
      val lengthRatio = sqrt(
        normSquared(vectorCurrentX, vectorCurrentY)
            / normSquared(vectorLastX, vectorLastY)
      )
      // Log.d(TAG, "Stretching map by ratio " + ratio);
      listener.onStretch(lengthRatio)
      val angleLast = atan2(vectorLastX, vectorLastY)
      val angleCurrent = atan2(vectorCurrentX, vectorCurrentY)
      // Log.d(TAG, "Angle before " + angleBefore);
      // Log.d(TAG, "Angle after " + angleAfter);
      val angleDelta = angleCurrent - angleLast
      // Log.d(TAG, "Rotating map by angle delta " + angleDelta);
      listener.onRotate(angleDelta * RADIANS_TO_DEGREES)
      last1X = current1X
      last1Y = current1Y
      last2X = current2X
      last2Y = current2Y
      return true
    }
    if (actionCode == MotionEvent.ACTION_UP && currentState != State.READY) {
      // Log.d(TAG, "Up");
      currentState = State.READY
      return true
    }
    if (actionCode == MotionEvent.ACTION_POINTER_DOWN && currentState == State.DRAGGING) {
      //Log.d(TAG, "Non primary pointer down " + pointer);
      val pointerCount = ev.pointerCount
      if (pointerCount != 2) {
        Log.w(TAG, "Expected exactly two pointers but got $pointerCount")
        return false
      }
      currentState = State.DRAGGING2
      last1X = ev.getX(0)
      last1Y = ev.getY(0)
      last2X = ev.getX(1)
      last2Y = ev.getY(1)
      return true
    }
    if (actionCode == MotionEvent.ACTION_POINTER_UP && currentState == State.DRAGGING2) {
      // Log.d(TAG, "Non primary pointer up " + pointer);
      // Let's just drop dragging for now - can worry about continuity with one finger
      // drag later.
      currentState = State.READY
      return true
    }
    // Log.d(TAG, "End state " + currentState);
    return false
  }

  companion object {
    private val TAG = getTag(DragRotateZoomGestureDetector::class.java)
    private fun normSquared(x: Float, y: Float): Float {
      return x * x + y * y
    }
  }
}