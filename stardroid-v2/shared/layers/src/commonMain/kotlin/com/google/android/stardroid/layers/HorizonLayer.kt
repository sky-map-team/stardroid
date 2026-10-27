/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

/**
 * The local horizon, ported from v1's `HorizonLayer`: the great circle through the cardinal
 * points plus cardinal-direction labels, all derived from [SkyModel.localFrame] (true north —
 * magnetic declination plays no part here).
 *
 * It used to also submit a soft additive glow just below the line (upstream #924). That glow was
 * doing two jobs — marking the horizon and suggesting depth below it — and its eight rings existed
 * only to trace an exponential falloff with the per-vertex colour a fixed pipeline offers. Both
 * jobs now belong to `Ground`, a render-state block each backend shades for itself, which leaves
 * this layer with nothing but reference geometry: where the horizon is, and which way is north.
 *
 * That split is also why the two are separate preferences. The line and the letters answer "where
 * am I pointing", and someone looking through the Earth to find the Sun still wants them; the
 * ground answers "what is below me" and is the thing they need out of the way.
 *
 * Zenith/nadir labels moved to [AltAzGridLayer] (#1022): they are that layer's coordinate-system
 * poles, not part of the horizon itself, and only show when the (off-by-default) alt/az grid is
 * enabled.
 *
 * The horizon drifts through celestial coordinates as the Earth turns (~0.25°/min), so the scene
 * recomputes on each [clock] emission — the caller picks the tick rate, and time travel
 * accelerates it like everything else (layers-and-app.md). [distinctUntilChanged] keeps
 * unchanged recomputations from reaching the renderer.
 */
class HorizonLayer(
    private val clock: Flow<Instant>,
    private val location: Flow<LatLong>,
    private val strings: Flow<LayerStrings>,
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH

    override fun scenes(): Flow<LayerScene> =
        combine(clock, location, strings) { time, loc, str -> Triple(time, loc, str) }
            .distinctUntilChanged()
            .map { (time, loc, str) -> buildScene(time, loc, str) }
            .flowOn(mapContext)

    internal fun buildScene(
        time: Instant,
        location: LatLong,
        strings: LayerStrings,
    ): LayerScene {
        val frame = SkyModel.localFrame(time, location)
        val north = frame.trueNorth
        val south = -frame.trueNorth
        val east = frame.trueEast
        val west = -frame.trueEast

        val horizon =
            LinePrimitive(
                listOf(north, east, south, west, north),
                SkyColors.HORIZON_LINE,
                LINE_WIDTH_DP,
            )
        val labels =
            listOf(
                label(north, strings.north),
                label(south, strings.south),
                label(east, strings.east),
                label(west, strings.west),
            )
        return LayerScene(
            depth = depth,
            lines = listOf(horizon),
            labels = labels,
        )
    }

    private fun label(
        pos: Vector3,
        text: String,
    ): LabelPrimitive =
        LabelPrimitive(
            pos = pos,
            text = text,
            style = LabelStyle(LabelSize.STANDARD, SkyColors.HORIZON_LABEL),
            priority = LABEL_PRIORITY,
        )

    companion object {
        val LAYER_ID = LayerId("computed/horizon")

        /**
         * v1 depth table: the horizon draws in front of everything.
         *
         * It must also stay greater than [LayerScene.GROUND_DEPTH], or the ground would wash over
         * the line and the cardinal labels instead of stopping beneath them. `HorizonLayerTest`
         * asserts that, because nothing about a bare integer says so.
         */
        private const val DEPTH = 90

        private const val LINE_WIDTH_DP = 2.5

        /** Above the catalog mid-band: orientation cues should survive decluttering. */
        private const val LABEL_PRIORITY = 70
    }
}
