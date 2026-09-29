/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import com.google.android.stardroid.render.api.GroundRamp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Asserts the numbers in `common.glsl` equal the Kotlin originals they were transcribed from.
 *
 * The conformance tests compare the shader's *functions* against `GroundRamp`, which is the right
 * guard for the algorithm but blind to the constants: they feed the shader an explicit `rampDeg`,
 * so the file's own `GROUND_EDGE_RAMP_MIN_DEG` is never read by any test. Two hand-kept copies of
 * a number with nothing comparing them is precisely the transcription drift this branch's
 * golden-reference arrangement exists to prevent — the constants had simply escaped it.
 *
 * A JVM test rather than an instrumented one: the shader sources are on disk, so this needs no
 * device and fails in `./gradlew check` where it will actually be seen.
 */
class ShaderConstantParityTest {
    private val source: String by lazy {
        val candidates =
            listOf(
                File("src/main/assets/shaders/common.glsl"),
                File("render/gles3/src/main/assets/shaders/common.glsl"),
                File("stardroid-v2/render/gles3/src/main/assets/shaders/common.glsl"),
            )
        val file =
            candidates.firstOrNull { it.isFile }
                ?: error(
                    "common.glsl not found; looked in ${candidates.map { it.absolutePath }}",
                )
        file.readText()
    }

    /** The value of `const float <name> = <literal>;`, which is the only form this file uses. */
    private fun glslConstant(name: String): Double {
        val match =
            Regex("""const\s+float\s+$name\s*=\s*([-+0-9.eE]+)\s*;""").find(source)
                ?: error("No `const float $name` in common.glsl")
        return match.groupValues[1].toDouble()
    }

    @Test
    fun `the ground ramp constants match their Kotlin originals`() {
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
            assertThat(glslConstant(name)).isWithin(1e-12).of(kotlinValue)
        }
    }

    @Test
    fun `the fragment stage is guaranteed full precision, which the ramp floor relies on`() {
        // EDGE_RAMP_MIN_DEG sits just above fp32 noise. At mediump it would be below half-float
        // resolution and the floor would mean nothing, so the precision qualifier is load-bearing
        // rather than boilerplate.
        val composed = ShaderProgram.compose("// common", "void main() {}", fragment = true)
        assertThat(composed).contains("precision highp float;")
    }
}
