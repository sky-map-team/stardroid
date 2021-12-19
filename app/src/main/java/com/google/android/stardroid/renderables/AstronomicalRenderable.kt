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
package com.google.android.stardroid.renderables

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import java.util.*

/**
 * This class represents a single source shown in SkyMap. An AstronomicalRenderable
 * may consist of several primitives. For instance, a constellation may have a
 * label, an image, as well as the star to star lines.
 *
 * @author Brent Bryan
 */
interface AstronomicalRenderable : Renderable {
    /**
     * Returns a list of names associated with this source. Names in this list
     * should be internationalized.
     */
    val names: List<String>

    /**
     * Returns the GeocentriCoordinates of the center of this object.
     * This is the point to which the user will be directed for a search.
     * @return
     */
    val searchLocation: Vector3

    /**
     * True if the object should be visible in search results
     */
    var isVisible: Boolean

    /**
     * Returns the zoom level to which the user should be taken (in manual mode)
     * to completely see this object when searching.
     */
    // float getSearchLevel();
    /**
     * Returns the level associated with this source. Levels typically corresponds
     * to the magnitude of the object and dictate whether the source will be shown
     * given the zoom level and limits set by the user.
     */
    // float getLevel();
    /**
     * Initializes and returns the elements for this [AstronomicalRenderable].
     * Elements should have their positions, images, etc update to the current
     * time / location information.
     */
    fun initialize(): Renderable

    /**
     * Updates the [Renderable] of this [AstronomicalRenderable] in response
     * to a change in the user's location or current time. Changes can be caused
     * by the user moving to a new location or time progressing, or by the user
     * manually selecting a different location. Returns the minimal Set of
     * UpdateType required to enact the changes required by this update.
     */
    fun update(): EnumSet<UpdateType>
}