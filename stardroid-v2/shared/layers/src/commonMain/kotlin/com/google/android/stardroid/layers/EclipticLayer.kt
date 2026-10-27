/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The ecliptic as a graduated reference line in the brand's Star Gold, ported from v1's
 * `EclipticLayer` after upstream #923/#925: the Sun's apparent path sampled at the equinoxes and
 * solstices (the backend's great-circle subdivision fills in the arcs), with graduation ticks
 * every 10° of ecliptic longitude (longer, heavier ticks at the 30° zodiac boundaries), degree
 * labels at the 30° marks, and a name label off the 45°/225° marks. Static geometry; re-emits
 * only on locale change.
 *
 * The line is opaque (a dimmed gold) so the perpendicular ticks merge into it cleanly instead of
 * compounding into bright patches where translucent quads overlap; correspondingly the layer
 * draws just above the grid and behind constellations, DSOs, and stars (see [DEPTH]).
 */
class EclipticLayer(
    private val strings: Flow<LayerStrings>,
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH

    override fun scenes(): Flow<LayerScene> =
        strings
            .distinctUntilChanged()
            .map { buildScene(it) }
            .flowOn(mapContext)

    internal fun buildScene(strings: LayerStrings): LayerScene {
        val lines = ArrayList<LinePrimitive>(1 + 360 / TICK_STEP_DEG)
        // Equinoxes and solstices, closing back on the starting point (v1's five vertices).
        val ras = doubleArrayOf(0.0, 90.0, 180.0, 270.0, 0.0)
        val decs = doubleArrayOf(0.0, OBLIQUITY_DEG, 0.0, -OBLIQUITY_DEG, 0.0)
        lines +=
            LinePrimitive(
                ras.indices.map { RaDec(ras[it], decs[it]).toGeocentricVector() },
                SkyColors.ECLIPTIC_LINE,
                LINE_WIDTH_DP,
            )

        // Graduation ticks every 10° of ecliptic longitude, each pointing off the line toward
        // its label. The 30° marks (zodiac/constellation boundaries) get longer, heavier ticks.
        for (longitude in 0 until 360 step TICK_STEP_DEG) {
            val isMajor = longitude % MAJOR_TICK_STEP_DEG == 0
            lines +=
                LinePrimitive(
                    listOf(
                        geocentricForEcliptic(longitude.toDouble(), 0.0),
                        geocentricForEcliptic(
                            longitude.toDouble(),
                            if (isMajor) MAJOR_TICK_LENGTH_DEG else MINOR_TICK_LENGTH_DEG,
                        ),
                    ),
                    SkyColors.ECLIPTIC_LINE,
                    if (isMajor) MAJOR_TICK_WIDTH_DP else LINE_WIDTH_DP_MINOR_TICK,
                )
        }

        val labels = ArrayList<LabelPrimitive>()
        // Place the descriptive name off the 30° marks (45 & 225) so it doesn't collide with
        // the degree labels.
        labels += label(geocentricForEcliptic(45.0, LABEL_LATITUDE_OFFSET_DEG), strings.ecliptic)
        labels += label(geocentricForEcliptic(225.0, LABEL_LATITUDE_OFFSET_DEG), strings.ecliptic)

        // Degree labels at the 30° marks, offset perpendicular to the line (in ecliptic
        // latitude) so they sit a uniform small distance off it at every longitude. The vernal
        // equinox (0°) coincides exactly with 0h RA / dec 0, so it is labelled once by the grid
        // layer (as "0") instead of here, to avoid two labels stacking at the same point.
        for (longitude in MAJOR_TICK_STEP_DEG until 360 step MAJOR_TICK_STEP_DEG) {
            labels +=
                label(
                    geocentricForEcliptic(longitude.toDouble(), LABEL_LATITUDE_OFFSET_DEG),
                    "$longitude°",
                )
        }
        return LayerScene(depth = depth, lines = lines, labels = labels)
    }

    private fun label(
        pos: Vector3,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = pos,
            text = text,
            style = LabelStyle(LabelSize.MINOR, SkyColors.ECLIPTIC_LABEL),
            priority = LABEL_PRIORITY,
        )

    companion object {
        val LAYER_ID = LayerId("computed/ecliptic")

        /**
         * Upstream #925 moved the ecliptic from v1's 50 to just above the grid (0) and behind
         * constellations (10), DSOs (20), and stars (30), so the opaque line doesn't occlude
         * them.
         */
        private const val DEPTH = 5

        /** Obliquity of the ecliptic at J2000, as in v1's `EclipticLayer`. */
        private const val OBLIQUITY_DEG = 23.439281

        private val COS_OBLIQUITY = cos(OBLIQUITY_DEG * DEGREES_TO_RADIANS)
        private val SIN_OBLIQUITY = sin(OBLIQUITY_DEG * DEGREES_TO_RADIANS)

        /**
         * Labels are nudged a few degrees off the ecliptic in ecliptic *latitude* (i.e.
         * perpendicular to the line everywhere), so they sit a uniform small distance from the
         * line rather than striking through it. The graduation ticks bridge the small gap.
         */
        private const val LABEL_LATITUDE_OFFSET_DEG = 3.0

        /** Minor ticks every 10°, longer major ticks at the 30° zodiac boundaries. */
        private const val TICK_STEP_DEG = 10
        private const val MAJOR_TICK_STEP_DEG = 30

        /** Tick lengths, in degrees of ecliptic latitude. */
        private const val MINOR_TICK_LENGTH_DEG = 1.0
        private const val MAJOR_TICK_LENGTH_DEG = 2.0

        private const val LINE_WIDTH_DP = 1.8
        private const val LINE_WIDTH_DP_MINOR_TICK = 1.5
        private const val MAJOR_TICK_WIDTH_DP = 2.0

        private const val LABEL_PRIORITY = 40

        /**
         * Geocentric unit vector for the point at the given ecliptic longitude and latitude
         * (degrees). A non-zero latitude offsets the point perpendicular to the ecliptic.
         */
        internal fun geocentricForEcliptic(
            longitudeDeg: Double,
            latitudeDeg: Double,
        ): Vector3 {
            val lambda = longitudeDeg * DEGREES_TO_RADIANS
            val beta = latitudeDeg * DEGREES_TO_RADIANS
            val cosBeta = cos(beta)
            val xe = cosBeta * cos(lambda)
            val ye = cosBeta * sin(lambda)
            val ze = sin(beta)
            // Rotate about the x-axis by the obliquity to convert ecliptic -> equatorial.
            return Vector3(
                xe,
                ye * COS_OBLIQUITY - ze * SIN_OBLIQUITY,
                ye * SIN_OBLIQUITY + ze * COS_OBLIQUITY,
            )
        }
    }
}
