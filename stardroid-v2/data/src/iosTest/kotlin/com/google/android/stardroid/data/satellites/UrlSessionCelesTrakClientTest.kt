/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class UrlSessionCelesTrakClientTest {
    private fun outcome(
        status: Int,
        body: String? = null,
        lastModified: String? = null,
    ) = fetchOutcomeFor(status, { body }, { lastModified })

    @Test
    fun okWithABodyIsASuccessCarryingLastModified() {
        assertThat(outcome(200, "ISS\n1 25544U", "Sat, 07 Nov 2026 00:00:00 GMT"))
            .isEqualTo(FetchOutcome.Success("ISS\n1 25544U", "Sat, 07 Nov 2026 00:00:00 GMT"))
    }

    @Test
    fun okWithNoUsableBodyIsUnusableNotAnError() {
        assertThat(outcome(200, body = null)).isInstanceOf(FetchOutcome.UnusableBody::class)
    }

    @Test
    fun notModifiedIsNotAnError() {
        assertThat(outcome(304)).isEqualTo(FetchOutcome.NotModified)
    }

    @Test
    fun aRedirectIsAnHttpErrorTheBreakerSees() {
        assertThat(outcome(301)).isEqualTo(FetchOutcome.HttpError(301))
    }

    @Test
    fun otherStatusesAreHttpErrors() {
        assertThat(outcome(403)).isEqualTo(FetchOutcome.HttpError(403))
        assertThat(outcome(503)).isEqualTo(FetchOutcome.HttpError(503))
    }

    @Test
    fun theBodyIsOnlyReadForOk() {
        var read = false
        fetchOutcomeFor(301, {
            read = true
            "x"
        }, {
            read = true
            "y"
        })
        assertThat(read).isFalse()
    }
}
