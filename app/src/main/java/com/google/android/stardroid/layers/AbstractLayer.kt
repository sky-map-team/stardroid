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

import android.content.res.Resources
import android.util.Log
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.renderer.RendererController.AtomicSection
import com.google.android.stardroid.renderer.RendererControllerBase
import com.google.android.stardroid.renderer.RendererControllerBase.RenderManager
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.renderer.util.UpdateClosure
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.source.ImagePrimitive
import com.google.android.stardroid.source.LinePrimitive
import com.google.android.stardroid.source.PointPrimitive
import com.google.android.stardroid.source.TextPrimitive
import com.google.android.stardroid.util.MiscUtil
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * Base implementation of the [Layer] interface.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
abstract class AbstractLayer(protected val resources: Resources) : Layer {
    private val renderMapLock = ReentrantLock()
    private val renderMap = HashMap<Class<*>, RenderManager<*>>()
    private lateinit var renderer: RendererController

    override fun registerWithRenderer(rendererController: RendererController) {
        renderMap.clear()
        renderer = rendererController
        updateLayerForControllerChange()
    }

    protected abstract fun updateLayerForControllerChange()

    override fun setVisible(visible: Boolean) {
        renderMapLock.lock()
        try {
            val atomic = renderer.createAtomic()
            for ((_, value) in renderMap) {
                value.queueEnabled(visible, atomic)
            }
            renderer.queueAtomic(atomic)
        } finally {
            renderMapLock.unlock()
        }
    }

    protected fun addUpdateClosure(closure: UpdateClosure) {
        renderer.addUpdateClosure(closure)
    }

    protected fun removeUpdateClosure(closure: UpdateClosure) {
        renderer.removeUpdateCallback(closure)
    }

    /**
     * Forces a redraw of this layer, clearing all of the information about this
     * layer in the renderer and repopulating it.
     */
    protected abstract fun redraw()

    /**
     * Updates the renderer (using the given [UpdateType]) with the given set of
     * UI elements.  Depending on the value of [UpdateType], current sources will
     * either have their state updated, or will be overwritten by the given set
     * of UI elements.
     */
    protected fun redraw(
        textPrimitives: List<TextPrimitive>,
        pointPrimitives: List<PointPrimitive>,
        linePrimitives: List<LinePrimitive>,
        imagePrimitives: List<ImagePrimitive>,
        updateTypes: EnumSet<UpdateType> = EnumSet.of(UpdateType.Reset)
    ) {
        renderMapLock.lock()
        try {
            // Blog.d(this, "Redraw: " + updateTypes);
            val atomic = (renderer ?: return).createAtomic()
            setSources(textPrimitives, updateTypes, TextPrimitive::class.java, atomic)
            setSources(pointPrimitives, updateTypes, PointPrimitive::class.java, atomic)
            setSources(linePrimitives, updateTypes, LinePrimitive::class.java, atomic)
            setSources(imagePrimitives, updateTypes, ImagePrimitive::class.java, atomic)
            renderer.queueAtomic(atomic)
        } finally {
            renderMapLock.unlock()
        }
    }

    /**
     * Sets the objects on the [RenderManager] to the given values,
     * creating (or disabling) the [RenderManager] if necessary.
     */
    private fun <E> setSources(
        sources: List<E>, updateType: EnumSet<UpdateType>,
        clazz: Class<E>, atomic: AtomicSection
    ) {
        var manager = renderMap[clazz] as RenderManager<E>?
        if (sources.isEmpty()) {
            if (manager != null) {
                // TODO(brent): we should really just disable this layer, but in a
                // manner that it will automatically be reenabled when appropriate.
                Log.d(TAG, "       " + clazz.simpleName)
                manager.queueObjects(emptyList(), updateType, atomic)
            }
            return
        }
        if (manager == null) {
            manager = createRenderManager(clazz, atomic)
            renderMap[clazz] = manager
        }
        // Blog.d(this, "       " + clazz.getSimpleName() + " " + sources.size());
        manager.queueObjects(sources, updateType, atomic)
    }

    fun <E> createRenderManager(
        clazz: Class<E>,
        controller: RendererControllerBase
    ) = when (clazz) {
        ImagePrimitive::class.java -> controller.createImageManager(layerDepthOrder) as RenderManager<E>
        TextPrimitive::class.java -> controller.createLabelManager(layerDepthOrder) as RenderManager<E>
        LinePrimitive::class.java -> controller.createLineManager(layerDepthOrder) as RenderManager<E>
        PointPrimitive::class.java -> controller.createPointManager(layerDepthOrder) as RenderManager<E>
        else -> throw IllegalStateException("Unknown source type: $clazz")
    }

    override fun searchByObjectName(name: String): List<SearchResult> {
        // By default, layers will return no search results.
        // Override this if the layer should be searchable.
        return emptyList()
    }

    override fun getObjectNamesMatchingPrefix(prefix: String): Set<String> {
        // By default, layers will return no search results.
        // Override this if the layer should be searchable.
        return emptySet()
    }

    /**
     * Provides a string ID to the internationalized name of this layer.
     */
    // TODO(brent): could this be combined with getLayerDepthOrder?  Not sure - they
    // serve slightly different purposes.
    protected abstract val layerNameId: Int
    override val preferenceId: String
        get() = getPreferenceId(layerNameId)

    protected fun getPreferenceId(layerNameId: Int) = "source_provider.$layerNameId"

    override val layerName: String
        get() = getStringFromId(layerNameId)

    /**
     * Return an internationalized string from a string resource id.
     */
    protected fun getStringFromId(resourceId: Int) = resources.getString(resourceId)

    private val TAG = MiscUtil.getTag(AbstractLayer::class.java)

}