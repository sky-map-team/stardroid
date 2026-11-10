/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

/** Every string the shared surfaces ask a [TextSource] for. */
enum class TextId {
    // Notifications.
    NOTIF_SHOWER_TITLE,
    NOTIF_SHOWER_DIRECTION,
    NOTIF_SHOWER_DARK_MOON,
    NOTIF_SHOWER_BRIGHT_MOON,
    NOTIF_DIGEST_TITLE,

    // Tonight rows, shared by the digest and the widget.
    TONIGHT_WELL_PLACED,
    TONIGHT_SHOWER_PEAK,
    TONIGHT_SHOWER_DAYS,
    TONIGHT_NEW_MOON,
    TONIGHT_FULL_MOON,
    TONIGHT_BRIGHT_MOON,
    TONIGHT_DARK_SKIES,
    TONIGHT_SATELLITE_PASS,
    TONIGHT_LUNAR_ECLIPSE_TOTAL,
    TONIGHT_LUNAR_ECLIPSE_PARTIAL,
    TONIGHT_LUNAR_ECLIPSE_PENUMBRAL,
    TONIGHT_TRAILING_DIRECTION,
    TONIGHT_TRAILING_MOON,
    TONIGHT_TRAILING_PASS_PEAK,
    MOON_SET_TIME,

    // Solar-system body names.
    BODY_SUN,
    BODY_MOON,
    BODY_MERCURY,
    BODY_VENUS,
    BODY_EARTH,
    BODY_MARS,
    BODY_JUPITER,
    BODY_SATURN,
    BODY_URANUS,
    BODY_NEPTUNE,
    BODY_PLUTO,

    // The 16-point compass rose, clockwise from north.
    COMPASS_N,
    COMPASS_NNE,
    COMPASS_NE,
    COMPASS_ENE,
    COMPASS_E,
    COMPASS_ESE,
    COMPASS_SE,
    COMPASS_SSE,
    COMPASS_S,
    COMPASS_SSW,
    COMPASS_SW,
    COMPASS_WSW,
    COMPASS_W,
    COMPASS_WNW,
    COMPASS_NW,
    COMPASS_NNW,
    ;

    companion object {
        /** The compass ids in rose order, north first. */
        val compassPoints: List<TextId> = entries.filter { it.name.startsWith("COMPASS_") }

        /** The id for point [index] of the compass rose (0 is north, 4 east). */
        fun compass(index: Int): TextId = compassPoints[index]
    }
}
