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

import android.content.Context
import android.util.Log
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.control.ControllerGroup
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.touch.DragRotateZoomGestureDetector.DragRotateZoomGestureDetectorListener
import com.google.android.stardroid.util.MiscUtil.getTag

/**
 * Applies drags, zooms and rotations to the model.
 * Listens for events from the DragRotateZoomGestureDetector.
 *
 * @author John Taylor
 */
class MapMover(
  private val model: AstronomerModel,
  private val controllerGroup: ControllerGroup,
  context: Context
) : DragRotateZoomGestureDetectorListener {
  private val sizeTimesRadiansToDegrees: Float
  override fun onDrag(xPixels: Float, yPixels: Float): Boolean {
    // Log.d(TAG, "Dragging by " + xPixels + ", " + yPixels);
    val pixelsToRadians = model.fieldOfView / sizeTimesRadiansToDegrees
    controllerGroup.changeUpDown(-yPixels * pixelsToRadians)
    controllerGroup.changeRightLeft(-xPixels * pixelsToRadians)
    return true
  }

  override fun onRotate(degrees: Float): Boolean {
    controllerGroup.rotate(-degrees)
    return true
  }

  override fun onStretch(ratio: Float): Boolean {
    controllerGroup.zoomBy(1.0f / ratio)
    return true
  }

  companion object {
    private val TAG = getTag(MapMover::class.java)
  }

  init {
    val metrics = context.resources.displayMetrics
    val screenLongSize = metrics.heightPixels
    Log.i(TAG, "Screen height is $screenLongSize pixels.")
    sizeTimesRadiansToDegrees = screenLongSize * RADIANS_TO_DEGREES
  }
}