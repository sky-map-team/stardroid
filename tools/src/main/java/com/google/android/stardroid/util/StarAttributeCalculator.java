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

package com.google.android.stardroid.util;

import android.graphics.Color;

/**
 * Converts celestial magnitudes to brightness on a scale of 0 to 1.
 *
 */
public class StarAttributeCalculator {
  // Much above this and the app crashes - best guess we run out of some openGL resource.
  // TODO(jontayler): find out why.
  public static final float MAX_MAGNITUDE = 5.6f;
  private static final int MAX_SIZE = 5;

  private enum Channel {
    A(24), R(16), G(8), B(0);

    private final int offset;

    Channel(int offset) {
      this.offset = offset;
    }

    public int getOffset() {
      return offset;
    }
  }

  private StarAttributeCalculator() {}

  public static int getConstellationColor(float magnitude) {
    return getColor(magnitude, Color.CYAN);
  }

  private static int getChannelValue(int baseColor, Channel c, float shade) {
    int value = (baseColor >> c.getOffset()) & 0xFF;
    int newValue = (int)(shade * value);
    return newValue << c.getOffset();
  }

  public static int getColor(float magnitude, int baseColor) {
    if (magnitude > MAX_MAGNITUDE) return Color.BLACK;
    if (magnitude <= 0.0) return baseColor;

    float shade = 1.0f - magnitude/(MAX_MAGNITUDE + 3.0f);

    int result = 0xFF000000;
    for (Channel c : Channel.values()) {
      result += getChannelValue(baseColor, c, shade);
    }
    return result;
  }

  /** Print out the byte associated with the R,G, and B color components in the given Color int. */
/*  private static void printBytes(int color) {
    System.out.println(colorToString(color));
  }
/*
  private static String colorToString(final int color) {
    return String.format("a=%03d, r=%03d, g=%03d, b=%03d",
        Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
  }

  public static void main(String[] args) {
    for (float m = 0.0f; m < 6.0f; m += 0.4f) {
      System.out.println("Magnitude: "+m);
      printBytes(getColor(m, Color.WHITE));
      printBytes(getConstellationColor(m));
      System.out.println();
    }
  }
*/
  public static int getSize(float magnitude) {
    return (int) Math.max(MAX_SIZE - magnitude, 1);
  }
}