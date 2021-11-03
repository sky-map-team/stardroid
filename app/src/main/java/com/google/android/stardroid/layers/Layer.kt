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

import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.search.SearchResult
import com.google.android.stardroid.source.AstronomicalSource

/**
 * A logical collection of objects which should be displayed in SkyMap. For
 * instance, the set of objects which should be turned off / on simultaneously.
 *
 * @author Brent Bryan
 */
interface Layer {
    /**
     * Initializes the layer; reading data and computing locations as necessary.
     * This method should return quickly - use a background thread if necessary.
     * This method is typically called before the [.registerWithRenderer]
     * method, but may not be.
     */
    fun initialize()

    /**
     * Registers this layer with the given [RendererController].  None of
     * the objects in this layer can be displayed until this method is called.
     */
    fun registerWithRenderer(controller: RendererController?)

    /**
     * Returns the z-ordering of the layers.  Lower numbers are rendered first and
     * are therefore 'behind' higher numbered layers.
     */
    val layerDepthOrder: Int

    /**
     * Returns the preference label associated with this layer.
     */
    val preferenceId: String

    /**
     * Returns the name associated with this layer.
     */
    val layerName: String

    /**
     * Sets whether the [AstronomicalSource]s in this layer should be shown
     * by the renderer.
     */
    fun setVisible(visible: Boolean)

    /**
     * Search the layer for an object with the given name.  The search is
     * case-insensitive.
     *
     * @param name the name to search for
     * @return a list of all matching objects.
     */
    fun searchByObjectName(name: String): List<SearchResult?>?

    /**
     * Given a string prefix, find all possible queries for which we have a
     * search result.  The search is case-insensitive.
     * @param prefix the prefix to search for.
     * @return a set of matching queries.
     */
    fun getObjectNamesMatchingPrefix(prefix: String): Set<String>
}