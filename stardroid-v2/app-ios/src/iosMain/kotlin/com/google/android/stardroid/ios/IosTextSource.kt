/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.ios

import com.google.android.stardroid.text.TextId
import com.google.android.stardroid.text.TextSource
import com.google.android.stardroid.text.TextTemplate
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.earth
import com.google.android.stardroid.ui.resources.hud_cardinal_directions
import com.google.android.stardroid.ui.resources.jupiter
import com.google.android.stardroid.ui.resources.mars
import com.google.android.stardroid.ui.resources.mercury
import com.google.android.stardroid.ui.resources.moon
import com.google.android.stardroid.ui.resources.moon_widget_set_time
import com.google.android.stardroid.ui.resources.neptune
import com.google.android.stardroid.ui.resources.notif_digest_title
import com.google.android.stardroid.ui.resources.notif_eclipse_text
import com.google.android.stardroid.ui.resources.notif_eclipse_title
import com.google.android.stardroid.ui.resources.notif_pass_text
import com.google.android.stardroid.ui.resources.notif_pass_title
import com.google.android.stardroid.ui.resources.notif_shower_bright_moon
import com.google.android.stardroid.ui.resources.notif_shower_dark_moon
import com.google.android.stardroid.ui.resources.notif_shower_direction
import com.google.android.stardroid.ui.resources.notif_shower_title
import com.google.android.stardroid.ui.resources.pluto
import com.google.android.stardroid.ui.resources.satellite_name_iss
import com.google.android.stardroid.ui.resources.satellite_name_tiangong
import com.google.android.stardroid.ui.resources.saturn
import com.google.android.stardroid.ui.resources.sun
import com.google.android.stardroid.ui.resources.tonight_bright_moon
import com.google.android.stardroid.ui.resources.tonight_dark_skies
import com.google.android.stardroid.ui.resources.tonight_full_moon
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_partial
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_penumbral
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_total
import com.google.android.stardroid.ui.resources.tonight_new_moon
import com.google.android.stardroid.ui.resources.tonight_satellite_pass
import com.google.android.stardroid.ui.resources.tonight_shower_days
import com.google.android.stardroid.ui.resources.tonight_shower_peak
import com.google.android.stardroid.ui.resources.tonight_trailing_direction
import com.google.android.stardroid.ui.resources.tonight_trailing_moon
import com.google.android.stardroid.ui.resources.tonight_trailing_pass_peak
import com.google.android.stardroid.ui.resources.tonight_well_placed
import com.google.android.stardroid.ui.resources.uranus
import com.google.android.stardroid.ui.resources.venus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

/**
 * The shared [TextId]s in the app's current language. Compose Resources are read with `suspend`
 * calls and [TextSource.text] is not, so every string is read here, once per planning run, and
 * the source answers from that.
 */
internal suspend fun loadIosTextSource(): TextSource {
    val compass = getStringArray(Res.array.hud_cardinal_directions)
    val templates =
        TextId.entries
            .filter { it !in TextId.compassPoints }
            .associateWith { getString(resourceFor(it)) }
    return TextSource { id, args ->
        val point = TextId.compassPoints.indexOf(id)
        if (point >= 0) compass[point] else TextTemplate.fill(templates.getValue(id), args)
    }
}

private fun resourceFor(id: TextId): StringResource =
    when (id) {
        TextId.NOTIF_SHOWER_TITLE -> Res.string.notif_shower_title
        TextId.NOTIF_SHOWER_DIRECTION -> Res.string.notif_shower_direction
        TextId.NOTIF_SHOWER_DARK_MOON -> Res.string.notif_shower_dark_moon
        TextId.NOTIF_SHOWER_BRIGHT_MOON -> Res.string.notif_shower_bright_moon
        TextId.NOTIF_DIGEST_TITLE -> Res.string.notif_digest_title
        TextId.NOTIF_PASS_TITLE -> Res.string.notif_pass_title
        TextId.NOTIF_PASS_TEXT -> Res.string.notif_pass_text
        TextId.NOTIF_ECLIPSE_TITLE -> Res.string.notif_eclipse_title
        TextId.NOTIF_ECLIPSE_TEXT -> Res.string.notif_eclipse_text
        TextId.SATELLITE_NAME_ISS -> Res.string.satellite_name_iss
        TextId.SATELLITE_NAME_TIANGONG -> Res.string.satellite_name_tiangong
        TextId.TONIGHT_WELL_PLACED -> Res.string.tonight_well_placed
        TextId.TONIGHT_SHOWER_PEAK -> Res.string.tonight_shower_peak
        TextId.TONIGHT_SHOWER_DAYS -> Res.string.tonight_shower_days
        TextId.TONIGHT_NEW_MOON -> Res.string.tonight_new_moon
        TextId.TONIGHT_FULL_MOON -> Res.string.tonight_full_moon
        TextId.TONIGHT_BRIGHT_MOON -> Res.string.tonight_bright_moon
        TextId.TONIGHT_DARK_SKIES -> Res.string.tonight_dark_skies
        TextId.TONIGHT_SATELLITE_PASS -> Res.string.tonight_satellite_pass
        TextId.TONIGHT_LUNAR_ECLIPSE_TOTAL -> Res.string.tonight_lunar_eclipse_total
        TextId.TONIGHT_LUNAR_ECLIPSE_PARTIAL -> Res.string.tonight_lunar_eclipse_partial
        TextId.TONIGHT_LUNAR_ECLIPSE_PENUMBRAL -> Res.string.tonight_lunar_eclipse_penumbral
        TextId.TONIGHT_TRAILING_DIRECTION -> Res.string.tonight_trailing_direction
        TextId.TONIGHT_TRAILING_MOON -> Res.string.tonight_trailing_moon
        TextId.TONIGHT_TRAILING_PASS_PEAK -> Res.string.tonight_trailing_pass_peak
        TextId.MOON_SET_TIME -> Res.string.moon_widget_set_time
        TextId.BODY_SUN -> Res.string.sun
        TextId.BODY_MOON -> Res.string.moon
        TextId.BODY_MERCURY -> Res.string.mercury
        TextId.BODY_VENUS -> Res.string.venus
        TextId.BODY_EARTH -> Res.string.earth
        TextId.BODY_MARS -> Res.string.mars
        TextId.BODY_JUPITER -> Res.string.jupiter
        TextId.BODY_SATURN -> Res.string.saturn
        TextId.BODY_URANUS -> Res.string.uranus
        TextId.BODY_NEPTUNE -> Res.string.neptune
        TextId.BODY_PLUTO -> Res.string.pluto
        TextId.COMPASS_N,
        TextId.COMPASS_NNE,
        TextId.COMPASS_NE,
        TextId.COMPASS_ENE,
        TextId.COMPASS_E,
        TextId.COMPASS_ESE,
        TextId.COMPASS_SE,
        TextId.COMPASS_SSE,
        TextId.COMPASS_S,
        TextId.COMPASS_SSW,
        TextId.COMPASS_SW,
        TextId.COMPASS_WSW,
        TextId.COMPASS_W,
        TextId.COMPASS_WNW,
        TextId.COMPASS_NW,
        TextId.COMPASS_NNW,
        ->
            error("$id is read from the compass array")
    }
