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

/** v1 `SessionBucketLength` boundaries: each bound is exclusive below, inclusive above. */
class SessionBucketTest {
    @Test
    fun `buckets match v1's boundaries`() {
        assertThat(SessionBucket.forSeconds(0)).isEqualTo(SessionBucket.LESS_THAN_TEN_SECS)
        assertThat(SessionBucket.forSeconds(9)).isEqualTo(SessionBucket.LESS_THAN_TEN_SECS)
        assertThat(SessionBucket.forSeconds(10)).isEqualTo(SessionBucket.TEN_SECS_TO_THIRTY_SECS)
        assertThat(SessionBucket.forSeconds(30)).isEqualTo(SessionBucket.THIRTY_SECS_TO_ONE_MIN)
        assertThat(SessionBucket.forSeconds(59)).isEqualTo(SessionBucket.THIRTY_SECS_TO_ONE_MIN)
        assertThat(SessionBucket.forSeconds(60)).isEqualTo(SessionBucket.ONE_MIN_TO_FIVE_MINS)
        assertThat(SessionBucket.forSeconds(299)).isEqualTo(SessionBucket.ONE_MIN_TO_FIVE_MINS)
        assertThat(SessionBucket.forSeconds(300)).isEqualTo(SessionBucket.MORE_THAN_FIVE_MINS)
        assertThat(SessionBucket.forSeconds(Int.MAX_VALUE - 1))
            .isEqualTo(SessionBucket.MORE_THAN_FIVE_MINS)
    }
}
