/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.GlowPrimitive
import com.google.android.stardroid.render.api.GlowRing
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.testing.assertThat
import com.google.android.stardroid.testscene.TestScene
import platform.Metal.MTLPixelFormatBGRA8Unorm
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

/**
 * The Metal backend rendered offscreen on the simulator's GPU, and sampled. Each test pins one
 * thing the port could get wrong; every frame is also written to build/reports/metal-renders so
 * a person can look at what the assertions only sample.
 */
class MetalRenderTest {
    private val device = metalDevice()
    private val testCamera =
        SkyCamera(TestScene.CAMERA_LOS, TestScene.CAMERA_UP, TestScene.CAMERA_FOV_DEG)

    @Test
    fun everyPipelineCompiles() {
        // Throws, naming the program, if any shader or pipeline fails to build.
        MetalPipelines(device, MTLPixelFormatBGRA8Unorm)
    }

    @Test
    fun testSceneDrawsStarsAndGrid() {
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(testCamera)
        renderer.submit(TestScene.STARS_LAYER, TestScene.buildStarsScene())
        renderer.submit(TestScene.GRID_LAYER, TestScene.buildGridScene())
        val frame = renderOffscreen(device, renderer, 720, 1280)
        frame.savePng("testscene-stars-grid")

        val brightStars = frame.count { r, g, b -> r > 200 && g > 200 && b > 200 }
        val grid = frame.count { r, g, b -> b > 120 && b > r + 40 && b > g + 20 }
        assertThat(brightStars).isGreaterThan(50)
        // The equator, the central meridian and the ±30° parallels cross this view.
        assertThat(grid).isGreaterThan(2000)
    }

    @Test
    fun lineIsAsWideAsItsWidth() {
        // A white equator seen edge-on through the test camera: a horizontal line through the
        // middle of the frame. Its brightness summed down a column is its width in pixels, which
        // is what the mitered strip and its one-pixel feather have to add up to.
        val renderer = MetalSkyRenderer(device, density = 1f)
        renderer.setCamera(testCamera)
        renderer.submit(LayerId("line"), LayerScene(0, lines = listOf(equator(widthDp = 4.0))))
        val frame = renderOffscreen(device, renderer, 400, 400)
        frame.savePng("line-4dp")

        val column = 100
        val coverage = (150 until 250).sumOf { frame.red(column, it) } / 255.0
        assertThat(coverage).isWithin(0.5).of(4.0)
        val brightestRow = (150 until 250).maxBy { frame.red(column, it) }
        assertThat(brightestRow).isAtLeast(197)
        assertThat(brightestRow).isAtMost(202)
    }

    @Test
    fun nightModeDrawsOnlyRed() {
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(testCamera)
        renderer.setRenderState(RenderState(nightMode = true))
        renderer.submit(TestScene.STARS_LAYER, TestScene.buildStarsScene())
        renderer.submit(TestScene.GRID_LAYER, TestScene.buildGridScene())
        val frame = renderOffscreen(device, renderer, 360, 640)
        frame.savePng("testscene-night")

        assertThat(frame.count { _, g, b -> g > 0 || b > 0 }).isEqualTo(0)
        assertThat(frame.count { r, _, _ -> r > 50 }).isGreaterThan(100)
    }

    @Test
    fun magnitudeLimitHidesFaintStars() {
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(testCamera)
        renderer.submit(TestScene.STARS_LAYER, TestScene.buildStarsScene())
        val lit = { frame: Frame -> frame.count { r, g, b -> r + g + b > 30 } }

        val unlimited = lit(renderOffscreen(device, renderer, 360, 640))
        renderer.setRenderState(RenderState(magnitudeLimit = 2.0))
        val limited = lit(renderOffscreen(device, renderer, 360, 640))
        assertThat(limited).isGreaterThan(0)
        assertThat(limited).isLessThan(unlimited / 2)
    }

    @Test
    fun daytimeSkyIsBlueOverhead() {
        val renderer = MetalSkyRenderer(device, density = 2f)
        // Looking straight up, with the sun 45° above the horizon.
        renderer.setCamera(SkyCamera(Vector3.UNIT_Z, Vector3.UNIT_Y, fovDeg = 60.0))
        renderer.setRenderState(
            RenderState(
                skyGradient =
                    SkyGradient(
                        sunDirection = Vector3(1.0, 0.0, 1.0).normalized(),
                        zenithDirection = Vector3.UNIT_Z,
                    ),
            ),
        )
        val frame = renderOffscreen(device, renderer, 360, 640)
        frame.savePng("sky-day-zenith")

        assertThat(frame.blue(180, 320)).isGreaterThan(frame.red(180, 320) + 30)
    }

    @Test
    fun twilightGlowsWarmTowardTheSetSun() {
        val renderer = MetalSkyRenderer(device, density = 2f)
        // Sun 4° below the horizon due +X; looking toward it, 5° above the horizon.
        renderer.setCamera(SkyCamera(altAz(5.0), Vector3.UNIT_Z, fovDeg = 60.0))
        renderer.setRenderState(
            RenderState(
                skyGradient =
                    SkyGradient(sunDirection = altAz(-4.0), zenithDirection = Vector3.UNIT_Z),
            ),
        )
        val frame = renderOffscreen(device, renderer, 640, 360)
        frame.savePng("sky-twilight-west")

        assertThat(frame.red(320, 180)).isGreaterThan(frame.blue(320, 180))
    }

    @Test
    fun glowFillsTheBandBetweenItsRings() {
        val renderer = MetalSkyRenderer(device, density = 1f)
        renderer.setCamera(testCamera)
        val green = Rgba(0f, 0.8f, 0f, 1f)
        val glow = GlowPrimitive(listOf(GlowRing(ring(20.0), green), GlowRing(ring(5.0), green)))
        renderer.submit(LayerId("glow"), LayerScene(0, glows = listOf(glow)))
        val frame = renderOffscreen(device, renderer, 400, 400)
        frame.savePng("glow-annulus")

        // 12° right of centre is inside the band; the centre, inside the inner ring, is not.
        val inBand =
            200 +
                (
                    200 / kotlin.math.tan(22.5 * DEGREES_TO_RADIANS) *
                        kotlin.math.tan(12.0 * DEGREES_TO_RADIANS)
                ).toInt()
        assertThat(frame.green(inBand, 200)).isGreaterThan(150)
        assertThat(frame.green(200, 200)).isEqualTo(0)
    }

    /** The celestial equator, which the test camera (looking at RA 6h, Dec 0) sees edge-on. */
    private fun equator(widthDp: Double) =
        LinePrimitive(
            (0..36).map { i ->
                val ra = i * 10.0 * DEGREES_TO_RADIANS
                Vector3(cos(ra), sin(ra), 0.0)
            },
            Rgba.WHITE,
            widthDp,
        )

    /** A circle of [radiusDeg] around the test camera's line of sight, in the plane facing it. */
    private fun ring(radiusDeg: Double): List<Vector3> {
        val r = radiusDeg * DEGREES_TO_RADIANS
        return (0..36).map { i ->
            val a = i * 10.0 * DEGREES_TO_RADIANS
            Vector3(sin(r) * cos(a), cos(r), sin(r) * sin(a))
        }
    }

    /** The direction [altitudeDeg] above the horizon toward +X, for a zenith of +Z. */
    private fun altAz(altitudeDeg: Double): Vector3 {
        val alt = altitudeDeg * DEGREES_TO_RADIANS
        return Vector3(cos(alt), 0.0, sin(alt))
    }
}
