// Copyright 2010 Google Inc.
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
import android.util.Log
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
import com.google.common.io.Closeables
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author Brent Bryan
 */
class IssLayer(resources: Resources, private val model: AstronomerModel) :
    AbstractSourceLayer(resources, true) {
    private val scheduler = Executors.newScheduledThreadPool(1)
    private var issSource: IssSource? = null
    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        issSource = IssSource(model, resources)
        sources.add(issSource!!)
        scheduler.scheduleAtFixedRate(
            OrbitalElementsGrabber(issSource!!), 0, 60, TimeUnit.SECONDS
        )
    }

    override val layerDepthOrder: Int
        get() = 70
    protected override val layerNameId: Int
        protected get() = R.string.show_satellite_layer_pref

    /** Thread Runnable which parses the orbital elements out of the Url.  */
    internal class OrbitalElementsGrabber(private val source: IssSource) : Runnable {
        private var lastSuccessfulUpdateMs = -1L

        /**
         * Parses the OrbitalElements from the given BufferedReader.  Factored out
         * of [.getOrbitalElements] to simplify testing.
         */
        @Throws(IOException::class)
        fun parseOrbitalElements(`in`: BufferedReader): OrbitalElements? {
            var s: String
            while (`in`.readLine().also { s = it } != null && !s.contains("M50 Keplerian")) {
            }

            // Skip the dashed line
            `in`.readLine()
            val params = FloatArray(9)
            var i = 0
            while (i < params.size && `in`.readLine().also { s = it } != null) {
                s = s.substring(46).trim { it <= ' ' }
                val tokens = s.split("\\s+").toTypedArray()
                params[i] = tokens[2].toFloat()
                i++
            }
            if (i == params.size) {  // we read all the data.
                // TODO(serafini): Add magic here to create orbital elements or whatever.
                val sb = StringBuilder()
                for (param in params) {
                    sb.append(" ").append(param)
                }
                //Blog.d(this, "Params: " + sb);
            }
            return null
        }

        /**
         * Reads the given URL and returns the OrbitalElements associated with the object
         * described therein.
         */
        fun getOrbitalElements(urlString: String?): OrbitalElements? {
            var `in`: BufferedReader? = null
            try {
                val connection = URL(urlString).openConnection()
                `in` = BufferedReader(InputStreamReader(connection.getInputStream()))
                return parseOrbitalElements(`in`)
            } catch (e: IOException) {
                Log.e(TAG, "Error reading Orbital Elements")
            } finally {
                Closeables.closeQuietly(`in`)
            }
            return null
        }

        override fun run() {
            val currentTimeMs = System.currentTimeMillis()
            if (currentTimeMs - lastSuccessfulUpdateMs > UPDATE_FREQ_MS) {
                //Blog.d(this, "Fetching ISS data...");
                val elements = getOrbitalElements(URL_STRING)
                if (elements == null) {
                    Log.d(TAG, "Error downloading ISS orbital data")
                } else {
                    lastSuccessfulUpdateMs = currentTimeMs
                    source.setOrbitalElements(elements)
                }
            }
        }

        companion object {
            private const val UPDATE_FREQ_MS = TimeConstants.MILLISECONDS_PER_HOUR
            private val TAG = MiscUtil.getTag(OrbitalElementsGrabber::class.java)
            private const val URL_STRING = "http://spaceflight.nasa.gov/realdata/" +
                    "sightings/SSapplications/Post/JavaSSOP/orbit/ISS/SVPOST.html"
        }
    }

    /** AstronomicalSource corresponding to the International Space Station.  */
    internal class IssSource(private val model: AstronomerModel, resources: Resources?) :
        AbstractAstronomicalSource() {
        private val coords = Vector3(1f, 0f, 0f)
        private val pointPrimitives = ArrayList<PointPrimitive>()
        private val textPrimitives = ArrayList<TextPrimitive>()
        private val name: String
        private var orbitalElements: OrbitalElements? = null
        private var orbitalElementsChanged = false
        private var lastUpdateTimeMs = 0L
        @Synchronized
        fun setOrbitalElements(elements: OrbitalElements?) {
            orbitalElements = elements
            orbitalElementsChanged = true
        }

        override fun getNames(): List<String> {
            return Lists.asList(name)
        }

        override fun getSearchLocation(): Vector3 {
            return coords
        }

        private fun updateCoords(time: Date) {
            lastUpdateTimeMs = time.time
            orbitalElementsChanged = false
            if (orbitalElements == null) {
                return
            }
            // TODO(serafini): Update coords of Iss from OrbitalElements.
            // issCoords.assign(...);
        }

        override fun initialize(): Sources {
            updateCoords(model.time)
            return this
        }

        @Synchronized
        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)
            val modelTime = model.time
            if (orbitalElementsChanged ||
                Math.abs(modelTime.time - lastUpdateTimeMs) > UPDATE_FREQ_MS
            ) {
                updateCoords(modelTime)
                if (orbitalElements != null) {
                    updateTypes.add(UpdateType.UpdatePositions)
                }
            }
            return updateTypes
        }

        override fun getLabels(): List<TextPrimitive> {
            return textPrimitives
        }

        override fun getPoints(): List<PointPrimitive> {
            return pointPrimitives
        }

        companion object {
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND
            private const val ISS_COLOR = Color.YELLOW
        }

        init {
            name = resources!!.getString(R.string.space_station)
            pointPrimitives.add(PointPrimitive(coords, ISS_COLOR, 5))
            textPrimitives.add(TextPrimitive(coords, name, ISS_COLOR))
        }
    }
}