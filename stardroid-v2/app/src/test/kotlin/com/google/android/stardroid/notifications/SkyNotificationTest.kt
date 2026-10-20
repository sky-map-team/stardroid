/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SkyNotificationTest {
    @Test
    fun ids_areUnique() {
        val ids = SkyNotification.entries.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }
}
