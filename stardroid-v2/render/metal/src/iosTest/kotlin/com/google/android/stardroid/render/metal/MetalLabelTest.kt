/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.AtlasCell
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.PlacedText
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.testing.assertThat
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIImage
import kotlin.math.abs
import kotlin.test.Test

/**
 * Labels and icons through the Metal backend, with label fades made instant so one frame shows
 * the declutterer's decision. The test camera looks along +Y with +Z up, so an anchor at +Y is the
 * frame's centre, (200, 200) in a 400 px frame.
 */
class MetalLabelTest {
    private val device = metalDevice()
    private val camera = SkyCamera(Vector3.UNIT_Y, Vector3.UNIT_Z, fovDeg = 45.0)
    private val size = 400
    private val square = ImageRef("icon/square")

    private fun render(
        scene: LayerScene,
        state: RenderState = RenderState(),
        density: Float = 2f,
    ): Frame {
        val renderer =
            MetalSkyRenderer(
                device,
                density = density,
                imageLoader = { if (it == square) whiteSquare() else null },
                labelFadeMillis = 0,
            )
        renderer.setCamera(camera)
        renderer.setRenderState(state)
        renderer.submit(LayerId("labels"), scene)
        return renderOffscreen(device, renderer, size, size)
    }

    private fun label(
        text: String = "Sirius",
        hasDetail: Boolean = false,
    ) = LabelPrimitive(
        Vector3.UNIT_Y,
        text,
        LabelStyle(LabelSize.STANDARD, Rgba.WHITE),
        priority = 1,
        hasDetail = hasDetail,
    )

    private val bright = { r: Int, g: Int, b: Int -> r + g + b > 300 }

    @Test
    fun theRasterizerKeepsCoverageInsideEachCell() {
        val rasterizer = UIKitGlyphRasterizer()
        val metrics = rasterizer.measure("Sirius", 20f)
        assertThat(metrics.widthPx).isGreaterThan(30)
        assertThat(metrics.ascentPx).isGreaterThan(10)
        assertThat(metrics.descentPx).isGreaterThan(2)

        val cell = AtlasCell(page = 0, u = 10, v = 5, w = metrics.widthPx, h = metrics.heightPx)
        val page =
            rasterizer.rasterizePage(
                128,
                64,
                listOf(PlacedText("Sirius", 20f, cell, metrics.ascentPx)),
            )
        var inside = 0
        var outside = 0
        for (y in 0 until 64) {
            for (x in 0 until 128) {
                if (page[y * 128 + x].toInt() == 0) continue
                val inCell = x in cell.u until cell.u + cell.w && y in cell.v until cell.v + cell.h
                if (inCell) inside++ else outside++
            }
        }
        assertThat(inside).isGreaterThan(50)
        assertThat(outside).isEqualTo(0)
    }

    @Test
    fun aLabelHangsBelowItsAnchorCentredOnIt() {
        val frame = render(LayerScene(0, labels = listOf(label())))
        frame.savePng("label-sirius")
        val box = frame.bounds(bright)!!
        // The 4 dp gap at density 2 is 8 px to the cell's top; the letters start a little lower.
        assertThat(box.top).isAtLeast(208)
        assertThat(box.top).isAtMost(216)
        assertThat(abs((box.left + box.right) / 2 - 200)).isAtMost(3)
    }

    @Test
    fun aLabelWithAnInfoCardIsUnderlined() {
        val plain = render(LayerScene(0, labels = listOf(label())))
        val carded = render(LayerScene(0, labels = listOf(label(hasDetail = true))))
        carded.savePng("label-sirius-carded")
        val plainBox = plain.bounds(bright)!!
        val cardedBox = carded.bounds(bright)!!
        // The rule sits under the cell, so the lit area reaches further down.
        assertThat(cardedBox.bottom).isGreaterThan(plainBox.bottom + 1)
    }

    @Test
    fun theFontSizePreferenceScalesLabels() {
        val normal = render(LayerScene(0, labels = listOf(label()))).bounds(bright)!!
        val large =
            render(LayerScene(0, labels = listOf(label())), RenderState(labelScaleFactor = 2.0))
                .bounds(bright)!!
        assertThat(large.width.toDouble() / normal.width).isWithin(0.2).of(2.0)
    }

    @Test
    fun nightModeTurnsLabelsRed() {
        val frame = render(LayerScene(0, labels = listOf(label())), RenderState(nightMode = true))
        assertThat(frame.count { _, g, b -> g > 0 || b > 0 }).isEqualTo(0)
        assertThat(frame.count { r, _, _ -> r > 100 }).isGreaterThan(20)
    }

    @Test
    fun theHaloDarkensABrightSkyAroundText() {
        // Looking straight up (the zenith is the camera's line of sight) with the sun 45° from it,
        // so the whole frame is bright daytime sky.
        val daySky =
            RenderState(
                skyGradient = SkyGradient(Vector3(0.0, 1.0, 1.0).normalized(), Vector3.UNIT_Y),
            )
        val without = render(LayerScene(0), daySky)
        val with = render(LayerScene(0, labels = listOf(label())), daySky)
        with.savePng("label-halo-day")
        // Relative to the sky itself: the outline is near-black at 0.85 alpha, times the 0.7 a
        // label without an info card is drawn at, so it pulls the sky under it well below the
        // darkest sky pixel.
        var skyFloor = Int.MAX_VALUE
        for (y in 0 until size) {
            for (x in 0 until size) {
                val sum = without.red(x, y) + without.green(x, y) + without.blue(x, y)
                skyFloor = minOf(skyFloor, sum)
            }
        }
        val dark = { r: Int, g: Int, b: Int -> r + g + b < skyFloor * 0.6 }
        assertThat(skyFloor).isGreaterThan(200)
        assertThat(with.count(dark)).isGreaterThan(40)
    }

    @Test
    fun anIconIsDrawnAtItsDpSizeInItsTint() {
        val icon =
            PointPrimitive(Vector3.UNIT_Y, PointAppearance.Icon(square, 20.0, Rgba(0f, 1f, 0f)))
        val frame = render(LayerScene(0, points = listOf(icon)), density = 1f)
        frame.savePng("icon-green-square")
        val box = frame.bounds { r, g, b -> g > 200 && r < 30 && b < 30 }!!
        assertThat(box.width.toDouble()).isWithin(2.0).of(20.0)
        assertThat(box.height.toDouble()).isWithin(2.0).of(20.0)
    }

    @Test
    fun anIconWithNoImageIsSkipped() {
        val icon = PointPrimitive(Vector3.UNIT_Y, PointAppearance.Icon(ImageRef("missing"), 20.0))
        val frame = render(LayerScene(0, points = listOf(icon)))
        assertThat(frame.count { r, g, b -> r + g + b > 0 }).isEqualTo(0)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun whiteSquare(): UIImage {
        val px = 16
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val rgba = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
        val context =
            CGBitmapContextCreate(
                null,
                px.toULong(),
                px.toULong(),
                8u,
                (px * 4).toULong(),
                colorSpace,
                rgba,
            )
        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, px.toDouble(), px.toDouble()))
        val cgImage = CGBitmapContextCreateImage(context)
        val image = UIImage.imageWithCGImage(cgImage)
        CGImageRelease(cgImage)
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
        return image
    }
}
