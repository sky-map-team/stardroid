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
import com.google.android.stardroid.astronomy.KeplerianEphemeris
import com.google.android.stardroid.astronomy.MeeusEphemeris
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.SizeFloor
import com.google.android.stardroid.ui.map.MapViewModel
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SolarSystemLayerTest {
    private val time = Instant.parse("2026-07-03T22:00:00Z")

    private val clock = MutableStateFlow(time)
    private val strings = MutableStateFlow<LayerStrings>(FakeLayerStrings())
    private val location = MutableStateFlow(LONDON)
    private val ephemeris = KeplerianEphemeris

    private fun TestScope.layer(images: BodyImageMapper = PlanetImages()) =
        SolarSystemLayer(
            ephemeris,
            clock,
            strings,
            location,
            images,
            mapContext = UnconfinedTestDispatcher(testScheduler),
        )

    @Test
    fun `scene has an image and a label per body, ordered by descending Earth distance`() =
        runTest {
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)

            assertThat(scene.depth).isEqualTo(60)
            assertThat(scene.images).hasSize(SolarSystemLayer.BODIES.size)
            assertThat(scene.labels).hasSize(SolarSystemLayer.BODIES.size)
            assertThat(scene.points).isEmpty()

            // D18: nearer bodies draw later. The Moon is always nearest, so always last.
            val moonRef = PlanetImages().imageFor(SolarSystemBody.MOON, time)
            assertThat(scene.images.last().image).isEqualTo(moonRef)
            // Every image ref resolves through the planet namespace.
            for (image in scene.images) {
                assertThat(image.image.key).startsWith("planet/")
            }
        }

    @Test
    fun `bodies without an image fall back to a fixed point`() =
        runTest {
            val pointsOnly = BodyImageMapper { _, _ -> null }
            val scene = layer(pointsOnly).buildScene(time, FakeLayerStrings(), LONDON)

            assertThat(scene.images).isEmpty()
            assertThat(scene.points).hasSize(SolarSystemLayer.BODIES.size)
        }

    @Test
    fun `labels rank by the ephemeris magnitude but skip the FOV magnitude gate`() =
        runTest {
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)

            val sun = scene.labels.first { it.text == "SUN" }
            assertThat(sun.priority).isEqualTo(100)
            // Faint bodies (Neptune, Pluto) must stay named at any zoom, so no label in this
            // layer carries a magnitude for the declutterer's FOV threshold to reject.
            assertThat(scene.labels.mapNotNull { it.magnitudeForThresholding }).isEmpty()
        }

    @Test
    fun `every body carries a terminator except the Sun`() =
        runTest {
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)
            val bodies = scene.images

            // The Sun is the light source, not something catching light (D88).
            val sun = bodies.single { it.image == ImageRef("planet/sun") }
            assertThat(sun.terminator).isNull()

            for (image in bodies - sun) {
                val terminator = image.terminator
                assertThat(terminator).isNotNull()
                assertThat(terminator!!.illuminatedFraction).isIn(Range.closed(0.0, 1.0))
                assertThat(terminator.brightLimbAngleDeg).isIn(Range.closedOpen(0.0, 360.0))
            }
        }

    @Test
    fun `re-submits only when a body could have moved perceptibly`() =
        runTest {
            val scenes = collectInBackground(layer().scenes())
            assertThat(scenes).hasSize(1)

            // One second: even the Moon (~15°/day) has moved only ~0.0002°.
            clock.value = time + 1.seconds
            assertThat(scenes).hasSize(1)

            // A day: everything has moved.
            clock.value = time + 1.days
            assertThat(scenes).hasSize(2)

            // A locale change forces a re-emission even with a still clock.
            strings.value = FakeLayerStrings(" [de]")
            assertThat(scenes).hasSize(3)
            assertThat(scenes.last().labels.map { it.text }).contains("MOON [de]")

            // So does a location change — the Moon's parallax depends on the observer (D54).
            location.value = SYDNEY
            assertThat(scenes).hasSize(4)
        }

    @Test
    fun `moon draws topocentrically - it shifts with the observer, the planets don't`() =
        runTest {
            val fromLondon = layer().buildScene(time, FakeLayerStrings(), LONDON)
            val fromSydney = layer().buildScene(time, FakeLayerStrings(), SYDNEY)

            // Diurnal parallax moves the Moon by up to ~1° between far-apart observers.
            val moonShiftDeg =
                separationDeg(fromLondon.images.last().center, fromSydney.images.last().center)
            assertThat(moonShiftDeg).isGreaterThan(0.1)
            assertThat(moonShiftDeg).isLessThan(2.1)

            // Everything else is effectively at infinity and stays put.
            for (i in 0 until fromLondon.images.size - 1) {
                assertThat(
                    separationDeg(fromLondon.images[i].center, fromSydney.images[i].center),
                ).isWithin(1e-9).of(0.0)
            }
        }

    @Test
    fun `angular size is the body's true apparent diameter`() =
        runTest {
            val layer = layer()
            // Half a degree for the Sun, arcseconds for the planets (D86).
            assertThat(layer.angularSizeDeg(SolarSystemBody.SUN, time)).isWithin(0.01).of(0.53)
            assertThat(layer.angularSizeDeg(SolarSystemBody.MARS, time) * 3600).isLessThan(26.0)

            // Saturn spans its ring system, because its texture does.
            val saturnGlobe = MeeusEphemeris.angularDiameterDeg(SolarSystemBody.SATURN, time)
            assertThat(layer.angularSizeDeg(SolarSystemBody.SATURN, time))
                .isWithin(1e-9)
                .of(saturnGlobe * 2.269)
        }

    @Test
    fun `discs are true size, floored only so they stay visible`() =
        runTest {
            // The map draws bodies at their real angular size at every zoom (user decision,
            // 2026-08-14, superseding D86's v1-calibrated floor). Only the absolute dp floor
            // remains, so nothing vanishes.
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)
            for (image in scene.images) {
                assertThat(image.minScreenFraction).isEqualTo(0.0)
                assertThat(image.minSizeDp).isEqualTo(3.0)
            }
        }

    @Test
    fun `zooming in actually grows a planet`() =
        runTest {
            // What the calibrated floor could not do: while it dominated, drawn pixels were
            // minScreenFraction * shortSide, with no field-of-view term at all, so a planet was
            // pinned to a constant size however far you zoomed.
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)
            val jupiter =
                scene.images.single { it.image == ImageRef("planet/jupiter") }

            fun pxAt(fovDeg: Double) =
                SizeFloor.drawnDiameterDeg(
                    jupiter.angularSizeDeg,
                    jupiter.minScreenFraction,
                    jupiter.minSizeDp,
                    fovDeg,
                    960,
                    3.0f,
                ) / fovDeg * 960

            assertThat(pxAt(0.03)).isGreaterThan(pxAt(0.3) * 5)
            assertThat(pxAt(0.3)).isGreaterThan(pxAt(3.0))
        }

    @Test
    fun `the calibration field of view is the one the map opens at`() {
        // The floors are calibrated against a copy of MapViewModel's opening FOV, which a layer
        // must not import. If someone retunes the opening framing, this is what catches it.
        assertThat(SolarSystemLayer.CALIBRATION_FOV_DEG)
            .isWithin(1e-9)
            .of(MapViewModel.INITIAL_FOV_DEG)
    }

    @Test
    fun `rotation stands the image's north up`() =
        runTest {
            // A body on the equator at RA 0. The default quad frame there has its up axis
            // pointing west, so standing the celestial pole up is a quarter turn.
            val onEquator = geometryLayer(moon = RaDec(0.0, 0.0), sun = RaDec(270.0, 0.0))
            assertThat(onEquator.images.last().rotationDeg).isWithin(1e-9).of(90.0)
        }

    @Test
    fun `changing the phase never rotates the surface`() =
        runTest {
            // The bug D88 fixes. v1 rotated the whole bitmap anti-solar, so the Man in the Moon
            // spun through the month; now rotation follows the pole and only the terminator
            // tracks the Sun. Same body position, two very different Sun positions.
            val moon = RaDec(0.0, 0.0)
            val sunEast = geometryLayer(moon = moon, sun = RaDec(60.0, 0.0)).images.last()
            val sunNorth = geometryLayer(moon = moon, sun = RaDec(0.0, 80.0)).images.last()

            assertThat(sunEast.rotationDeg).isWithin(1e-9).of(sunNorth.rotationDeg)
            assertThat(
                abs(
                    sunEast.terminator!!.brightLimbAngleDeg -
                        sunNorth.terminator!!.brightLimbAngleDeg,
                ),
            ).isGreaterThan(45.0)
        }

    @Test
    fun `no eclipse shadow on an ordinary night`() =
        runTest {
            val scene = layer().buildScene(time, FakeLayerStrings(), LONDON)
            val moon = scene.images.single { it.image == ImageRef("planet/moon") }
            assertThat(moon.terminator!!.eclipse).isNull()

            // Nor on any other body, whatever the date.
            for (image in scene.images.filter { it.image != ImageRef("planet/moon") }) {
                assertThat(image.terminator?.eclipse).isNull()
            }
        }

    @Test
    fun `the Moon carries an eclipse shadow during a known total eclipse`() =
        runTest {
            // Greatest eclipse of the 2025-03-14 total lunar eclipse (also exercised in
            // core/astronomy's LunarEclipseTest).
            val eclipseTime = Instant.parse("2025-03-14T06:59:00Z")
            val scene = layer().buildScene(eclipseTime, FakeLayerStrings(), LONDON)
            val shadow =
                scene.images.single { it.image == ImageRef("planet/moon") }.terminator!!.eclipse

            assertThat(shadow).isNotNull()
            assertThat(shadow!!.umbraRadius).isLessThan(shadow.penumbraRadius)
            assertThat(shadow.umbraRadius).isGreaterThan(0.0)
            // Totality: the Moon's centre sits well inside the umbra, whose radius comfortably
            // exceeds the Moon's own (1.0 in these units).
            assertThat(shadow.offset).isLessThan(shadow.umbraRadius)
            assertThat(shadow.umbraRadius).isGreaterThan(1.0)
        }

    @Test
    fun `the lit limb sweeps right around through a lunation`() =
        runTest {
            val limbAngles = mutableListOf<Double>()
            for (day in 0..27 step 3) {
                val scene = layer().buildScene(time + day.days, FakeLayerStrings(), LONDON)
                limbAngles +=
                    scene.images
                        .single { it.image == ImageRef("planet/moon") }
                        .terminator!!
                        .brightLimbAngleDeg
            }
            assertThat(limbAngles.max() - limbAngles.min()).isGreaterThan(180.0)
        }

    private fun TestScope.geometryLayer(
        moon: RaDec,
        sun: RaDec,
    ) = SolarSystemLayer(
        FakeGeometryEphemeris(moon = moon, sun = sun),
        clock,
        strings,
        location,
        BodyImageMapper { _, _ -> ImageRef("planet/x") },
        mapContext = UnconfinedTestDispatcher(testScheduler),
    ).buildScene(time, FakeLayerStrings(), LONDON)

    /** Fixed geometry for the rotation tests: Moon nearest, Sun wherever the test needs it. */
    private class FakeGeometryEphemeris(
        private val moon: RaDec,
        private val sun: RaDec,
    ) : Ephemeris {
        override fun geocentricPosition(
            body: SolarSystemBody,
            time: Instant,
        ): RaDec =
            when (body) {
                SolarSystemBody.MOON -> moon
                SolarSystemBody.SUN -> sun
                else -> RaDec(180.0, 45.0)
            }

        override fun phaseAngleDeg(
            body: SolarSystemBody,
            time: Instant,
        ): Double = 0.0

        override fun illuminatedFraction(
            body: SolarSystemBody,
            time: Instant,
        ): Double = 1.0

        override fun magnitude(
            body: SolarSystemBody,
            time: Instant,
        ): Double = 0.0

        override fun maxAngularVelocityDegPerDay(body: SolarSystemBody): Double = 1.0

        override fun earthDistanceAu(
            body: SolarSystemBody,
            time: Instant,
        ): Double = if (body == SolarSystemBody.MOON) 0.00257 else 1.0

        override val validRange: ClosedRange<Instant> =
            Instant.DISTANT_PAST..Instant.DISTANT_FUTURE
    }

    private fun <T> TestScope.collectInBackground(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { items += it }
        }
        return items
    }

    private fun separationDeg(
        a: Vector3,
        b: Vector3,
    ): Double = acos((a.normalized() dot b.normalized()).coerceIn(-1.0, 1.0)) * RADIANS_TO_DEGREES

    private companion object {
        private val LONDON = LatLong(51.51, -0.13)
        private val SYDNEY = LatLong(-33.87, 151.21)
    }
}
