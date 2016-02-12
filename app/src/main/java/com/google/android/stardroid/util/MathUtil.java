// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.util;

import android.util.FloatMath;

/**
 * Methods for doing mathematical operations with floats.
 *
 * @author Brent Bryan
 */
public class MathUtil {
  private MathUtil() {}

  public static final float PI = (float) Math.PI;
  public static final float TWO_PI = 2f * PI;
  public static final float DEGREES_TO_RADIANS = PI / 180;
  public static final float RADIANS_TO_DEGREES = 180 / PI;

  public static float abs(float x) {
    return Math.abs(x);
  }

  public static float sqrt(float x) {
    return FloatMath.sqrt(x);
  }

  public static float floor(float x) {
    return FloatMath.floor(x);
  }

  public static float ceil(float x) {
    return FloatMath.ceil(x);
  }

  public static float sin(float x) {
    return FloatMath.sin(x);
  }

  public static float cos(float x) {
    return FloatMath.cos(x);
  }

  public static float tan(float x) {
    return FloatMath.sin(x) / FloatMath.cos(x);
  }

  public static float asin(float x) {
    return (float) Math.asin(x);
  }

  public static float acos(float x) {
    return (float) Math.acos(x);
  }

  public static float atan(float x) {
    return (float) Math.atan(x);
  }

  public static float atan2(float y, float x) {
    return (float) Math.atan2(y, x);
  }

  public static float log10(float x) {
    return (float) Math.log10(x);
  }

  /**
   * Returns x if x <= y, or x-y if not. While this utility performs a role similar to a modulo
   * operation, it assumes x >=0 and that x < 2y.
   */
  public static float quickModulo(float x, float y) {
    if (x > y) return x - y;
    return x;
  }

  /**
   * Returns a random number between 0 and f.
   */
  public static float random(float f) {
    return ((float) Math.random()) * f;
  }

  public static float pow(float x, float exponent) {
    return (float) Math.pow(x, exponent);
  }

}