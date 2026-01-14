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
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import com.google.android.stardroid.activities.util.FullscreenControlsManager
import com.google.android.stardroid.education.ObjectInfoTapHandler
import com.google.android.stardroid.util.MiscUtil.getTag

/**
 * Processes touch events and scrolls the screen in manual mode.
 *
 * @author John Taylor
 */
class GestureInterpreter(
  private val fullscreenControlsManager: FullscreenControlsManager,
  private val mapMover: MapMover,
  private val objectInfoTapHandler: ObjectInfoTapHandler? = null,
  private val screenDimensionsProvider: ScreenDimensionsProvider? = null
) : SimpleOnGestureListener() {

  /**
   * Interface to provide screen dimensions for hit testing.
   */
  interface ScreenDimensionsProvider {
    val screenWidth: Int
    val screenHeight: Int
  }

  private val flinger = Flinger { distanceX: Float, distanceY: Float ->
    mapMover.onDrag(
      distanceX,
      distanceY
    )
  }

  override fun onDown(unused: MotionEvent): Boolean {
    Log.d(TAG, "Tap down")
    flinger.stop()
    return true
  }

  override fun onFling(
    unused1: MotionEvent?,
    unused2: MotionEvent,
    velocityX: Float,
    velocityY: Float
  ): Boolean {
    Log.d(TAG, "Flinging $velocityX, $velocityY")
    flinger.fling(velocityX, velocityY)
    return true
  }

  override fun onSingleTapUp(e: MotionEvent): Boolean {
    Log.d(TAG, "Tap up")
    // Try to handle tap for object info first (if handler is available)
    val dimensionsProvider = screenDimensionsProvider
    val tapHandler = objectInfoTapHandler
    if (tapHandler != null && dimensionsProvider != null) {
      val consumed = tapHandler.handleTap(
        e.x,
        e.y,
        dimensionsProvider.screenWidth,
        dimensionsProvider.screenHeight
      )
      if (consumed) {
        Log.d(TAG, "Tap consumed by object info handler")
        return true
      }
    }
    // If not consumed by object info handler, toggle fullscreen controls
    fullscreenControlsManager.toggleControls()
    return true
  }

  override fun onDoubleTap(e: MotionEvent): Boolean {
    Log.d(TAG, "Double tap")
    return false
  }

  override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
    Log.d(TAG, "Confirmed single tap")
    return false
  }

  companion object {
    private val TAG = getTag(GestureInterpreter::class.java)
  }
}