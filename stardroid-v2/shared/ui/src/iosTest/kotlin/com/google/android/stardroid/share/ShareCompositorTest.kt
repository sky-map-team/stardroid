/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareCompositorTest {
    private val compositor =
        ShareCompositor(
            TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, 0),
        )
    private val branding =
        ShareBranding(null, "Sky Map", "The Universe in your pocket", "https://getskymap.app")

    private fun filled(
        width: Int,
        height: Int,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
    ): ImageBitmap {
        val pixel = byteArrayOf(r.toByte(), g.toByte(), b.toByte(), a.toByte())
        val pixels = ByteArray(width * height * 4) { pixel[it % 4] }
        return imageBitmapOfPixels(pixels, width, height, bgra = false)!!
    }

    private fun ImageBitmap.rgbAt(
        x: Int,
        y: Int,
    ): List<Int> {
        val color = toPixelMap()[x, y]
        return listOf(color.red, color.green, color.blue).map { (it * 255 + 0.5f).toInt() }
    }

    private fun assertNear(
        expected: List<Int>,
        actual: List<Int>,
    ) {
        val near = expected.zip(actual).all { (e, a) -> abs(e - a) <= 2 }
        assertTrue(near, "expected $expected, was $actual")
    }

    private fun overlayPixel(
        map: ImageBitmap,
        camera: ImageBitmap,
        dim: Double = 0.0,
    ) = runBlocking { compositor.arLayouts(map, camera, dim, branding).overlay.rgbAt(100, 100) }

    @Test
    fun aClearMapShowsTheCamera() {
        val pixel = overlayPixel(filled(300, 600, 0, 0, 0, 0), filled(400, 300, 80, 120, 160, 255))
        assertNear(listOf(80, 120, 160), pixel)
    }

    @Test
    fun theDimmerDarkensTheCamera() {
        val pixel =
            overlayPixel(filled(300, 600, 0, 0, 0, 0), filled(400, 300, 80, 120, 160, 255), 0.5)
        assertNear(listOf(40, 60, 80), pixel)
    }

    // The renderers blend alpha with the colour's factors, so a half-covered white pixel is
    // stored with colour 128 over alpha 64, which is not valid premultiplied colour.
    @Test
    fun theMapsOwnAlphaCompositesAsTheScreenDoes() {
        val pixel =
            overlayPixel(filled(300, 600, 128, 128, 128, 64), filled(400, 300, 40, 40, 40, 255))
        assertNear(listOf(158, 158, 158), pixel)
    }

    @Test
    fun aBrightPixelOverABrightCameraSaturatesRatherThanWraps() {
        val pixel =
            overlayPixel(filled(300, 600, 200, 200, 200, 64), filled(400, 300, 240, 240, 240, 255))
        assertNear(listOf(255, 255, 255), pixel)
    }

    @Test
    fun sideBySidePutsTheMapOnBlackAtTheRight() =
        runBlocking {
            val map = filled(300, 600, 0, 0, 0, 0)
            val camera = filled(400, 300, 80, 120, 160, 255)
            val layouts = compositor.arLayouts(map, camera, 0.0, branding)

            assertEquals(602, layouts.sideBySide.width)
            assertNear(listOf(80, 120, 160), layouts.sideBySide.rgbAt(100, 100))
            assertNear(listOf(0, 0, 0), layouts.sideBySide.rgbAt(450, 100))
        }

    @Test
    fun everyLayoutEndsInTheBrandStrip() =
        runBlocking {
            val image = compositor.mapOnly(filled(300, 600, 0, 0, 0, 0), branding)

            assertEquals(600 + 48, image.height)
            assertNear(listOf(0x13, 0x17, 0x20), image.rgbAt(297, 645))
            val strip = image.toPixelMap()
            val goldPixels =
                (600 until 630).sumOf { y ->
                    (0 until 150).count { x -> strip[x, y].red > 0.6f && strip[x, y].blue < 0.5f }
                }
            assertTrue(goldPixels > 10, "no title drawn")
        }

    // Arabic, Persian and Hebrew read from the right, so the first phrase sits rightmost. Glyphs
    // land a pixel apart between the two directions, so this compares against both orders.
    @Test
    fun aRightToLeftInvitationKeepsItsWordOrder() =
        runBlocking {
            suspend fun footerOf(invitation: String): IntArray {
                val text = ShareBranding(null, "Sky Map", invitation, "https://getskymap.app")
                val image = compositor.mapOnly(filled(600, 600, 0, 0, 0, 0), text)
                return image.toPixelMap(startY = 600, height = image.height - 600).buffer
            }

            fun distance(
                a: IntArray,
                b: IntArray,
            ): Long = a.indices.sumOf { abs((a[it] and 0xFF) - (b[it] and 0xFF)).toLong() }

            val hebrew = "\u05d0\u05d1\u05d2\u05d3\u05d4\u05d5"
            val shown = footerOf("$hebrew abc")
            val firstPhraseRightmost = footerOf("abc $hebrew")
            // A left-to-right mark in front forces the wrong direction.
            val reversed = footerOf("\u200e$hebrew abc")
            assertTrue(
                distance(shown, firstPhraseRightmost) < distance(shown, reversed),
                "the invitation's phrases are in left-to-right order",
            )
        }

    @Test
    fun anEncodedPngHasNoMetadataChunks() {
        val png = filled(40, 40, 10, 20, 30, 255).encodePng()!!
        val chunks = mutableListOf<String>()
        var at = 8
        while (at + 8 <= png.size) {
            val length = (0 until 4).fold(0) { n, i -> (n shl 8) or (png[at + i].toInt() and 0xFF) }
            chunks += png.decodeToString(at + 4, at + 8)
            at += 12 + length
        }
        assertContentEquals(emptyList(), chunks.filter { it in METADATA_CHUNKS })
    }

    private companion object {
        val METADATA_CHUNKS = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")
    }
}
