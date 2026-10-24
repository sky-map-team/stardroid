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
import com.google.android.stardroid.render.api.EclipseShadow
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageQuad
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Terminator
import com.google.android.stardroid.render.api.Viewport
import com.google.android.stardroid.testing.assertThat
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillEllipseInRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIImage
import kotlin.math.tan
import kotlin.test.Test

/**
 * Images through the Metal backend: sized for the field of view, floored, culled, phased and
 * eclipsed. The image is the Android harness's synthetic planet — a white disc filling a 64 px
 * square, 1 px in from the edge — so a lit pixel reads 255 and anything else is shading.
 */
class MetalImageTest {
    private val device = metalDevice()
    private val disc = ImageRef("test/disc")
    private val camera = SkyCamera(Vector3.UNIT_Y, Vector3.UNIT_Z, fovDeg = 45.0)
    private val size = 400

    /** Pixels per unit of tangent across the short side: the projection's focal length. */
    private val focalPx = size / 2.0 / tan(22.5 * DEGREES_TO_RADIANS)

    private fun render(
        vararg images: ImagePrimitive,
        loader: (ImageRef) -> UIImage? = { if (it == disc) whiteDisc() else null },
    ): Frame {
        val renderer = MetalSkyRenderer(device, density = 1f, imageLoader = loader)
        renderer.setCamera(camera)
        renderer.submit(LayerId("images"), LayerScene(0, images = images.toList()))
        return renderOffscreen(device, renderer, size, size)
    }

    private fun image(
        angularSizeDeg: Double = 10.0,
        minSizeDp: Double = 0.0,
        visibleBelowFovDeg: Double? = null,
        terminator: Terminator? = null,
    ) = ImagePrimitive(
        center = Vector3.UNIT_Y,
        angularSizeDeg = angularSizeDeg,
        rotationDeg = 0.0,
        image = disc,
        terminator = terminator,
        minSizeDp = minSizeDp,
        visibleBelowFovDeg = visibleBelowFovDeg,
    )

    /** Width in pixels of the lit run through the middle row. */
    private fun litWidth(frame: Frame) = (0 until size).count { frame.green(it, size / 2) > 128 }

    @Test
    fun anImageIsDrawnAtItsAngularSize() {
        val frame = render(image(angularSizeDeg = 10.0))
        frame.savePng("image-10deg")
        // A 10° image spans 2 sin 5° of chord; the disc fills 62 of its 64 px.
        val expected = 2 * focalPx * kotlin.math.sin(5.0 * DEGREES_TO_RADIANS) * 62 / 64
        assertThat(litWidth(frame).toDouble()).isWithin(3.0).of(expected)
    }

    @Test
    fun aTinyImageIsFlooredToItsMinimumSize() {
        val frame = render(image(angularSizeDeg = 0.01, minSizeDp = 30.0))
        assertThat(litWidth(frame).toDouble()).isWithin(3.0).of(30.0 * 62 / 64)
    }

    @Test
    fun anImageAboveItsVisibleFovIsNotDrawn() {
        val frame = render(image(visibleBelowFovDeg = 30.0))
        assertThat(frame.count { r, g, b -> r + g + b > 0 }).isEqualTo(0)
    }

    @Test
    fun anUnavailableImageIsSkipped() {
        val frame = render(image(), loader = { null })
        assertThat(frame.count { r, g, b -> r + g + b > 0 }).isEqualTo(0)
    }

    @Test
    fun aHalfPhaseLightsTheSideItsLimbFaces() {
        val halfMoon = image(angularSizeDeg = 20.0, terminator = Terminator(0.5, 270.0))
        val frame = render(halfMoon)
        frame.savePng("image-half-phase")

        // Position angle 270° lights texture +x, which is the quad's +u axis; find which screen
        // side that is from the same geometry the renderer used, halfway to each limb.
        val axes = ImageQuad.halfAxes(halfMoon)
        val projection = SkyProjection(camera, Viewport(size, size, 1f))
        val litSide = projection.worldToScreen(halfMoon.center + axes.u * 0.5)!!
        val darkSide = projection.worldToScreen(halfMoon.center - axes.u * 0.5)!!
        assertThat(frame.green(litSide.xPx.toInt(), litSide.yPx.toInt())).isGreaterThan(240)
        // The shadowed side keeps its floor plus earthshine: (0.10 + 0.13 × 0.5) × 255 ≈ 42.
        assertThat(frame.green(darkSide.xPx.toInt(), darkSide.yPx.toInt()).toDouble())
            .isWithin(10.0)
            .of(42.0)
    }

    @Test
    fun theUmbraDarkensAndReddensTheMoon() {
        val eclipsed =
            image(
                angularSizeDeg = 20.0,
                terminator =
                    Terminator(
                        illuminatedFraction = 1.0,
                        brightLimbAngleDeg = 0.0,
                        eclipse = EclipseShadow(3.0, 4.0, offset = 0.0, directionDeg = 0.0),
                    ),
            )
        val frame = render(eclipsed)
        frame.savePng("image-total-eclipse")

        val r = frame.red(size / 2, size / 2)
        val g = frame.green(size / 2, size / 2)
        assertThat(r).isLessThan(128)
        assertThat(r).isGreaterThan(3 * g)
    }

    /** The Android harness's planet: a white disc 1 px in from the edges of a 64 px square. */
    @OptIn(ExperimentalForeignApi::class)
    private fun whiteDisc(): UIImage {
        val px = 64
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context =
            CGBitmapContextCreate(
                null,
                px.toULong(),
                px.toULong(),
                8u,
                (px * 4).toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            )
        CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
        CGContextFillEllipseInRect(context, CGRectMake(1.0, 1.0, px - 2.0, px - 2.0))
        val cgImage = CGBitmapContextCreateImage(context)
        val image = UIImage.imageWithCGImage(cgImage)
        CGImageRelease(cgImage)
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
        return image
    }
}
