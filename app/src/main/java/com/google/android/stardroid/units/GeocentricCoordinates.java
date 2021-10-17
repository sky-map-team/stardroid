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

package com.google.android.stardroid.units;

import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MathUtil;

/**
 * Utilities for dealing with a vector representing an object's location in Euclidean space
 * when it is projected onto a unit sphere (with the Earth at the
 * center).
 *
 * @author Brent Bryan
 */
public class GeocentricCoordinates {

  private GeocentricCoordinates() {
  }

  /**
   * Updates the given vector with the supplied RaDec.
   * {@link RaDec}.
   */
  public static void updateFromRaDec(Vector3 v, RaDec raDec) {
    updateFromRaDec(v, raDec.getRa(), raDec.getDec());
  }

  /**
   * Updates these coordinates with the given ra and dec in degrees.
   */
  private static void updateFromRaDec(Vector3 v, float ra, float dec) {
    float raRadians = ra * Geometry.DEGREES_TO_RADIANS;
    float decRadians = dec * Geometry.DEGREES_TO_RADIANS;

    v.x = MathUtil.cos(raRadians) * MathUtil.cos(decRadians);
    v.y = MathUtil.sin(raRadians) * MathUtil.cos(decRadians);
    v.z = MathUtil.sin(decRadians);
  }

  /** Returns the RA in degrees from the given vector assuming it's in Geocentric coordinates */
  // TODO(jontayler): define the different coordinate systems somewhere.
  public static float getRaOfUnitGeocentricVector(Vector3 v) {
    // Assumes unit sphere.
    return Geometry.RADIANS_TO_DEGREES * MathUtil.atan2(v.y, v.x);
  }

  /** Returns the declination in degrees from the given vector assuming it's in Geocentric coordinates */
  public static float getDecOfUnitGeocentricVector(Vector3 v) {
    // Assumes unit sphere.
    return Geometry.RADIANS_TO_DEGREES * MathUtil.asin(v.z);
  }

  /**
   * Convert ra and dec to x,y,z where the point is place on the unit sphere.
   */
  public static Vector3 getGeocentricCoords(RaDec raDec) {
    return getGeocentricCoords(raDec.getRa(), raDec.getDec());
  }

  public static Vector3 getGeocentricCoords(float ra, float dec) {
    Vector3 coords = new Vector3(0.0f, 0.0f, 0.0f);
    updateFromRaDec(coords, ra, dec);
    return coords;
  }
}