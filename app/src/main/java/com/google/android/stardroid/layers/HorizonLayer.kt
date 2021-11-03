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
import com.google.android.stardroid.base.Lists
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
import java.util.*

/**
 * Creates a mark at the zenith, nadir and cardinal point and a horizon.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
class HorizonLayer(private val model: AstronomerModel, resources: Resources) :
    AbstractSourceLayer(resources, true) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        sources.add(HorizonSource(model, resources))
    }

    override val layerDepthOrder: Int
        get() = 90

    // TODO(brent): Remove this.
    override val preferenceId: String
        get() = "source_provider.5"

    // TODO(johntaylor): i18n
    override val layerName: String
        get() =// TODO(johntaylor): i18n
            "Horizon"

    // TODO(johntaylor): rename this string id
    protected override val layerNameId: Int
        protected get() = R.string.show_horizon_pref // TODO(johntaylor): rename this string id

    /** Implementation of [AstronomicalSource] for the horizon source.  */
    internal class HorizonSource(private val model: AstronomerModel, res: Resources?) :
        AbstractAstronomicalSource() {
        private val zenith = Vector3(0, 0, 0)
        private val nadir = Vector3(0, 0, 0)
        private val north = Vector3(0, 0, 0)
        private val south = Vector3(0, 0, 0)
        private val east = Vector3(0, 0, 0)
        private val west = Vector3(0, 0, 0)
        private val linePrimitives = ArrayList<LinePrimitive>()
        private val textPrimitives = ArrayList<TextPrimitive>()
        private var lastUpdateTimeMs = 0L
        private fun updateCoords() {
            // Blog.d(this, "Updating Coords: " + (model.getTime().getTime() - lastUpdateTimeMs));
            lastUpdateTimeMs = model.time.time
            zenith.assign(model.zenith)
            nadir.assign(model.nadir)
            north.assign(model.north)
            south.assign(model.south)
            east.assign(model.east)
            west.assign(model.west)
        }

        override fun initialize(): Sources {
            updateCoords()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)

            // TODO(brent): Add distance here.
            if (Math.abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateCoords()
                updateTypes.add(UpdateType.UpdatePositions)
            }
            return updateTypes
        }

        override fun getLabels(): List<TextPrimitive> {
            return textPrimitives
        }

        override fun getLines(): List<LinePrimitive> {
            return linePrimitives
        }

        companion object {
            // Due to a bug in the G1 rendering code text and lines render in different
            // colors.
            private val LINE_COLOR = Color.argb(120, 86, 176, 245)
            private val LABEL_COLOR = Color.argb(120, 245, 176, 86)
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND
        }

        init {
            val vertices = Lists.asList(north, east, south, west, north)
            linePrimitives.add(LinePrimitive(LINE_COLOR, vertices, 1.5f))
            textPrimitives.add(TextPrimitive(zenith, res!!.getString(R.string.zenith), LABEL_COLOR))
            textPrimitives.add(TextPrimitive(nadir, res.getString(R.string.nadir), LABEL_COLOR))
            textPrimitives.add(TextPrimitive(north, res.getString(R.string.north), LABEL_COLOR))
            textPrimitives.add(TextPrimitive(south, res.getString(R.string.south), LABEL_COLOR))
            textPrimitives.add(TextPrimitive(east, res.getString(R.string.east), LABEL_COLOR))
            textPrimitives.add(TextPrimitive(west, res.getString(R.string.west), LABEL_COLOR))
        }
    }
}