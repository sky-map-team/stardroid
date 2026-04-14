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
package com.google.android.stardroid.ephemeris

import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.julianCenturies
import com.google.android.stardroid.math.mod2pi
import com.google.android.stardroid.util.MiscUtil
import java.util.*

/**
 * A data holder for some static data about solar system objects.
 * These are usually planets.
 */
// Add Color, magnitude, etc.
enum class SolarSystemBody
    (
    // Resource ID to use for a planet's image.
    val imageResourceId: Int,
    // String ID for the body's name
    val nameResourceId: Int,
    // How frequently to update the body's position
    val updateFrequencyMs: Long
) {
    // The order here is the order in which they are drawn.  To ensure that during
    // conjunctions they display "naturally" order them in reverse distance from Earth.
    // TODO(jontayler): do this more rigorously - the only times it could really matter are when
    // Mercury and Venus ought to be behind the Sun.
    Pluto(
        R.drawable.pluto,
        R.string.pluto,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Neptune(R.drawable.neptune, R.string.neptune, TimeConstants.MILLISECONDS_PER_HOUR),
    Uranus(
        R.drawable.uranus,
        R.string.uranus,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Saturn(
        R.drawable.saturn,
        R.string.saturn,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Jupiter(
        R.drawable.jupiter,
        R.string.jupiter,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Mars(
        R.drawable.mars,
        R.string.mars,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Sun(
        R.drawable.sun,
        R.string.sun,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Mercury(
        R.drawable.mercury,
        R.string.mercury,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Venus(
        R.drawable.venus,
        R.string.venus,
        TimeConstants.MILLISECONDS_PER_HOUR
    ),
    Moon(R.drawable.moon4, R.string.moon, TimeConstants.MILLISECONDS_PER_MINUTE),
    Earth(R.drawable.earth, R.string.earth, TimeConstants.MILLISECONDS_PER_HOUR);

    // Taken from JPL's Planetary Positions page: http://ssd.jpl.nasa.gov/?planet_pos
    // This gives us a good approximation for the years 1800 to 2050 AD.
    // TODO(serafini): Update the numbers so we can extend the approximation to cover 
    // 3000 BC to 3000 AD.
    fun getOrbitalElements(date: Date): OrbitalElements {
        // Centuries since J2000
        val jc = julianCenturies(date).toFloat()
        return when (this) {
            Mercury -> {
                val a = 0.38709927f + 0.00000037f * jc
                val e = 0.20563593f + 0.00001906f * jc
                val i: Float = (7.00497902f - 0.00594749f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((252.25032350f + 149472.67411175f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (77.45779628f + 0.16047689f * jc) * DEGREES_TO_RADIANS
                val o: Float = (48.33076593f - 0.12534081f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Venus -> {
                val a = 0.72333566f + 0.00000390f * jc
                val e = 0.00677672f - 0.00004107f * jc
                val i: Float = (3.39467605f - 0.00078890f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((181.97909950f + 58517.81538729f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (131.60246718f + 0.00268329f * jc) * DEGREES_TO_RADIANS
                val o: Float = (76.67984255f - 0.27769418f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Earth -> {
                val a = 1.00000261f + 0.00000562f * jc
                val e = 0.01671123f - 0.00004392f * jc
                val i: Float = (-0.00001531f - 0.01294668f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((100.46457166f + 35999.37244981f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (102.93768193f + 0.32327364f * jc) * DEGREES_TO_RADIANS
                val o = 0.0f
                OrbitalElements(a, e, i, o, w, l)
            }
            Mars -> {
                val a = 1.52371034f + 0.00001847f * jc
                val e = 0.09339410f + 0.00007882f * jc
                val i: Float = (1.84969142f - 0.00813131f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((-4.55343205f + 19140.30268499f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (-23.94362959f + 0.44441088f * jc) * DEGREES_TO_RADIANS
                val o: Float = (49.55953891f - 0.29257343f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Jupiter -> {
                val a = 5.20288700f - 0.00011607f * jc
                val e = 0.04838624f - 0.00013253f * jc
                val i: Float = (1.30439695f - 0.00183714f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((34.39644051f + 3034.74612775f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (14.72847983f + 0.21252668f * jc) * DEGREES_TO_RADIANS
                val o: Float = (100.47390909f + 0.20469106f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Saturn -> {
                val a = 9.53667594f - 0.00125060f * jc
                val e = 0.05386179f - 0.00050991f * jc
                val i: Float = (2.48599187f + 0.00193609f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((49.95424423f + 1222.49362201f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (92.59887831f - 0.41897216f * jc) * DEGREES_TO_RADIANS
                val o: Float = (113.66242448f - 0.28867794f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Uranus -> {
                val a = 19.18916464f - 0.00196176f * jc
                val e = 0.04725744f - 0.00004397f * jc
                val i: Float = (0.77263783f - 0.00242939f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((313.23810451f + 428.48202785f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (170.95427630f + 0.40805281f * jc) * DEGREES_TO_RADIANS
                val o: Float = (74.01692503f + 0.04240589f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Neptune -> {
                val a = 30.06992276f + 0.00026291f * jc
                val e = 0.00859048f + 0.00005105f * jc
                val i: Float = (1.77004347f + 0.00035372f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((-55.12002969f + 218.45945325f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (44.96476227f - 0.32241464f * jc) * DEGREES_TO_RADIANS
                val o: Float = (131.78422574f - 0.00508664f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            Pluto -> {
                val a = 39.48211675f - 0.00031596f * jc
                val e = 0.24882730f + 0.00005170f * jc
                val i: Float = (17.14001206f + 0.00004818f * jc) * DEGREES_TO_RADIANS
                val l = mod2pi((238.92903833f + 145.20780515f * jc) * DEGREES_TO_RADIANS)
                val w: Float = (224.06891629f - 0.04062942f * jc) * DEGREES_TO_RADIANS
                val o: Float = (110.30393684f - 0.01183482f * jc) * DEGREES_TO_RADIANS
                OrbitalElements(a, e, i, o, w, l)
            }
            else -> throw RuntimeException("Unknown orbital elements for Solar System Object: $this")
        }
    }

    companion object {
        private val TAG = MiscUtil.getTag(SolarSystemBody::class.java)
    }
}