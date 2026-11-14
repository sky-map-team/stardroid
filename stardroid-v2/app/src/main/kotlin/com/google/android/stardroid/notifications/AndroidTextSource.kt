/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.notifications

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.stardroid.R
import com.google.android.stardroid.surfaces.NotificationText
import com.google.android.stardroid.text.TextId
import com.google.android.stardroid.text.TextSource
import com.google.android.stardroid.widget.formatTime

/** Maps the shared [TextId]s to this app's string resources, in the context's locale. */
class AndroidTextSource(
    private val context: Context,
) : TextSource {
    override fun text(
        id: TextId,
        vararg args: Any,
    ): String {
        val compassIndex = TextId.compassPoints.indexOf(id)
        if (compassIndex >= 0) {
            return context.resources.getStringArray(R.array.hud_cardinal_directions)[compassIndex]
        }
        return context.getString(resourceFor(id), *args)
    }

    @StringRes
    private fun resourceFor(id: TextId): Int =
        when (id) {
            TextId.NOTIF_SHOWER_TITLE -> R.string.notif_shower_title
            TextId.NOTIF_SHOWER_DIRECTION -> R.string.notif_shower_direction
            TextId.NOTIF_SHOWER_DARK_MOON -> R.string.notif_shower_dark_moon
            TextId.NOTIF_SHOWER_BRIGHT_MOON -> R.string.notif_shower_bright_moon
            TextId.NOTIF_DIGEST_TITLE -> R.string.notif_digest_title
            TextId.NOTIF_PASS_TITLE -> R.string.notif_pass_title
            TextId.NOTIF_PASS_TEXT -> R.string.notif_pass_text
            TextId.NOTIF_ECLIPSE_TITLE -> R.string.notif_eclipse_title
            TextId.NOTIF_ECLIPSE_TEXT -> R.string.notif_eclipse_text
            TextId.SATELLITE_NAME_ISS -> R.string.satellite_name_iss
            TextId.SATELLITE_NAME_TIANGONG -> R.string.satellite_name_tiangong
            TextId.TONIGHT_WELL_PLACED -> R.string.tonight_well_placed
            TextId.TONIGHT_SHOWER_PEAK -> R.string.tonight_shower_peak
            TextId.TONIGHT_SHOWER_DAYS -> R.string.tonight_shower_days
            TextId.TONIGHT_NEW_MOON -> R.string.tonight_new_moon
            TextId.TONIGHT_FULL_MOON -> R.string.tonight_full_moon
            TextId.TONIGHT_BRIGHT_MOON -> R.string.tonight_bright_moon
            TextId.TONIGHT_DARK_SKIES -> R.string.tonight_dark_skies
            TextId.TONIGHT_SATELLITE_PASS -> R.string.tonight_satellite_pass
            TextId.TONIGHT_LUNAR_ECLIPSE_TOTAL -> R.string.tonight_lunar_eclipse_total
            TextId.TONIGHT_LUNAR_ECLIPSE_PARTIAL -> R.string.tonight_lunar_eclipse_partial
            TextId.TONIGHT_LUNAR_ECLIPSE_PENUMBRAL -> R.string.tonight_lunar_eclipse_penumbral
            TextId.TONIGHT_TRAILING_DIRECTION -> R.string.tonight_trailing_direction
            TextId.TONIGHT_TRAILING_MOON -> R.string.tonight_trailing_moon
            TextId.TONIGHT_TRAILING_PASS_PEAK -> R.string.tonight_trailing_pass_peak
            TextId.MOON_SET_TIME -> R.string.moon_widget_set_time
            TextId.BODY_SUN -> R.string.sun
            TextId.BODY_MOON -> R.string.moon
            TextId.BODY_MERCURY -> R.string.mercury
            TextId.BODY_VENUS -> R.string.venus
            TextId.BODY_EARTH -> R.string.earth
            TextId.BODY_MARS -> R.string.mars
            TextId.BODY_JUPITER -> R.string.jupiter
            TextId.BODY_SATURN -> R.string.saturn
            TextId.BODY_URANUS -> R.string.uranus
            TextId.BODY_NEPTUNE -> R.string.neptune
            TextId.BODY_PLUTO -> R.string.pluto
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
}

/** The shared notification and tonight-row wording, in this device's language and clock. */
fun notificationText(context: Context): NotificationText =
    NotificationText(AndroidTextSource(context)) { formatTime(context, it) }
