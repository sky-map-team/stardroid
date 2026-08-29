/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.SatelliteEphemeris
import com.google.android.stardroid.astronomy.Sgp4
import com.google.android.stardroid.astronomy.Tle
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.data.satellites.SatelliteElements
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Satellites in their true sky positions, moving in real time (D92 phase 4).
 *
 * Follows [SolarSystemLayer]'s shape, with one part deliberately rewritten rather than copied.
 *
 * **The resubmit gate.** `SolarSystemLayer` re-emits once any body has moved
 * `RESUBMIT_THRESHOLD_DEG = 0.01`, which against the Moon's ~15°/day means about a minute. At the
 * ISS's ~4° per *minute* that same threshold would fire roughly every 150 ms — several rebuilds a
 * second. This layer instead samples on its own fixed [REFRESH_INTERVAL] of the wall clock,
 * independent of how often `elements`, the shared app clock, or `location` actually emit. That
 * independence matters in both directions: the shared clock (`TimeController.times`) only ticks
 * once a minute in real time, which alone would make the ISS visibly jump ~4° at a time, and it
 * ticks every 32 ms during time travel, which alone would rebuild the scene ~30 times a second for
 * no visible benefit. Sampling gives ~0.25° of ISS motion per rebuild in real time — sub-pixel at
 * typical fields of view — while still tracking accelerated time during travel, just without
 * paying for a rebuild on every one of its ticks. Recorded as a deliberate divergence rather than
 * left to look like an oversight.
 *
 * **Depth 55** — in front of the solar system (60), behind the horizon. D18's occlusion sort by
 * descending distance naturally puts a satellite nearest the viewer, which is correct: it really
 * is the closest thing in the scene by four orders of magnitude.
 *
 * **The marker is star-white with a short fading trail, not a new hue.** Naked-eye the ISS looks
 * like a fast steady star, and motion is what distinguishes it. Lens Blue already means deep-sky
 * and Planet Red already means planets, showers and comets, so claiming a third hue would dilute
 * an existing meaning to say something the trail says better. The label reuses
 * [SkyColors.SKY_LABEL], putting satellites in the same "computed, real-time" family as planets
 * and meteor showers.
 */
class SatelliteLayer(
    private val elements: Flow<SatelliteElements>,
    private val clock: Flow<Instant>,
    private val location: Flow<LatLong>,
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH
    override val parameters = PARAMETERS

    @OptIn(FlowPreview::class)
    override fun scenes(): Flow<LayerScene> {
        val data = combine(elements, clock, location, ::Triple)
        return data
            .sample(REFRESH_INTERVAL)
            .onStart { emit(data.first()) }
            .map { (current, time, observer) -> buildScene(current, time, observer) }
            .flowOn(mapContext)
    }

    internal fun buildScene(
        current: SatelliteElements,
        time: Instant,
        observer: LatLong,
    ): LayerScene {
        // Nothing cached means nothing to draw. The app says so with the empty-state card rather
        // than here — an empty scene is the honest render of "we have no orbits".
        if (current.freshness == ElementFreshness.ABSENT) return LayerScene(depth = depth)

        val points = mutableListOf<PointPrimitive>()
        val labels = mutableListOf<LabelPrimitive>()

        for (tle in current.tles.filter { it.noradId in TRACKED_NORAD_IDS }) {
            val track = trackFor(tle, time, observer) ?: continue
            // Trail first so the head draws over it.
            track.trail.forEachIndexed { index, position ->
                val fade = (index + 1).toDouble() / (track.trail.size + 1)
                val faded = MARKER_COLOR.withAlpha(fade * TRAIL_MAX_ALPHA)
                points += PointPrimitive(position, PointAppearance.Fixed(faded, TRAIL_SIZE_DP))
            }
            points +=
                PointPrimitive(track.head, PointAppearance.Fixed(MARKER_COLOR, MARKER_SIZE_DP))
            labels +=
                LabelPrimitive(
                    pos = track.head,
                    text = tle.name.ifEmpty { "#${tle.noradId}" },
                    style =
                        LabelStyle(
                            LabelSize.STANDARD,
                            SkyColors.SKY_LABEL,
                            offsetDp = MARKER_SIZE_DP / 2 + 2.0,
                        ),
                    priority = CatalogLayers.labelPriority(null),
                    magnitudeForThresholding = null,
                )
        }
        return LayerScene(depth = depth, points = points, labels = labels)
    }

    /**
     * Where a satellite is now, plus where it just was.
     *
     * Returns null when SGP4 declines to propagate — a decayed or corrupt element set — which is a
     * per-satellite absence rather than a reason to drop the whole scene.
     */
    private fun trackFor(
        tle: Tle,
        time: Instant,
        observer: LatLong,
    ): Track? {
        val sgp4 = runCatching { Sgp4(tle) }.getOrNull() ?: return null
        val head = positionAt(sgp4, time, observer) ?: return null
        val trail =
            (1..TRAIL_POINTS).mapNotNull { step ->
                positionAt(sgp4, time - TRAIL_STEP * step.toDouble(), observer)
            }
        // Oldest first, so the fade index runs from faintest to brightest.
        return Track(head, trail.reversed())
    }

    private fun positionAt(
        sgp4: Sgp4,
        time: Instant,
        observer: LatLong,
    ): Vector3? {
        val state = sgp4.propagateAt(time) ?: return null
        // J2000, because that is the frame the catalog and everything else on the map is drawn in.
        return SatelliteEphemeris
            .topocentricJ2000(state, time, observer)
            .raDec
            .toGeocentricVector()
    }

    private data class Track(val head: Vector3, val trail: List<Vector3>)

    companion object {
        val LAYER_ID = LayerId("computed/satellites")

        /**
         * Declared once here and read by the layer's own callers (D91).
         *
         * `pass_alerts` is the codebase's first [LayerParameter.Toggle]. It lives with the layer
         * rather than in Settings because a notification about satellites belongs where you engage
         * with satellites — and because turning the layer on to *look* is not the same act as
         * asking to be interrupted.
         */
        val PARAMETERS = listOf(LayerParameter.PASS_ALERTS_PARAMETER)

        /**
         * Which catalog objects to draw.
         *
         * **Not everything the feed returns.** `GROUP=stations` sounds like "the space stations"
         * and is actually 21 objects, verified against a live fetch: seven of them — the ISS
         * modules plus whatever Dragon, Progress and Cygnus are currently docked — carry
         * *identical* orbital elements, because they are physically bolted together. Drawing the
         * feed as-is stacks seven markers and seven labels on one pixel, then does it again with
         * five more at Tiangong, and adds spent upper-stage debris and cubesats far too faint to
         * see.
         *
         * So the layer draws the two objects a naked-eye observer can actually identify. The
         * `fleet` layer parameter in phase 5 is where this becomes a user choice over
         * `GROUP=visual`; until then an explicit list beats showing everything and hoping.
         */
        val TRACKED_NORAD_IDS = setOf(ISS_NORAD_ID, TIANGONG_NORAD_ID)

        /** The International Space Station (ZARYA is the core module the catalog names it by). */
        const val ISS_NORAD_ID = 25544

        /** Tiangong, catalogued under its Tianhe core module. */
        const val TIANGONG_NORAD_ID = 48274

        /** In front of the solar system (60), behind the horizon. */
        private const val DEPTH = 55

        /** The scene rebuild cadence — see the class doc's resubmit-gate note. */
        private val REFRESH_INTERVAL = 1.seconds

        /** Star-white: the ISS looks like a fast steady star; motion is the distinguishing cue. */
        private val MARKER_COLOR = Rgba.WHITE

        private const val MARKER_SIZE_DP = 5.0
        private const val TRAIL_SIZE_DP = 3.0

        /** ~20 seconds of travel: long enough to read as motion, short enough not to clutter. */
        private const val TRAIL_POINTS = 4
        private val TRAIL_STEP = 5.seconds

        /** The brightest trail point stays clearly below the head, so the direction reads. */
        private const val TRAIL_MAX_ALPHA = 0.5
    }
}

private fun Rgba.withAlpha(alpha: Double): Rgba = Rgba(r, g, b, alpha.toFloat())
