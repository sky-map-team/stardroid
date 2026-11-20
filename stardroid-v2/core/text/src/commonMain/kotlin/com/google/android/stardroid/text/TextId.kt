/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

/**
 * Every string the shared surfaces ask a [TextSource] for.
 *
 * [resourceName] is the string resource it reads, which is also its key in the Apple `.strings`
 * that `ExportAppleStrings` writes. A compass point is an item of the `hud_cardinal_directions`
 * array, exported as `hud_cardinal_directions.<index>`.
 */
enum class TextId(
    val resourceName: String,
) {
    // Notifications.
    NOTIF_SHOWER_TITLE("notif_shower_title"),
    NOTIF_SHOWER_DIRECTION("notif_shower_direction"),
    NOTIF_SHOWER_DARK_MOON("notif_shower_dark_moon"),
    NOTIF_SHOWER_BRIGHT_MOON("notif_shower_bright_moon"),
    NOTIF_DIGEST_TITLE("notif_digest_title"),
    NOTIF_PASS_TITLE("notif_pass_title"),
    NOTIF_PASS_TEXT("notif_pass_text"),
    NOTIF_ECLIPSE_TITLE("notif_eclipse_title"),
    NOTIF_ECLIPSE_TEXT("notif_eclipse_text"),
    SATELLITE_NAME_ISS("satellite_name_iss"),
    SATELLITE_NAME_TIANGONG("satellite_name_tiangong"),

    // Tonight rows, shared by the digest and the widget.
    TONIGHT_WELL_PLACED("tonight_well_placed"),
    TONIGHT_SHOWER_PEAK("tonight_shower_peak"),
    TONIGHT_SHOWER_DAYS("tonight_shower_days"),
    TONIGHT_NEW_MOON("tonight_new_moon"),
    TONIGHT_FULL_MOON("tonight_full_moon"),
    TONIGHT_BRIGHT_MOON("tonight_bright_moon"),
    TONIGHT_DARK_SKIES("tonight_dark_skies"),
    TONIGHT_SATELLITE_PASS("tonight_satellite_pass"),
    TONIGHT_LUNAR_ECLIPSE_TOTAL("tonight_lunar_eclipse_total"),
    TONIGHT_LUNAR_ECLIPSE_PARTIAL("tonight_lunar_eclipse_partial"),
    TONIGHT_LUNAR_ECLIPSE_PENUMBRAL("tonight_lunar_eclipse_penumbral"),
    TONIGHT_TRAILING_DIRECTION("tonight_trailing_direction"),
    TONIGHT_TRAILING_MOON("tonight_trailing_moon"),
    TONIGHT_TRAILING_PASS_PEAK("tonight_trailing_pass_peak"),
    MOON_SET_TIME("moon_widget_set_time"),

    // Solar-system body names.
    BODY_SUN("sun"),
    BODY_MOON("moon"),
    BODY_MERCURY("mercury"),
    BODY_VENUS("venus"),
    BODY_EARTH("earth"),
    BODY_MARS("mars"),
    BODY_JUPITER("jupiter"),
    BODY_SATURN("saturn"),
    BODY_URANUS("uranus"),
    BODY_NEPTUNE("neptune"),
    BODY_PLUTO("pluto"),

    // The 16-point compass rose, clockwise from north.
    COMPASS_N("hud_cardinal_directions.0"),
    COMPASS_NNE("hud_cardinal_directions.1"),
    COMPASS_NE("hud_cardinal_directions.2"),
    COMPASS_ENE("hud_cardinal_directions.3"),
    COMPASS_E("hud_cardinal_directions.4"),
    COMPASS_ESE("hud_cardinal_directions.5"),
    COMPASS_SE("hud_cardinal_directions.6"),
    COMPASS_SSE("hud_cardinal_directions.7"),
    COMPASS_S("hud_cardinal_directions.8"),
    COMPASS_SSW("hud_cardinal_directions.9"),
    COMPASS_SW("hud_cardinal_directions.10"),
    COMPASS_WSW("hud_cardinal_directions.11"),
    COMPASS_W("hud_cardinal_directions.12"),
    COMPASS_WNW("hud_cardinal_directions.13"),
    COMPASS_NW("hud_cardinal_directions.14"),
    COMPASS_NNW("hud_cardinal_directions.15"),
    ;

    companion object {
        /** The compass ids in rose order, north first. */
        val compassPoints: List<TextId> = entries.filter { it.name.startsWith("COMPASS_") }

        /** The id for point [index] of the compass rose (0 is north, 4 east). */
        fun compass(index: Int): TextId = compassPoints[index]
    }
}
