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
 * Creates a Layer for the Ecliptic.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
class EclipticLayer(resources: Resources) : AbstractSourceLayer(resources, false) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        sources.add(EclipticSource(resources))
    }

    override val layerDepthOrder: Int
        get() = 50
    protected override val layerNameId: Int
        protected get() = R.string.show_grid_pref
    override val preferenceId: String
        get() = "source_provider.4"

    /** Implementation of [AstronomicalSource] for the ecliptic source.  */
    private class EclipticSource(res: Resources?) : AbstractAstronomicalSource() {
        private val linePrimitives = ArrayList<LinePrimitive>()
        private val textPrimitives = ArrayList<TextPrimitive>()
        override fun getLabels(): List<TextPrimitive> {
            return textPrimitives
        }

        override fun getLines(): List<LinePrimitive> {
            return linePrimitives
        }

        companion object {
            // Earth's Angular Tilt
            private const val EPSILON = 23.439281f
            private val LINE_COLOR = Color.argb(20, 248, 239, 188)
        }

        init {
            val title = res!!.getString(R.string.ecliptic)
            textPrimitives.add(TextPrimitive(90.0f, EPSILON, title, LINE_COLOR))
            textPrimitives.add(TextPrimitive(270f, -EPSILON, title, LINE_COLOR))

            // Create line source.
            val ra = floatArrayOf(0f, 90f, 180f, 270f, 0f)
            val dec = floatArrayOf(0f, EPSILON, 0f, -EPSILON, 0f)
            val vertices = ArrayList<Vector3>()
            for (i in ra.indices) {
                vertices.add(getGeocentricCoords(ra[i], dec[i]))
            }
            linePrimitives.add(LinePrimitive(LINE_COLOR, vertices, 1.5f))
        }
    }
}