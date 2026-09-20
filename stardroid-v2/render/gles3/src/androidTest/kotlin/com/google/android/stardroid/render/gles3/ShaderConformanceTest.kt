/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.opengl.GLES30
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.stardroid.render.api.EclipseGeometry
import com.google.android.stardroid.render.api.EclipseShadow
import com.google.android.stardroid.render.api.PhaseGeometry
import com.google.android.stardroid.render.api.StellarRamps
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The GLSL copies of the pure Kotlin functions agree with the Kotlin.
 *
 * Moving logic into shaders moves it out of `./gradlew check`, and that is the real cost of this
 * port. The mitigation is that nothing is *rewritten* in GLSL — it is transcribed, with the
 * Kotlin kept as the golden reference — and that this test renders the GLSL version through a
 * real driver and compares. The realistic failure mode is not a wrong algorithm but drift: a
 * constant changed on one side and not the other, months apart.
 *
 * Each function is evaluated across a strip of inputs written into `gl_FragCoord.x`, read back
 * with `glReadPixels`, and compared to the Kotlin at the same inputs.
 */
@RunWith(AndroidJUnit4::class)
class ShaderConformanceTest {
    private var gl: OffscreenGlContext? = null
    private var fbo = 0
    private var texture = 0

    @Before
    fun setUp() {
        gl = OffscreenGlContext.createOrNull()
        assumeTrue("No GL ES 3.0 context available on this device", gl != null)
        texture = genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            SAMPLES,
            1,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null,
        )
        val names = IntArray(1)
        GLES30.glGenFramebuffers(1, names, 0)
        fbo = names[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texture,
            0,
        )
        GLES30.glViewport(0, 0, SAMPLES, 1)
    }

    @After
    fun tearDown() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (texture != 0) deleteTextures(intArrayOf(texture))
        gl?.release()
        gl = null
    }

    /**
     * Renders [body] — a GLSL expression producing a `vec3`, with `t` in `[0, 1)` in scope —
     * across [SAMPLES] pixels and returns the three channels of each.
     */
    private fun evaluate(body: String): Array<FloatArray> {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val common = assets.open(ShaderProgram.COMMON_ASSET).use { String(it.readBytes()) }
        val vertex =
            """
            out vec2 vNdc;
            void main() {
                vec2 corner = unitQuadCorner(gl_VertexID);
                vNdc = corner * 2.0 - 1.0;
                gl_Position = vec4(vNdc, 0.0, 1.0);
            }
            """.trimIndent()
        val fragment =
            """
            out vec4 fragColor;
            void main() {
                float t = (gl_FragCoord.x - 0.5) / float($SAMPLES - 1);
                fragColor = vec4($body, 1.0);
            }
            """.trimIndent()
        val program =
            ShaderProgram.fromSource(
                "conformance",
                ShaderProgram.compose(common, vertex, fragment = false),
                ShaderProgram.compose(common, fragment, fragment = true),
            )
        program.use()
        GLES30.glBindVertexArray(Mesh.genVertexArray())
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        val pixels = ByteBuffer.allocateDirect(SAMPLES * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(
            0,
            0,
            SAMPLES,
            1,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            pixels,
        )
        program.release()
        pixels.position(0)
        return Array(SAMPLES) {
            floatArrayOf(
                (pixels.get().toInt() and 0xFF) / 255f,
                (pixels.get().toInt() and 0xFF) / 255f,
                (pixels.get().toInt() and 0xFF) / 255f,
            ).also { pixels.get() }
        }
    }

    /** The input value at sample [i], mapping `t` in `[0, 1]` onto `[min, max]`. */
    private fun inputAt(
        i: Int,
        min: Double,
        max: Double,
    ): Double = min + (max - min) * i / (SAMPLES - 1).toDouble()

    @Test
    fun magnitudeAlphaMatchesStellarRamps() {
        // Across the whole fade window and well past both ends of it.
        val min = -1.0
        val max = 2.0
        val limit = 1.0
        val actual =
            evaluate("vec3(magnitudeAlpha(mix(${min}, ${max}, t), ${limit}))")
        for (i in 0 until SAMPLES) {
            val expected = StellarRamps.magnitudeAlpha(inputAt(i, min, max), limit)
            assertThat(actual[i][0]).isWithin(TOLERANCE).of(expected)
        }
    }

    @Test
    fun litOffsetMatchesPhaseGeometry() {
        // A horizontal cut across the disc at y = 0.3, at a gibbous phase. The output is signed,
        // so it is mapped into [0, 1] for an 8-bit readback and mapped back for comparison.
        val fraction = 0.72
        val y = 0.3
        val actual =
            evaluate("vec3(litOffset(mix(-1.0, 1.0, t), ${y}, ${fraction}) * 0.25 + 0.5)")
        for (i in 0 until SAMPLES) {
            val x = inputAt(i, -1.0, 1.0)
            val expected = PhaseGeometry.litOffset(x, y, fraction)
            assertThat((actual[i][0] - 0.5f) * 4f).isWithin(TOLERANCE * 4f).of(expected.toFloat())
        }
    }

    @Test
    fun eclipseTintMatchesEclipseGeometry() {
        // A cut straight through a shadow centred on the disc, crossing the umbra, the penumbra
        // and the unshadowed edge — every branch of the function in one strip.
        val shadow =
            EclipseShadow(
                umbraRadius = 0.4,
                penumbraRadius = 0.9,
                offset = 0.0,
                directionDeg = 0.0,
            )
        val actual =
            evaluate(
                "eclipseTint(vec2(mix(-1.0, 1.0, t), 0.0), " +
                    "${shadow.umbraRadius}, ${shadow.penumbraRadius}, vec2(0.0))",
            )
        for (i in 0 until SAMPLES) {
            val expected = EclipseGeometry.tint(inputAt(i, -1.0, 1.0), 0.0, shadow)
            assertThat(actual[i][0]).isWithin(TOLERANCE).of(expected.red.toFloat())
            assertThat(actual[i][1]).isWithin(TOLERANCE).of(expected.green.toFloat())
            assertThat(actual[i][2]).isWithin(TOLERANCE).of(expected.blue.toFloat())
        }
    }

    private companion object {
        const val SAMPLES = 64

        /**
         * Two 8-bit steps. The readback is `RGBA8`, so a exact match is not available; this is
         * tight enough to catch a changed constant and loose enough to survive rounding and
         * `mediump`-vs-`highp` differences between drivers.
         */
        const val TOLERANCE = 2f / 255f
    }
}
