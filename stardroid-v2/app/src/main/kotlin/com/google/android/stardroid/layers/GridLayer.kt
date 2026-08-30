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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * The RA/Dec graticule, ported from v1's `GridLayer`: meridians at each hour of right ascension,
 * declination circles every 10°, pole markers, hour labels along the equator, and degree labels
 * along the prime meridian. Geometry is fixed on the celestial sphere; only the label text
 * depends on [strings], so the scene re-emits only on locale change (layers-and-app.md).
 */
class GridLayer(
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
        val lines = ArrayList<LinePrimitive>(NUM_RA_LINES + 2 * (NUM_DEC_LINES - 1) + 1)
        for (i in 0 until NUM_RA_LINES) {
            lines += raLine(i * 360.0 / NUM_RA_LINES)
        }
        // Equator plus a circle every 10° on each side; none at the poles themselves.
        lines += decLine(0.0)
        for (d in 1 until NUM_DEC_LINES) {
            lines += decLine(d * 90.0 / NUM_DEC_LINES)
            lines += decLine(-d * 90.0 / NUM_DEC_LINES)
        }

        val labels = ArrayList<LabelPrimitive>()
        labels += label(0.0, 90.0, strings.northPole)
        labels += label(0.0, -90.0, strings.southPole)
        for (hour in 0 until 24 step 2) {
            // RA 0h / dec 0 is also the ecliptic's vernal equinox (ecliptic longitude 0). The
            // ecliptic layer deliberately omits its "0°" label there, so this single "0" (in
            // the grid/RA color) serves both.
            labels += label(hour * 15.0, 0.0, if (hour == 0) "0" else "${hour}h")
        }
        for (d in 1 until NUM_DEC_LINES) {
            val dec = d * 90 / NUM_DEC_LINES
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

        /** v1 `GridLayer` wiring: a meridian per hour, a declination circle per 10°. */
        private const val NUM_RA_LINES = 24
        private const val NUM_DEC_LINES = 9

        /** Vertices per declination circle — every 10°, as in v1. */
        private const val NUM_RA_VERTICES = 36

        private const val LINE_WIDTH_DP = 1.5

        /** Below every named object ([CatalogLayers.labelPriority]'s mid-band is 50). */
        private const val LABEL_PRIORITY = 40
    }
}
