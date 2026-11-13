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

/** What keeps satellite downloads going without the app being opened (#1272). */
class SatelliteBackgroundUseTest {
    private fun use(
        passAlertsOn: Boolean = false,
        digestOn: Boolean = false,
        notificationsFlagOn: Boolean = true,
        notificationsAllowed: Boolean = true,
        tonightWidgetPlaced: Boolean = false,
    ) = satelliteBackgroundUse(
        passAlertsOn,
        digestOn,
        notificationsFlagOn,
        notificationsAllowed,
        tonightWidgetPlaced,
    )

    @Test
    fun `nothing placed or enabled is no background use`() {
        assertThat(use()).isFalse()
    }

    @Test
    fun `a placed Tonight widget counts`() {
        assertThat(use(tonightWidgetPlaced = true)).isTrue()
        // The widget isn't a notification, so the notification gates don't apply to it.
        val noNotifications =
            use(
                tonightWidgetPlaced = true,
                notificationsFlagOn = false,
                notificationsAllowed = false,
            )
        assertThat(noNotifications).isTrue()
    }

    @Test
    fun `pass alerts count`() {
        assertThat(use(passAlertsOn = true)).isTrue()
    }

    @Test
    fun `the tonight digest counts`() {
        assertThat(use(digestOn = true)).isTrue()
    }

    @Test
    fun `alerts and the digest need the notifications flag`() {
        assertThat(use(passAlertsOn = true, notificationsFlagOn = false)).isFalse()
        assertThat(use(digestOn = true, notificationsFlagOn = false)).isFalse()
    }

    @Test
    fun `alerts and the digest need notifications allowed by the OS`() {
        assertThat(use(passAlertsOn = true, notificationsAllowed = false)).isFalse()
        assertThat(use(digestOn = true, notificationsAllowed = false)).isFalse()
    }
}
