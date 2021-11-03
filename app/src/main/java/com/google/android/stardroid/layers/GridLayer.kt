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

import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.math.Vector3.assign
import com.google.android.stardroid.space.Universe.solarSystemObjectFor
import com.google.android.stardroid.space.CelestialObject.getRaDec
import android.content.res.AssetManager
import com.google.android.stardroid.layers.AbstractSourceLayer
import com.google.android.stardroid.source.AstronomicalSource
import com.google.android.stardroid.layers.AbstractFileBasedLayer
import com.google.android.stardroid.source.proto.ProtobufAstronomicalSource
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.renderer.RendererControllerBase.RenderManager
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.layers.AbstractLayer
import com.google.android.stardroid.renderer.RendererController.AtomicSection
import com.google.android.stardroid.renderer.util.UpdateClosure
import com.google.android.stardroid.source.TextPrimitive
import com.google.android.stardroid.source.PointPrimitive
import com.google.android.stardroid.source.LinePrimitive
import com.google.android.stardroid.source.ImagePrimitive
import com.google.android.stardroid.renderer.RendererControllerBase
import com.google.android.stardroid.search.PrefixStore
import com.google.android.stardroid.layers.AbstractSourceLayer.SourceUpdateClosure
import com.google.android.stardroid.source.Sources
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.util.AbstractUpdateClosure
import com.google.android.stardroid.layers.EclipticLayer.EclipticSource
import com.google.android.stardroid.R
import com.google.android.stardroid.source.AbstractAstronomicalSource
import com.google.android.stardroid.layers.GridLayer.GridSource
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.layers.HorizonLayer.HorizonSource
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.layers.IssLayer.IssSource
import com.google.android.stardroid.layers.IssLayer.OrbitalElementsGrabber
import kotlin.Throws
import com.google.android.stardroid.ephemeris.OrbitalElements
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.search.SearchTermsProvider.SearchTerm
import com.google.android.stardroid.layers.MeteorShowerLayer.Shower
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.MeteorShowerLayer.MeteorRadiantSource
import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.ephemeris.PlanetSource
import com.google.android.stardroid.layers.SkyGradientLayer
import com.google.android.stardroid.layers.StarOfBethlehemLayer.StarOfBethlehemSource
import com.google.android.stardroid.layers.StarOfBethlehemLayer
import java.util.ArrayList

/**
 * Creates a Layer which returns Sources which correspond to grid lines parallel
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
 * @param numRightAscentionLines
 * @param numDeclinationLines The number of declination lines to show including the poles
 * on each side of the equator. 9 is a good number for 10 degree
 * intervals.
 */(
    resources: Resources,
    private val numRightAscentionLines: Int,
    private val numDeclinationLines: Int
) : AbstractSourceLayer(resources, false) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        sources.add(GridSource(resources, numRightAscentionLines, numDeclinationLines))
    }

    override val layerDepthOrder: Int
        get() = 0

    // TODO(johntaylor): rename this string Id.
    protected override val layerNameId: Int
        protected get() = R.string.show_grid_pref // TODO(johntaylor): rename this string Id.

    // TODO(brent): Remove this.
    override val preferenceId: String
        get() = "source_provider.4"

    /** Implementation of the grid elements as an [AstronomicalSource]  */
    internal class GridSource(res: Resources?, numRaSources: Int, numDecSources: Int) :
        AbstractAstronomicalSource() {
        private val linePrimitives = ArrayList<LinePrimitive>()
        private val textPrimitives = ArrayList<TextPrimitive>()

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

        private fun createDecLine(index: Int, dec: Float): LinePrimitive {
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

        override fun getLabels(): List<TextPrimitive> {
            return textPrimitives
        }

        override fun getLines(): List<LinePrimitive> {
            return linePrimitives
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
                linePrimitives.add(createRaLine(r, numRaSources))
            }
            /** North & South pole, hour markers every 2hrs.  */
            textPrimitives.add(
                TextPrimitive(
                    0f,
                    90f,
                    res!!.getString(R.string.north_pole),
                    LINE_COLOR
                )
            )
            textPrimitives.add(
                TextPrimitive(
                    0f,
                    -90f,
                    res.getString(R.string.south_pole),
                    LINE_COLOR
                )
            )
            for (index in 0..11) {
                val ra = index * 30.0f
                val title = String.format("%dh", 2 * index)
                textPrimitives.add(TextPrimitive(ra, 0.0f, title, LINE_COLOR))
            }
            linePrimitives.add(createDecLine(0, 0f)) // Equator
            // Note that we don't create lines at the poles.
            for (d in 1 until numDecSources) {
                val dec = d * 90.0f / numDecSources
                linePrimitives.add(createDecLine(d, dec))
                textPrimitives.add(
                    TextPrimitive(
                        0f,
                        dec,
                        String.format("%d°", dec.toInt()),
                        LINE_COLOR
                    )
                )
                linePrimitives.add(createDecLine(d, -dec))
                textPrimitives.add(
                    TextPrimitive(
                        0f,
                        -dec,
                        String.format("%d°", -dec as Int),
                        LINE_COLOR
                    )
                )
            }
        }
    }
}