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

import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.R
import com.google.android.stardroid.base.Lists
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import java.util.*
import kotlin.math.abs

/**
 * Creates a mark at the zenith, nadir and cardinal point and a horizon.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
class HorizonLayer(private val model: AstronomerModel, resources: Resources) :
    AbstractRenderablesLayer(resources, true) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(HorizonRenderable(model, resources))
    }

    override val layerDepthOrder = 90

    // TODO(brent): Remove this.
    override val preferenceId = "source_provider.5"

    // TODO(johntaylor): i18n
    override val layerName = "Horizon"

    override val layerNameId = R.string.show_horizon_pref // TODO(johntaylor): rename this string id

    /** Implementation of [AstronomicalRenderable] for the horizon source.  */
    internal class HorizonRenderable(private val model: AstronomerModel, resources: Resources) :
        AbstractAstronomicalRenderable() {
        private val zenith = Vector3(0f, 0f, 0f)
        private val nadir = Vector3(0f, 0f, 0f)
        private val north = Vector3(0f, 0f, 0f)
        private val south = Vector3(0f, 0f, 0f)
        private val east = Vector3(0f, 0f, 0f)
        private val west = Vector3(0f, 0f, 0f)
        private var lastUpdateTimeMs = 0L
        private fun updateCoords() {
            // Blog.d(this, "Updating Coords: " + (model.getTime().getTime() - lastUpdateTimeMs));
            lastUpdateTimeMs = model.time.time
            zenith.assign(model.zenith)
            nadir.assign(model.nadir)
            north.assign(model.north)
            south.assign(model.south)
            east.assign(model.east)
            west.assign(model.west)
        }

        override fun initialize(): Renderable {
            updateCoords()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)

            // TODO(brent): Add distance here.
            if (abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateCoords()
                updateTypes.add(UpdateType.UpdatePositions)
            }
            return updateTypes
        }

        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        companion object {
            // Due to a bug in the G1 rendering code text and lines render in different
            // colors.
            private val LINE_COLOR = Color.argb(120, 86, 176, 245)
            private val LABEL_COLOR = Color.argb(120, 245, 176, 86)
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND
        }

        init {
            val vertices = Lists.asList(north, east, south, west, north)
            lines.add(LinePrimitive(LINE_COLOR, vertices, 1.5f))
            labels.add(TextPrimitive(zenith, resources.getString(R.string.zenith), LABEL_COLOR))
            labels.add(TextPrimitive(nadir, resources.getString(R.string.nadir), LABEL_COLOR))
            labels.add(TextPrimitive(north, resources.getString(R.string.north), LABEL_COLOR))
            labels.add(TextPrimitive(south, resources.getString(R.string.south), LABEL_COLOR))
            labels.add(TextPrimitive(east, resources.getString(R.string.east), LABEL_COLOR))
            labels.add(TextPrimitive(west, resources.getString(R.string.west), LABEL_COLOR))
        }
    }
}