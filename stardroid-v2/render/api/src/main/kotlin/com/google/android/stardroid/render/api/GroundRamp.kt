/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import kotlin.math.abs
import kotlin.math.exp

/**
 * How opaque the [Ground] is, as a function of where you are looking and where the Sun is.
 *
 * Pure, so it is unit-testable without GL, and it is the **golden reference** for
 * `:render:gles3`'s `ground.frag`, which transcribes it line for line. That arrangement is the
 * branch's standing answer to shaders escaping `./gradlew check` (see [SizeFloor] and
 * `StellarRamps`): the realistic failure mode is transcription drift, not a wrong algorithm, so
 * the Kotlin original is kept and conformance-tested rather than deleted.
 *
 * Three independent terms multiply together, each answering a different question.
 */
object GroundRamp {
    /**
     * Where the ground stops, in degrees of view altitude. The cut lands on zero — the horizon —
     * and this is only the width of the antialiasing ramp, not a fade: a wider ramp puts the
     * boundary visibly *below* the horizon line, which reads as a second unexplained edge rather
     * than as ground. That was learned the hard way with a four-degree fade on the sky dome.
     */
    const val EDGE_RAMP_DEG = 0.25

    /**
     * How fast the horizon brightening decays with depth below the horizon, in degrees.
     *
     * This is the term that imparts distance. The eight-ring glow it replaces achieved the same
     * thing over a much tighter span (about two degrees), which made it read as a band along the
     * horizon rather than as ground receding; widening it is the point of doing this in a shader,
     * where the falloff costs one `exp` instead of a ring of geometry per stop.
     */
    const val DEPTH_SCALE_DEG = 7.0

    /**
     * The ground's opacity far from the horizon, as a fraction of its opacity at the horizon.
     *
     * Non-zero because the ground is a solid hemisphere, not a band: looking straight down should
     * show ground, not the horizon's absence. The gap between this and 1.0 is the whole depth
     * cue, so it trades directly against [DEPTH_SCALE_DEG] — a high floor with a long scale is a
     * flat wash, and a low floor is a band with a hole beneath it.
     */
    const val NADIR_FRACTION = 0.6

    /** Below this solar altitude the night scaling is fully applied, in degrees. */
    const val NIGHT_SUN_ALTITUDE_DEG = -12.0

    /** At and above this solar altitude the ground is at full strength, in degrees. */
    const val DAY_SUN_ALTITUDE_DEG = 0.0

    /**
     * How much of the ground survives at night.
     *
     * In daylight the ground has to be about as substantial as the lit sky above it or the lower
     * hemisphere reads as a hole punched in a bright scene. At night there is no bright scene to
     * be a hole in, and the sky below the horizon is already black, so the same opacity would
     * only be dimming stars for nothing — and dimming stars is precisely what the
     * look-through-the-Earth view exists to avoid.
     */
    const val NIGHT_FRACTION = 0.35

    /**
     * Zero above the horizon, one below it, with [EDGE_RAMP_DEG] of smoothing so the boundary
     * antialiases instead of stair-stepping along the pixel grid.
     */
    fun coverage(viewAltitudeDeg: Double): Double = smoothstep(0.0, -EDGE_RAMP_DEG, viewAltitudeDeg)

    /**
     * The depth cue: 1 at the horizon decaying to [NADIR_FRACTION] far below it. Uses the
     * absolute altitude so it is symmetric about the horizon, which matters only inside the edge
     * ramp but keeps the function continuous there.
     */
    fun depthProfile(viewAltitudeDeg: Double): Double =
        NADIR_FRACTION +
            (1.0 - NADIR_FRACTION) * exp(-abs(viewAltitudeDeg) / DEPTH_SCALE_DEG)

    /** Full strength in daylight, easing to [NIGHT_FRACTION] once the Sun is well down. */
    fun solarScale(sunAltitudeDeg: Double): Double {
        val day = smoothstep(NIGHT_SUN_ALTITUDE_DEG, DAY_SUN_ALTITUDE_DEG, sunAltitudeDeg)
        return NIGHT_FRACTION + (1.0 - NIGHT_FRACTION) * day
    }

    /**
     * The ground's alpha for one view direction: [Ground.opacity] scaled by all three terms.
     *
     * @param viewAltitudeDeg the view direction's altitude above the horizon, degrees.
     * @param sunAltitudeDeg the Sun's altitude above the horizon, degrees.
     * @param opacity [Ground.opacity].
     */
    fun alpha(
        viewAltitudeDeg: Double,
        sunAltitudeDeg: Double,
        opacity: Double,
    ): Double =
        opacity *
            coverage(viewAltitudeDeg) *
            depthProfile(viewAltitudeDeg) *
            solarScale(sunAltitudeDeg)

    /**
     * GLSL's `smoothstep`, which Kotlin has no equivalent of. Handles `edge0 > edge1` (the
     * descending case [coverage] needs) because the division flips the sense before clamping.
     */
    private fun smoothstep(
        edge0: Double,
        edge1: Double,
        x: Double,
    ): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}
