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
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Creates a mark at the zenith, nadir and cardinal point and a horizon.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
class HorizonLayer(private val model: AstronomerModel, resources: Resources, preferences: SharedPreferences) :
    AbstractRenderablesLayer(resources, true, preferences) {
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
        // NUM_SEGMENTS+1 because the last vertex closes the loop back to the first.
        private val horizonVerts: Array<Vector3> = Array(NUM_SEGMENTS + 1) { Vector3(0f, 0f, 0f) }
        // 25 narrow glow rings, each recomputed from horizonVerts in updateCoords().
        private val glowRings: Array<Array<Vector3>> =
            Array(NUM_GLOW_RINGS) { Array(NUM_SEGMENTS + 1) { Vector3(0f, 0f, 0f) } }
        private var lastUpdateTimeMs = 0L

        private fun updateCoords() {
            lastUpdateTimeMs = model.time.time
            zenith.assign(model.zenith)
            nadir.assign(model.nadir)
            north.assign(model.north)
            south.assign(model.south)
            east.assign(model.east)
            west.assign(model.west)

            // Pre-compute tilt sin/cos for each glow ring once per update.
            val cosTilts = FloatArray(NUM_GLOW_RINGS)
            val sinTilts = FloatArray(NUM_GLOW_RINGS)
            for (ringIdx in 0 until NUM_GLOW_RINGS) {
                val tiltRad = Math.toRadians(((ringIdx + 1) * GLOW_RING_SPACING_DEG).toDouble())
                cosTilts[ringIdx] = cos(tiltRad).toFloat()
                sinTilts[ringIdx] = sin(tiltRad).toFloat()
            }

            // Horizon circle: p(θ) = north·cos(θ) + east·sin(θ)
            for (i in 0..NUM_SEGMENTS) {
                val angle = 2.0 * Math.PI * i / NUM_SEGMENTS
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                val hx = north.x * cosA + east.x * sinA
                val hy = north.y * cosA + east.y * sinA
                val hz = north.z * cosA + east.z * sinA
                horizonVerts[i].assign(hx, hy, hz)
                // Tilt each glow ring toward nadir: p_tilted = p·cos(t) + nadir·sin(t)
                for (ringIdx in 0 until NUM_GLOW_RINGS) {
                    glowRings[ringIdx][i].assign(
                        hx * cosTilts[ringIdx] + nadir.x * sinTilts[ringIdx],
                        hy * cosTilts[ringIdx] + nadir.y * sinTilts[ringIdx],
                        hz * cosTilts[ringIdx] + nadir.z * sinTilts[ringIdx]
                    )
                }
            }
        }

        override fun initialize(): Renderable {
            updateCoords()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)
            if (abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateCoords()
                updateTypes.add(UpdateType.UpdatePositions)
            }
            return updateTypes
        }

        override val labels: MutableList<TextPrimitive> = ArrayList()
        override val lines: MutableList<LinePrimitive> = ArrayList()

        companion object {
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND
            // 180 segments per ring: visually smooth, and keeps total quad count
            // (26 rings × 180) × 4 vertices = 18 720 — safely under the signed-short
            // index limit of 32 767 in PolyLineObjectManager.
            private const val NUM_SEGMENTS = 180
            // 25 narrow rings spaced 0.5° apart → smooth exponential glow from horizon
            // down to ~12.5°. lineWidth 14 ≈ 0.97° half-width, so adjacent rings overlap
            // by ~1.4° and each interior point is covered by ~3 rings simultaneously,
            // making the per-ring alpha steps imperceptible.
            private const val NUM_GLOW_RINGS = 25
            private const val GLOW_RING_SPACING_DEG = 0.5f
            private const val GLOW_LINE_WIDTH = 14f
            private const val GLOW_ALPHA_BASE = 0.50f
            private const val GLOW_ALPHA_DECAY = 0.20f  // per ring index (natural units)
        }

        init {
            val lineColor = resources.getColor(R.color.horizon_line, null)
            val labelColor = resources.getColor(R.color.horizon_label, null)
            lines.add(LinePrimitive(lineColor, horizonVerts.toList(), 2.5f))

            val baseAlpha = android.graphics.Color.alpha(lineColor)
            val r = android.graphics.Color.red(lineColor)
            val g = android.graphics.Color.green(lineColor)
            val b = android.graphics.Color.blue(lineColor)
            for (i in 0 until NUM_GLOW_RINGS) {
                val alpha = (GLOW_ALPHA_BASE * exp(-i * GLOW_ALPHA_DECAY) * baseAlpha)
                    .toInt().coerceIn(0, 255)
                val glowColor = android.graphics.Color.argb(alpha, r, g, b)
                lines.add(LinePrimitive(glowColor, glowRings[i].toList(), GLOW_LINE_WIDTH))
            }
            labels.add(TextPrimitive(zenith, resources.getString(R.string.zenith), labelColor))
            labels.add(TextPrimitive(nadir, resources.getString(R.string.nadir), labelColor))
            labels.add(TextPrimitive(north, resources.getString(R.string.north), labelColor))
            labels.add(TextPrimitive(south, resources.getString(R.string.south), labelColor))
            labels.add(TextPrimitive(east, resources.getString(R.string.east), labelColor))
            labels.add(TextPrimitive(west, resources.getString(R.string.west), labelColor))
        }
    }
}
