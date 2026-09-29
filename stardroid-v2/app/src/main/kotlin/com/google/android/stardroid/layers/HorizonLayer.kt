/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.GlowPrimitive
import com.google.android.stardroid.render.api.GlowRing
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * The local horizon, ported from v1's `HorizonLayer` (after upstream #924): the great circle
 * through the cardinal points, a soft additive glow just below it, plus cardinal-direction
 * labels, all derived from [SkyModel.localFrame] (true north — magnetic declination plays no
 * part here).
 *
 * Zenith/nadir labels moved to [AltAzGridLayer] (#1022): they are that layer's coordinate-system
 * poles, not part of the horizon itself, and only show when the (off-by-default) alt/az grid is
 * enabled.
 *
 * The horizon drifts through celestial coordinates as the Earth turns (~0.25°/min), so the scene
 * recomputes on each [clock] emission — the caller picks the tick rate, and time travel
 * accelerates it like everything else (layers-and-app.md). [distinctUntilChanged] keeps
 * unchanged recomputations from reaching the renderer.
 */
class HorizonLayer(
    private val clock: Flow<Instant>,
    private val location: Flow<LatLong>,
    private val strings: Flow<LayerStrings>,
    /**
     * Used only to find the sun's altitude, which sets how much of the glow is drawn — see
     * [daylightGlowScale].
     */
    private val ephemeris: Ephemeris,
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH

    override fun scenes(): Flow<LayerScene> =
        combine(clock, location, strings) { time, loc, str -> Triple(time, loc, str) }
            .distinctUntilChanged()
            .map { (time, loc, str) -> buildScene(time, loc, str) }
            .flowOn(mapContext)

    internal fun buildScene(
        time: Instant,
        location: LatLong,
        strings: LayerStrings,
    ): LayerScene {
        val frame = SkyModel.localFrame(time, location)
        val north = frame.trueNorth
        val south = -frame.trueNorth
        val east = frame.trueEast
        val west = -frame.trueEast
        val nadir = -frame.up

        val horizon =
            LinePrimitive(
                listOf(north, east, south, west, north),
                SkyColors.HORIZON_LINE,
                LINE_WIDTH_DP,
            )
        val labels =
            listOf(
                label(north, strings.north),
                label(south, strings.south),
                label(east, strings.east),
                label(west, strings.west),
            )
        val sunDirection =
            ephemeris.geocentricPosition(SolarSystemBody.SUN, time).toGeocentricVector()
        val sunAltitudeDeg =
            asin((sunDirection.normalized() dot frame.up).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES
        return LayerScene(
            depth = depth,
            lines = listOf(horizon),
            labels = labels,
            glows = listOf(glowMesh(north, east, nadir, daylightGlowScale(sunAltitudeDeg))),
        )
    }

    /**
     * The glow gradient mesh (upstream #924): ring 0 is the horizon circle
     * `p(θ) = north·cos θ + east·sin θ`; each further ring is the circle tilted toward the nadir
     * (`p·cos t + nadir·sin t`) at [GLOW_RING_SPACING_DEG] steps, so the glow reaches
     * `NUM_GLOW_RINGS × spacing` below the horizon and never above it. Each ring is painted in
     * the horizon color with an exponentially decaying alpha; the renderer interpolates these
     * across the bands for a smooth, additively-blended glow. The deepest ring is fully
     * transparent so the gradient fades out instead of ending in a hard edge.
     *
     * Why several rings even though the GPU interpolates color across the bands? The
     * interpolation is what makes the gradient smooth, so it is NOT the reason for the ring
     * count: even two rings (peak at the horizon, zero at the bottom) would give a seam-free
     * gradient. But fixed-function (Gouraud) shading interpolates alpha *linearly*, so two stops
     * can only produce a straight ramp. We want the *exponential* falloff below (bright at the
     * horizon with a long soft tail), so we give the curve multiple stops and let the per-band
     * linear interpolation trace it piecewise. More rings = finer approximation of the curve;
     * 8 is enough that the corners are imperceptible. Drop to 2 if a plain linear fade is ever
     * wanted.
     */
    private fun glowMesh(
        north: Vector3,
        east: Vector3,
        nadir: Vector3,
        scale: Float,
    ): GlowPrimitive {
        val rings =
            (0..NUM_GLOW_RINGS).map { ringIdx ->
                val cosTilt = COS_TILTS[ringIdx]
                val sinTilt = SIN_TILTS[ringIdx]
                val vertices =
                    // NUM_SEGMENTS+1 vertices: the modulo makes the closing vertex bitwise
                    // equal to the first (sin(2π) isn't exactly 0 in floating point).
                    (0..NUM_SEGMENTS).map { i ->
                        val cosA = COS_ANGLES[i]
                        val sinA = SIN_ANGLES[i]
                        val factorNorth = cosA * cosTilt
                        val factorEast = sinA * cosTilt
                        Vector3(
                            north.x * factorNorth + east.x * factorEast + nadir.x * sinTilt,
                            north.y * factorNorth + east.y * factorEast + nadir.y * sinTilt,
                            north.z * factorNorth + east.z * factorEast + nadir.z * sinTilt,
                        )
                    }
                GlowRing(vertices, SkyColors.HORIZON_LINE.copy(a = ALPHAS[ringIdx] * scale))
            }
        return GlowPrimitive(rings)
    }

    /**
     * How much of the glow to draw, from the sun's altitude in degrees.
     *
     * The glow is additive — it *adds* light to what is behind it. That is right against a night
     * sky, which is what it was built for, but a lit sky has nothing to add to: over daylight or
     * bright twilight the same mesh saturates to near-white and becomes the brightest thing on
     * screen, sitting below the horizon where the scene should be darkest. GLES1 never showed
     * this because its eight-band dome is too dim to saturate against; the analytic sky in
     * `:render:gles3` is not, so the interaction only surfaced once that landed.
     *
     * Full strength once the sun is well down (astronomical twilight and darker), fading out as
     * it rises to the horizon, where the real sky's own glow takes over the job. Scaling the
     * producer's alpha rather than special-casing the blend in the backend keeps the two
     * renderers drawing the same scene, and keeps "how bright should this be" a question about
     * the sky rather than about GL.
     */
    internal fun daylightGlowScale(sunAltitudeDeg: Double): Float {
        val t =
            ((sunAltitudeDeg - GLOW_FADE_START_DEG) /
                (GLOW_FADE_END_DEG - GLOW_FADE_START_DEG)).coerceIn(0.0, 1.0)
        return (1.0 - t).toFloat()
    }

    private fun label(
        pos: Vector3,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = pos,
            text = text,
            style = LabelStyle(LabelSize.STANDARD, SkyColors.HORIZON_LABEL),
            priority = LABEL_PRIORITY,
        )

    companion object {
        val LAYER_ID = LayerId("computed/horizon")

        /** v1 depth table: the horizon draws in front of everything. */
        private const val DEPTH = 90

        private const val LINE_WIDTH_DP = 2.5

        /** Above the catalog mid-band: orientation cues should survive decluttering. */
        private const val LABEL_PRIORITY = 70

        /**
         * 180 segments around each glow ring: visually smooth. The horizon *line* stays a
         * 5-vertex loop (the backend's great-circle subdivision smooths it), but the glow mesh
         * carries explicit ring vertices — no subdivision applies to [GlowPrimitive]s. Mesh
         * vertex count is (NUM_GLOW_RINGS + 1) × (NUM_SEGMENTS + 1), well under the backend's
         * signed-short index limit.
         */
        private const val NUM_SEGMENTS = 180
        private const val NUM_GLOW_RINGS = 8
        private const val GLOW_RING_SPACING_DEG = 1.0

        /**
         * Additive glow intensity at the horizon, with an exponential falloff (per ring index,
         * natural units) toward the deepest ring, which is forced fully transparent.
         */
        /**
         * Sun altitudes between which the glow fades out, in degrees. Full below
         * [GLOW_FADE_START_DEG] (astronomical twilight, where the sky is dark enough for an
         * additive glow to read as glow), gone by [GLOW_FADE_END_DEG], a little above the
         * horizon, where the sky is bright enough to saturate.
         */
        private const val GLOW_FADE_START_DEG = -18.0
        private const val GLOW_FADE_END_DEG = -2.0

        private const val GLOW_PEAK_ALPHA = 0.7
        private const val GLOW_ALPHA_DECAY = 0.55

        // The mesh's per-vertex angle/tilt trig and per-ring alpha depend only on these
        // constants, not on the local frame — precomputed once rather than recomputed on every
        // buildScene (up to per-frame during time travel).
        private val COS_ANGLES =
            DoubleArray(
                NUM_SEGMENTS + 1,
            ) { i -> cos(2.0 * Math.PI * (i % NUM_SEGMENTS) / NUM_SEGMENTS) }
        private val SIN_ANGLES =
            DoubleArray(
                NUM_SEGMENTS + 1,
            ) { i -> sin(2.0 * Math.PI * (i % NUM_SEGMENTS) / NUM_SEGMENTS) }
        private val COS_TILTS =
            DoubleArray(
                NUM_GLOW_RINGS + 1,
            ) { ringIdx -> cos(ringIdx * GLOW_RING_SPACING_DEG * DEGREES_TO_RADIANS) }
        private val SIN_TILTS =
            DoubleArray(
                NUM_GLOW_RINGS + 1,
            ) { ringIdx -> sin(ringIdx * GLOW_RING_SPACING_DEG * DEGREES_TO_RADIANS) }
        private val ALPHAS =
            FloatArray(NUM_GLOW_RINGS + 1) { ringIdx ->
                if (ringIdx == NUM_GLOW_RINGS) {
                    0f
                } else {
                    (GLOW_PEAK_ALPHA * exp(-ringIdx * GLOW_ALPHA_DECAY)).toFloat()
                }
            }
    }
}
