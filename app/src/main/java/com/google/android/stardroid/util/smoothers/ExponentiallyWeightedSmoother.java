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
package com.google.android.stardroid.util.smoothers;

import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.google.android.stardroid.util.MathUtil;
import com.google.android.stardroid.util.MiscUtil;

/**
 * Exponentially weighted smoothing, as suggested by Chris M.
 *
 */
public class ExponentiallyWeightedSmoother extends SensorSmoother {
  private static final String TAG = MiscUtil.getTag(ExponentiallyWeightedSmoother.class);
  private float alpha;
  private int exponent;

  public ExponentiallyWeightedSmoother(SensorEventListener listener, float alpha, int exponent) {
    super(listener);
    Log.d(TAG, "ExponentionallyWeightedSmoother with alpha = " + alpha + " and exp = " + exponent);
    this.alpha = alpha;
    this.exponent = exponent;
  }

  private float[] last = new float[3];
  private float[] current = new float[3];

  @Override
  public void onSensorChanged(SensorEvent event) {
    for (int i = 0; i < 3; ++i) {
      last[i] = current[i];
      float diff = event.values[i] - last[i];
      float correction = diff * alpha;
      for (int j = 1; j < exponent; ++j) {
        correction *= MathUtil.abs(diff);
      }
      if (correction > MathUtil.abs(diff) ||
              correction < -MathUtil.abs(diff)) correction = diff;
      current[i] = last[i] + correction;
    }
    listener.onSensorChanged(event);
  }
}
