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

import com.google.android.stardroid.provider.ephemeris.Planet;
import com.google.android.stardroid.util.Geometry;
import com.google.android.stardroid.util.MathUtil;

import java.util.Date;

public class RaDec {
  public float ra;        // In degrees
  public float dec;       // In degrees

  public RaDec(float ra, float dec) {
    this.ra = ra;
    this.dec = dec;
  }

  @Override public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("RA: " + ra + " degrees\n");
    sb.append("Dec: " + dec + " degrees\n");
    return sb.toString();
  }

  public static RaDec calculateRaDecDist(HeliocentricCoordinates coords) {
    // find the RA and DEC from the rectangular equatorial coords
    float ra = Geometry.mod2pi(MathUtil.atan2(coords.y, coords.x)) * Geometry.RADIANS_TO_DEGREES;
    float dec = MathUtil.atan(coords.z / MathUtil.sqrt(coords.x * coords.x + coords.y * coords.y))
        * Geometry.RADIANS_TO_DEGREES;

    return new RaDec(ra, dec);
  }

  public static RaDec getInstance(Planet planet, Date time,
      HeliocentricCoordinates earthCoordinates) {
    // TODO(serafini): This is a temporary hack until we re-factor the Planetary calculations.
    if (planet.equals(Planet.Moon)) {
      return Planet.calculateLunarGeocentricLocation(time);
    }

    HeliocentricCoordinates coords = null;
    if (planet.equals(Planet.Sun)) {
      // Invert the view, since we want the Sun in earth coordinates, not the Earth in sun
      // coordinates.
      coords = new HeliocentricCoordinates(earthCoordinates.radius, earthCoordinates.x * -1.0f,
                                           earthCoordinates.y * -1.0f, earthCoordinates.z * -1.0f);
    } else {
      coords = HeliocentricCoordinates.getInstance(planet, time);
      coords.Subtract(earthCoordinates);
    }
    HeliocentricCoordinates equ = coords.CalculateEquatorialCoordinates();
    return calculateRaDecDist(equ);
  }

  public static RaDec getInstance(GeocentricCoordinates coords) {
    float raRad = MathUtil.atan2(coords.y, coords.x);
    if (raRad < 0) raRad += MathUtil.TWO_PI;
    float decRad = MathUtil.atan2(coords.z,
                                  MathUtil.sqrt(coords.x * coords.x + coords.y * coords.y));

    return new RaDec(raRad * Geometry.RADIANS_TO_DEGREES,
                             decRad * Geometry.RADIANS_TO_DEGREES);
  }


    // This should be relatively easy to do. In the northern hemisphere,
    // objects never set if dec > 90 - lat and never rise if dec < lat -
    // 90. In the southern hemisphere, objects never set if dec < -90 - lat
    // and never rise if dec > 90 + lat. There must be a better way to do
    // this...


  /**
   * Return true if the given Ra/Dec is always above the horizon. Return
   * false otherwise.
   * In the northern hemisphere, objects never set if dec > 90 - lat.
   * In the southern hemisphere, objects never set if dec < -90 - lat.
   */
  private boolean isCircumpolarFor(LatLong loc) {
    if (loc.getLatitude() > 0.0f) {
      return (this.dec > (90.0f - loc.getLatitude()));
    } else {
      return (this.dec < (-90.0f - loc.getLatitude()));
    }
  }


  /**
   * Return true if the given Ra/Dec is always below the horizon. Return
   * false otherwise.
   * In the northern hemisphere, objects never rise if dec < lat - 90.
   * In the southern hemisphere, objects never rise if dec > 90 - lat.
   */
  private boolean isNeverVisible(LatLong loc) {
    if (loc.getLatitude() > 0.0f) {
      return (this.dec < (loc.getLatitude() - 90.0f));
    } else {
      return (this.dec > (90.0f + loc.getLatitude()));
    }
  }
}
