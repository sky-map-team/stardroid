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
import android.util.Log
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.search.PrefixStore
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.source.*
import com.google.android.stardroid.util.MiscUtil
import java.util.*

/**
 * Layer for objects which are [AstronomicalRenderable]s.
 *
 * @author Brent Bryan
 */
// TODO(brent): merge with AbstractLayer?
abstract class AbstractSourceLayer(resources: Resources, private val shouldUpdate: Boolean) :
    AbstractLayer(resources) {
    private val textPrimitives = ArrayList<TextPrimitive>()
    private val imagePrimitives = ArrayList<ImagePrimitive>()
    private val pointPrimitives = ArrayList<PointPrimitive>()
    private val linePrimitives = ArrayList<LinePrimitive>()
    private val astroRenderables = ArrayList<AstronomicalRenderable>()
    private val searchIndex = HashMap<String, SearchResult>()
    private val prefixStore = PrefixStore()

    @Synchronized
    override fun initialize() {
        astroRenderables.clear()
        initializeAstroSources(astroRenderables)
        for (astroRenderable in astroRenderables) {
            val renderables = astroRenderable.initialize()
            textPrimitives.addAll(renderables.labels)
            imagePrimitives.addAll(renderables.images)
            pointPrimitives.addAll(renderables.points)
            linePrimitives.addAll(renderables.lines)
            val names = astroRenderable.names
            if (!names.isEmpty()) {
                val searchLoc = astroRenderable.searchLocation
                for (name in names) {
                    searchIndex[name.toLowerCase()] = SearchResult(name, searchLoc)
                    prefixStore.add(name.toLowerCase())
                }
            }
        }

        // update the renderer
        updateLayerForControllerChange()
    }

    override fun updateLayerForControllerChange() {
        refreshSources(EnumSet.of(UpdateType.Reset))
        if (shouldUpdate) {
            addUpdateClosure(this::refreshSources)
        }
    }

    /**
     * Subclasses should override this method and add all their
     * [AstronomicalRenderable] to the given [ArrayList].
     */
    protected abstract fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>)

    /**
     * Redraws the sources on this layer, after first refreshing them based on
     * the current state of the
     * [com.google.android.stardroid.control.AstronomerModel].
     */
    protected fun refreshSources() {
        refreshSources(EnumSet.noneOf(UpdateType::class.java))
    }

    /**
     * Redraws the sources on this layer, after first refreshing them based on
     * the current state of the
     * [com.google.android.stardroid.control.AstronomerModel].
     */
    @Synchronized
    protected fun refreshSources(updateTypes: EnumSet<UpdateType>) {
        for (astroRenderable in astroRenderables) {
            updateTypes.addAll(astroRenderable.update())
        }
        if (!updateTypes.isEmpty()) {
            redraw(updateTypes)
        }
    }

    /**
     * Forcefully resets and redraws all sources on this layer everything on
     * this layer.
     */
    override fun redraw() {
        refreshSources(EnumSet.of(UpdateType.Reset))
    }

    private fun redraw(updateTypes: EnumSet<UpdateType>) {
        super.redraw(textPrimitives, pointPrimitives, linePrimitives, imagePrimitives, updateTypes)
    }

    override fun searchByObjectName(name: String): List<SearchResult> {
        Log.d(TAG, "Search planets layer for $name")
        val matches: MutableList<SearchResult> = ArrayList()
        val searchResult = searchIndex[name.toLowerCase()]
        if (searchResult != null) {
            matches.add(searchResult)
        }
        Log.d(TAG, layerName + " provided " + matches.size + "results for " + name)
        return matches
    }

    override fun getObjectNamesMatchingPrefix(prefix: String): Set<String> {
        Log.d(TAG, "Searching planets layer for prefix $prefix")
        val results = prefixStore.queryByPrefix(prefix)
        Log.d(TAG, "Got " + results.size + " results for prefix " + prefix + " in " + layerName)
        return results
    }

    companion object {
        private val TAG = MiscUtil.getTag(AbstractSourceLayer::class.java)
    }
}