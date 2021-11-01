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

package com.google.android.stardroid.ephemeris;

import static com.google.android.stardroid.math.MathUtilsKt.DEGREES_TO_RADIANS;
import static com.google.android.stardroid.math.MathUtilsKt.mod2pi;
import static com.google.android.stardroid.math.TimeUtilsKt.julianCenturies;

import com.google.android.stardroid.R;
import com.google.android.stardroid.base.TimeConstants;
import com.google.android.stardroid.util.MiscUtil;

import java.util.Date;

public enum Planet {
  // The order here is the order in which they are drawn.  To ensure that during
  // conjunctions they display "naturally" order them in reverse distance from Earth.
  Pluto(R.drawable.pluto, R.string.pluto, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Neptune(R.drawable.neptune, R.string.neptune, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Uranus(R.drawable.uranus, R.string.uranus, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Saturn(R.drawable.saturn, R.string.saturn, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Jupiter(R.drawable.jupiter, R.string.jupiter, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Mars(R.drawable.mars, R.string.mars, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Sun(R.drawable.sun, R.string.sun, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Mercury(R.drawable.mercury, R.string.mercury, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Venus(R.drawable.venus, R.string.venus, 1L * TimeConstants.MILLISECONDS_PER_HOUR),
  Moon(R.drawable.moon4, R.string.moon, 1L * TimeConstants.MILLISECONDS_PER_MINUTE);

  private static final String TAG = MiscUtil.getTag(Planet.class);

  // Resource ID to use for a planet's image.
  private final int imageResourceId;

  // String ID
  private final int nameResourceId;

  private final long updateFreqMs;

  Planet(int imageResourceId, int nameResourceId, long updateFreqMs) {
    this.imageResourceId = imageResourceId;
    this.nameResourceId = nameResourceId;
    this.updateFreqMs = updateFreqMs;
    // Add Color, magnitude, etc.
  }

  public long getUpdateFrequencyMs() {
    return updateFreqMs;
  }

  /**
   * Returns the resource id for the string corresponding to the name of this
   * planet.
   */
  public int getNameResourceId() {
    return nameResourceId;
  }

  /** Returns the resource id for the planet's image. */
  public int getImageResourceId() {
    return this.imageResourceId;
  }

  // Taken from JPL's Planetary Positions page: http://ssd.jpl.nasa.gov/?planet_pos
  // This gives us a good approximation for the years 1800 to 2050 AD.
  // TODO(serafini): Update the numbers so we can extend the approximation to cover 
  // 3000 BC to 3000 AD.
  public OrbitalElements getOrbitalElements(Date date) {
    // Centuries since J2000
    float jc = (float) julianCenturies(date);

    switch (this) {
      case Mercury: {
        float a = 0.38709927f + 0.00000037f * jc;
        float e = 0.20563593f + 0.00001906f * jc;
        float i = (7.00497902f - 0.00594749f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((252.25032350f + 149472.67411175f * jc) * DEGREES_TO_RADIANS);
        float w = (77.45779628f + 0.16047689f * jc) * DEGREES_TO_RADIANS;
        float o = (48.33076593f - 0.12534081f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Venus: {
        float a = 0.72333566f + 0.00000390f * jc;
        float e = 0.00677672f - 0.00004107f * jc;
        float i = (3.39467605f - 0.00078890f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((181.97909950f + 58517.81538729f * jc) * DEGREES_TO_RADIANS);
        float w = (131.60246718f + 0.00268329f * jc) * DEGREES_TO_RADIANS;
        float o = (76.67984255f - 0.27769418f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      // Note that this is the orbital data for Earth.
      case Sun: {
        float a = 1.00000261f + 0.00000562f * jc;
        float e = 0.01671123f - 0.00004392f * jc;
        float i = (-0.00001531f - 0.01294668f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((100.46457166f + 35999.37244981f * jc) * DEGREES_TO_RADIANS);
        float w = (102.93768193f + 0.32327364f * jc) * DEGREES_TO_RADIANS;
        float o = 0.0f;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Mars: {
        float a = 1.52371034f + 0.00001847f * jc;
        float e = 0.09339410f + 0.00007882f * jc;
        float i = (1.84969142f - 0.00813131f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((-4.55343205f + 19140.30268499f * jc) * DEGREES_TO_RADIANS);
        float w = (-23.94362959f + 0.44441088f * jc) * DEGREES_TO_RADIANS;
        float o = (49.55953891f - 0.29257343f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Jupiter: {
        float a = 5.20288700f - 0.00011607f * jc;
        float e = 0.04838624f - 0.00013253f * jc;
        float i = (1.30439695f - 0.00183714f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((34.39644051f + 3034.74612775f * jc) * DEGREES_TO_RADIANS);
        float w = (14.72847983f + 0.21252668f * jc) * DEGREES_TO_RADIANS;
        float o = (100.47390909f + 0.20469106f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Saturn: {
        float a = 9.53667594f - 0.00125060f * jc;
        float e = 0.05386179f - 0.00050991f * jc;
        float i = (2.48599187f + 0.00193609f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((49.95424423f + 1222.49362201f * jc) * DEGREES_TO_RADIANS);
        float w = (92.59887831f - 0.41897216f * jc) * DEGREES_TO_RADIANS;
        float o = (113.66242448f - 0.28867794f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Uranus: {
        float a = 19.18916464f - 0.00196176f * jc;
        float e = 0.04725744f - 0.00004397f * jc;
        float i = (0.77263783f - 0.00242939f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((313.23810451f + 428.48202785f * jc) * DEGREES_TO_RADIANS);
        float w = (170.95427630f + 0.40805281f * jc) * DEGREES_TO_RADIANS;
        float o = (74.01692503f + 0.04240589f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Neptune: {
        float a = 30.06992276f + 0.00026291f * jc;
        float e = 0.00859048f + 0.00005105f * jc;
        float i = (1.77004347f + 0.00035372f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((-55.12002969f + 218.45945325f * jc) * DEGREES_TO_RADIANS);
        float w = (44.96476227f - 0.32241464f * jc) * DEGREES_TO_RADIANS;
        float o = (131.78422574f - 0.00508664f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      case Pluto: {
        float a = 39.48211675f - 0.00031596f * jc;
        float e = 0.24882730f + 0.00005170f * jc;
        float i = (17.14001206f + 0.00004818f * jc) * DEGREES_TO_RADIANS;
        float l =
            mod2pi((238.92903833f + 145.20780515f * jc) * DEGREES_TO_RADIANS);
        float w = (224.06891629f - 0.04062942f * jc) * DEGREES_TO_RADIANS;
        float o = (110.30393684f - 0.01183482f * jc) * DEGREES_TO_RADIANS;
        return new OrbitalElements(a, e, i, o, w, l);
      }

      default:
        throw new RuntimeException("Unknown Planet: " + this);
    }
  }
}
