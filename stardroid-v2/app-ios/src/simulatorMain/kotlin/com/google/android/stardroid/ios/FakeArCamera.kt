/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.ImageBitmap
import com.google.android.stardroid.camera.arLenses
import com.google.android.stardroid.camera.cameraShortHalfTan
import com.google.android.stardroid.ui.map.ArCameraSpecs
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import kotlin.math.PI
import kotlin.random.Random

/**
 * The simulator's camera (camera-ar-launch.md §4.6): a painted dusk scene on a dual-wide's
 * specs, so the layering, scrim, zoom lock and share can be driven without hardware. The ring at
 * the centre is there to check that the layers and crops line up.
 */
@OptIn(ExperimentalForeignApi::class)
internal class FakeArCamera : IosArCamera {
    override val hasCamera: Boolean = true
    override val isAuthorized: Boolean = true

    private var target: ArPreviewTarget? = null
    private var onReady: ((ArCameraSpecs) -> Unit)? = null
    private var preview: UIImageView? = null
    private var laidOut: Triple<Double, Double, Boolean>? = null
    private var reportedShape: PreviewShape? = null
    private var zoom = 1.0

    override fun requestAccess(onResult: (granted: Boolean) -> Unit) = onResult(true)

    override fun bind(
        target: ArPreviewTarget,
        onPreviewLuma: (Double) -> Unit,
        onReady: (ArCameraSpecs) -> Unit,
    ) {
        unbind()
        this.target = target
        this.onReady = onReady
        val view = UIImageView(frame = CGRectZero.readValue())
        view.userInteractionEnabled = false
        view.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
        target.container.insertSubview(view, atIndex = 0)
        preview = view
        layoutChanged()
        onPreviewLuma(PREVIEW_LUMA)
    }

    override fun unbind() {
        preview?.removeFromSuperview()
        preview = null
        target = null
        onReady = null
        laidOut = null
        reportedShape = null
        zoom = 1.0
    }

    override fun setActive(active: Boolean) {
        preview?.setHidden(!active)
    }

    override fun layoutChanged() {
        val target = target ?: return
        val view = preview ?: return
        val (width, height) = target.container.sizeInPoints()
        if (width <= 0.0 || height <= 0.0) return
        val landscape = target.rotation().isLandscape
        val layout = Triple(width, height, landscape)
        if (layout == laidOut) return
        laidOut = layout

        view.transform = CGAffineTransformMakeScale(1.0, 1.0)
        view.setFrame(CGRectMake(0.0, 0.0, width, height))
        val (imageWidth, imageHeight) = imageSize(landscape)
        view.image =
            drawUIImage(imageWidth, imageHeight) { paintScene(imageWidth, imageHeight, 1.0) }
        applyZoom()
        val shape = PreviewShape.of(width, height, landscape)
        if (shape == reportedShape) return
        reportedShape = shape

        fun halfTan(horizontalFovDeg: Double) =
            cameraShortHalfTan(
                horizontalFovDeg,
                FORMAT_LONG,
                FORMAT_SHORT,
                shape.shortSide,
                shape.longSide,
                sensorWidthAlongViewWidth = shape.crossways,
            )
        val lenses =
            arLenses(
                listOf(MAIN_FACTOR),
                listOf(halfTan(ULTRA_WIDE_FOV_DEG), halfTan(WIDE_FOV_DEG)),
                MAIN_FACTOR,
            )
        onReady?.invoke(lockedSpecs(lenses, MIN_ZOOM, MAX_ZOOM, -EXPOSURE_STEPS, EXPOSURE_STEPS))
    }

    override fun setZoomRatio(ratio: Double) {
        zoom = ratio
        applyZoom()
    }

    // The painted scene is the widest lens's view.
    private fun applyZoom() {
        val scale = zoom / MIN_ZOOM
        preview?.transform = CGAffineTransformMakeScale(scale, scale)
    }

    override fun setExposureIndex(index: Int) = Unit

    override suspend fun takeStill(): ImageBitmap? {
        val target = target ?: return null
        val (width, height) = imageSize(target.rotation().isLandscape)
        return drawImageBitmap(width, height) { paintScene(width, height, zoom / MIN_ZOOM) }
    }

    private fun imageSize(landscape: Boolean): Pair<Int, Int> =
        if (landscape) FORMAT_LONG to FORMAT_SHORT else FORMAT_SHORT to FORMAT_LONG

    private fun paintScene(
        width: Int,
        height: Int,
        magnification: Double,
    ) {
        fun x(fraction: Double) = width * (0.5 + (fraction - 0.5) * magnification)

        fun y(fraction: Double) = height * (0.5 + (fraction - 0.5) * magnification)

        fun fill(
            color: UIColor,
            path: UIBezierPath,
        ) {
            color.setFill()
            path.fill()
        }

        fun band(
            top: Double,
            bottom: Double,
        ) = UIBezierPath.bezierPathWithRect(
            CGRectMake(x(0.0), y(top), x(1.0) - x(0.0), y(bottom) - y(top)),
        )

        fill(rgb(0.10, 0.16, 0.34), band(0.0, 1.0))
        fill(rgb(0.22, 0.26, 0.42), band(0.55, 0.66))
        fill(rgb(0.10, 0.22, 0.12), band(0.66, 1.0))
        val random = Random(STAR_SEED)
        val radius = width * 0.002 * magnification
        repeat(STAR_COUNT) {
            val left = x(random.nextDouble()) - radius
            val top = y(random.nextDouble() * 0.6) - radius
            val rect = CGRectMake(left, top, 2 * radius, 2 * radius)
            fill(UIColor.whiteColor, UIBezierPath.bezierPathWithOvalInRect(rect))
        }
        val ring =
            UIBezierPath.bezierPathWithArcCenter(
                center = CGPointMake(width / 2.0, height / 2.0),
                radius = width * 0.04 * magnification,
                startAngle = 0.0,
                endAngle = 2 * PI,
                clockwise = true,
            )
        rgb(1.0, 0.1, 0.8).setStroke()
        ring.lineWidth = width * 0.006 * magnification
        ring.stroke()
    }

    private fun rgb(
        red: Double,
        green: Double,
        blue: Double,
    ) = UIColor(red = red, green = green, blue = blue, alpha = 1.0)

    private companion object {
        const val FORMAT_LONG = 2016
        const val FORMAT_SHORT = 1512

        // An iPhone 13's, nominally.
        const val ULTRA_WIDE_FOV_DEG = 106.0
        const val WIDE_FOV_DEG = 69.4
        const val MAIN_FACTOR = 2.0
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 8.0
        const val EXPOSURE_STEPS = 6

        /** Dark enough that the automatic dimmer stays off. */
        const val PREVIEW_LUMA = 0.15
        const val STAR_SEED = 13
        const val STAR_COUNT = 90
    }
}
