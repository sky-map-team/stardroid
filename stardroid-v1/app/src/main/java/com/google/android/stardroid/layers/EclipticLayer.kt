// Copyright 2009 Google Inc.
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
package com.google.android.stardroid.layers

import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.stardroid.R
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Creates a Layer for the Ecliptic.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
class EclipticLayer(resources: Resources, preferences: SharedPreferences) : AbstractRenderablesLayer
      (resources, false, preferences) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(EclipticRenderable(resources))
    }

    override val layerDepthOrder = 50
    override val layerNameId = R.string.show_grid_pref
    override val preferenceId = "source_provider.4"

    /** Implementation of [AstronomicalRenderable] for the ecliptic source.  */
    private class EclipticRenderable(resources: Resources) : AbstractAstronomicalRenderable() {
        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        companion object {
            private const val EARTHS_ANGULAR_TILT = 23.439281f

            // Labels are nudged a few degrees off the ecliptic in ecliptic *latitude* (i.e.
            // perpendicular to the line everywhere), so they sit a uniform small distance from the
            // line rather than striking through it. The ticks below bridge the small gap.
            private const val LABEL_LATITUDE_OFFSET = 3f

            // Tick lengths, in degrees of ecliptic latitude. Minor ticks every 10 degrees, longer
            // major ticks at the 30 degree zodiac/constellation boundaries.
            private const val MINOR_TICK_LENGTH = 1f
            private const val MAJOR_TICK_LENGTH = 2f

            private val DEGREES_TO_RADIANS = (Math.PI / 180.0).toFloat()
            private val OBLIQUITY_RADIANS = EARTHS_ANGULAR_TILT * DEGREES_TO_RADIANS
            private val COS_OBLIQUITY = cos(OBLIQUITY_RADIANS)
            private val SIN_OBLIQUITY = sin(OBLIQUITY_RADIANS)

            /**
             * Geocentric unit vector for the point at the given ecliptic longitude and latitude
             * (degrees). A non-zero latitude offsets the point perpendicular to the ecliptic.
             */
            private fun geocentricForEcliptic(longitude: Float, latitude: Float): Vector3 {
                val lambda = longitude * DEGREES_TO_RADIANS
                val beta = latitude * DEGREES_TO_RADIANS
                val cosBeta = cos(beta)
                val xe = cosBeta * cos(lambda)
                val ye = cosBeta * sin(lambda)
                val ze = sin(beta)
                // Rotate about the x-axis by the obliquity to convert ecliptic -> equatorial.
                return Vector3(
                    xe,
                    ye * COS_OBLIQUITY - ze * SIN_OBLIQUITY,
                    ye * SIN_OBLIQUITY + ze * COS_OBLIQUITY
                )
            }
        }

        init {
            // Star Gold (#FF9F1C), loaded from resources per the style guide. The renderer
            // red-shifts both automatically in night-vision mode.
            val labelColor = resources.getColor(R.color.ecliptic_label, null)
            val lineColor = resources.getColor(R.color.ecliptic_line, null)
            val title = resources.getString(R.string.ecliptic)
            // Place the descriptive name off the 30-degree marks (45 & 225) so it doesn't collide
            // with the degree labels.
            labels.add(
                TextPrimitive(
                    geocentricForEcliptic(45f, LABEL_LATITUDE_OFFSET), title, labelColor
                )
            )
            labels.add(
                TextPrimitive(
                    geocentricForEcliptic(225f, LABEL_LATITUDE_OFFSET), title, labelColor
                )
            )

            // Graduation ticks every 10 degrees of ecliptic longitude, each pointing off the line
            // toward its label. The 30 degree marks (zodiac/constellation boundaries) get longer,
            // slightly heavier ticks.
            for (longitude in 0 until 360 step 10) {
                val isMajor = longitude % 30 == 0
                val tickLength = if (isMajor) MAJOR_TICK_LENGTH else MINOR_TICK_LENGTH
                val tickWidth = if (isMajor) 2.0f else 1.5f
                val tick = listOf(
                    geocentricForEcliptic(longitude.toFloat(), 0f),
                    geocentricForEcliptic(longitude.toFloat(), tickLength)
                )
                lines.add(LinePrimitive(lineColor, tick, tickWidth))
            }

            // Degree labels at the 30 degree marks. The vernal equinox (0 deg) coincides exactly
            // with 0h RA / 0 deg dec, so it is labelled once by the grid layer (as "0") instead of
            // here, to avoid two labels stacking at the same point.
            for (longitude in 30 until 360 step 30) {
                labels.add(
                    TextPrimitive(
                        geocentricForEcliptic(longitude.toFloat(), LABEL_LATITUDE_OFFSET),
                        "$longitude°",
                        labelColor
                    )
                )
            }

            // Create line source.
            val ra = floatArrayOf(0f, 90f, 180f, 270f, 0f)
            val dec = floatArrayOf(0f, EARTHS_ANGULAR_TILT, 0f, -EARTHS_ANGULAR_TILT, 0f)
            val vertices = ArrayList<Vector3>()
            for (i in ra.indices) {
                vertices.add(getGeocentricCoords(ra[i], dec[i]))
            }
            lines.add(LinePrimitive(lineColor, vertices, 1.8f))
        }
    }
}