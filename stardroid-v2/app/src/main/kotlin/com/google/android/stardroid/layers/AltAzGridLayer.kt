/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.LocalFrame
import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
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
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The horizontal (alt/az) graticule (#1022): azimuth lines through the zenith and nadir, plus
 * altitude circles, for naked-eye or manual alt-az-mount observers who think in direction and
 * height rather than right ascension and declination. Off by default
 * ([LayerRegistry.defaultEnabled]) — a niche aid, not something to surface unasked.
 *
 * Also owns the zenith/nadir labels, moved here from [HorizonLayer] (#1022 review): zenith and
 * nadir are this coordinate system's poles, the same relationship [GridLayer] has with the
 * celestial poles, so they belong to the layer that draws the rest of the graticule rather than
 * to the horizon line. The tradeoff is deliberate: those labels now show only when this
 * (off-by-default) layer is enabled, rather than always.
 *
 * Geometry depends on the observer's [SkyModel.localFrame] (time + location), so — unlike
 * [GridLayer]'s fixed celestial geometry — the scene recomputes whenever [clock] or [location]
 * changes, exactly as [HorizonLayer] does. [density] (D87 parameter, see
 * [LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER]) scales the whole graticule together: how many
 * azimuth lines are drawn ([AZ_COUNTS]), how many altitude circles ([ALT_LINE_COUNTS] — the
 * densest tier matches [GridLayer]'s 10° declination spacing), and how finely each individual
 * circle is sampled ([CIRCLE_VERTEX_COUNTS], so the near-horizon circles don't look faceted).
 */
class AltAzGridLayer(
    private val clock: Flow<Instant>,
    private val location: Flow<LatLong>,
    private val strings: Flow<LayerStrings>,
    private val density: Flow<String> =
        flowOf(LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER.defaultOption),
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH
    override val parameters = PARAMETERS

    override fun scenes(): Flow<LayerScene> =
        combine(clock, location, strings, density, ::Inputs)
            .distinctUntilChanged()
            .map { (time, loc, str, dens) -> buildScene(time, loc, str, dens) }
            .flowOn(mapContext)

    private data class Inputs(
        val time: Instant,
        val location: LatLong,
        val strings: LayerStrings,
        val density: String,
    )

    internal fun buildScene(
        time: Instant,
        location: LatLong,
        strings: LayerStrings,
        density: String,
    ): LayerScene {
        val frame = SkyModel.localFrame(time, location)
        val azCount = AZ_COUNTS[density] ?: AZ_COUNTS.getValue(DEFAULT_DENSITY)
        val altLineCount = ALT_LINE_COUNTS[density] ?: ALT_LINE_COUNTS.getValue(DEFAULT_DENSITY)
        val circleVertexCount =
            CIRCLE_VERTEX_COUNTS[density] ?: CIRCLE_VERTEX_COUNTS.getValue(DEFAULT_DENSITY)
        // Azimuth 0/90/180/270 always fall among the generated lines for every supported count
        // (each divides 360 into a multiple of 4), so skipping every azCount/4-th index skips
        // exactly the cardinal directions.
        val cardinalStride = azCount / 4

        val lines = ArrayList<LinePrimitive>(azCount + 2 * (altLineCount - 1))
        val labels = ArrayList<LabelPrimitive>(azCount + 2 * (altLineCount - 1) + 2)

        for (i in 0 until azCount) {
            val azDeg = i * 360.0 / azCount
            lines += azimuthLine(frame, azDeg)
            if (i % cardinalStride != 0) {
                // HorizonLayer already labels the cardinal directions at these same points.
                labels += label(altAzToVector(0.0, azDeg, frame), "${azDeg.toInt()}°")
            }
        }
        for (d in 1 until altLineCount) {
            val altDeg = d * 90.0 / altLineCount
            lines += altitudeLine(frame, altDeg, circleVertexCount)
            lines += altitudeLine(frame, -altDeg, circleVertexCount)
            labels += label(altAzToVector(altDeg, 0.0, frame), "${altDeg.toInt()}°")
            labels += label(altAzToVector(-altDeg, 0.0, frame), "-${altDeg.toInt()}°")
        }

        labels += poleLabel(frame.up, strings.zenith)
        labels += poleLabel(-frame.up, strings.nadir)

        return LayerScene(depth = depth, lines = lines, labels = labels)
    }

    /** A meridian of constant azimuth: zenith to nadir through the horizon. The backend
     * subdivides the long arcs. */
    private fun azimuthLine(
        frame: LocalFrame,
        azDeg: Double,
    ): LinePrimitive =
        LinePrimitive(
            listOf(frame.up, altAzToVector(0.0, azDeg, frame), -frame.up),
            SkyColors.ALTAZ_GRID_LINE,
            LINE_WIDTH_DP,
        )

    /**
     * A circle of constant altitude, closed back on its first vertex. Away from the horizon
     * these are small circles, so sampling (not the backend's great-circle subdivision) bounds
     * the chord error — the same trade [GridLayer] makes for declination circles. [vertexCount]
     * follows [density] (via [CIRCLE_VERTEX_COUNTS]): the near-horizon circles are close to full
     * great circles, so a fixed low sample count reads as faceted rather than round unless it
     * scales with how many azimuth lines the user asked for.
     */
    private fun altitudeLine(
        frame: LocalFrame,
        altDeg: Double,
        vertexCount: Int,
    ): LinePrimitive {
        val vertices =
            (0..vertexCount).map { i ->
                altAzToVector(altDeg, (i % vertexCount) * 360.0 / vertexCount, frame)
            }
        return LinePrimitive(vertices, SkyColors.ALTAZ_GRID_LINE, LINE_WIDTH_DP)
    }

    private fun label(
        pos: Vector3,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = pos,
            text = text,
            style = LabelStyle(LabelSize.MINOR, SkyColors.ALTAZ_GRID_LABEL),
            priority = LABEL_PRIORITY,
        )

    private fun poleLabel(
        pos: Vector3,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = pos,
            text = text,
            style = LabelStyle(LabelSize.STANDARD, SkyColors.HORIZON_LABEL),
            priority = POLE_LABEL_PRIORITY,
        )

    companion object {
        val LAYER_ID = LayerId("computed/altaz_grid")

        /** Alongside the horizon: in front of the celestial grid and catalog objects. */
        private const val DEPTH = 80

        private const val LINE_WIDTH_DP = 1.5

        /** Below every named object — the same band [GridLayer] uses for its own labels. */
        private const val LABEL_PRIORITY = 40

        /**
         * Orientation cues that should survive decluttering — the priority zenith/nadir carried
         * as [HorizonLayer] labels before moving here.
         */
        private const val POLE_LABEL_PRIORITY = 70

        private const val DEFAULT_DENSITY = LayerParameter.ALTAZ_GRID_DENSITY_MEDIUM

        private val AZ_COUNTS =
            mapOf(
                LayerParameter.ALTAZ_GRID_DENSITY_COARSE to 8,
                LayerParameter.ALTAZ_GRID_DENSITY_MEDIUM to 12,
                LayerParameter.ALTAZ_GRID_DENSITY_FINE to 24,
            )

        /**
         * How many altitude circles are drawn on each side of the horizon, dividing 90° evenly
         * so the step size stays a round number: 30°, then 15°, then 10° (matching [GridLayer]'s
         * declination spacing at the densest tier). A fixed count regardless of [density] (#1022
         * review) was the second half of the "line count" setting doing nothing visible — this
         * is the other half of the graticule the user actually meant.
         */
        private val ALT_LINE_COUNTS =
            mapOf(
                LayerParameter.ALTAZ_GRID_DENSITY_COARSE to 3,
                LayerParameter.ALTAZ_GRID_DENSITY_MEDIUM to 6,
                LayerParameter.ALTAZ_GRID_DENSITY_FINE to 9,
            )

        /**
         * Vertices per altitude circle. Scales with [AZ_COUNTS] rather than sharing its value
         * outright: the near-horizon circles are as big on screen as the azimuth meridians, so
         * they need several samples between each meridian's bearing to read as round rather than
         * faceted, at every density.
         */
        private val CIRCLE_VERTEX_COUNTS =
            mapOf(
                LayerParameter.ALTAZ_GRID_DENSITY_COARSE to 36,
                LayerParameter.ALTAZ_GRID_DENSITY_MEDIUM to 60,
                LayerParameter.ALTAZ_GRID_DENSITY_FINE to 90,
            )

        val PARAMETERS = listOf(LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER)

        /**
         * The registry's entry point (D91): the layer reads its own density parameter out of
         * [settings], so nothing above it has to know the parameter exists. Direct construction
         * stays available for tests, which supply the density themselves.
         */
        fun create(
            clock: Flow<Instant>,
            location: Flow<LatLong>,
            strings: Flow<LayerStrings>,
            settings: Settings,
        ): AltAzGridLayer =
            AltAzGridLayer(
                clock,
                location,
                strings,
                settings.layerParameter(
                    LAYER_ID,
                    LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER.key,
                    LayerParameter.ALTAZ_GRID_DENSITY_PARAMETER.defaultOption,
                ),
            )

        /**
         * A unit vector for an [altDeg]/[azDeg] pair expressed in [frame]'s basis:
         * `north·cos(alt)cos(az) + east·cos(alt)sin(az) + up·sin(alt)`.
         * [com.google.android.stardroid.math.RaDec.toGeocentricVector] is the fixed-frame
         * analogue; this one is observer-relative, so it takes the frame as a parameter instead
         * of being frame-independent.
         */
        private fun altAzToVector(
            altDeg: Double,
            azDeg: Double,
            frame: LocalFrame,
        ): Vector3 {
            val alt = altDeg * DEGREES_TO_RADIANS
            val az = azDeg * DEGREES_TO_RADIANS
            val cosAlt = cos(alt)
            return frame.trueNorth * (cosAlt * cos(az)) +
                frame.trueEast * (cosAlt * sin(az)) +
                frame.up * sin(alt)
        }
    }
}
