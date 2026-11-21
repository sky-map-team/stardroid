/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.testing.assertThat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

class ArStampTest {
    private val pointing =
        ArStampPointing(
            epochMillis = 1_795_222_920_000L,
            skyEpochMillis = 1_795_222_920_000L,
            raDeg = 88.8,
            decDeg = 7.4,
            azDeg = 123.44,
            altDeg = 45.6,
            rollDeg = 2.1,
            sensorFrame = true,
            declinationDeg = -0.9,
            magneticCorrection = true,
            correctionAzDeg = 0.0,
            correctionAltDeg = 0.0,
            latitudeDeg = 53.2028,
            longitudeDeg = -3.0691,
        )

    private val map =
        ArStampMap(
            fovDeg = 35.6,
            lensFovDeg = 35.6,
            zoomRequested = 1.0,
            widthPx = 784,
            heightPx = 1750,
            exposureIndex = 0,
            manual = null,
            scrimUser = 0.0,
            scrimApplied = 0.35,
            previewLuma = 0.12,
        )

    private val still =
        ArStillReport(
            path = "night",
            extension = "night",
            extensionReport = "camerax: night",
            lens = "id 0 sensor 9.8x7.3mm focal 6.9mm (first used)",
            zoomReported = 1.0,
            sourceWidth = 4080,
            sourceHeight = 3072,
            rotationDegrees = 90,
            width = 1440,
            height = 1920,
            captureMillis = 3_200L,
            exposureTimeSec = 0.348,
            iso = 16899,
            fNumber = 1.68,
            focalLengthMm = 6.9,
            focalLength35mm = 24,
            exifNote = null,
        )

    @Test
    fun `roll is zero upright and follows the right-hand rule about the line of sight`() {
        val los = Vector3(1.0, 0.0, 0.0)
        val zenith = Vector3(0.0, 0.0, 1.0)
        assertThat(ArStampMath.rollDeg(los, zenith, zenith)).isWithin(1e-9).of(0.0)
        val a = 30.0 * kotlin.math.PI / 180.0
        val tilted = Vector3(0.0, -sin(a), cos(a))
        assertThat(ArStampMath.rollDeg(los, tilted, zenith)).isWithin(1e-9).of(30.0)
    }

    @Test
    fun `roll is zero rather than unstable when looking at the zenith`() {
        val zenith = Vector3(0.0, 0.0, 1.0)
        val up = Vector3(1.0, 0.0, 0.0)
        assertThat(ArStampMath.rollDeg(zenith, up, zenith)).isEqualTo(0.0)
    }

    @Test
    fun `fixed formats without a JDK and keeps the sign of small negatives out of zero`() {
        assertThat(ArStampFormat.fixed(3.14159, 2)).isEqualTo("3.14")
        assertThat(ArStampFormat.fixed(-0.004, 2)).isEqualTo("0.00")
        assertThat(ArStampFormat.fixed(-12.5, 1)).isEqualTo("-12.5")
        assertThat(ArStampFormat.fixed(7.0, 0)).isEqualTo("7")
        assertThat(ArStampFormat.signed(0.0, 1)).isEqualTo("+0.0")
        assertThat(ArStampFormat.signed(-0.9, 1)).isEqualTo("-0.9")
    }

    @Test
    fun `stamp records the key tuning numbers`() {
        val lines =
            ArStampFormat.lines(
                before = pointing,
                after = pointing.copy(azDeg = 124.44),
                map = map,
                still = still,
                crop = ArStampCrop(width = 864, height = 1920),
                appVersion = "2.0",
                device = "Pixel 9 Pro API36",
            )
        val text = lines.joinToString("\n")
        assertThat(text.contains("UTC 2026-11-21 01:02:00")).isTrue()
        assertThat(text.contains("took 3.2s")).isTrue()
        assertThat(text.contains("decl -0.9")).isTrue()
        assertThat(text.contains("moved dAz +1.00")).isTrue()
        assertThat(text.contains("MOVED")).isTrue()
        assertThat(text.contains("map fov 35.6 lens@1x 35.6 zoom 1.00")).isTrue()
        assertThat(text.contains("22.0px/deg")).isTrue()
        assertThat(text.contains("crop 864x1920 (60% of width)")).isTrue()
        assertThat(text.contains("exif 1/3s ISO16899 f/1.68 6.9mm(24eq)")).isTrue()
        assertThat(text.contains("ev 0 auto")).isTrue()
    }

    @Test
    fun `missing exif says why instead of printing blanks`() {
        val lines =
            ArStampFormat.lines(
                before = pointing,
                after = null,
                map = map,
                still =
                    still.copy(
                        exposureTimeSec = null,
                        iso = null,
                        fNumber = null,
                        focalLengthMm = null,
                        focalLength35mm = null,
                        exifNote = "format 35, not JPEG",
                    ),
                crop = ArStampCrop(864, 1920),
                appVersion = "2.0",
                device = "x",
            )
        assertThat(lines.any { it == "exif n/a (format 35, not JPEG)" }).isTrue()
        assertThat(lines.none { it.startsWith("moved") }).isTrue()
    }

    @Test
    fun `a manual exposure and a shifted sky clock are called out`() {
        val lines =
            ArStampFormat.lines(
                before = pointing.copy(skyEpochMillis = pointing.epochMillis + 3_600_000L),
                after = null,
                map =
                    map.copy(
                        manual = ArManualExposure(iso = 3200, exposureTimeNs = 2_000_000_000L),
                    ),
                still = still,
                crop = ArStampCrop(864, 1920),
                appVersion = "2.0",
                device = "x",
            )
        val text = lines.joinToString("\n")
        assertThat(text.contains("SKY TIME")).isTrue()
        assertThat(text.contains("manual ISO3200 2.0s")).isTrue()
    }
}
