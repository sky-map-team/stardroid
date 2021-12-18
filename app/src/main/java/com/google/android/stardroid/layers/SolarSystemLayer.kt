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

import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.stardroid.R
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.ephemeris.SolarSystemBody
import com.google.android.stardroid.ephemeris.SolarSystemRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import java.util.*

/**
 * An implementation of the [Layer] interface for displaying planets in
 * the Renderer.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
class SolarSystemLayer(
    private val model: AstronomerModel,
    resources: Resources,
    private val preferences: SharedPreferences
) : AbstractRenderablesLayer(resources, true) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        for (solarSystemBody in SolarSystemBody.values()) {
            if (solarSystemBody === SolarSystemBody.Earth) continue // Don't try to display earth!
            // TODO(jontayler): IIRC this determines the rendering order. We should base it
            // on actual distrance to Earth.
            sources.add(
                SolarSystemRenderable(
                    solarSystemBody,
                    resources,
                    model,
                    preferences
                )
            )
        }
    }

    // TODO(brent): Remove this.
    override val preferenceId = "source_provider.3"

    // TODO(brent): refactor these to a common location.
    override val layerDepthOrder = 60

    // TODO(johntaylor): rename the string id.
    override val layerNameId  = R.string.show_planets_pref // TODO(johntaylor): rename the string id.
}