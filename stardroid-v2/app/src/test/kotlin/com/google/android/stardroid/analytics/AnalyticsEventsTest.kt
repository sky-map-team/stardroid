/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AnalyticsEventsTest {
    @Test
    fun `user property names fit Firebase's 24 character limit`() {
        val userProperties = AnalyticsEvents::class.java.declaredFields
            .filter { it.name in USER_PROPERTY_FIELDS }
            .map { it.apply { isAccessible = true }.get(null) as String }

        assertThat(userProperties).hasSize(USER_PROPERTY_FIELDS.size)
        userProperties.forEach { assertThat(it.length).isAtMost(24) }
    }

    private companion object {
        val USER_PROPERTY_FIELDS = setOf(
            "NEW_USER",
            "DEVICE_SENSORS",
            "HAS_GYRO",
            "HAS_ROTATION_VECTOR",
            "FIRST_INSTALL_VERSION",
            "USER_LOCALE",
            "COMPLETED_WARM_WELCOME",
        )
    }
}
