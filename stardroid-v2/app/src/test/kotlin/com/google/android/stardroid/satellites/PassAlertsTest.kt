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
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PassAlertsTest {
    private val passStart = Instant.parse("2026-10-15T06:25:00Z")

    @Test
    fun `an alert fired at its lead time is not stale`() {
        assertThat(PassAlerts.isStale(passStart, passStart - PassAlerts.LEAD_TIME)).isFalse()
    }

    @Test
    fun `an alert delayed but still before the pass is not stale`() {
        assertThat(PassAlerts.isStale(passStart, passStart - 1.minutes)).isFalse()
    }

    @Test
    fun `an alert delivered once the pass has begun is stale`() {
        assertThat(PassAlerts.isStale(passStart, passStart)).isTrue()
    }

    @Test
    fun `an alert delivered hours late is stale`() {
        assertThat(PassAlerts.isStale(passStart, passStart + 3.hours)).isTrue()
    }
}
