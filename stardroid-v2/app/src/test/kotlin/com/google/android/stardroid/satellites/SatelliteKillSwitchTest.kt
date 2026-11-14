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
 * What the Android worker tells WorkManager for each flag state. The gate itself is shared and
 * pinned in `SatelliteFlagStateTest`.
 */
class SatelliteKillSwitchTest {
    @Test
    fun `only an unknown flag asks WorkManager to retry, and only on is allowed to download`() {
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.UNKNOWN))
            .isEqualTo(SatelliteWorkAction.RETRY)
        // Off cancels the schedule (#1272), so a later ON resumes only as people open the app.
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.OFF))
            .isEqualTo(SatelliteWorkAction.CANCEL)
        assertThat(satelliteFlagWorkAction(SatelliteFlagState.ON)).isNull()
    }
}
