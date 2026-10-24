/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.render.api.GroundRamp
import com.google.android.stardroid.render.api.MoonShading
import com.google.android.stardroid.render.api.StellarRamps
import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

/**
 * The Metal shaders' tuning constants match their Kotlin originals in `:render:api`, the golden
 * references — the counterpart of `:render:gles3`'s ShaderConstantParityTest. Transcription
 * drift, not a wrong algorithm, is the realistic failure, and a constant changed on one side
 * only would stay invisible until someone looked at a sunset. Reads the exact source the
 * renderer compiles (MetalShaderSource), so it cannot check a stale copy.
 */
class MetalShaderConstantParityTest {
    /**
     * The value of `constant float <name> = <literal>;` or its function-local `const` form,
     * anchored to a line start so a commented-out declaration cannot match in place of the live
     * one.
     */
    private fun metalConstant(name: String): Double {
        val pattern =
            Regex("(?m)^\\s*(?:constant|const)\\s+float\\s+$name\\s*=\\s*([-+0-9.eE]+)\\s*;")
        val match =
            pattern.find(MetalShaderSource.SOURCE) ?: error("No `float $name` in the Metal source")
        return match.groupValues[1].toDouble()
    }

    @Test
    fun theGroundRampConstantsMatchTheirKotlinOriginals() {
        val expected =
            mapOf(
                "GROUND_EDGE_RAMP_DEG" to GroundRamp.EDGE_RAMP_DEG,
                "GROUND_EDGE_RAMP_MIN_DEG" to GroundRamp.EDGE_RAMP_MIN_DEG,
                "GROUND_DEPTH_SCALE_DEG" to GroundRamp.DEPTH_SCALE_DEG,
                "GROUND_NADIR_FRACTION" to GroundRamp.NADIR_FRACTION,
                "GROUND_NIGHT_SUN_ALTITUDE_DEG" to GroundRamp.NIGHT_SUN_ALTITUDE_DEG,
                "GROUND_DAY_SUN_ALTITUDE_DEG" to GroundRamp.DAY_SUN_ALTITUDE_DEG,
            )
        for ((name, kotlinValue) in expected) {
            assertThat(metalConstant(name)).isWithin(1e-12).of(kotlinValue)
        }
    }

    @Test
    fun theMagnitudeFadeConstantsMatchTheirKotlinOriginals() {
        assertThat(metalConstant("fadeRange"))
            .isWithin(1e-12)
            .of(StellarRamps.FADE_RANGE_MAGNITUDES)
        assertThat(metalConstant("faintAlphaFloor"))
            .isWithin(1e-6)
            .of(StellarRamps.FAINT_ALPHA_FLOOR.toDouble())
    }

    @Test
    fun theMoonShadingConstantsMatchTheirKotlinOriginals() {
        val expected =
            mapOf(
                "DARK_FLOOR" to MoonShading.DARK_FLOOR,
                "EARTHSHINE" to MoonShading.EARTHSHINE,
                "LIMB_RING" to MoonShading.LIMB_RING,
                "LIMB_RING_WIDTH" to MoonShading.LIMB_RING_WIDTH,
                "TERMINATOR_SOFTNESS" to MoonShading.TERMINATOR_SOFTNESS,
            )
        for ((name, kotlinValue) in expected) {
            assertThat(metalConstant(name)).isWithin(1e-12).of(kotlinValue)
        }
    }
}
