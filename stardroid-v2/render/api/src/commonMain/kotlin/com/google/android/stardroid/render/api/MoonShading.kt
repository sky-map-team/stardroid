/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * How the Moon's disc is shaded either side of the terminator — the tuning values, where
 * [PhaseGeometry] is the geometry.
 *
 * Here rather than in a backend because both backends need them and neither may depend on the
 * other: `:render:gles1` applies them per texel on the CPU in `PhaseCompositor`, and
 * `:render:gles3` transcribes them into `skyquad.frag` and evaluates them per fragment. They were
 * `private` in the GLES1 compositor and copied by hand into the shader, with nothing comparing
 * the two — the same unguarded duplication that `StellarRamps` and `GroundRamp` exist to prevent,
 * and the reason those moved here too. `ShaderConstantParityTest` now compares them.
 *
 * Values are fractions of the disc's lit brightness or, for the widths, of its radius.
 */
object MoonShading {
    /**
     * What the shadowed hemisphere keeps of its lit brightness. A true New Moon painted black is
     * invisible against a black sky and reads as the Moon having vanished, so the dark side stays
     * a dark grey sphere with its maria faintly legible (D88 §4.2).
     */
    const val DARK_FLOOR = 0.10

    /**
     * Peak earthshine, added across the shadowed side and scaled by `1 − illuminatedFraction`.
     * Physically real — sunlight off the Earth — and brightest exactly when it is needed most,
     * at the thin crescent and New.
     */
    const val EARTHSHINE = 0.13

    /**
     * Brightening applied in a thin ring around the whole limb, at every phase, so the disc's
     * extent stays locatable even when almost all of it is in shadow.
     */
    const val LIMB_RING = 0.22

    /** Width of that ring, as a fraction of the radius. */
    const val LIMB_RING_WIDTH = 0.04

    /** Width of the terminator's softening, as a fraction of the radius. */
    const val TERMINATOR_SOFTNESS = 0.012
}
