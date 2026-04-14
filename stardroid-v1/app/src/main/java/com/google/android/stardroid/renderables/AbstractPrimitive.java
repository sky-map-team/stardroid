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

package com.google.android.stardroid.renderables;

import android.graphics.Color;

import com.google.android.stardroid.math.CoordinateManipulationsKt;
import com.google.android.stardroid.math.Vector3;

import java.util.List;

/** This class represents the base of an astronomical object to be
 * displayed by the UI.  These object need not be only stars and
 * galaxies but also include labels (such as the name of major stars)
 * and constellation depictions.
 *
 * @author Brent Bryan
 */
public abstract class AbstractPrimitive {
  /** Each source has an update granularity associated with it, which
   *  defines how often it's provider expects its value to change.
   */
  public enum UpdateGranularity {
    Second, Minute, Hour, Day, Year
  }

  public UpdateGranularity granularity;

  private final int color;
  private final Vector3 xyz;
  private List<String> names;

  @Deprecated
  AbstractPrimitive() {
    this(CoordinateManipulationsKt.getGeocentricCoords(0.0f, 0.0f), Color.BLACK);
  }

  protected AbstractPrimitive(int color) {
    this(CoordinateManipulationsKt.getGeocentricCoords(0.0f, 0.0f), color);
  }

  protected AbstractPrimitive(Vector3 geocentricCoords, int color) {
    this.xyz = geocentricCoords;
    this.color = color;
  }

  public List<String> getNames() {
    return names;
  }

  public void setNames(List<String> names) {
    this.names = names;
  }

  public int getColor() {
    return color;
  }

  public Vector3 getLocation() {
    return xyz;
  }
}