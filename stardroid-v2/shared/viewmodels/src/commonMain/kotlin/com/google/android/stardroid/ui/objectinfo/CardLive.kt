/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.events.SkyEvent

/** A live fact about the shown object at the map's time: one label/value row (D141 §3.1). */
sealed interface CardFact {
    data class RiseSet(val state: RiseSetState) : CardFact

    data class MoonPhase(val phase: LunarPhase, val illuminatedFraction: Double) : CardFact

    /** Days since the last new moon, in `[0, 29.53)`; the card counts new moon as day 1. */
    data class MoonAge(val ageDays: Double) : CardFact
}

/** What an [Upcoming] section is about; picks its heading and empty-state wording. */
enum class UpcomingKind {
    SATELLITE_PASSES,
    LUNAR_ECLIPSE,
}

/** The card's "when to look" section, always in exactly one state (D141 §3.2). */
sealed interface Upcoming {
    /** Rendered in the order given; the card does not re-rank. */
    data class Events(val events: List<SkyEvent>) : Upcoming

    /** Fresh data, nothing qualifies: normal, not broken. */
    data class NothingComing(val kind: UpcomingKind) : Upcoming

    /** Satellite element sets too stale to time a pass honestly (D92). */
    data object DataTooOld : Upcoming
}

/** Everything object-specific the card shows beyond its catalog text (D141). */
data class CardLive(
    val now: List<CardFact> = emptyList(),
    val upcoming: Upcoming? = null,
)
