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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Shadow-cone geometry and eclipse-circumstance search.
 *
 * The 2025-03-14 case is a well-documented total lunar eclipse (widely reported across the
 * Americas); the assertion here is deliberately date-level, not minute-level, since it is a
 * memory-based reference rather than a value transcribed from a NASA/USNO table. Tightening this
 * to published contact times (as [LunarPhaseTest] does for phase events) is a follow-up, not done
 * here for lack of a verifiable source in this environment.
 */
class LunarEclipseTest {
    @Test
    fun shadowCone_umbraSmallerThanPenumbra() {
        val cone = shadowCone(utc(2025, 3, 14, 6))
        assertThat(cone.umbraRadiusDeg).isLessThan(cone.penumbraRadiusDeg)
        // Earth's shadow at the Moon's distance is on the order of a degree across, not tiny and
        // not huge — a sanity bound against a unit or formula error.
        assertThat(cone.umbraRadiusDeg).isGreaterThan(0.3)
        assertThat(cone.umbraRadiusDeg).isLessThan(1.2)
        assertThat(cone.penumbraRadiusDeg).isGreaterThan(cone.umbraRadiusDeg)
        assertThat(cone.penumbraRadiusDeg).isLessThan(2.0)
    }

    @Test
    fun nextLunarEclipse_findsTheKnown2025TotalEclipse() {
        val eclipse = nextLunarEclipse(utc(2025, 3, 1))
        assertThat(eclipse).isNotNull()
        assertThat(eclipse!!.type).isEqualTo(LunarEclipseType.TOTAL)
        assertThat(eclipse.umbralMagnitude).isGreaterThan(1.0)
        // Greatest eclipse was in the early hours of 2025-03-14 UTC; generous window because this
        // reference is a recollection, not a transcribed published time (see class doc).
        val expected = utc(2025, 3, 14, 7)
        assertThat((eclipse.greatestEclipse - expected).absoluteValue).isLessThan(20.hours)
    }

    @Test
    fun lunarEclipseNear_mostFullMoonsHaveNoEclipse() {
        // An arbitrary full moon well clear of any eclipse season.
        val fullMoon = nextLunarPhaseEvent(LunarPhase.FULL, utc(2024, 4, 1))
        val eclipse = lunarEclipseNear(fullMoon)
        assertThat(eclipse.type).isEqualTo(LunarEclipseType.NONE)
        assertThat(eclipse.penumbralBegin).isNull()
        assertThat(eclipse.umbralBegin).isNull()
        assertThat(eclipse.totalityBegin).isNull()
    }

    @Test
    fun lunarEclipseNear_contactsAreOrderedAroundGreatestEclipse() {
        val eclipse = nextLunarEclipse(utc(2025, 3, 1))!!
        assertThat(eclipse.type).isEqualTo(LunarEclipseType.TOTAL)

        val p1 = eclipse.penumbralBegin!!
        val u1 = eclipse.umbralBegin!!
        val u2 = eclipse.totalityBegin!!
        val u3 = eclipse.totalityEnd!!
        val u4 = eclipse.umbralEnd!!
        val p4 = eclipse.penumbralEnd!!

        assertThat(p1).isLessThan(u1)
        assertThat(u1).isLessThan(u2)
        assertThat(u2).isLessThan(eclipse.greatestEclipse)
        assertThat(eclipse.greatestEclipse).isLessThan(u3)
        assertThat(u3).isLessThan(u4)
        assertThat(u4).isLessThan(p4)
    }

    @Test
    fun nextLunarEclipse_isStrictlyAfterAndWithinThreeYears() {
        val after = utc(2024, 6, 1, 12)
        val eclipse = nextLunarEclipse(after)
        assertThat(eclipse).isNotNull()
        assertThat(eclipse!!.greatestEclipse).isGreaterThan(after)
        assertThat((eclipse.greatestEclipse - after).inWholeDays).isLessThan(3 * 365)
    }

    @Test
    fun nextLunarEclipse_typeMatchesMagnitudeSign() {
        var after = utc(2024, 1, 1)
        repeat(5) {
            val eclipse = nextLunarEclipse(after)!!
            when (eclipse.type) {
                LunarEclipseType.TOTAL -> assertThat(eclipse.umbralMagnitude).isAtLeast(1.0)
                LunarEclipseType.PARTIAL -> {
                    assertThat(eclipse.umbralMagnitude).isGreaterThan(0.0)
                    assertThat(eclipse.umbralMagnitude).isLessThan(1.0)
                }
                LunarEclipseType.PENUMBRAL -> {
                    assertThat(eclipse.umbralMagnitude).isAtMost(0.0)
                    assertThat(eclipse.penumbralMagnitude).isGreaterThan(0.0)
                }
                LunarEclipseType.NONE -> throw AssertionError("nextLunarEclipse returned NONE")
            }
            // Advance past this eclipse's whole window, not just its greatest moment, so each
            // iteration finds a genuinely different eclipse.
            after = eclipse.penumbralEnd!! + 1.hours
        }
    }

    @Test
    fun nextLunarEclipse_findsAnEclipseAlreadyInProgress() {
        val fromScratch = nextLunarEclipse(utc(2025, 3, 1))!!
        // Ask again from a moment inside the eclipse's own window (after greatest eclipse, but
        // before it fully ends) — the same eclipse must still come back, not next month's full
        // moon. This is the scenario a user opening the app mid-eclipse hits.
        val midEclipse = fromScratch.greatestEclipse + 30.minutes
        val found = nextLunarEclipse(midEclipse)
        assertThat(found).isNotNull()
        // The golden-section search converges to the same root from either starting bracket, but
        // not bit-for-bit — a sub-second tolerance distinguishes "found the same eclipse" from
        // "skipped ahead to next month's", which is the actual regression this test guards.
        val diff = (found!!.greatestEclipse - fromScratch.greatestEclipse).absoluteValue
        assertThat(diff.inWholeSeconds).isLessThan(5)
    }
}
