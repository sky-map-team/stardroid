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
import com.google.android.stardroid.search.SearchResult
import java.util.ArrayList
import java.util.HashSet

/**
 * Allows a group of layers to be controlled together.
 */
class LayerManager(sharedPreferences: SharedPreferences) : OnSharedPreferenceChangeListener {
    private val layers: MutableList<Layer> = ArrayList()
    private val sharedPreferences: SharedPreferences
    fun addLayer(layer: Layer) {
        layers.add(layer)
    }

    fun initialize() {
        for (layer in layers) {
            layer.initialize()
        }
    }

    fun registerWithRenderer(renderer: RendererController?) {
        for (layer in layers) {
            layer.registerWithRenderer(renderer)
            val prefId = layer.preferenceId
            val visible = sharedPreferences.getBoolean(prefId, true)
            layer.setVisible(visible)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        for (layer in layers) {
            if (layer.preferenceId == key) {
                val visible = prefs.getBoolean(key, true)
                layer.setVisible(visible)
            }
        }
    }

    /**
     * Returns the name of this object.
     */
    val name: String
        get() = "Layer Manager"

    /**
     * Search all visible layers for an object with the given name.
     * @param name the name to search for
     * @return a list of all matching objects.
     */
    fun searchByObjectName(name: String): List<SearchResult?> {
        val all: MutableList<SearchResult?> = ArrayList()
        for (layer in layers) {
            if (isLayerVisible(layer)) {
                all.addAll(layer.searchByObjectName(name)!!)
            }
        }
        Log.d(TAG, "Got " + all.size + " results in total for " + name)
        return all
    }

    /**
     * Given a string prefix, find all possible queries for which we have a
     * result in the visible layers.
     * @param prefix the prefix to search for.
     * @return a set of matching queries.
     */
    fun getObjectNamesMatchingPrefix(prefix: String): Set<SearchTerm> {
        val all: MutableSet<SearchTerm> = HashSet()
        for (layer in layers) {
            if (isLayerVisible(layer)) {
                for (query in layer.getObjectNamesMatchingPrefix(prefix)) {
                    val result = SearchTerm(query, layer.layerName)
                    all.add(result)
                }
            }
        }
        Log.d(TAG, "Got " + all.size + " results in total for " + prefix)
        return all
    }

    private fun isLayerVisible(layer: Layer): Boolean {
        return sharedPreferences.getBoolean(layer.preferenceId, true)
    }

    companion object {
        private val TAG = MiscUtil.getTag(LayerManager::class.java)
    }

    init {
        Log.d(TAG, "Creating LayerManager")
        this.sharedPreferences = sharedPreferences
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }
}