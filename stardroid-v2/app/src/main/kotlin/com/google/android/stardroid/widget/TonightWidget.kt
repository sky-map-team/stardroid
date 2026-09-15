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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.android.stardroid.R
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.SkyEvent
import com.google.android.stardroid.events.TonightSky
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.satellites.tonightSatellitePasses
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The tonight's-sky widget (D75 phase 2): sunset/darkness header plus up to three ranked
 * highlights from the events engine, each deep-linking to its object's info card. All the
 * astronomy lives in `:core:events`'s [tonightSky]; this class only formats rows.
 */
class TonightWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = widgetEntryPoint(context)
        val enabled = entryPoint.experimentConfig().isEnabled(Experiment.TONIGHT_WIDGET)
        val sky =
            if (enabled) {
                val location = entryPoint.settings().savedLocation.first()
                val showers =
                    entryPoint
                        .catalogAccess()
                        .repository()
                        .meteorShowers(entryPoint.localeSource().current)
                        .first()
                tonightSky(
                    Clock.System.now(),
                    location,
                    showers,
                    passes = tonightSatellitePasses(context, location),
                )
            } else {
                null
            }
        provideContent {
            TonightContent(sky, context)
        }
    }
}

class TonightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TonightWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.syncSchedule(context)
    }
}

/**
 * IFTTT: if you change the visuals here or in [HeaderRow]/[HighlightRow], update
 * `res/layout/tonight_widget_initial.xml` to match. That layout is the widget's `initialLayout`
 * and picker `previewLayout` — the launcher inflates it in its own process before ours runs, so
 * Glance cannot supply it and the two are kept in sync by hand. Padding, colors, text sizes and
 * the 8dp dot with its 9dp gap are duplicated there.
 */
@Composable
private fun TonightContent(
    sky: TonightSky?,
    context: Context,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(actionStartActivity<MainActivity>())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        // Centered so short content (one highlight) reads as composed, not as a
        // half-empty box; the provider info lets users resize down to one row.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sky == null) return@Column
        HeaderRow(sky, context)
        if (sky.highlights.isEmpty()) {
            QuietNight(sky.countdown, context)
        } else {
            for (event in sky.highlights) {
                HighlightRow(event, context)
            }
        }
    }
}

@Composable
private fun HeaderRow(
    sky: TonightSky,
    context: Context,
) {
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.tonight_widget_header, date),
            style =
                TextStyle(
                    color = ColorProvider(MUTED),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        sunTimes(sky, context)?.let {
            Text(
                text = it,
                style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

private fun sunTimes(
    sky: TonightSky,
    context: Context,
): String? {
    val sunset = sky.sunset?.let { formatTime(context, it) } ?: return null
    val dark = sky.darknessStart?.let { formatTime(context, it) }
    return if (dark != null) {
        context.getString(R.string.tonight_widget_sun_times, sunset, dark)
    } else {
        context.getString(R.string.tonight_widget_sunset_only, sunset)
    }
}

@Composable
private fun HighlightRow(
    event: SkyEvent,
    context: Context,
) {
    val line = highlightLine(event, context)
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<String>(MoonWidget.EXTRA_SHOW_OBJECT_ID) to
                                line.targetId,
                        ),
                    ),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(dotRes(event.quality)),
            contentDescription = null,
            modifier = GlanceModifier.size(8.dp),
        )
        Spacer(GlanceModifier.width(9.dp))
        Text(
            text = line.text,
            style = TextStyle(color = ColorProvider(STARLIGHT), fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = line.trailing,
            style = TextStyle(color = ColorProvider(MUTED), fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun QuietNight(
    countdown: CountdownTarget?,
    context: Context,
) {
    Text(
        text = context.getString(R.string.tonight_quiet),
        style = TextStyle(color = ColorProvider(STARLIGHT), fontSize = 13.sp),
        modifier = GlanceModifier.padding(top = 8.dp),
    )
    if (countdown != null) {
        val days =
            Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .daysUntil(
                    countdown.time.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                )
        Text(
            text =
                context.getString(
                    R.string.tonight_quiet_next,
                    countdownName(countdown, context),
                    days,
                ),
            style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
            modifier = GlanceModifier.padding(top = 2.dp),
        )
    }
}
