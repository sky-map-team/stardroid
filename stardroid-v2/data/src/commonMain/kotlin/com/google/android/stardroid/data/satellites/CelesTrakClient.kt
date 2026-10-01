/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

/**
 * Which CelesTrak group to fetch. "Download only what you need" is part of the usage policy, so
 * this is a closed set rather than a free-form query.
 */
enum class SatelliteGroup(
    val queryValue: String,
) {
    /** ISS, Tiangong and station-adjacent objects — about 1 KB. What ships first. */
    STATIONS("stations"),

    /** The ~100 brightest objects, about 50 KB. Phase 5 only. */
    VISUAL("visual"),
}

/**
 * Fetches two-line element sets from CelesTrak.
 *
 * An interface with one method, so the circuit breaker and repository can be tested against a
 * fake without a socket. That testability is the whole reason a real HTTP client library was not
 * worth adding for a single ~1 KB GET every 12 hours.
 *
 * Each platform implements it over its own HTTP stack — [HttpUrlConnectionCelesTrakClient] on
 * Android; on iOS, URLSession once satellites ship there — rather than sharing a multiplatform
 * HTTP library that Android would then carry too (D127).
 */
fun interface CelesTrakClient {
    /**
     * One GET. Implementations must never retry internally — retry policy belongs to
     * [SatelliteFetchPolicy], and a library that helpfully retries is precisely what CelesTrak's
     * usage policy forbids.
     */
    fun fetch(
        group: SatelliteGroup,
        ifModifiedSince: String?,
    ): FetchOutcome
}
