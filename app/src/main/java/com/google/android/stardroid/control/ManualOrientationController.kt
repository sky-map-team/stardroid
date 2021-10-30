// Copyright 2008 Google Inc.
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
package com.google.android.stardroid.control

import android.util.Log
import com.google.android.stardroid.math.calculateRotationMatrix
import com.google.android.stardroid.util.MiscUtil

/**
 * Allows user-input elements such as touch screens and trackballs to move the
 * map.
 *
 * @author John Taylor
 */
class ManualOrientationController : AbstractController() {
    override fun start() {
        // Nothing to do
    }

    override fun stop() {
        // Nothing to do
    }

    /**
     * Moves the astronomer's pointing right or left.
     *
     * @param radians the angular change in the pointing in radians (only
     * accurate in the limit as radians tends to 0.)
     */
    fun changeRightLeft(radians: Float) {
        // TODO(johntaylor): Some of the Math in here perhaps belongs in
        // AstronomerModel.
        if (!enabled) {
            return
        }
        val pointing = model.pointing
        val pointingXyz = pointing.lineOfSight
        val topXyz = pointing.perpendicular
        val horizontalXyz = pointingXyz * topXyz
        val deltaXyz = horizontalXyz * radians
        val newPointingXyz = pointingXyz + deltaXyz
        newPointingXyz.normalize()
        model.setPointing(newPointingXyz, topXyz)
    }

    /**
     * Moves the astronomer's pointing up or down.
     *
     * @param radians the angular change in the pointing in radians (only
     * accurate in the limit as radians tends to 0.)
     */
    fun changeUpDown(radians: Float) {
        if (!enabled) {
            return
        }
        // Log.d(TAG, "Scrolling up down");
        val pointing = model.pointing
        val pointingXyz = pointing.lineOfSight
        // Log.d(TAG, "Current view direction " + viewDir);
        val topXyz = pointing.perpendicular
        val deltaXyz = topXyz * -radians
        val newPointingXyz = pointingXyz + deltaXyz
        newPointingXyz.normalize()
        val deltaUpXyz = pointingXyz * radians
        val newUpXyz =  topXyz + deltaUpXyz
        newUpXyz.normalize()
        model.setPointing(newPointingXyz, newUpXyz)
    }

    /**
     * Rotates the astronomer's view.
     */
    fun rotate(degrees: Float) {
        if (!enabled) {
            return
        }
        Log.d(TAG, "Rotating by $degrees")
        val pointing = model.pointing
        val pointingXyz = pointing.lineOfSight
        val rotation = calculateRotationMatrix(degrees, pointingXyz)
        val topXyz = pointing.perpendicular
        val newUpXyz = rotation * topXyz
        newUpXyz.normalize()
        model.setPointing(pointingXyz, newUpXyz)
    }

    companion object {
        private val TAG = MiscUtil.getTag(ManualOrientationController::class.java)
    }
}