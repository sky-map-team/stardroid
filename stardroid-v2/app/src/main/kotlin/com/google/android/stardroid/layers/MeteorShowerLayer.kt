/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.MeteorShower
import com.google.android.stardroid.catalog.MonthDay
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.CoroutineContext

/** The year-agnostic month/day this date falls on — the key a shower's window is tested against. */
internal fun LocalDate.toMonthDay(): MonthDay = MonthDay(monthNumber, dayOfMonth)

/**
 * The UTC calendar date a shower's activity window is tested against — v1's date bucketing
 * (activity is date-dependent, not per-frame). Tap-to-identify shares this with the layer so the
 * tappable set is exactly the drawn set.
 */
internal fun utcMonthDay(time: Instant): MonthDay =
    time.toLocalDateTime(TimeZone.UTC).date.toMonthDay()

/**
 * Well-known annual meteor showers, ported from v1's `MeteorShowerLayer` over the D58 catalog
 * sidecar: each shower renders a radiant icon + name label, but only while the UTC date is
 * inside its activity window. The icon swaps to the denser "peak" glyph while the linearly
 * interpolated rate exceeds [PEAK_ICON_THRESHOLD_ZHR] meteors/hour — v1's exact policy,
 * including its UTC date bucketing (activity is date-dependent, not per-frame).
 *
 * Scenes recompute when the shower set, the locale, or the UTC *calendar date* changes — time
 * travel just makes the date change faster, like every other clock consumer. Inactive showers
 * contribute nothing: v2's declarative scene replaces v1's blank-image workaround.
 */
class MeteorShowerLayer(
    private val catalog: CatalogRepository,
    private val locale: Flow<LocaleSpec>,
    private val clock: Flow<Instant>,
    private val mapContext: CoroutineContext = Dispatchers.Default,
) : SkyLayer {
    override val id = LAYER_ID
    override val depth = DEPTH

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scenes(): Flow<LayerScene> =
        locale
            .distinctUntilChanged()
            .flatMapLatest { localeSpec ->
                combine(
                    catalog.meteorShowers(localeSpec),
                    clock.map(::utcDate).distinctUntilChanged(),
                ) { showers, date -> buildScene(showers, date) }
            }
            .flowOn(mapContext)

    internal fun buildScene(
        showers: List<MeteorShower>,
        date: LocalDate,
    ): LayerScene {
        val active = showers.filter { it.isActiveOn(date.toMonthDay()) }
        val points =
            active.map { shower ->
                PointPrimitive(
                    shower.radiant.toGeocentricVector(),
                    PointAppearance.Icon(iconFor(shower, date), ICON_SIZE_DP),
                )
            }
        val labels =
            active.map { shower ->
                LabelPrimitive(
                    pos = shower.radiant.toGeocentricVector(),
                    text = shower.name,
                    // Offset past the radiant icon's lower half.
                    style =
                        LabelStyle(
                            LabelSize.STANDARD,
                            SkyColors.SKY_LABEL,
                            offsetDp = ICON_SIZE_DP / 2 + 2.0,
                        ),
                    priority = CatalogLayers.labelPriority(null),
                    magnitudeForThresholding = null,
                )
            }
        return LayerScene(depth = depth, points = points, labels = labels)
    }

    /** v1's icon swap: the denser glyph while the interpolated rate tops the threshold. */
    private fun iconFor(
        shower: MeteorShower,
        date: LocalDate,
    ): ImageRef =
        if (zenithalHourlyRate(shower, date) > PEAK_ICON_THRESHOLD_ZHR) {
            ICON_RADIANT_PEAK
        } else {
            ICON_RADIANT
        }

    /**
     * v1's linear interpolation of the meteor rate: 0 at the window edges, [MeteorShower.peakZhr]
     * at the peak date. Windows never cross a year boundary (generator-validated). Day-of-year
     * arithmetic uses a fixed leap reference year so a Feb 29 catalog date never constructs an
     * invalid [LocalDate] and interpolation is identical every year.
     */
    internal fun zenithalHourlyRate(
        shower: MeteorShower,
        date: LocalDate,
    ): Double {
        fun dayOfYear(day: MonthDay) = LocalDate(REFERENCE_YEAR, day.month, day.day).dayOfYear
        val now = LocalDate(REFERENCE_YEAR, date.monthNumber, date.dayOfMonth).dayOfYear
        val start = dayOfYear(shower.activeFrom)
        val peak = dayOfYear(shower.peak)
        val end = dayOfYear(shower.activeTo)
        if (now < start || now > end) return 0.0
        val toPeak =
            when {
                now <= peak -> if (peak == start) 1.0 else (now - start).toDouble() / (peak - start)
                else -> if (end == peak) 1.0 else (end - now).toDouble() / (end - peak)
            }
        return shower.peakZhr * toPeak
    }

    private fun utcDate(time: Instant): LocalDate = time.toLocalDateTime(TimeZone.UTC).date

    companion object {
        val LAYER_ID = LayerId("catalog/meteor_showers")

        /** v1 depth table: in front of the solar system (60), behind the horizon (90). */
        private const val DEPTH = 80

        /** Any leap year works; 2000 makes every MonthDay (incl. Feb 29) a valid LocalDate. */
        private const val REFERENCE_YEAR = 2000

        /** v1's `METEOR_THRESHOLD_PER_HR` for switching to the larger radiant graphic. */
        internal const val PEAK_ICON_THRESHOLD_ZHR = 10.0

        /** The v1 radiant glyphs are 64 px bitmaps authored for ~2x density: 32 dp on screen. */
        private const val ICON_SIZE_DP = 32.0

        internal val ICON_RADIANT = ImageRef("icon/meteor_radiant")
        internal val ICON_RADIANT_PEAK = ImageRef("icon/meteor_radiant_peak")
    }
}
