/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** The boot receiver is enabled exactly while a pass alert is armed. */
@RunWith(AndroidJUnit4::class)
class PassAlertBootReceiverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pass(hoursAhead: Int): SatellitePass {
        val start = Clock.System.now() + hoursAhead.hours
        return SatellitePass(
            satelliteName = "ISS",
            noradId = 25544,
            start = start,
            culmination = start + 3.minutes,
            end = start + 6.minutes,
            maxAltitudeDeg = 60.0,
            startAzimuthDeg = 270.0,
            endAzimuthDeg = 90.0,
            peakMagnitude = -3.0,
            shadowEntry = null,
        )
    }

    @Before
    fun clean() = PassAlerts.cancel(context)

    /** Leaves the app's own state for whatever runs next: nothing armed, receiver off. */
    @After
    fun restore() = PassAlerts.cancel(context)

    @Test
    fun disabledWhenNothingIsArmed() {
        assertThat(PassAlertBootReceiver.isEnabled(context)).isFalse()
    }

    @Test
    fun enabledOnceAPassIsArmed() {
        PassAlerts.armBestPass(context, listOf(pass(hoursAhead = 3)))
        assertThat(PassAlertBootReceiver.isEnabled(context)).isTrue()
    }

    @Test
    fun staysDisabledWhenNoPassIsWorthArming() {
        PassAlerts.armBestPass(context, emptyList())
        assertThat(PassAlertBootReceiver.isEnabled(context)).isFalse()
    }

    @Test
    fun disabledAgainWhenTheAlertIsCancelled() {
        PassAlerts.armBestPass(context, listOf(pass(hoursAhead = 3)))
        PassAlerts.cancel(context)
        assertThat(PassAlertBootReceiver.isEnabled(context)).isFalse()
    }

    @Test
    fun disabledAfterTheRiseAlertFiresButNotTheAdvanceOne() {
        PassAlerts.armBestPass(context, listOf(pass(hoursAhead = 3)))
        PassAlerts.onAlarmFired(context, atRise = false)
        assertThat(PassAlertBootReceiver.isEnabled(context)).isTrue()
        PassAlerts.onAlarmFired(context, atRise = true)
        assertThat(PassAlertBootReceiver.isEnabled(context)).isFalse()
    }
}
