/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal.harness

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.render.metal.MetalSkyRenderer
import com.google.android.stardroid.testscene.TestImageRef
import com.google.android.stardroid.testscene.TestScene
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillEllipseInRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPointZero
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.MetalKit.MTKView
import platform.MetalKit.MTKViewDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIPinchGestureRecognizer
import platform.UIKit.UIScreen
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * The iOS renderer harness — the counterpart of Android's `RendererTestActivity` (D28/D31): the
 * Metal backend drawing the shared seeded [TestScene] in an `MTKView`, with the same camera, so the
 * two platforms can be compared side by side.
 *
 * It draws every test layer: stars, grid, planet and labels. Drag to look around, pinch to zoom,
 * tap to cycle the render state (night sky, daytime sky,
 * twilight, night mode). Swift only has to host [viewController], and must keep this object alive
 * as long as it shows it: the view's delegate and the gesture targets are weak references, held
 * here.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class RendererHarness {
    private val device = MTLCreateSystemDefaultDevice() ?: error("this device has no Metal GPU")
    private val renderer =
        MetalSkyRenderer(
            device,
            density = UIScreen.mainScreen.scale.toFloat(),
            imageLoader = { ref -> if (ref == TestImageRef.PLANET) syntheticPlanet() else null },
        )
    private val drawLoop = DrawLoop(renderer, device.newCommandQueue() ?: error("no command queue"))
    private val gestures = Gestures()

    private var lineOfSight = TestScene.CAMERA_LOS
    private var up = TestScene.CAMERA_UP
    private var fovDeg = TestScene.CAMERA_FOV_DEG
    private var mode = 0

    val viewController: UIViewController = UIViewController()

    init {
        val view = MTKView(CGRectZero.readValue(), device)
        view.colorPixelFormat = MTLPixelFormatBGRA8Unorm
        view.preferredFramesPerSecond = 60
        view.delegate = drawLoop
        view.addGestureRecognizer(UIPanGestureRecognizer(gestures, NSSelectorFromString("pan:")))
        view.addGestureRecognizer(
            UIPinchGestureRecognizer(gestures, NSSelectorFromString("pinch:")),
        )
        view.addGestureRecognizer(UITapGestureRecognizer(gestures, NSSelectorFromString("tap:")))
        viewController.view = view

        renderer.submit(TestScene.STARS_LAYER, TestScene.buildStarsScene())
        renderer.submit(TestScene.GRID_LAYER, TestScene.buildGridScene())
        renderer.submit(TestScene.IMAGES_LAYER, TestScene.buildImagesScene())
        renderer.submit(TestScene.LABELS_LAYER, TestScene.buildLabelsScene())
        publishCamera()
        publishMode()
    }

    private fun publishCamera() {
        renderer.setCamera(SkyCamera(lineOfSight, up, fovDeg))
    }

    private fun publishMode() {
        // The test camera looks along +Y with celestial north (+Z) up; the harness borrows that
        // as the observer's zenith, so the sun sits ahead of the default view.
        val zenith = Vector3.UNIT_Z
        renderer.setRenderState(
            when (mode) {
                1 -> RenderState(skyGradient = SkyGradient(sunAhead(altitudeDeg = 30.0), zenith))
                2 -> RenderState(skyGradient = SkyGradient(sunAhead(altitudeDeg = -4.0), zenith))
                3 -> RenderState(nightMode = true)
                else -> RenderState()
            },
        )
    }

    private fun sunAhead(altitudeDeg: Double): Vector3 {
        val alt = altitudeDeg * DEGREES_TO_RADIANS
        return Vector3(0.0, cos(alt), sin(alt))
    }

    /** Draws a frame whenever the view asks for one. */
    private class DrawLoop(
        private val renderer: MetalSkyRenderer,
        private val queue: MTLCommandQueueProtocol,
    ) : NSObject(), MTKViewDelegateProtocol {
        override fun mtkView(
            view: MTKView,
            drawableSizeWillChange: CValue<CGSize>,
        ) = Unit

        override fun drawInMTKView(view: MTKView) {
            val pass = view.currentRenderPassDescriptor ?: return
            val drawable = view.currentDrawable ?: return
            val commands = queue.commandBuffer() ?: return
            val (width, height) = view.drawableSize.useContents { width.toInt() to height.toInt() }
            renderer.encode(commands, pass, width, height)
            commands.presentDrawable(drawable)
            commands.commit()
        }
    }

    /** Target-action receivers for the view's gesture recognizers. */
    private inner class Gestures : NSObject() {
        @ObjCAction
        fun pan(recognizer: UIPanGestureRecognizer) {
            val view = recognizer.view ?: return
            val shortSidePt = view.bounds.useContents { minOf(size.width, size.height) }
            val (dx, dy) = recognizer.translationInView(view).useContents { x to y }
            recognizer.setTranslation(CGPointZero.readValue(), view)
            // The sky follows the finger: dragging across the short side sweeps one FOV (the FOV
            // spans the short side). Turning about `up` pans; turning about `right` tilts, and
            // dragging down tilts the view up.
            val degreesPerPoint = fovDeg / shortSidePt
            val right = (lineOfSight cross up).normalized()
            lineOfSight = rotate(lineOfSight, up, dx * degreesPerPoint)
            lineOfSight = rotate(lineOfSight, right, dy * degreesPerPoint)
            up = rotate(up, right, dy * degreesPerPoint)
            publishCamera()
        }

        @ObjCAction
        fun pinch(recognizer: UIPinchGestureRecognizer) {
            fovDeg = (fovDeg / recognizer.scale).coerceIn(MIN_FOV_DEG, MAX_FOV_DEG)
            recognizer.scale = 1.0
            publishCamera()
        }

        @ObjCAction
        fun tap(recognizer: UITapGestureRecognizer) {
            mode = (mode + 1) % MODE_COUNT
            publishMode()
        }
    }

    private companion object {
        /** Android's harness planet, drawn the same way: a white disc filling a 64 px square. */
        fun syntheticPlanet(): UIImage {
            val px = 64
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val rgba =
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
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
            CGContextFillEllipseInRect(context, CGRectMake(1.0, 1.0, px - 2.0, px - 2.0))
            val cgImage = CGBitmapContextCreateImage(context)
            val image = UIImage.imageWithCGImage(cgImage)
            CGImageRelease(cgImage)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
            return image
        }

        const val MIN_FOV_DEG = 1.0
        const val MAX_FOV_DEG = 120.0
        const val MODE_COUNT = 4

        /** Rotates [v] about the unit [axis] by [degrees] (Rodrigues' formula). */
        fun rotate(
            v: Vector3,
            axis: Vector3,
            degrees: Double,
        ): Vector3 {
            val a = degrees * DEGREES_TO_RADIANS
            val k = axis.normalized()
            return (v * cos(a) + (k cross v) * sin(a) + k * ((k dot v) * (1 - cos(a))))
                .normalized()
        }
    }
}
