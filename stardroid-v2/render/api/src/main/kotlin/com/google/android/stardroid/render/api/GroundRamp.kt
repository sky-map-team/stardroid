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
 * How the [Ground] is shaded: its opacity from where you are looking, its colour from where the
 * Sun is.
 *
 * Pure, so it is unit-testable without GL, and it is the **golden reference** for
 * `:render:gles3`'s `ground.frag`, which transcribes it via `common.glsl`. That arrangement is the
 * branch's standing answer to shaders escaping `./gradlew check` (see [SizeFloor] and
 * `StellarRamps`): the realistic failure mode is transcription drift, not a wrong algorithm.
 *
 * **The split between the two inputs is the design, not an implementation detail.** Opacity is a
 * function of view altitude alone — a mostly uniform wash that celestial objects show through,
 * denser near the horizon to suggest depth. Colour is a function of solar altitude alone — lighter
 * by day so the lower hemisphere does not read as a hole punched in a lit scene, darker by night.
 * Nothing here depends on azimuth or on anything the sky shader computes.
 *
 * That last point is load-bearing and was got wrong twice. The first attempt varied *opacity* with
 * the Sun, which cannot work: the ground composites over black, so its brightness is capped at
 * `opacity × colour`, and on device it measured 2.8× darker than the sky it met even near full
 * opacity — the dial did not reach. The tempting fix, letting a dimmed sky show through the ground
 * so it inherits the sky's light, is worse: it would make the ground warm in the west and neutral
 * in the east at sunset. The ground is the same substance all the way round.
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
     * How fast the density falls off with depth below the horizon, in degrees.
     *
     * This is the term that imparts distance. The eight-ring glow it replaces did the same thing
     * over about two degrees, which read as a band along the horizon rather than as ground
     * receding; doing it in a shader is what makes a longer falloff free, since it costs one `exp`
     * rather than a ring of geometry per stop.
     */
    const val DEPTH_SCALE_DEG = 12.0

    /**
     * The ground's opacity at the nadir, as a fraction of its opacity at the horizon.
     *
     * The brief pulls in two directions here — "a mostly uniform wash" and "denser near the
     * horizon to give an illusion of depth" — and this is the number that balances them. At 1.0
     * the ground is a flat panel with no depth at all, which is how the first version looked and
     * read as exactly that. Much below this and the wash stops being uniform and becomes a band
     * with a hole under it. The resulting horizon-to-nadir contrast is 1/this, so 2.5:1.
     */
    const val NADIR_FRACTION = 0.4

    /** Below this solar altitude the ground is at its night colour, in degrees. */
    const val NIGHT_SUN_ALTITUDE_DEG = -18.0

    /** At and above this solar altitude the ground is at its day colour, in degrees. */
    const val DAY_SUN_ALTITUDE_DEG = 0.0

    /**
     * Zero above the horizon, one below it, with [EDGE_RAMP_DEG] of smoothing so the boundary
     * antialiases instead of stair-stepping along the pixel grid.
     */
    fun coverage(viewAltitudeDeg: Double): Double = smoothstep(0.0, -EDGE_RAMP_DEG, viewAltitudeDeg)

    /**
     * The depth cue: 1 at the horizon, decaying to [NADIR_FRACTION] below it. Uses the absolute
     * altitude so it stays symmetric about the horizon, which matters only inside the edge ramp
     * but keeps the function continuous there.
     */
    fun depthProfile(viewAltitudeDeg: Double): Double =
        NADIR_FRACTION + (1.0 - NADIR_FRACTION) * exp(-abs(viewAltitudeDeg) / DEPTH_SCALE_DEG)

    /**
     * How daylit the ground is: 1 with the Sun up, 0 once it is well below the horizon. The only
     * thing the Sun controls, and it controls only [Ground]'s colour.
     *
     * Anchored to astronomical twilight rather than to sunset so it tracks the sky's own darkening
     * — the sky is still visibly lit at -12°, and a ground that had already gone fully dark by
     * then would be a hole again, just at dusk instead of at noon.
     */
    fun daylight(sunAltitudeDeg: Double): Double =
        smoothstep(NIGHT_SUN_ALTITUDE_DEG, DAY_SUN_ALTITUDE_DEG, sunAltitudeDeg)

    /**
     * The ground's alpha for one view direction. Depends on where you are looking and nothing
     * else — see the class note on why the Sun is deliberately absent here.
     *
     * @param viewAltitudeDeg the view direction's altitude above the horizon, degrees.
     * @param opacity [Ground.opacity].
     */
    fun alpha(
        viewAltitudeDeg: Double,
        opacity: Double,
    ): Double = opacity * coverage(viewAltitudeDeg) * depthProfile(viewAltitudeDeg)

    /**
     * GLSL's `smoothstep`, which Kotlin has no equivalent of. Handles `edge0 > edge1` (the
     * descending case [coverage] needs) because the division flips the sense before clamping —
     * GLSL leaves that case undefined, so the transcription uses the complement form instead.
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
