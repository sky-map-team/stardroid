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

import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.ephemeris.OrbitalElements
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.util.MiscUtil
import com.google.common.io.Closeables
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * @author Brent Bryan
 */
class IssLayer(resources: Resources, model: AstronomerModel) :
    AbstractRenderablesLayer(resources, true) {
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val issRenderable: IssRenderable = IssRenderable(model, resources)
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(issRenderable)
        scheduler.scheduleAtFixedRate(
            OrbitalElementsGrabber(issRenderable), 0, 60, TimeUnit.SECONDS
        )
    }

    override val layerDepthOrder = 70
    override val layerNameId = R.string.show_satellite_layer_pref

    /** Thread Runnable which parses the orbital elements out of the Url.  */
    internal class OrbitalElementsGrabber(private val renderable: IssRenderable) : Runnable {
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
                    renderable.setOrbitalElements(elements)
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

    /** AstronomicalRenderable corresponding to the International Space Station.  */
    internal class IssRenderable(private val model: AstronomerModel, resources: Resources?) :
        AbstractAstronomicalRenderable() {
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

        private fun updateCoords(time: Date) {
            lastUpdateTimeMs = time.time
            orbitalElementsChanged = false
            if (orbitalElements == null) {
                return
            }
            // TODO(serafini): Update coords of Iss from OrbitalElements.
            // issCoords.assign(...);
        }

        override fun initialize(): Renderable {
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