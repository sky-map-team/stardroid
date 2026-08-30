/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.astronomy.Ephemeris
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.astronomy.brightLimbAngleDeg
import com.google.android.stardroid.astronomy.equatorialRadiusKm
import com.google.android.stardroid.astronomy.moonShadowSeparationDeg
import com.google.android.stardroid.astronomy.shadowCone
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.EclipseShadow
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Terminator
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.time.DurationUnit

/**
 * Maps a body at an instant to its image, or `null` for a plain dot — the app-side imagery
 * policy the layer stays agnostic of (layers-and-app.md's `BodyImageMapper`): [PlanetImages]
 * gives every body its disc; a future "show planetary images" preference swaps in a mapper that
 * returns `null` for everything but the Sun and Moon, as v1's did.
 */
fun interface BodyImageMapper {
    fun imageFor(
        body: SolarSystemBody,
        time: Instant,
    ): ImageRef?
}

/**
 * The Sun, Moon, and planets, ported from v1's `SolarSystemLayer`/`SolarSystemRenderable`:
 * per-body positions from [ephemeris], drawn as world-anchored images (or dots where [images]
 * declines) with a name label.
 *
 * Positions are topocentric for the observer at [location] (D54): the Moon's diurnal parallax
 * moves it by up to ~1° depending on where on Earth you stand — v1 drew it geocentrically,
 * visibly off against nearby stars. Every other body falls through to its geocentric position.
 *
 * Ordering (D18): all primitives sort by **descending Earth distance at each submission**, so
 * during a conjunction or eclipse the nearer body draws last and occludes — v1's static enum
 * order got this wrong when Mercury or Venus passed behind the Sun.
 *
 * Re-submission cadence (D13): the scene recomputes on a [clock] emission only once some body
 * could have moved ≥ [RESUBMIT_THRESHOLD_DEG] since the last computation (from
 * [Ephemeris.maxAngularVelocityDegPerDay] — in practice the Moon dominates), or when the
 * locale or the observer's location changes. Time travel just feeds this flow bigger jumps.
 */
class SolarSystemLayer(
    private val ephemeris: Ephemeris,
    private val clock: Flow<Instant>,
    private val strings: Flow<LayerStrings>,
    private val location: Flow<LatLong>,
    private val images: BodyImageMapper,
    private val discSize: Flow<String> =
        flowOf(LayerParameter.DISC_SIZE_PARAMETER.defaultOption),
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH
    override val parameters = PARAMETERS

    override fun scenes(): Flow<LayerScene> =
        flow {
            var lastTime: Instant? = null
            var lastStrings: LayerStrings? = null
            var lastLocation: LatLong? = null
            var lastDiscSize: String? = null
            combine(clock, strings, location, discSize) { time, str, loc, size ->
                Scene(time, str, loc, size)
            }
                .collect { (time, str, loc, size) ->
                    if (lastStrings !== str ||
                        lastLocation != loc ||
                        lastDiscSize != size ||
                        due(lastTime, time)
                    ) {
                        lastTime = time
                        lastStrings = str
                        lastLocation = loc
                        lastDiscSize = size
                        emit(buildScene(time, str, loc, size))
                    }
                }
        }.flowOn(mapContext)

    private fun due(
        last: Instant?,
        now: Instant,
    ): Boolean {
        if (last == null) return true
        val elapsedDays = abs((now - last).toDouble(DurationUnit.DAYS))
        return BODIES.any {
            elapsedDays * ephemeris.maxAngularVelocityDegPerDay(it) >= RESUBMIT_THRESHOLD_DEG
        }
    }

    /** The inputs a scene is rebuilt from; a parameter change counts, like a locale change. */
    private data class Scene(
        val time: Instant,
        val strings: LayerStrings,
        val location: LatLong,
        val discSize: String,
    )

    internal fun buildScene(
        time: Instant,
        strings: LayerStrings,
        observer: LatLong,
        discSize: String = LayerParameter.DISC_SIZE_PARAMETER.defaultOption,
    ): LayerScene {
        val byDistance =
            BODIES.map { it to ephemeris.earthDistanceAu(it, time) }
                .sortedByDescending { it.second }
                .map { it.first }
        // Time-only, and identical for all ten bodies: computing it inside the loop ran the
        // full solar series (and a precession matrix build) ten times per scene (audit-2026-08
        // M1).
        val sunPosition = ephemeris.geocentricPosition(SolarSystemBody.SUN, time)
        val points = ArrayList<PointPrimitive>()
        val imagePrimitives = ArrayList<ImagePrimitive>()
        val labels = ArrayList<LabelPrimitive>()
        for (body in byDistance) {
            val pos = ephemeris.topocentricPosition(body, time, observer).toGeocentricVector()
            val image = images.imageFor(body, time)
            if (image != null) {
                imagePrimitives +=
                    ImagePrimitive(
                        center = pos,
                        angularSizeDeg = drawnSizeDeg(body, time, discSize),
                        rotationDeg = northPoleAngleDeg(pos),
                        image = image,
                        terminator = terminatorFor(body, time, sunPosition),
                        minScreenFraction = minScreenFraction(body, time, discSize),
                        minSizeDp = MIN_BODY_SIZE_DP,
                    )
            } else {
                points += PointPrimitive(pos, PointAppearance.Fixed(POINT_COLOR, POINT_SIZE_DP))
            }
            val magnitude = ephemeris.magnitude(body, time)
            // Bodies drawn as images are angularly sized, so their labels carry an angular
            // clearance floor (past the disc's edge); point-drawn bodies use the standard
            // screen-space gap.
            // Clearance follows the *drawn* size, which only the backend knows, so the label
            // carries the same floors and the label drawer applies the same formula. Otherwise a
            // name would sit inside a floored — or a true-scale — Moon (D86).
            val clearanceDeg =
                if (image != null) drawnSizeDeg(body, time, discSize) * LABEL_CLEARANCE else 0.0
            labels +=
                LabelPrimitive(
                    pos = pos,
                    text = strings.bodyName(body),
                    style =
                        LabelStyle(
                            LabelSize.STANDARD,
                            LABEL_COLOR,
                            clearanceDeg = clearanceDeg,
                            clearanceMinScreenFraction =
                                if (image != null) {
                                    minScreenFraction(body, time, discSize) * LABEL_CLEARANCE
                                } else {
                                    0.0
                                },
                            clearanceMinSizeDp =
                                if (image != null) MIN_BODY_SIZE_DP * LABEL_CLEARANCE else 0.0,
                        ),
                    priority = CatalogLayers.labelPriority(magnitude),
                    // Exempt from the FOV→magnitude gate, the way named deep-sky objects
                    // effectively are via their bonus: there are only ten bodies here, and a
                    // user who turns the planet layer on wants Neptune (mag ~7.8) and Pluto
                    // (~14) named, not silently dropped for being faint. The true magnitude
                    // still sets the decluttering priority above, so a planet label never wins
                    // a screen-space conflict against a brighter star.
                    magnitudeForThresholding = null,
                )
        }
        return LayerScene(depth = depth, points = points, images = imagePrimitives, labels = labels)
    }

    /**
     * Rotation that stands the image's north up, expressed against the backend's default image
     * frame (`ImageDrawer.quadCorners`: up toward the world Y axis).
     *
     * The discs are authored with the body's north pole up (`assets/planets/SOURCES.md`), so
     * aligning the quad's up axis with the celestial pole's tangential direction at the body puts
     * the surface where it belongs — and, unlike v1, keeps it there. v1 rotated the whole bitmap
     * anti-solar, which was only ever coherent because the terminator was baked into the same
     * pixels; the side effect was that the Man in the Moon spun through the month (D88).
     *
     * The body's own pole is approximated by the celestial pole, which is also the reference
     * `brightLimbAngleDeg` measures from, so the two stay consistent. For the Moon the true axis
     * wanders up to a couple of dozen degrees from that over a year; correcting it needs the
     * physical-libration terms that D88 left out along with libration itself.
     */
    private fun northPoleAngleDeg(body: Vector3): Double {
        val defaultCross = body cross WORLD_UP
        val poleCross = body cross CELESTIAL_POLE
        if (defaultCross.length2 < POLE_TOL || poleCross.length2 < FRAME_TOL) return 0.0
        val horizontal = -defaultCross.normalized()
        val vertical = horizontal cross body
        // The pole's tangential direction at the body. (Note the sign: v1's anti-solar helper
        // negated this, which was invisible because the lit side was baked into the bitmap.)
        val poleUp = poleCross.normalized() cross body
        // quadCorners rotates up to `-horizontal·sin(rot) + vertical·cos(rot)`; solving that for
        // poleUp gives the angle below.
        return atan2(-(poleUp dot horizontal), poleUp dot vertical) * RADIANS_TO_DEGREES
    }

    /**
     * How much of [body] is lit and which way its lit limb faces, or null for the Sun, which is
     * the light source rather than something catching it.
     *
     * Applied to every body: for the outer planets it costs nothing, since the backend skips
     * compositing above 99.5% illumination, and it earns its keep on Mercury and Venus, which
     * show phases as pronounced as the Moon's (D88 §5). Mars picks up its gibbous phase — down to
     * about 84% — for free.
     */
    private fun terminatorFor(
        body: SolarSystemBody,
        time: Instant,
        sun: RaDec,
    ): Terminator? {
        if (body == SolarSystemBody.SUN) return null
        return Terminator(
            illuminatedFraction = ephemeris.illuminatedFraction(body, time),
            brightLimbAngleDeg = brightLimbAngleDeg(ephemeris.geocentricPosition(body, time), sun),
            eclipse = if (body == SolarSystemBody.MOON) eclipseShadowFor(time) else null,
        )
    }

    /**
     * The Moon's position relative to Earth's shadow at [time], in Moon-radii units so the
     * GLES1 backend's phase compositor needs no astronomy of its own (D106) — or null on the
     * great majority of nights, when the Moon's disc doesn't overlap the penumbra at all.
     *
     * Always [MeeusEphemeris], regardless of [ephemeris]: eclipse geometry needs the accurate,
     * frame-consistent Sun/Moon positions (D84) this layer's own baseline isn't tuned for.
     */
    private fun eclipseShadowFor(time: Instant): EclipseShadow? {
        val moonRadiusDeg = MeeusEphemeris.angularDiameterDeg(SolarSystemBody.MOON, time) / 2.0
        if (moonRadiusDeg <= 0.0) return null
        val cone = shadowCone(time, MeeusEphemeris)
        val penumbraRadius = cone.penumbraRadiusDeg / moonRadiusDeg
        val offset = moonShadowSeparationDeg(time, MeeusEphemeris) / moonRadiusDeg
        // No overlap when the gap between the disc's near edge and the penumbra's edge is
        // positive, i.e. the two circles (radii `penumbraRadius` and 1, centres `offset` apart)
        // are disjoint.
        if (offset - penumbraRadius >= 1.0) return null
        val moon = MeeusEphemeris.geocentricPosition(SolarSystemBody.MOON, time)
        return EclipseShadow(
            umbraRadius = cone.umbraRadiusDeg / moonRadiusDeg,
            penumbraRadius = penumbraRadius,
            offset = offset,
            directionDeg = brightLimbAngleDeg(moon, cone.antiSolarDirection),
        )
    }

    /**
     * The angular diameter the primitive carries, which is the body's true apparent size in every
     * mode but [LayerParameter.DISC_SIZE_GLYPHS] (D86, D87).
     *
     * Saturn is scaled up to span its ring system, because its texture does
     * (`assets/planets/SOURCES.md`): the rings reach 2.269 equatorial radii, and sizing to the
     * globe alone would draw them half the size the art expects.
     */
    internal fun angularSizeDeg(
        body: SolarSystemBody,
        time: Instant,
    ): Double {
        val disc = ephemeris.angularDiameterDeg(body, time)
        return if (body == SolarSystemBody.SATURN) disc * SATURN_RING_SPAN else disc
    }

    /**
     * The angular size for [discSize] mode. "Glyphs" is v1 exactly: a fixed angular size, which
     * grows on screen as you zoom in and never becomes honest. The other two modes carry the true
     * size and differ only in their floor.
     */
    private fun drawnSizeDeg(
        body: SolarSystemBody,
        time: Instant,
        discSize: String,
    ): Double =
        if (discSize == LayerParameter.DISC_SIZE_GLYPHS) {
            glyphSizeDeg(body)
        } else {
            angularSizeDeg(body, time)
        }

    /**
     * The floor below which [body] is not allowed to shrink, as a fraction of the short viewport
     * side — the one thing that separates the three disc-size modes (D87).
     *
     * Only "Auto" uses it, and it is worth being precise about what it does: while the fractional
     * floor dominates, drawn pixels are `minScreenFraction × shortSide`, with no field-of-view
     * term, so the body holds a **constant** share of the screen and zooming appears to do
     * nothing to it until the crossover. That is the mode's whole character — stable and
     * uncluttered — and it is also why it is not the default. True scale and Glyphs both grow
     * with zoom; Auto is the only one that does not.
     */
    private fun minScreenFraction(
        body: SolarSystemBody,
        time: Instant,
        discSize: String,
    ): Double =
        if (discSize == LayerParameter.DISC_SIZE_AUTO) {
            glyphSizeDeg(body) / CALIBRATION_FOV_DEG
        } else {
            0.0
        }

    /**
     * The size a body is drawn at when it is not drawn to scale: its **physical** size,
     * compressed — an orrery rather than a view through a telescope.
     *
     * v1's fixed sizes had no principle behind them at all: Saturn was drawn larger than the Sun,
     * and Uranus larger than Venus. Ranking by physical size teaches the fact worth knowing, and
     * the one that does not change with the date: Jupiter is a giant whether it is at opposition
     * or right across the solar system.
     *
     * A power law well under one is what makes that fit on a screen: the Sun is 586 times Pluto's
     * diameter, which nothing can show honestly, and an exponent of 0.3 compresses it to about
     * 7:1 with every body still correctly ranked. Pluto anchors the small end at 0.6°, big enough
     * to read as a disc rather than a dot.
     *
     * **The Moon is the deliberate exception**, drawn at the Sun's size. Physically it is the
     * runt of the set — smaller than Mercury — but it is one of the two things people open the
     * app to look at, and the sky's most famous coincidence is that the two appear the same size.
     * Ranking it honestly would shrink the most-looked-at object to a third of its old size to
     * make a point nobody came for.
     */
    private fun glyphSizeDeg(body: SolarSystemBody): Double {
        if (body == SolarSystemBody.MOON) return glyphSizeDeg(SolarSystemBody.SUN)
        val globe = compressedGlobeDeg(body)
        // Saturn's texture spans its rings, so the *globe* is what has to be ranked and the
        // rings follow it. Compressing the ring span instead drew the globe at 1.10° against
        // Uranus's 1.51° — Saturn's body smaller than Uranus, which is 2.4 times narrower.
        return if (body == SolarSystemBody.SATURN) globe * SATURN_RING_SPAN else globe
    }

    /** The body's own disc, compressed onto the glyph ladder. */
    private fun compressedGlobeDeg(body: SolarSystemBody): Double =
        GLYPH_SMALLEST_DEG *
            (2.0 * equatorialRadiusKm(body) / GLYPH_SMALLEST_KM).pow(GLYPH_EXPONENT)

    companion object {
        val LAYER_ID = LayerId("computed/solar_system")

        /**
         * What this layer lets the user choose (D87), declared once. The instance's
         * [parameters] and the sheet's static list (`LayerRegistry.PARAMETERS`) are both this
         * object rather than copies of it — D91: a layer owns its parameters end-to-end.
         *
         * `eclipse_alerts` (D106) is read by its own caller (`SkyMapApplication`), the same
         * shape `SatelliteLayer.PARAMETERS`' `pass_alerts` already uses — it isn't threaded into
         * this layer's own constructor because it affects a notification, not anything drawn.
         */
        val PARAMETERS =
            listOf(LayerParameter.DISC_SIZE_PARAMETER, LayerParameter.ECLIPSE_ALERTS_PARAMETER)

        /**
         * The registry's entry point (D91): the layer reads its own parameter out of
         * [settings], so nothing above it has to know that a disc size exists. Direct
         * construction stays available for tests and the dev test scene, which supply the mode
         * themselves.
         */
        fun create(
            ephemeris: Ephemeris,
            clock: Flow<Instant>,
            strings: Flow<LayerStrings>,
            location: Flow<LatLong>,
            images: BodyImageMapper,
            settings: Settings,
        ): SolarSystemLayer =
            SolarSystemLayer(
                ephemeris,
                clock,
                strings,
                location,
                images,
                settings.layerParameter(
                    LAYER_ID,
                    LayerParameter.DISC_SIZE_PARAMETER.key,
                    LayerParameter.DISC_SIZE_PARAMETER.defaultOption,
                ),
            )

        /**
         * The field of view the size floors are calibrated against — the one the map opens at.
         * Mirrors `MapViewModel.INITIAL_FOV_DEG`, which a layer has no business importing;
         * `SolarSystemLayerTest` fails if the two drift apart.
         */
        internal const val CALIBRATION_FOV_DEG = 21.1

        /**
         * Compression exponent for glyph sizes. 0.3 squeezes the 586:1 physical range between the
         * Sun and Pluto to under 7:1, which fits a screen while keeping every body ranked. Lower
         * flattens the ladder until the planets are indistinguishable; higher stretches it until
         * the small ones are dots again.
         */
        private const val GLYPH_EXPONENT = 0.3

        /** Pluto anchors the small end, large enough to read clearly as a disc. */
        private const val GLYPH_SMALLEST_DEG = 0.6

        /** Pluto's equatorial diameter in km, the reference the ladder is built from. */
        private const val GLYPH_SMALLEST_KM = 2376.6

        /**
         * Saturn's A ring reaches 2.269 equatorial radii, and the texture spans it, so the
         * primitive's angular size has to as well.
         */
        private const val SATURN_RING_SPAN = 2.269

        /**
         * The absolute floor, in dp. D86 chose 3 rather than "at least one pixel" so a floored
         * body is genuinely visible and tappable, not a single physical pixel on a 3x screen.
         */
        private const val MIN_BODY_SIZE_DP = 3.0

        /** A name clears its disc by this fraction of the drawn diameter. */
        private const val LABEL_CLEARANCE = 0.6

        /** v1 depth table: in front of the ecliptic, behind the horizon. */
        private const val DEPTH = 60

        /** Everything viewable — Earth is the observer, not a target (D16 keeps Pluto). */
        internal val BODIES = SolarSystemBody.entries.filter { it != SolarSystemBody.EARTH }

        /**
         * Minimum possible apparent motion before a recompute is worth it. The Moon
         * (15°/day ≈ 0.01°/min) crosses this in about a minute — v1's Moon update cadence.
         */
        private const val RESUBMIT_THRESHOLD_DEG = 0.01

        /** Must match the GLES1 `ImageDrawer` default frame's world up. */
        private val WORLD_UP = Vector3.UNIT_Y

        /** +Z is the north celestial pole in the geocentric equatorial frame (`RaDec`). */
        private val CELESTIAL_POLE = Vector3.UNIT_Z

        /**
         * sin²(8°): below this the moon is near the pole and the tangent frame is degenerate,
         * matching ImageDrawer's own pole fallback threshold (D30).
         */
        private const val POLE_TOL = 0.01937

        /** sin²(1°): below this the anti-solar direction is collinear; keep default rotation. */
        private const val FRAME_TOL = 3.05e-4

        /** Upstream `sky_label` Planet Red (D40). */
        private val LABEL_COLOR = SkyColors.SKY_LABEL

        /** Upstream `planet_body` dot fallback at point size 3 (D40). */
        private val POINT_COLOR = SkyColors.PLANET_BODY
        private const val POINT_SIZE_DP = 3.0
    }
}
