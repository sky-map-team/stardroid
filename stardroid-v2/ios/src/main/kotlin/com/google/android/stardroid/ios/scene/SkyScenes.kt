/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.scene

import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.LocalFrame
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import kotlinx.datetime.Instant
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds the iOS shell's layers as ordinary [LayerScene]s — the same `:render:api` contract the
 * Android layers feed the GLES backend.
 *
 * Deliberately small: stars, constellation figures, the solar system as coloured points and the
 * horizon. The Android layers in `:app/layers` are almost free of Android APIs already; moving
 * them into a shared pure module would replace this file (see ios/README.md).
 */
object SkyScenes {
    val STARS = LayerId("catalog/stars")
    val CONSTELLATIONS = LayerId("catalog/constellations")
    val SOLAR_SYSTEM = LayerId("computed/solar_system")
    val HORIZON = LayerId("computed/horizon")

    // The v1 depth table the Android layers use, so draw order matches.
    private const val CONSTELLATIONS_DEPTH = 10
    private const val STARS_DEPTH = 30
    private const val SOLAR_SYSTEM_DEPTH = 60
    private const val HORIZON_DEPTH = 90

    /** Stars fainter than this are culled here; the renderer's magnitude limit filters further. */
    private const val MAX_STAR_MAGNITUDE = 6.5

    /** Only stars at least this bright are named, as on Android's default settings. */
    private const val MAX_LABELLED_MAGNITUDE = 2.5

    private val STAR_LABEL = LabelStyle(LabelSize.MINOR, Rgba.fromArgb(0xFF9BB1D8.toInt()))
    private val CONSTELLATION_LINE = Rgba.fromArgb(0x80597BC8.toInt())
    private val PLANET_LABEL = LabelStyle(LabelSize.STANDARD, Rgba.fromArgb(0xFFF2D38A.toInt()))
    private val HORIZON_LINE = Rgba.fromArgb(0xFFD08A4E.toInt())
    private val HORIZON_LABEL = LabelStyle(LabelSize.TITLE, HORIZON_LINE)

    fun stars(catalog: List<CatalogStar>): LayerScene {
        val visible = catalog.filter { it.magnitude <= MAX_STAR_MAGNITUDE }
        return LayerScene(
            depth = STARS_DEPTH,
            points =
                visible.map {
                    PointPrimitive(
                        it.position,
                        PointAppearance.Stellar(it.magnitude),
                    )
                },
            labels =
                visible
                    .filter { it.name != null && it.magnitude <= MAX_LABELLED_MAGNITUDE }
                    .map { star ->
                        LabelPrimitive(
                            pos = star.position,
                            text = star.name!!,
                            style = STAR_LABEL,
                            // Brighter (smaller magnitude) wins declutter conflicts.
                            priority = (-star.magnitude * 100).roundToInt(),
                            magnitudeForThresholding = star.magnitude,
                        )
                    },
        )
    }

    fun constellations(figures: List<ConstellationFigure>): LayerScene =
        LayerScene(
            depth = CONSTELLATIONS_DEPTH,
            lines =
                figures.flatMap { figure ->
                    figure.strokes.map { LinePrimitive(it, CONSTELLATION_LINE, widthDp = 1.0) }
                },
        )

    fun solarSystem(
        time: Instant,
        observer: LatLong,
        ephemeris: Ephemeris = MeeusEphemeris,
    ): LayerScene {
        val bodies =
            SolarSystemBody.entries
                .filter { it != SolarSystemBody.EARTH }
                // Farthest first, so nearer bodies paint over farther ones (D18).
                .sortedByDescending { ephemeris.earthDistanceAu(it, time) }
        val positions =
            bodies.associateWith {
                ephemeris.topocentricPosition(it, time, observer).toGeocentricVector()
            }
        return LayerScene(
            depth = SOLAR_SYSTEM_DEPTH,
            points =
                bodies.map { body ->
                    val (color, size) = bodyStyle(body)
                    PointPrimitive(positions.getValue(body), PointAppearance.Fixed(color, size))
                },
            labels =
                bodies.map { body ->
                    LabelPrimitive(
                        pos = positions.getValue(body),
                        text = displayName(body),
                        style = PLANET_LABEL,
                        // Above every star label; null threshold keeps faint planets named.
                        priority = PLANET_LABEL_PRIORITY,
                    )
                },
        )
    }

    fun horizon(frame: LocalFrame): LayerScene {
        val circle =
            (0..HORIZON_SEGMENTS).map { i ->
                val az = i * 360.0 / HORIZON_SEGMENTS * DEGREES_TO_RADIANS
                frame.trueNorth * cos(az) + frame.trueEast * sin(az)
            }
        val cardinals =
            listOf(
                "N" to frame.trueNorth,
                "E" to frame.trueEast,
                "S" to -frame.trueNorth,
                "W" to -frame.trueEast,
            )
        return LayerScene(
            depth = HORIZON_DEPTH,
            lines = listOf(LinePrimitive(circle, HORIZON_LINE, widthDp = 1.5)),
            labels =
                cardinals.map { (text, direction) ->
                    LabelPrimitive(direction, text, HORIZON_LABEL, priority = CARDINAL_PRIORITY)
                },
        )
    }

    private fun bodyStyle(body: SolarSystemBody): Pair<Rgba, Double> =
        when (body) {
            SolarSystemBody.SUN -> Rgba.fromArgb(0xFFFFE27A.toInt()) to 14.0
            SolarSystemBody.MOON -> Rgba.fromArgb(0xFFE8E8F0.toInt()) to 12.0
            SolarSystemBody.MARS -> Rgba.fromArgb(0xFFE0714A.toInt()) to 6.0
            SolarSystemBody.JUPITER -> Rgba.fromArgb(0xFFF0D9B5.toInt()) to 7.0
            SolarSystemBody.SATURN -> Rgba.fromArgb(0xFFE6CF8F.toInt()) to 6.5
            SolarSystemBody.VENUS -> Rgba.fromArgb(0xFFFFF6D8.toInt()) to 7.0
            else -> Rgba.fromArgb(0xFFC9D6E8.toInt()) to 5.0
        }

    /** English only for now; the iOS shell has no string-resource pipeline yet. */
    private fun displayName(body: SolarSystemBody): String =
        body.name.lowercase().replaceFirstChar { it.uppercase() }

    private const val HORIZON_SEGMENTS = 180
    private const val PLANET_LABEL_PRIORITY = 10_000
    private const val CARDINAL_PRIORITY = 20_000
}
