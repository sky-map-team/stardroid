/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * The RA/Dec graticule, ported from v1's `GridLayer`: meridians of right ascension, declination
 * circles, pole markers, hour labels along the equator, and degree labels along the prime
 * meridian. Geometry is fixed on the celestial sphere; only the label text depends on [strings],
 * so the scene re-emits only on locale change or when [density] changes (layers-and-app.md).
 *
 * [density] (D87 parameter, see [LayerParameter.RADEC_GRID_DENSITY_PARAMETER]) scales the whole
 * graticule together, mirroring [AltAzGridLayer]: fine is v1's grid (a meridian per hour, a
 * declination circle per 10°) and the default, so the map looks as it always has.
 */
class GridLayer(
    private val strings: Flow<LayerStrings>,
    private val density: Flow<String> =
        flowOf(LayerParameter.RADEC_GRID_DENSITY_PARAMETER.defaultOption),
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH
    override val parameters = PARAMETERS

    override fun scenes(): Flow<LayerScene> =
        combine(strings, density) { str, dens -> str to dens }
            .distinctUntilChanged()
            .map { (str, dens) -> buildScene(str, dens) }
            .flowOn(mapContext)

    internal fun buildScene(
        strings: LayerStrings,
        density: String = DEFAULT_DENSITY,
    ): LayerScene {
        val numRaLines = RA_LINE_COUNTS[density] ?: RA_LINE_COUNTS.getValue(DEFAULT_DENSITY)
        val numDecLines = DEC_LINE_COUNTS[density] ?: DEC_LINE_COUNTS.getValue(DEFAULT_DENSITY)
        // Exact only while every meridian count divides 24, so each label lands on a meridian.
        val hourLabelStride = 24 / numRaLines.coerceAtMost(HOUR_LABEL_MAX_COUNT)
        val lines = ArrayList<LinePrimitive>(numRaLines + 2 * (numDecLines - 1) + 1)
        for (i in 0 until numRaLines) {
            lines += raLine(i * 360.0 / numRaLines)
        }
        // Equator plus evenly spaced circles on each side; none at the poles themselves.
        lines += decLine(0.0)
        for (d in 1 until numDecLines) {
            lines += decLine(d * 90.0 / numDecLines)
            lines += decLine(-d * 90.0 / numDecLines)
        }

        val labels = ArrayList<LabelPrimitive>()
        labels += label(0.0, 90.0, strings.northPole)
        labels += label(0.0, -90.0, strings.southPole)
        for (hour in 0 until 24 step hourLabelStride) {
            // RA 0h / dec 0 is also the ecliptic's vernal equinox (ecliptic longitude 0). The
            // ecliptic layer deliberately omits its "0°" label there, so this single "0" (in
            // the grid/RA color) serves both.
            labels += label(hour * 15.0, 0.0, if (hour == 0) "0" else "${hour}h")
        }
        for (d in 1 until numDecLines) {
            val dec = d * 90 / numDecLines
            labels += label(0.0, dec.toDouble(), "$dec°")
            labels += label(0.0, -dec.toDouble(), "-$dec°")
        }
        return LayerScene(depth = depth, lines = lines, labels = labels)
    }

    /** A meridian of constant [raDeg]: pole to pole. The backend subdivides the long arcs. */
    private fun raLine(raDeg: Double): LinePrimitive =
        LinePrimitive(
            listOf(
                RaDec(raDeg, 90.0).toGeocentricVector(),
                RaDec(raDeg, 0.0).toGeocentricVector(),
                RaDec(raDeg, -90.0).toGeocentricVector(),
            ),
            SkyColors.GRID_LINE,
            LINE_WIDTH_DP,
        )

    /**
     * A circle of constant [decDeg], closed back on its first vertex. Off the equator these are
     * small circles, so the 10° sampling (not the backend's great-circle subdivision) is what
     * bounds the chord error — the same trade v1 made.
     */
    private fun decLine(decDeg: Double): LinePrimitive {
        val vertices =
            (0..NUM_RA_VERTICES).map { i ->
                RaDec((i % NUM_RA_VERTICES) * 360.0 / NUM_RA_VERTICES, decDeg)
                    .toGeocentricVector()
            }
        return LinePrimitive(vertices, SkyColors.GRID_LINE, LINE_WIDTH_DP)
    }

    private fun label(
        raDeg: Double,
        decDeg: Double,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = RaDec(raDeg, decDeg).toGeocentricVector(),
            text = text,
            style = LabelStyle(LabelSize.MINOR, SkyColors.GRID_LABEL),
            priority = LABEL_PRIORITY,
        )

    companion object {
        val LAYER_ID = LayerId("computed/grid")

        /** v1 depth table: the grid draws behind everything. */
        private const val DEPTH = 0

        private const val DEFAULT_DENSITY = LayerParameter.RADEC_GRID_DENSITY_FINE

        /**
         * Meridians drawn: every 3h, 2h or 1h. v1's grid (fine) is a meridian per hour. The
         * hour labels thin out to at most [HOUR_LABEL_MAX_COUNT] so the finest grid isn't a wall
         * of text.
         */
        private val RA_LINE_COUNTS =
            mapOf(
                LayerParameter.RADEC_GRID_DENSITY_COARSE to 8,
                LayerParameter.RADEC_GRID_DENSITY_MEDIUM to 12,
                LayerParameter.RADEC_GRID_DENSITY_FINE to 24,
            )

        /**
         * Declination circles on each side of the equator, dividing 90° evenly: 30°, 15°, then
         * v1's 10°. The same spacing [AltAzGridLayer] uses at its matching tiers.
         */
        private val DEC_LINE_COUNTS =
            mapOf(
                LayerParameter.RADEC_GRID_DENSITY_COARSE to 3,
                LayerParameter.RADEC_GRID_DENSITY_MEDIUM to 6,
                LayerParameter.RADEC_GRID_DENSITY_FINE to 9,
            )

        private const val HOUR_LABEL_MAX_COUNT = 12

        val PARAMETERS = listOf(LayerParameter.RADEC_GRID_DENSITY_PARAMETER)

        /** The registry's entry point (D91), as [AltAzGridLayer.create]. */
        fun create(
            strings: Flow<LayerStrings>,
            settings: Settings,
        ): GridLayer =
            GridLayer(
                strings,
                settings.layerParameter(
                    LAYER_ID,
                    LayerParameter.RADEC_GRID_DENSITY_PARAMETER.key,
                    LayerParameter.RADEC_GRID_DENSITY_PARAMETER.defaultOption,
                ),
            )

        /** Vertices per declination circle — every 10°, as in v1. */
        private const val NUM_RA_VERTICES = 36

        private const val LINE_WIDTH_DP = 1.5

        /** Below every named object ([CatalogLayers.labelPriority]'s mid-band is 50). */
        private const val LABEL_PRIORITY = 40
    }
}
