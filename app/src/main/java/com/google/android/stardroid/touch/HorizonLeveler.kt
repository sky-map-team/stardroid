// Copyright 2024 Google Inc.
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
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.calculateRotationMatrix
import com.google.android.stardroid.util.MiscUtil.getTag
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * After a two-finger rotation gesture, gently springs the horizon back to
 * horizontal over roughly 1–2 seconds, modelled on [Flinger].
 */
class HorizonLeveler(
    private val model: AstronomerModel,
    private val rotationCallback: (Float) -> Unit
) {
    private val updatesPerSecond = 20
    private val timeIntervalMillis = 1000L / updatesPerSecond
    private val springFactor = 0.20f
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private var levelTask: ScheduledFuture<*>? = null

    fun start() {
        stop()
        Log.d(TAG, "Starting horizon leveler")
        levelTask = executor.scheduleAtFixedRate(
            ::step, 0, timeIntervalMillis, TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        levelTask?.cancel(true)
        levelTask = null
    }

    private fun step() {
        val angle = computeMisalignmentDegrees()
        if (kotlin.math.abs(angle) < 0.1f) {
            Log.d(TAG, "Horizon level reached, stopping leveler")
            stop()
            return
        }
        // Negate: calculateRotationMatrix uses a transposed (CW) convention, so a positive
        // angle here would rotate *away* from the target.  The sign flip mirrors what
        // MapMover.onRotate already does when forwarding gesture rotations.
        val delta = -angle * springFactor
        rotationCallback(delta)
    }

    /**
     * Returns the signed angle (in degrees) by which [currentPerp] must rotate
     * around [lineOfSight] to reach the phone-roll-corrected zenith direction.
     * Returns 0f when looking straight at or away from the zenith (degenerate case).
     */
    private fun computeMisalignmentDegrees(): Float {
        val pointing = model.pointing
        val lineOfSight = pointing.lineOfSight
        val currentPerp = pointing.perpendicular
        val zenith = model.zenith

        // Project zenith onto the view plane (perpendicular to lineOfSight).
        val zenithDotLos = zenith dot lineOfSight
        val zenithProj = zenith - (lineOfSight * zenithDotLos)

        if (zenithProj.length2 < 0.001f) {
            // Looking straight up or down — degenerate; skip leveling.
            return 0f
        }
        zenithProj.normalize()

        // Snap the phone's roll to the nearest 90° so the horizon aligns with a
        // physical edge of the phone (portrait or landscape, normal or reversed).
        val upPhone = model.phoneUpDirection
        val rawRollDeg = kotlin.math.atan2(upPhone.x, upPhone.y) * RADIANS_TO_DEGREES
        val snappedRollDeg = kotlin.math.round(rawRollDeg / 90f) * 90f
        val targetPerp = calculateRotationMatrix(snappedRollDeg.toFloat(), lineOfSight) * zenithProj

        // Signed angle from currentPerp to targetPerp around lineOfSight.
        val cross = currentPerp * targetPerp          // cross product
        val sinAngle = cross dot lineOfSight           // component along line of sight gives sign
        val cosAngle = currentPerp dot targetPerp
        return kotlin.math.atan2(sinAngle, cosAngle) * RADIANS_TO_DEGREES
    }

    companion object {
        private val TAG = getTag(HorizonLeveler::class.java)
    }
}
