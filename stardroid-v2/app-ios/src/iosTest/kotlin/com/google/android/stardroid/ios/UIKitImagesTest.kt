/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.ui.graphics.toPixelMap
import com.google.android.stardroid.testing.assertThat
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import kotlin.test.Test

/** The camera still's path to pixels: a sensor-landscape photo, upright by its orientation tag. */
@OptIn(ExperimentalForeignApi::class)
class UIKitImagesTest {
    // 400 × 300, its left half red and its right half blue.
    private fun sensorImage(): UIImage =
        drawUIImage(400, 300) {
            UIColor.redColor.setFill()
            UIBezierPath.bezierPathWithRect(CGRectMake(0.0, 0.0, 200.0, 300.0)).fill()
            UIColor.blueColor.setFill()
            UIBezierPath.bezierPathWithRect(CGRectMake(200.0, 0.0, 200.0, 300.0)).fill()
        }!!

    @Test
    fun aPortraitPhotoComesOutUpright() {
        val photo =
            UIImage.imageWithCGImage(
                sensorImage().CGImage,
                scale = 1.0,
                orientation = UIImageOrientation.UIImageOrientationRight,
            )
        val still = photo.toImageBitmap(maxEdge = 2048)!!

        assertThat(still.width).isEqualTo(300)
        assertThat(still.height).isEqualTo(400)
        // Turned clockwise, the sensor's left is the top.
        val pixels = still.toPixelMap()
        assertThat(pixels[150, 50].red > 0.9f).isTrue()
        assertThat(pixels[150, 350].blue > 0.9f).isTrue()
    }

    @Test
    fun aStillIsScaledToShareSize() {
        val still = sensorImage().toImageBitmap(maxEdge = 200)!!

        assertThat(still.width).isEqualTo(200)
        assertThat(still.height).isEqualTo(150)
    }

    @Test
    fun aTurnKeepsThePreviewsShape() {
        val portrait = PreviewShape.of(390.0, 844.0, landscape = false)
        val landscape = PreviewShape.of(844.0, 390.0, landscape = true)
        val tallWindowSideways = PreviewShape.of(390.0, 844.0, landscape = true)

        assertThat(landscape).isEqualTo(portrait)
        assertThat(tallWindowSideways == portrait).isFalse()
    }
}
