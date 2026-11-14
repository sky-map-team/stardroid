/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.android.stardroid.astronomy.SpaceStations

/**
 * One object to fetch from CelesTrak, by NORAD catalog number. "Download only what you need" is
 * part of the usage policy, so this is a closed set of the objects the app draws rather than a
 * group or free-form query.
 */
enum class SatelliteTarget(
    val catalogNumber: Int,
) {
    /** The International Space Station. */
    ISS(SpaceStations.ISS_NORAD_ID),

    /** Tiangong, catalogued under its Tianhe core module. */
    CSS(SpaceStations.TIANGONG_NORAD_ID),
}

/**
 * Fetches one object's orbital elements from CelesTrak, as OMM CSV (a header row and a data row).
 *
 * An interface with one method, so the circuit breaker and repository can be tested against a
 * fake without a socket. That testability is the whole reason a real HTTP client library was not
 * worth adding for a single ~0.5 KB GET per object every 12 hours.
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
        target: SatelliteTarget,
        ifModifiedSince: String?,
    ): FetchOutcome
}
