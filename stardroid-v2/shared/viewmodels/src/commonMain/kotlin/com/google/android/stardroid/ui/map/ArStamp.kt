/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// TODO(ar-debug-stamp): temporary, for tuning the AR share still against the drawn sky. Remove
// with the "Debug stamp" switch once the still/overlay mismatch is understood.

/** Where the phone pointed and when, sampled around a share capture. */
data class ArStampPointing(
    val epochMillis: Long,
    val skyEpochMillis: Long,
    val raDeg: Double,
    val decDeg: Double,
    val azDeg: Double,
    val altDeg: Double,
    val rollDeg: Double,
    val sensorFrame: Boolean,
    val declinationDeg: Double,
    val magneticCorrection: Boolean,
    val correctionAzDeg: Double,
    val correctionAltDeg: Double,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
)

/** The map side of the overlay: the FOV it drew, the lens FOV it was locked to, the settings. */
data class ArStampMap(
    val fovDeg: Double,
    val lensFovDeg: Double?,
    val zoomRequested: Double,
    val widthPx: Int,
    val heightPx: Int,
    val exposureIndex: Int,
    val manual: ArManualExposure?,
    val scrimUser: Double,
    val scrimApplied: Double,
    val previewLuma: Double,
)

/** What the camera edge knows about the still it just took; null fields were not available. */
data class ArStillReport(
    val path: String,
    val extension: String,
    val extensionReport: String,
    val lens: String,
    val zoomReported: Double?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int,
    val captureMillis: Long,
    val exposureTimeSec: Double?,
    val iso: Int?,
    val fNumber: Double?,
    val focalLengthMm: Double?,
    val focalLength35mm: Int?,
    val exifNote: String?,
)

/** The share's centre-crop of the still, in the still's pixels. */
data class ArStampCrop(
    val width: Int,
    val height: Int,
)

object ArStampMath {
    /**
     * The screen-up direction's tilt from the zenith, about the line of sight (right-hand rule),
     * in degrees. Zero when the phone is held upright; unstable near the zenith, where it is 0.
     */
    fun rollDeg(
        lineOfSight: Vector3,
        screenUp: Vector3,
        zenith: Vector3,
    ): Double {
        val zenithOnScreen = zenith - lineOfSight * (zenith dot lineOfSight)
        if (zenithOnScreen.length2 < 1e-9) return 0.0
        val x = zenithOnScreen dot screenUp
        val y = (zenithOnScreen cross screenUp) dot lineOfSight
        return atan2(y, x) * RADIANS_TO_DEGREES
    }
}

object ArStampFormat {
    private const val WRAP_COLUMNS = 62
    private const val MOVED_WARN_DEG = 0.5
    private const val SKY_TIME_DIFF_MS = 2_000L

    fun lines(
        before: ArStampPointing,
        after: ArStampPointing?,
        map: ArStampMap,
        still: ArStillReport,
        crop: ArStampCrop,
        appVersion: String,
        device: String,
    ): List<String> =
        buildList {
            add("SKY MAP DEBUG $appVersion  $device")
            add(timeLine(before, still))
            add(
                "loc ${fixed(before.latitudeDeg, 2)},${fixed(before.longitudeDeg, 2)}  " +
                    "decl ${signed(before.declinationDeg, 1)} " +
                    if (before.magneticCorrection) "(on)" else "(off)",
            )
            add(
                "pt RA ${fixed(before.raDeg / 15.0, 2)}h Dec ${signed(before.decDeg, 1)}  " +
                    "Az ${fixed(before.azDeg, 1)} Alt ${signed(before.altDeg, 1)} " +
                    "roll ${signed(before.rollDeg, 1)}",
            )
            add(
                "drag az ${signed(before.correctionAzDeg, 1)} " +
                    "alt ${signed(before.correctionAltDeg, 1)}  " +
                    if (before.sensorFrame) "sensor" else "MANUAL",
            )
            if (after != null) add(movedLine(before, after))
            addAll(mapLines(map))
            addAll(stillLines(still, crop))
            addAll(settingLines(map))
        }

    private fun timeLine(
        before: ArStampPointing,
        still: ArStillReport,
    ): String {
        val utc = Instant.fromEpochMilliseconds(before.epochMillis).toString().take(19)
        val skyNote =
            if (abs(before.skyEpochMillis - before.epochMillis) > SKY_TIME_DIFF_MS) {
                "  SKY TIME " +
                    Instant.fromEpochMilliseconds(before.skyEpochMillis).toString().take(19)
            } else {
                ""
            }
        return "UTC ${utc.replace('T', ' ')}  took ${fixed(still.captureMillis / 1000.0, 1)}s" +
            skyNote
    }

    private fun movedLine(
        before: ArStampPointing,
        after: ArStampPointing,
    ): String {
        val dAz = wrap180(after.azDeg - before.azDeg)
        val dAlt = after.altDeg - before.altDeg
        val flag = if (abs(dAz) > MOVED_WARN_DEG || abs(dAlt) > MOVED_WARN_DEG) " MOVED" else ""
        return "moved dAz ${signed(dAz, 2)} dAlt ${signed(dAlt, 2)}$flag"
    }

    private fun mapLines(map: ArStampMap): List<String> {
        val pxPerDeg = min(map.widthPx, map.heightPx) / map.fovDeg
        val lens = map.lensFovDeg?.let { fixed(it, 1) } ?: "none"
        val expected = map.lensFovDeg?.let { fixed(it / map.zoomRequested, 1) } ?: "-"
        return listOf(
            "map fov ${fixed(map.fovDeg, 1)} lens@1x $lens zoom ${fixed(map.zoomRequested, 2)}",
            "map fov expected lens/zoom=$expected  ${map.widthPx}x${map.heightPx} " +
                "${fixed(pxPerDeg, 1)}px/deg",
        )
    }

    private fun stillLines(
        still: ArStillReport,
        crop: ArStampCrop,
    ): List<String> {
        val keptPct = if (still.width > 0) (crop.width * 100.0 / still.width).roundToInt() else 0
        val exposure =
            listOfNotNull(
                still.exposureTimeSec?.let { shutter(it) },
                still.iso?.let { "ISO$it" },
                still.fNumber?.let { "f/${fixed(it, 2)}" },
                still.focalLengthMm?.let { mm ->
                    "${fixed(mm, 1)}mm" +
                        (still.focalLength35mm?.let { "(${it}eq)" } ?: "")
                },
            )
        return buildList {
            add("still ${still.path} ext=${still.extension}")
            add(
                "still ${still.sourceWidth}x${still.sourceHeight} rot${still.rotationDegrees} " +
                    "-> ${still.width}x${still.height}  crop ${crop.width}x${crop.height} " +
                    "($keptPct% of width)",
            )
            add(
                "cam zoom reported " +
                    (still.zoomReported?.let { fixed(it, 2) } ?: "n/a"),
            )
            add(
                "exif " +
                    if (exposure.isEmpty()) {
                        "n/a" + (still.exifNote?.let { " ($it)" } ?: "")
                    } else {
                        exposure.joinToString(" ")
                    },
            )
            addAll(wrap("lens ${still.lens}"))
            addAll(wrap("ext ${still.extensionReport}"))
        }
    }

    private fun settingLines(map: ArStampMap): List<String> {
        val manual =
            map.manual?.let {
                "manual ISO${it.iso} ${shutter(it.exposureTimeNs / 1e9)}"
            } ?: "auto"
        return listOf(
            "ev ${map.exposureIndex} $manual",
            "scrim user ${fixed(map.scrimUser, 2)} applied ${fixed(map.scrimApplied, 2)} " +
                "luma ${(map.previewLuma * 100).roundToInt()}%",
        )
    }

    private fun wrap(text: String): List<String> = text.chunked(WRAP_COLUMNS)

    private fun shutter(seconds: Double): String =
        if (seconds > 0.0 && seconds < 1.0) {
            "1/${(1.0 / seconds).roundToInt()}s"
        } else {
            "${fixed(seconds, 1)}s"
        }

    private fun wrap180(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    /** [value] to [decimals] places without locale or JDK formatting (shared code). */
    internal fun fixed(
        value: Double,
        decimals: Int,
    ): String {
        val factor = 10.0.pow(decimals).roundToLong()
        val scaled = (abs(value) * factor).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val whole = scaled / factor
        if (decimals == 0) return "$sign$whole"
        val frac = (scaled % factor).toString().padStart(decimals, '0')
        return "$sign$whole.$frac"
    }

    internal fun signed(
        value: Double,
        decimals: Int,
    ): String {
        val text = fixed(value, decimals)
        return if (text.startsWith("-")) text else "+$text"
    }
}
