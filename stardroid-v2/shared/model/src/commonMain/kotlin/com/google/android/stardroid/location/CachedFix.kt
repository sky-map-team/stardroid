/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.location

import com.google.android.stardroid.math.LatLong

/**
 * A position the OS already had cached when we asked (`getLastKnownLocation`), used to seed
 * acquisition so a fix another app obtained shows up immediately instead of after a fresh
 * one arrives — which, with only the coarse permission, may be never on a device with no
 * network location backend.
 */
data class CachedFix(
    val location: LatLong,
    val accuracyM: Float?,
    /** How long ago the OS obtained the fix; smaller is fresher. */
    val ageMillis: Long,
)

/**
 * The best seed among per-provider cached fixes: the freshest, with the more accurate one
 * winning a tie. A stale fix is still worth having for a sky map — the observer's position
 * rarely changes by more than the pointing error — so there is deliberately no age cutoff.
 */
fun freshestFix(candidates: List<CachedFix>): CachedFix? =
    candidates.minWithOrNull(
        compareBy<CachedFix> { it.ageMillis }.thenBy { it.accuracyM ?: Float.MAX_VALUE },
    )
