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

import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.R
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import java.util.*

/**
 * Creates a Layer for the Ecliptic.
 *
 * @author John Taylor
 * @author Brent Bryan
 */
class EclipticLayer(resources: Resources) : AbstractRenderablesLayer(resources, false) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(EclipticRenderable(resources))
    }

    override val layerDepthOrder = 50
    override val layerNameId = R.string.show_grid_pref
    override val preferenceId = "source_provider.4"

    /** Implementation of [AstronomicalRenderable] for the ecliptic source.  */
    private class EclipticRenderable(resources: Resources) : AbstractAstronomicalRenderable() {
        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        companion object {
            private const val EARTHS_ANGULAR_TILT = 23.439281f
            private val LINE_COLOR = Color.argb(20, 248, 239, 188)
        }

        init {
            val title = resources.getString(R.string.ecliptic)
            labels.add(TextPrimitive(90.0f, EARTHS_ANGULAR_TILT, title, LINE_COLOR))
            labels.add(TextPrimitive(270f, -EARTHS_ANGULAR_TILT, title, LINE_COLOR))

            // Create line source.
            val ra = floatArrayOf(0f, 90f, 180f, 270f, 0f)
            val dec = floatArrayOf(0f, EARTHS_ANGULAR_TILT, 0f, -EARTHS_ANGULAR_TILT, 0f)
            val vertices = ArrayList<Vector3>()
            for (i in ra.indices) {
                vertices.add(getGeocentricCoords(ra[i], dec[i]))
            }
            lines.add(LinePrimitive(LINE_COLOR, vertices, 1.5f))
        }
    }
}