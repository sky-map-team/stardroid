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
        // Glow rings below the horizon, each recomputed from horizonVerts in updateCoords().
        // Together with horizonVerts they form the concentric loops of the glow gradient mesh.
        private val glowRings: Array<Array<Vector3>> =
            Array(NUM_GLOW_RINGS) { Array(NUM_SEGMENTS + 1) { Vector3(0f, 0f, 0f) } }
        // Each glow ring's tilt toward the nadir depends only on constants, so compute the
        // sin/cos once here rather than on every (per-second) coordinate update.
        private val cosTilts = FloatArray(NUM_GLOW_RINGS) { ringIdx ->
            cos(Math.toRadians(((ringIdx + 1) * GLOW_RING_SPACING_DEG).toDouble())).toFloat()
        }
        private val sinTilts = FloatArray(NUM_GLOW_RINGS) { ringIdx ->
            sin(Math.toRadians(((ringIdx + 1) * GLOW_RING_SPACING_DEG).toDouble())).toFloat()
        }
        private var lastUpdateTimeMs = 0L

        private fun updateCoords() {
            lastUpdateTimeMs = model.time.time
            zenith.assign(model.zenith)
            nadir.assign(model.nadir)
            north.assign(model.north)
            south.assign(model.south)
            east.assign(model.east)
            west.assign(model.west)

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
        override val glows: MutableList<HorizonGlowPrimitive> = ArrayList()

        companion object {
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_SECOND
            // 180 segments around each ring: visually smooth. Mesh vertex count is
            // (NUM_GLOW_RINGS + 1) × (NUM_SEGMENTS + 1) — well under the signed-short index
            // limit of 32 767 used by HorizonGlowObjectManager.
            private const val NUM_SEGMENTS = 180
            // The glow is a single gradient mesh, not a stack of translucent strips. Ring 0 is
            // the horizon itself; NUM_GLOW_RINGS further rings are tilted toward the nadir at
            // GLOW_RING_SPACING_DEG steps, so the glow reaches NUM_GLOW_RINGS × spacing below
            // the horizon and never above it.
            //
            // Why several rings even though the GPU interpolates color across the bands? The
            // interpolation is what makes the gradient smooth, so it is NOT the reason for the
            // ring count: even two rings (peak at the horizon, zero at the bottom) would give a
            // seam-free gradient. But fixed-function (Gouraud) shading interpolates alpha
            // *linearly*, so two stops can only produce a straight ramp. We want the
            // *exponential* falloff below (bright at the horizon with a long soft tail), so we
            // give the curve multiple stops and let the per-band linear interpolation trace it
            // piecewise. More rings = finer approximation of the curve; 8 is enough that the
            // corners are imperceptible. Drop to 2 if a plain linear fade is ever wanted.
            private const val NUM_GLOW_RINGS = 8
            private const val GLOW_RING_SPACING_DEG = 1.0f
            // Additive glow intensity at the horizon, as a fraction of full (255) alpha, with
            // an exponential falloff toward the deepest ring (which is forced fully
            // transparent so the gradient fades out cleanly).
            private const val GLOW_PEAK_ALPHA = 0.7f
            private const val GLOW_ALPHA_DECAY = 0.55f  // per ring index (natural units)
        }

        init {
            val lineColor = resources.getColor(R.color.horizon_line, null)
            val labelColor = resources.getColor(R.color.horizon_label, null)
            lines.add(LinePrimitive(lineColor, horizonVerts.toList(), 2.5f))

            // Build the glow gradient mesh: ring 0 is the horizon, the rest are the glow rings
            // descending toward the nadir. Each ring is painted in the horizon color with an
            // exponentially decaying alpha; the renderer interpolates these across the bands
            // for a smooth, additively-blended glow. The deepest ring is fully transparent so
            // the gradient fades out instead of ending in a hard edge.
            val r = android.graphics.Color.red(lineColor)
            val g = android.graphics.Color.green(lineColor)
            val b = android.graphics.Color.blue(lineColor)
            val meshRings = ArrayList<List<Vector3>>(NUM_GLOW_RINGS + 1)
            meshRings.add(horizonVerts.toList())
            for (i in 0 until NUM_GLOW_RINGS) {
                meshRings.add(glowRings[i].toList())
            }
            val ringColors = IntArray(NUM_GLOW_RINGS + 1) { ring ->
                val alpha = if (ring == NUM_GLOW_RINGS) {
                    0
                } else {
                    (GLOW_PEAK_ALPHA * exp(-ring * GLOW_ALPHA_DECAY) * 255f)
                        .toInt().coerceIn(0, 255)
                }
                android.graphics.Color.argb(alpha, r, g, b)
            }
            glows.add(HorizonGlowPrimitive(meshRings, ringColors))
            labels.add(TextPrimitive(zenith, resources.getString(R.string.zenith), labelColor))
            labels.add(TextPrimitive(nadir, resources.getString(R.string.nadir), labelColor))
            labels.add(TextPrimitive(north, resources.getString(R.string.north), labelColor))
            labels.add(TextPrimitive(south, resources.getString(R.string.south), labelColor))
            labels.add(TextPrimitive(east, resources.getString(R.string.east), labelColor))
            labels.add(TextPrimitive(west, resources.getString(R.string.west), labelColor))
        }
    }
}
