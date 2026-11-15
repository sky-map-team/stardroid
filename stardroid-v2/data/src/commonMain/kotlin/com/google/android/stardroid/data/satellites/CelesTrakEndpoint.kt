/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

/** Where and as whom every platform's [CelesTrakClient] asks, so the two can't drift apart. */
object CelesTrakEndpoint {
    const val GP_URL = "https://celestrak.org/NORAD/elements/gp.php"

    /** OMM CSV, not TLE: TLEs can't carry the 6-digit catalog numbers now being issued. */
    fun gpUrl(
        baseUrl: String,
        target: SatelliteTarget,
    ) = "$baseUrl?CATNR=${target.catalogNumber}&FORMAT=CSV"

    /**
     * Descriptive, contactable and versioned, so a host can tell releases and platforms apart.
     * Only the number of [appVersionName] is sent, not a release name ("2.3.2:Cassini").
     */
    fun userAgent(
        appVersionName: String,
        platform: String,
    ): String {
        val version = appVersionName.substringBefore(':').trim().ifEmpty { "unknown" }
        return "SkyMap/$version ($platform; +https://github.com/sky-map-team/stardroid)"
    }
}
