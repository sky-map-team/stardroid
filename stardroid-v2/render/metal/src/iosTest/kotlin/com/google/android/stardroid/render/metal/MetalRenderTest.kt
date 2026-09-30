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
import com.google.android.stardroid.render.api.Ground
import com.google.android.stardroid.render.api.GroundRamp
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
                        ground = testGround,
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
                    SkyGradient(
                        sunDirection = altAz(-4.0),
                        zenithDirection = Vector3.UNIT_Z,
                        ground = testGround,
                    ),
            ),
        )
        val frame = renderOffscreen(device, renderer, 640, 360)
        frame.savePng("sky-twilight-west")

        assertThat(frame.red(320, 180)).isGreaterThan(frame.blue(320, 180))
    }

    @Test
    fun theSkyEndsAtTheHorizon() {
        // No ground (opacity 0), so below the horizon there is nothing but black.
        val frame = renderDay(ground = testGround.copy(opacity = 0.0))
        frame.savePng("sky-ends-at-horizon")
        assertThat(frame.blue(320, 150)).isGreaterThan(100)
        assertThat(frame.red(320, 220) + frame.green(320, 220) + frame.blue(320, 220)).isAtMost(3)
    }

    @Test
    fun theGroundMatchesItsKotlinRamp() {
        // GroundRamp is the golden reference, as for GLES3's conformance test. Below the horizon
        // the sky writes black, so a ground pixel is its day colour times GroundRamp.alpha.
        val frame = renderDay(ground = testGround)
        frame.savePng("ground-day")
        val row = 235
        // The pixel centre's altitude: the camera is level, and its FOV spans the 360 px height.
        val ndcY = 1.0 - 2.0 * (row + 0.5) / 360.0
        val altitudeDeg =
            kotlin.math.atan(ndcY * kotlin.math.tan(30.0 * DEGREES_TO_RADIANS)) / DEGREES_TO_RADIANS
        val alpha = GroundRamp.alpha(altitudeDeg, testGround.opacity)
        assertThat(
            frame.red(320, row).toDouble(),
        ).isWithin(3.0).of(255 * testGround.dayColor.r * alpha)
        assertThat(
            frame.green(320, row).toDouble(),
        ).isWithin(3.0).of(255 * testGround.dayColor.g * alpha)
    }

    @Test
    fun theGroundCoversShallowLayersButNotTheHorizonLayer() {
        // Two white lines 10° below the horizon: one in an ordinary layer, which the ground washes
        // over, and one at GROUND_DEPTH, where the horizon layer lives, drawn on top of it.
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(SkyCamera(altAz(0.0), Vector3.UNIT_Z, fovDeg = 60.0))
        renderer.setRenderState(RenderState(skyGradient = daySky(testGround)))
        renderer.submit(LayerId("shallow"), LayerScene(10, lines = listOf(belowHorizonLine(-20.0))))
        renderer.submit(
            LayerId("horizon"),
            LayerScene(LayerScene.GROUND_DEPTH, lines = listOf(belowHorizonLine(20.0))),
        )
        val frame = renderOffscreen(device, renderer, 640, 360)
        frame.savePng("ground-depth-order")
        // Looking along +X with +Z up, screen-right is -Y: the shallow line (azimuth -20°) sits
        // right of centre, near x = 320 + 311.8 tan 20° ≈ 433, and the horizon-layer line left of
        // it, near x ≈ 207.
        val horizonColumn = 207
        val shallowColumn = 433
        assertThat(frame.red(horizonColumn, frame.brightestRow(horizonColumn))).isGreaterThan(250)
        assertThat(frame.red(shallowColumn, frame.brightestRow(shallowColumn))).isLessThan(235)
    }

    /** The test ground: the app's own palette (SkyColors), at the default opacity. */
    private val testGround =
        Ground(
            nightColor = Rgba(0x59 / 255f, 0x7c / 255f, 0x4a / 255f),
            dayColor = Rgba(0x9c / 255f, 0xb4 / 255f, 0x8a / 255f),
        )

    private fun daySky(ground: Ground) =
        SkyGradient(
            sunDirection = Vector3(1.0, 0.0, 1.0).normalized(),
            zenithDirection = Vector3.UNIT_Z,
            ground = ground,
        )

    /** A level view toward +X with the Sun 45° up ahead of it. */
    private fun renderDay(ground: Ground): Frame {
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(SkyCamera(altAz(0.0), Vector3.UNIT_Z, fovDeg = 60.0))
        renderer.setRenderState(RenderState(skyGradient = daySky(ground)))
        return renderOffscreen(device, renderer, 640, 360)
    }

    /** A short level line 10° below the horizon, centred [azimuthDeg] from +X toward +Y. */
    private fun belowHorizonLine(azimuthDeg: Double): LinePrimitive {
        val alt = -10.0 * DEGREES_TO_RADIANS
        return LinePrimitive(
            listOf(azimuthDeg - 3.0, azimuthDeg + 3.0).map { az ->
                val a = az * DEGREES_TO_RADIANS
                Vector3(cos(alt) * cos(a), cos(alt) * sin(a), sin(alt))
            },
            Rgba.WHITE,
            widthDp = 3.0,
        )
    }

    private fun Frame.brightestRow(column: Int): Int = (0 until height).maxBy { red(column, it) }

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

    /** The direction [altitudeDeg] above the horizon toward +X, for a zenith of +Z. */
    private fun altAz(altitudeDeg: Double): Vector3 {
        val alt = altitudeDeg * DEGREES_TO_RADIANS
        return Vector3(cos(alt), 0.0, sin(alt))
    }
}
