// Copyright 2009 Google Inc.
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
import android.util.Log
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
import com.google.android.stardroid.source.proto.SourceProto
import com.google.common.io.Closeables
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Implementation of the [Layer] interface which reads its data from
 * a file during the [Layer.initialize] method.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
abstract class AbstractFileBasedLayer(
    private val assetManager: AssetManager,
    resources: Resources,
    private val fileName: String
) : AbstractSourceLayer(resources, false) {
    private val fileSources: MutableList<AstronomicalSource> = ArrayList()
    @Synchronized
    override fun initialize() {
        BACKGROUND_EXECUTOR.execute {
            readSourceFile(fileName)
            super@AbstractFileBasedLayer.initialize()
        }
    }

    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        sources.addAll(fileSources)
    }

    private fun readSourceFile(sourceFilename: String) {
        Log.d(TAG, "Loading Proto File: $sourceFilename...")
        var `in`: InputStream? = null
        try {
            `in` = assetManager.open(sourceFilename, AssetManager.ACCESS_BUFFER)
            val parser = SourceProto.AstronomicalSourcesProto.parser()
            val sources = parser.parseFrom(`in`)
            for (proto in sources.sourceList) {
                fileSources.add(ProtobufAstronomicalSource(proto, resources))
            }
            Log.d(TAG, "Found: " + fileSources.size + " sources")
            val s = String.format(
                "Finished Loading: %s | Found %s sourcs.\n",
                sourceFilename, fileSources.size
            )
            Log.d(TAG, s)
            refreshSources(EnumSet.of(UpdateType.Reset))
        } catch (e: IOException) {
            Log.e(TAG, "Unable to open $sourceFilename")
        } finally {
            Closeables.closeQuietly(`in`)
        }
    }

    companion object {
        private val TAG = MiscUtil.getTag(AbstractFileBasedLayer::class.java)
        private val BACKGROUND_EXECUTOR: Executor = Executors.newFixedThreadPool(1)
    }
}