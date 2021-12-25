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
package com.google.android.stardroid.util.smoothers

import android.hardware.SensorListener
import android.util.Log
import com.google.android.stardroid.math.MathUtils.abs
import com.google.android.stardroid.util.MiscUtil.getTag

/**
 * Exponentially weighted smoothing, as suggested by Chris M.
 *
 */
class ExponentiallyWeightedSmoother(listener: SensorListener, alpha: Float, exponent: Int) :
  SensorSmoother(listener) {
  private val alpha: Float
  private val exponent: Int
  private val last = FloatArray(3)
  private val current = FloatArray(3)
  override fun onSensorChanged(sensor: Int, values: FloatArray) {
    for (i in 0..2) {
      last[i] = current[i]
      val diff = values[i] - last[i]
      var correction = diff * alpha
      for (j in 1 until exponent) {
        correction *= abs(diff)
      }
      if (correction > abs(diff) ||
        correction < -abs(diff)
      ) correction = diff
      current[i] = last[i] + correction
    }
    listener.onSensorChanged(sensor, current)
  }

  companion object {
    private val TAG = getTag(ExponentiallyWeightedSmoother::class.java)
  }

  init {
    Log.d(TAG, "ExponentiallyWeightedSmoother with alpha = $alpha and exp = $exponent")
    this.alpha = alpha
    this.exponent = exponent
  }
}