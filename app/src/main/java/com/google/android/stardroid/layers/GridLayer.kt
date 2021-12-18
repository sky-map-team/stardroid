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
package com.google.android.stardroid.layers

import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.R
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.*

/**
 * Creates a Layer containing a Renderable which correspond to grid lines parallel
 * to the celestial equator and the hour angle. That is, returns a set of lines
 * with constant right ascension, and another set with constant declination.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
class GridLayer
/**
 *
 * @param resources
 * @param numRightAscensionLines
 * @param numDeclinationLines The number of declination lines to show including the poles
 * on each side of the equator. 9 is a good number for 10 degree intervals.
 */(
    resources: Resources,
    private val numRightAscensionLines: Int,
    private val numDeclinationLines: Int
) : AbstractRenderablesLayer(resources, false) {

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(GridRenderable(resources, numRightAscensionLines, numDeclinationLines))
    }

    override val layerDepthOrder = 0

    override val layerNameId = R.string.show_grid_pref // TODO(johntaylor): rename this string Id.

    // TODO(brent): Remove this.
    override val preferenceId = "source_provider.4"

    /** Implementation of the grid elements as an [AstronomicalRenderable]  */
    internal class GridRenderable(resources: Resources, numRaSources: Int, numDecSources: Int) :
        AbstractAstronomicalRenderable() {
        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        /**
         * Constructs a single longitude line. These lines run from the north pole to
         * the south pole at fixed Right Ascensions.
         */
        private fun createRaLine(index: Int, numRaSources: Int): LinePrimitive {
            val line = LinePrimitive(LINE_COLOR)
            val ra = index * 360.0f / numRaSources
            for (i in 0 until NUM_DEC_VERTICES - 1) {
                val dec = 90.0f - i * 180.0f / (NUM_DEC_VERTICES - 1)
                val raDec = RaDec(ra, dec)
                line.raDecs.add(raDec)
                line.vertices.add(getGeocentricCoords(raDec))
            }
            val raDec = RaDec(0.0f, -90.0f)
            line.raDecs.add(raDec)
            line.vertices.add(getGeocentricCoords(raDec))
            return line
        }

        private fun createDecLine(dec: Float): LinePrimitive {
            val line = LinePrimitive(LINE_COLOR)
            for (i in 0 until NUM_RA_VERTICES) {
                val ra = i * 360.0f / NUM_RA_VERTICES
                val raDec = RaDec(ra, dec)
                line.raDecs.add(raDec)
                line.vertices.add(getGeocentricCoords(raDec))
            }
            val raDec = RaDec(0.0f, dec)
            line.raDecs.add(raDec)
            line.vertices.add(getGeocentricCoords(raDec))
            return line
        }

        companion object {
            private val LINE_COLOR = Color.argb(20, 248, 239, 188)

            /** These are great (semi)circles, so only need 3 points.  */
            private const val NUM_DEC_VERTICES = 3

            /** every 10 degrees  */
            private const val NUM_RA_VERTICES = 36
        }

        init {
            for (r in 0 until numRaSources) {
                lines.add(createRaLine(r, numRaSources))
            }
            /** North & South pole, hour markers every 2hrs.  */
            labels.add(
                TextPrimitive(
                    0f,
                    90f,
                    resources.getString(R.string.north_pole),
                    LINE_COLOR
                )
            )
            labels.add(
                TextPrimitive(
                    0f,
                    -90f,
                    resources.getString(R.string.south_pole),
                    LINE_COLOR
                )
            )
            for (index in 0..11) {
                val ra = index * 30.0f
                val title = String.format("%dh", 2 * index)
                labels.add(TextPrimitive(ra, 0.0f, title, LINE_COLOR))
            }
            lines.add(createDecLine(0f)) // Equator
            // Note that we don't create lines at the poles.
            for (d in 1 until numDecSources) {
                val dec = d * 90.0f / numDecSources
                lines.add(createDecLine(dec))
                labels.add(
                    TextPrimitive(
                        0f,
                        dec,
                        String.format("%d°", dec.toInt()),
                        LINE_COLOR
                    )
                )
                lines.add(createDecLine(-dec))
                labels.add(
                    TextPrimitive(
                        0f,
                        -dec,
                        String.format("%d°", -dec.toInt()),
                        LINE_COLOR
                    )
                )
            }
        }
    }
}