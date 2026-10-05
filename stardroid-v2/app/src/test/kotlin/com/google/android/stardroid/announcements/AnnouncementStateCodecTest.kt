/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.announcements

import com.google.android.stardroid.announcements.DataStoreAnnouncementState.Entry
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class AnnouncementStateCodecTest {
    @Test
    fun `round trips shown surfaces, dismissal and first-seen`() {
        val entries =
            mapOf(
                "a" to
                    Entry(
                        SeenRecord(setOf(Surface.NOTIFICATION, Surface.INTERSTITIAL), false),
                        Instant.fromEpochSeconds(1_000),
                    ),
                "b" to Entry(SeenRecord(emptySet(), true), Instant.fromEpochSeconds(2_000)),
            )
        assertThat(
            DataStoreAnnouncementState.decode(DataStoreAnnouncementState.encode(entries)),
        ).isEqualTo(entries)
    }

    @Test
    fun `unreadable or missing value decodes to nothing seen`() {
        assertThat(DataStoreAnnouncementState.decode(null)).isEmpty()
        assertThat(DataStoreAnnouncementState.decode("{oops")).isEmpty()
        assertThat(DataStoreAnnouncementState.decode("[1]")).isEmpty()
    }
}
