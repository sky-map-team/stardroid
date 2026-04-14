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

import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.stardroid.R
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.renderables.AstronomicalRenderable

/**
 * Layer for tracking the International Space Station.
 *
 * NOTE: This layer is currently disabled (no-op). The original implementation fetched
 * orbital data from a NASA URL that is now defunct, and the orbital position calculation
 * was never completed. The layer is kept as a placeholder for potential future implementation
 * using a working data source (e.g., CelesTrak TLE data).
 *
 * @author Brent Bryan
 */
class IssLayer(
    resources: Resources,
    model: AstronomerModel,
    preferences: SharedPreferences
) : AbstractRenderablesLayer(resources, true, preferences) {

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        // No-op: ISS tracking is disabled pending reimplementation with a working data source
    }

    override val layerDepthOrder = 70
    override val layerNameId = R.string.show_satellite_layer_pref
}
