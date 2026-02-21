// Copyright 2024 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.activities.dialogs

import androidx.annotation.StringRes
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.dialogs.TimeTravelEvent.Type

/**
 * Represents a named astronomical event that can be selected in the Time Travel dialog.
 *
 * @param displayNameRes String resource ID for the event's display name.
 * @param type How to compute the target time (computed dynamically or fixed epoch ms).
 * @param timestampMs Epoch milliseconds for FIXED events; ignored for computed types.
 * @param searchTargetRes String resource ID of the celestial object to search for after time
 *   travel (e.g. R.string.sun), or 0 for none. Using a resource ID ensures the search matches
 *   the localized name that the layer indexed itself under.
 */
data class TimeTravelEvent(
  @StringRes val displayNameRes: Int,
  val type: Type,
  val timestampMs: Long = 0L,
  @StringRes val searchTargetRes: Int = 0
) {
  enum class Type { NOW, NEXT_SUNSET, NEXT_SUNRISE, NEXT_FULL_MOON, FIXED }
}

/**
 * The canonical list of all time travel events shown in the dialog.
 *
 * To add a new event:
 *   1. Add a <string name="time_travel_..."> entry to strings.xml.
 *   2. Add a TimeTravelEvent(...) entry below.
 *
 * Position 0 is a non-selectable hint item — do not remove or reorder it.
 */
object TimeTravelEvents {
  @JvmField
  val ALL: List<TimeTravelEvent> = listOf(
    // Position 0: hint/placeholder shown when no event is selected
    TimeTravelEvent(R.string.time_travel_select_hint, Type.NOW),

    // Dynamically computed events
    TimeTravelEvent(R.string.time_travel_next_sunset,   Type.NEXT_SUNSET,    searchTargetRes = R.string.sun),
    TimeTravelEvent(R.string.time_travel_next_sunrise,  Type.NEXT_SUNRISE,   searchTargetRes = R.string.sun),
    TimeTravelEvent(R.string.time_travel_next_fullmoon, Type.NEXT_FULL_MOON, searchTargetRes = R.string.moon),

    // 2026 events (chronological)
    TimeTravelEvent(R.string.time_travel_six_planet_parade_2026,   Type.FIXED, 1772321400000L,  R.string.saturn),
    TimeTravelEvent(R.string.time_travel_lunar_eclipse_2026,       Type.FIXED, 1772537400000L,  R.string.moon),
    TimeTravelEvent(R.string.time_travel_lyrids_2026,              Type.FIXED, 1776816000000L,  R.string.lyrids),
    TimeTravelEvent(R.string.time_travel_venus_jupiter_2026,       Type.FIXED, 1781035200000L,  R.string.venus),
    TimeTravelEvent(R.string.time_travel_mars_uranus_2026,         Type.FIXED, 1783206000000L,  R.string.mars),
    TimeTravelEvent(R.string.time_travel_solar_eclipse_2026,       Type.FIXED, 1786558200000L,  R.string.sun),
    TimeTravelEvent(R.string.time_travel_perseids_2026,            Type.FIXED, 1786579200000L,  R.string.perseids),
    TimeTravelEvent(R.string.time_travel_jupiter_occultation_2026, Type.FIXED, 1791293400000L,  R.string.jupiter),
    TimeTravelEvent(R.string.time_travel_geminids_2026,            Type.FIXED, 1797206400000L,  R.string.geminids),
    TimeTravelEvent(R.string.time_travel_supermoon_2026,           Type.FIXED, 1798149000000L,  R.string.moon),

    // Fixed historical events
    TimeTravelEvent(R.string.time_travel_mercury_transit_2016,     Type.FIXED, 1462805846000L,  R.string.mercury),
    TimeTravelEvent(R.string.time_travel_solar_eclipse_2024,       Type.FIXED, 1712604000000L,  R.string.sun),
    TimeTravelEvent(R.string.time_travel_apollo_11,                Type.FIXED, -14182953622L,   R.string.moon),
    TimeTravelEvent(R.string.time_travel_jupiter_saturn_2020,      Type.FIXED, 1608574800000L,  R.string.jupiter),
  )
}
