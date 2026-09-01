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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.google.android.stardroid.R
import com.google.android.stardroid.astronomy.SolarSystemBody
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.events.tonightSky
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.ui.MainActivity
import com.google.android.stardroid.ui.search.SolarSystemIds
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * The countdown widget (D75 phase 2): a ring counting down to the events engine's
 * [CountdownTarget] — the next major shower peak, else the next moon extreme. The target
 * auto-advances once an event passes, so the widget never goes stale.
 */
class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = widgetEntryPoint(context)
        val enabled = entryPoint.experimentConfig().isEnabled(Experiment.TONIGHT_WIDGET)
        val countdown =
            if (enabled) {
                val showers =
                    entryPoint
                        .catalogAccess()
                        .repository()
                        .meteorShowers(entryPoint.localeSource().current)
                        .first()
                // Location-free: the countdown is about dates, not local geometry.
                tonightSky(Clock.System.now(), location = null, showers = showers).countdown
            } else {
                null
            }
        provideContent {
            CountdownContent(countdown, context)
        }
    }
}

class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

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
 * IFTTT: if you change the visuals here, update `res/layout/countdown_widget_initial.xml` to
 * match. That layout is the widget's `initialLayout` and picker `previewLayout` — the launcher
 * inflates it in its own process before ours runs, so Glance cannot supply it and the two are
 * kept in sync by hand. Colors and text sizes are duplicated there; the ring drawn by
 * [CountdownRingRenderer] is deliberately absent, having no RemoteViews equivalent.
 */
@Composable
private fun CountdownContent(
    countdown: CountdownTarget?,
    context: Context,
) {
    val targetId =
        when (countdown) {
            is CountdownTarget.ToShowerPeak -> countdown.shower.id.value
            is CountdownTarget.ToMoonPhase -> SolarSystemIds.idFor(SolarSystemBody.MOON).value
            null -> null
        }
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(
                    if (targetId != null) {
                        actionStartActivity<MainActivity>(
                            actionParametersOf(
                                ActionParameters.Key<String>(MoonWidget.EXTRA_SHOW_OBJECT_ID) to
                                    targetId,
                            ),
                        )
                    } else {
                        actionStartActivity<MainActivity>()
                    },
                ).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (countdown == null) return@Column
        val zone = TimeZone.currentSystemDefault()
        val days =
            Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
                .daysUntil(countdown.time.toLocalDateTime(zone).date)
                .coerceAtLeast(0)
        Box(contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(CountdownRingRenderer.render(RING_SIZE_PX, days)),
                contentDescription = null,
                modifier = GlanceModifier.size(74.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        if (days == 0) {
                            context.getString(R.string.countdown_tonight)
                        } else {
                            days.toString()
                        },
                    style =
                        TextStyle(
                            color = ColorProvider(STARLIGHT),
                            fontSize = if (days == 0) 16.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                if (days > 0) {
                    Text(
                        text = context.getString(R.string.countdown_days),
                        style = TextStyle(color = ColorProvider(MUTED), fontSize = 9.sp),
                    )
                }
            }
        }
        Text(
            text = countdownName(countdown, context),
            style =
                TextStyle(
                    color = ColorProvider(STARLIGHT),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 4.dp),
        )
        if (countdown is CountdownTarget.ToShowerPeak && countdown.darkPeak) {
            Text(
                text = context.getString(R.string.countdown_new_moon_peak),
                style =
                    TextStyle(
                        color = ColorProvider(PLANET_GREEN),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Draws the countdown ring: a full navy track with a Star Gold arc that fills as the event
 * approaches (30 days out = empty, tonight = full). Drawn, like the moon disc, because
 * Glance has no arc primitive.
 */
private object CountdownRingRenderer {
    private const val TRACK_COLOR = 0xFF2A3355.toInt()
    private const val ARC_COLOR = 0xFFFFC107.toInt()
    private const val WINDOW_DAYS = 30

    fun render(
        sizePx: Int,
        daysRemaining: Int,
    ): android.graphics.Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val stroke = sizePx * 0.07f
        val inset = stroke / 2f + 1f
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
        paint.color = TRACK_COLOR
        canvas.drawOval(bounds, paint)
        val progress = 1f - daysRemaining.coerceIn(0, WINDOW_DAYS) / WINDOW_DAYS.toFloat()
        if (progress > 0f) {
            paint.color = ARC_COLOR
            canvas.drawArc(bounds, -90f, 360f * progress, false, paint)
        }
        return bitmap
    }
}

// 74 dp at xxhdpi.
private const val RING_SIZE_PX = 222
