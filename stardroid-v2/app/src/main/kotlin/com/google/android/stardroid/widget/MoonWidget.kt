/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.astronomy.MoonWidgetModel
import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.ui.MainActivity
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Date
import kotlin.math.roundToInt

/**
 * The moon-phase home-screen widget (D75): drawn phase disc, phase name, illumination, and
 * today's moonrise/set. All astronomy lives in `:core:astronomy`'s [moonWidgetModel]; this
 * class only formats and draws. Rendered fresh on every [provideContent] call, so a refresh
 * is just [androidx.glance.appwidget.updateAll] from [MoonWidgetRefreshWorker].
 */
class MoonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = widgetEntryPoint(context)
        val now = Clock.System.now()
        // Kill switch (D75): instances placed before a flag flip freeze as a quiet brand tile
        // rather than continuing to update; the component gate stops new placements.
        val model =
            moonWidgetModelFor(
                entryPoint.experimentConfig(),
                now,
                entryPoint.settings().savedLocation,
            )
        provideContent {
            MoonWidgetContent(model, now, context)
        }
    }

    companion object {
        /** Intent extra: the catalog id whose info card the app opens on widget tap. */
        const val EXTRA_SHOW_OBJECT_ID = "extra_show_object_id"
        const val MOON_OBJECT_ID = "planet/moon"
    }
}

/**
 * IFTTT: if you change the visuals here, update `res/layout/moon_widget_initial.xml` to match.
 * That layout is the widget's `initialLayout` and picker `previewLayout` — the launcher inflates
 * it in its own process before ours runs, so Glance cannot supply it and the two are kept in
 * sync by hand. Padding, colors, text sizes and the 64dp disc are duplicated there.
 */
@Composable
private fun MoonWidgetContent(
    model: MoonWidgetModel?,
    now: Instant,
    context: Context,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>(MoonWidget.EXTRA_SHOW_OBJECT_ID) to
                                MoonWidget.MOON_OBJECT_ID,
                        ),
                    ),
                ).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model == null) return@Column
        Image(
            provider =
                ImageProvider(
                    MoonDiscRenderer.render(
                        sizePx = DISC_SIZE_PX,
                        illuminatedFraction = model.illuminatedFraction,
                        waxing = model.waxing,
                        mirrored = model.mirrored,
                    ),
                ),
            contentDescription = context.getString(phaseNameRes(model.phase)),
            modifier = GlanceModifier.size(64.dp),
        )
        Text(
            text = context.getString(phaseNameRes(model.phase)),
            style =
                TextStyle(
                    color = ColorProvider(STARLIGHT),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 6.dp),
        )
        Text(
            text =
                context.getString(
                    R.string.moon_widget_illumination,
                    (model.illuminatedFraction * 100).roundToInt(),
                ),
            style =
                TextStyle(
                    color = ColorProvider(STAR_GOLD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        // One row per crossing (they don't fit side by side in 2 cells); either alone when
        // only one converged (D51), neither without a location. riseTime and setTime are each
        // independently "next after now" (issue #1067), so whichever comes first chronologically
        // — rise or set — must render first, or the pair reads as if it happened in the wrong
        // order (e.g. "rises 6:36PM" above a "sets 5:56AM" that is actually earlier, tonight).
        val rows =
            listOfNotNull(
                model.riseTime?.let { R.string.moon_widget_rise_time to it },
                model.setTime?.let { R.string.moon_widget_set_time to it },
            ).sortedBy { it.second }
        rows.forEachIndexed { index, (labelRes, time) ->
            TimeRow(labelRes, time, now, context, topPadding = if (index == 0) 2.dp else 0.dp)
        }
    }
}

/**
 * A "rises 17:42" / "sets 03:12" line in the device's 12/24-hour format. When [time] falls on a
 * different local day than [now] — the widget only refreshes every 12h and at local midnight
 * (see [WidgetScheduler]), so a shown crossing is often tomorrow's — a compact "+1"-style suffix
 * is appended rather than a full date, which wouldn't fit this widget's minimum 2x2 size.
 */
@Composable
private fun TimeRow(
    labelRes: Int,
    time: Instant,
    now: Instant,
    context: Context,
    topPadding: Dp = 0.dp,
) {
    val formatted =
        android.text.format.DateFormat
            .getTimeFormat(context)
            .format(Date(time.toEpochMilliseconds()))
    val text = context.getString(labelRes, formatted) + dayOffsetSuffix(now, time, context)
    Text(
        text = text,
        style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = topPadding),
    )
}

/** " +1" when [time]'s local calendar day is after [now]'s, else empty. */
private fun dayOffsetSuffix(
    now: Instant,
    time: Instant,
    context: Context,
): String {
    val zone = TimeZone.currentSystemDefault()
    val dayOffset =
        time.toLocalDateTime(zone).date.toEpochDays() - now.toLocalDateTime(zone).date.toEpochDays()
    return if (dayOffset > 0) context.getString(R.string.moon_widget_time_day_offset, dayOffset) else ""
}

private fun phaseNameRes(phase: LunarPhase): Int =
    when (phase) {
        LunarPhase.NEW -> R.string.moon_phase_new
        LunarPhase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        LunarPhase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        LunarPhase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        LunarPhase.FULL -> R.string.moon_phase_full
        LunarPhase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        LunarPhase.LAST_QUARTER -> R.string.moon_phase_last_quarter
        LunarPhase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    }

// 64 dp at xxhdpi; Glance scales the bitmap to the Image's dp size.
private const val DISC_SIZE_PX = 192
