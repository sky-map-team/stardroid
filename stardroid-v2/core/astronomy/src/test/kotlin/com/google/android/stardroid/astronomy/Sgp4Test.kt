/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.astronomy

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

/**
 * SGP4 against the canonical verification vectors from Vallado et al., *Revisiting Spacetrack
 * Report #3* (AIAA 2006-6753), whose `SGP4-VER.TLE` and `tcppver.out` are the fixture every
 * implementation of this algorithm is checked against.
 *
 * This suite is the whole mitigation for the feature's main risk. SGP4 is several hundred lines of
 * unexplained coefficients, and a single mistranscribed term does not crash — it returns
 * plausible-looking positions that are quietly wrong. Only published expected output catches that.
 *
 * The cases are the curated near-earth subset named in docs/design/satellite-tracking.md: each is
 * here because it exercises a branch, not for coverage's sake.
 */
class Sgp4Test {
    @Test
    fun `catalog 00005 Vanguard matches the published vectors`() {
        // The canonical first case of every SGP4 suite: high eccentricity (0.186), propagated
        // three days out, which is where a wrong secular term shows up first.
        assertMatchesVectors(
            line1 = VANGUARD_LINE_1,
            line2 = VANGUARD_LINE_2,
            expected = VANGUARD_VECTORS,
        )
    }

    @Test
    fun `catalog 06251 matches the published vectors`() {
        // Near-earth normal drag at a ~93 minute period — the closest analogue in the fixture to
        // the ISS, and so the case that most directly stands for what this feature tracks.
        assertMatchesVectors(
            line1 = "1 06251U 62025E   06176.82412014  .00008885  00000-0  12808-3 0  3985",
            line2 = "2 06251  58.0579  54.0425 0030035 139.1568 221.1854 15.56387291  6774",
            expected = CATALOG_06251_VECTORS,
        )
    }

    @Test
    fun `catalog 28057 sun-synchronous matches the published vectors`() {
        // Near-polar and near-circular (e = 0.00009): the shape of most of the `visual` group, and
        // the case where the long-period J3 term's 1 + cos(i) divisor is closest to trouble.
        assertMatchesVectors(
            line1 = "1 28057U 03049A   06177.78615833  .00000060  00000-0  35940-4 0  1836",
            line2 = "2 28057  98.4283 247.6961 0000884  88.1964 271.9322 14.35478080140550",
            expected = CATALOG_28057_VECTORS,
        )
    }

    @Test
    fun `catalog 22312 exercises the simplified low-perigee drag branch`() {
        // Perigee below 220 km, so the higher-order drag terms d2/d3/d4 are dropped. A port that
        // ignores this branch still produces sensible-looking numbers, which is exactly why the
        // case is here. Note the vectors start 54.2 minutes after epoch, not zero.
        assertMatchesVectors(
            line1 = "1 22312U 93002D   06094.46235912  .99999999  81888-5  49949-3 0  3953",
            line2 = "2 22312  62.1486  77.4698 0308723 267.9229  88.7392 15.95744531 98783",
            expected = CATALOG_22312_VECTORS,
        )
    }

    @Test
    fun `the simplified-drag branch is the one catalog 22312 actually takes`() {
        // Guards the test above from silently becoming a duplicate of the others: if a refactor
        // ever mis-computes perigee, 22312 would take the full-drag path and this fails loudly
        // rather than the vectors drifting inside tolerance.
        val lowPerigee =
            Sgp4(
                Tle.parse(
                    "1 22312U 93002D   06094.46235912  .99999999  81888-5  49949-3 0  3953",
                    "2 22312  62.1486  77.4698 0308723 267.9229  88.7392 15.95744531 98783",
                ),
            )
        assertThat(lowPerigee.usesSimplifiedDrag).isTrue()
        assertThat(Sgp4(Tle.parse(VANGUARD_LINE_1, VANGUARD_LINE_2)).usesSimplifiedDrag)
            .isFalse()
    }

    @Test
    fun `deep-space element sets are rejected rather than propagated wrongly`() {
        // Catalog 04632 (period ~20 h) and 09880 (~12 h) need SDP4's lunar-solar and resonance
        // terms. SGP4 alone does not degrade on these, it produces grossly wrong positions, so the
        // contract is a refusal at construction. This pins the scope decision in the design doc.
        val molniyaLike =
            Tle.parse(
                "1 09880U 77021A   06176.56157475  .00000421  00000-0  10000-3 0  9814",
                "2 09880  64.5968 349.3786 7069051 270.0229  16.3320  2.00813614112380",
            )
        assertThat(molniyaLike.isDeepSpace).isTrue()
        assertThrows<IllegalArgumentException> { Sgp4(molniyaLike) }

        val highOrbit =
            Tle.parse(
                "1 04632U 70093B   04031.91070959 -.00000084  00000-0  10000-3 0  9955",
                "2 04632  11.4628 273.1101 1450506 207.6000 143.9350  1.20231981 44145",
            )
        assertThat(highOrbit.isDeepSpace).isTrue()
        assertThrows<IllegalArgumentException> { Sgp4(highOrbit) }
    }

    @Test
    fun `a near-earth element set is not mistaken for deep space at the boundary`() {
        // The split is at a 225 minute period. 06251 is ~93 min and the ISS ~93 min, so nothing
        // this app tracks is near the line — but the boundary should still be the documented one.
        val leo =
            Tle.parse(
                "1 06251U 62025E   06176.82412014  .00008885  00000-0  12808-3 0  3985",
                "2 06251  58.0579  54.0425 0030035 139.1568 221.1854 15.56387291  6774",
            )
        assertThat(leo.isDeepSpace).isFalse()
        assertThat(leo.periodMinutes).isWithin(0.5).of(92.5)
    }

    @Test
    fun `propagating from an instant agrees with propagating from minutes`() {
        // The Instant overload is what every non-test caller uses, so the epoch arithmetic that
        // backs it needs pinning: at 7.7 km/s a rounded millisecond would already be metres.
        val tle = Tle.parse(VANGUARD_LINE_1, VANGUARD_LINE_2)
        val sgp4 = Sgp4(tle)
        val minutes = 843.5
        val fromMinutes = checkNotNull(sgp4.propagate(minutes))
        val fromInstant =
            checkNotNull(sgp4.propagateAt(tle.epoch + (minutes * 60.0).seconds))
        assertThat(fromInstant.positionKm.distanceTo(fromMinutes.positionKm)).isLessThan(1e-6)
    }

    @Test
    fun `propagating backwards through the epoch is continuous`() {
        // SGP4 is symmetric about its epoch; negative arguments are as valid as positive ones.
        // A pass search that starts before the element set's epoch relies on this.
        val sgp4 = Sgp4(Tle.parse(VANGUARD_LINE_1, VANGUARD_LINE_2))
        val justBefore = checkNotNull(sgp4.propagate(-0.001))
        val justAfter = checkNotNull(sgp4.propagate(0.001))
        // 0.002 minutes of motion at Vanguard's ~8 km/s is under a kilometre.
        assertThat(justBefore.positionKm.distanceTo(justAfter.positionKm)).isLessThan(1.0)
    }

    /**
     * Checks the propagator against rows of `tcppver.out`, each given as the reference file's own
     * seven whitespace-separated columns: minutes since epoch, then position x/y/z in km, then
     * velocity x/y/z in km/s.
     *
     * The rows are kept as text rather than as typed literals so that a reviewer can diff them
     * straight against the published file — which is the only way to confirm the expected values
     * were not quietly adjusted to match a buggy implementation.
     */
    private fun assertMatchesVectors(
        line1: String,
        line2: String,
        expected: String,
    ) {
        val sgp4 = Sgp4(Tle.parse(line1, line2))
        for (row in expected.lines()) {
            val columns = row.split(" ").map(String::toDouble)
            require(columns.size == 7) { "Expected 7 columns in reference row: $row" }
            val (minutes, x, y, z) = columns
            val state =
                checkNotNull(sgp4.propagate(minutes)) {
                    "propagate($minutes) returned null but the reference has a vector"
                }
            assertThat(state.positionKm.x).isWithin(POSITION_TOLERANCE_KM).of(x)
            assertThat(state.positionKm.y).isWithin(POSITION_TOLERANCE_KM).of(y)
            assertThat(state.positionKm.z).isWithin(POSITION_TOLERANCE_KM).of(z)
            assertThat(state.velocityKmPerSec.x)
                .isWithin(VELOCITY_TOLERANCE_KM_PER_SEC)
                .of(columns[4])
            assertThat(state.velocityKmPerSec.y)
                .isWithin(VELOCITY_TOLERANCE_KM_PER_SEC)
                .of(columns[5])
            assertThat(state.velocityKmPerSec.z)
                .isWithin(VELOCITY_TOLERANCE_KM_PER_SEC)
                .of(columns[6])
        }
    }

    companion object {
        private const val VANGUARD_LINE_1 =
            "1 00005U 58002B   00179.78495062  .00000023  00000-0  28098-4 0  4753"
        private const val VANGUARD_LINE_2 =
            "2 00005  34.2682 348.7242 1859667 331.7664  19.3264 10.82419157413667"

        /**
         * The reference prints eight decimal places of a kilometre and a correct double-precision
         * port agrees to ~1e-6 km. 1e-4 km (10 cm) leaves room for the last-digit differences a
         * different order of operations produces, while failing any real algorithmic slip — a
         * dropped term moves positions by kilometres, four orders of magnitude clear of this.
         */
        private const val POSITION_TOLERANCE_KM = 1.0e-4

        /** The same reasoning at the velocity's nine printed decimal places. */
        private const val VELOCITY_TOLERANCE_KM_PER_SEC = 1.0e-6
    }
}

/**
 * The `00005 xx` block of `tcppver.out`, verbatim: minutes since epoch, then position
 * x/y/z in km, then velocity x/y/z in km/s.
 */
private val VANGUARD_VECTORS =
    """
    0.0 7022.46529266 -1400.08296755 0.03995155 1.893841015 6.405893759 4.534807250
    360.0 -7154.03120202 -3783.17682504 -3536.19412294 4.741887409 -4.151817765 -2.093935425
    1440.0 -938.55923943 -6268.18748831 -4294.02924751 7.536105209 -0.427127707 0.989878080
    2880.0 -8650.73082219 -1914.93811525 -3007.03603443 3.067165127 -4.828384068 -2.515322836
    4320.0 -9060.47373569 4658.70952502 813.68673153 -2.232832783 -4.110453490 -3.157345433
    """.trimIndent()

/**
 * The `06251 xx` block of `tcppver.out`, verbatim: minutes since epoch, then position
 * x/y/z in km, then velocity x/y/z in km/s.
 */
private val CATALOG_06251_VECTORS =
    """
    0.0 3988.31022699 5498.96657235 0.90055879 -3.290032738 2.357652820 6.496623475
    120.0 -3935.69800083 409.10980837 5471.33577327 -3.374784183 -6.635211043 -1.942056221
    1440.0 -2777.14682335 -5663.16031708 -2462.54889123 4.915493146 0.123328992 -5.896495091
    2880.0 1159.27802897 5056.60175495 4353.49418579 -5.968060341 -2.314790406 4.230722669
    """.trimIndent()

/**
 * The `28057 xx` block of `tcppver.out`, verbatim: minutes since epoch, then position
 * x/y/z in km, then velocity x/y/z in km/s.
 */
private val CATALOG_28057_VECTORS =
    """
    0.0 -2715.28237486 -6619.26436889 -0.01341443 -1.008587273 0.422782003 7.385272942
    120.0 -1816.87920942 -1835.78762132 6661.07926465 2.325140071 6.655669329 2.463394512
    1440.0 688.16056594 4124.87618964 5794.55994449 2.810973665 5.479585563 -4.224866316
    2880.0 1788.42334580 1990.50530957 -6640.59337725 -2.074169091 -6.683381288 -2.562777776
    """.trimIndent()

/**
 * The `22312 xx` block of `tcppver.out`, verbatim: minutes since epoch, then position
 * x/y/z in km, then velocity x/y/z in km/s.
 */
private val CATALOG_22312_VECTORS =
    """
    0.0 1442.10132912 6510.23625449 8.83145885 -3.475714837 0.997262768 6.835860345
    54.2028672 306.10478453 -5816.45655525 -2979.55846068 3.950663855 3.415332543 -5.879974329
    234.2028672 815.32034678 -5231.67692249 -3760.04690354 3.870864200 4.455588552 -5.211082191
    474.2028672 -3181.54698042 -3831.29976506 4096.80242787 1.114159970 -6.104773578 -4.829967400
    """.trimIndent()
