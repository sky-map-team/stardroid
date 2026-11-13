/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Who may schedule the CelesTrak download (#1272). The gates themselves are pinned in
 * `SatelliteFetchConditionsTest`; this is the rule that 2.3.0 lacked, which let a reboot or a
 * clock change schedule downloads for installs nobody used (#1269).
 */
class SatelliteScheduleActionTest {
    @Test
    fun `a background-only process start never schedules`() {
        assertThat(satelliteScheduleAction(allowed = true, userVisible = false))
            .isEqualTo(SatelliteScheduleAction.LEAVE)
    }

    @Test
    fun `a background-only process start still cancels when a gate is closed`() {
        assertThat(satelliteScheduleAction(allowed = false, userVisible = false))
            .isEqualTo(SatelliteScheduleAction.CANCEL)
    }

    @Test
    fun `a session the user sees schedules when every gate is open`() {
        assertThat(satelliteScheduleAction(allowed = true, userVisible = true))
            .isEqualTo(SatelliteScheduleAction.SCHEDULE)
    }

    @Test
    fun `a session the user sees cancels when a gate is closed`() {
        assertThat(satelliteScheduleAction(allowed = false, userVisible = true))
            .isEqualTo(SatelliteScheduleAction.CANCEL)
    }
}
