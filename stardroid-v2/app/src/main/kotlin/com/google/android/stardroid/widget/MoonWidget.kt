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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import com.google.android.stardroid.astronomy.dayOfCycle
import com.google.android.stardroid.astronomy.illuminationPercent
import com.google.android.stardroid.astronomy.moonWidgetModel
import com.google.android.stardroid.surfaces.crossingsInOrder
import com.google.android.stardroid.surfaces.dayOffset
import com.google.android.stardroid.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.Date
import com.google.android.stardroid.surfaces.MOON_OBJECT_ID as SHARED_MOON_OBJECT_ID

/**
 * The moon-phase home-screen widget (D75): drawn phase disc, phase name, illumination, and
 * today's moonrise/set. All astronomy lives in `:core:astronomy`'s [moonWidgetModel]; this
 * class only formats and draws. Rendered fresh on every [provideContent] call, so a refresh
 * is just [androidx.glance.appwidget.updateAll] from [MoonWidgetRefreshWorker].
 */
class MoonWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE) + FULL_SIZES)

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = widgetEntryPoint(context)
        val now = Clock.System.now()
        val model = moonWidgetModel(now, entryPoint.settings().savedLocation.first())
        provideContent {
            MoonWidgetContent(model, now, context)
        }
    }

    companion object {
        /** Intent extra: the catalog id whose info card the app opens on widget tap. */
        const val EXTRA_SHOW_OBJECT_ID = "extra_show_object_id"
        const val MOON_OBJECT_ID = SHARED_MOON_OBJECT_ID

        // Responsive picks the largest listed size that fits the placed widget, and LocalSize
        // reports that listed size, not the real one. The extra heights are how the full
        // layout learns how much room it has (see [fullLayoutPlan]).
        internal val COMPACT_SIZE = DpSize(40.dp, 40.dp)
        internal val FULL_SIZE = DpSize(110.dp, 110.dp)
        internal val FULL_SIZES = (110..190 step 10).map { DpSize(110.dp, it.dp) }
    }
}

/**
 * IFTTT: if you change the visuals here, update `res/layout/moon_widget_initial.xml` to match.
 * That layout is the widget's `initialLayout` and picker `previewLayout` — the launcher inflates
 * it in its own process before ours runs, so Glance cannot supply it and the two are kept in
 * sync by hand. Padding, colors, text sizes and the 56dp disc are duplicated there; the
 * layout shrinks its disc to fit when the initial frame lands in a 1x1 cell.
 */
@Composable
private fun MoonWidgetContent(
    model: MoonWidgetModel,
    now: Instant,
    context: Context,
) {
    when (moonWidgetLayoutFor(LocalSize.current)) {
        MoonWidgetLayout.COMPACT -> CompactMoonWidget(model, context)
        MoonWidgetLayout.FULL ->
            FullMoonWidget(
                model,
                now,
                context,
                fullLayoutPlan(
                    heightDp = LocalSize.current.height.value,
                    fontScale = context.resources.configuration.fontScale,
                    timeRows = listOfNotNull(model.riseTime, model.setTime).size,
                ),
            )
    }
}

@Composable
private fun CompactMoonWidget(
    model: MoonWidgetModel,
    context: Context,
) {
    Column(
        modifier = moonWidgetModifier().padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(moonDisc(model, COMPACT_DISC_SIZE_PX)),
            contentDescription = context.getString(phaseNameRes(model.phase)),
            modifier = GlanceModifier.size(COMPACT_DISC_DP),
        )
        Text(
            text =
                context.getString(
                    R.string.moon_widget_illumination_short,
                    model.illuminationPercent,
                ),
            style =
                TextStyle(
                    color = ColorProvider(STAR_GOLD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 2.dp),
        )
    }
}

private fun moonWidgetModifier(): GlanceModifier =
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
        )

private fun moonDisc(
    model: MoonWidgetModel,
    sizePx: Int,
) = MoonDiscRenderer.render(
    sizePx = sizePx,
    illuminatedFraction = model.illuminatedFraction,
    waxing = model.waxing,
    mirrored = model.mirrored,
)

@Composable
private fun FullMoonWidget(
    model: MoonWidgetModel,
    now: Instant,
    context: Context,
    plan: FullLayoutPlan,
) {
    Column(
        modifier = moonWidgetModifier().padding(FULL_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(moonDisc(model, DISC_SIZE_PX)),
            contentDescription = context.getString(phaseNameRes(model.phase)),
            modifier = GlanceModifier.size(plan.discDp.dp),
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
            modifier = GlanceModifier.padding(top = 4.dp),
        )
        Text(
            text =
                context.getString(
                    R.string.moon_widget_illumination,
                    model.illuminationPercent,
                ),
            style =
                TextStyle(
                    color = ColorProvider(STAR_GOLD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        if (plan.showAge) {
            Text(
                text = context.getString(R.string.moon_widget_age, model.dayOfCycle),
                style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
                maxLines = 1,
            )
        }
        // One row per crossing: they don't fit side by side in 2 cells.
        model.crossingsInOrder.forEachIndexed { index, crossing ->
            val labelRes =
                if (crossing.rise) R.string.moon_widget_rise_time else R.string.moon_widget_set_time
            val time = crossing.time
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
    val offset = dayOffset(now, time, TimeZone.currentSystemDefault())
    val suffix =
        if (offset > 0) context.getString(R.string.moon_widget_time_day_offset, offset) else ""
    Text(
        text = context.getString(labelRes, formatted) + suffix,
        style = TextStyle(color = ColorProvider(MUTED), fontSize = 12.sp),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = topPadding),
    )
}

/** What the full layout can afford at a given height: the optional age line, and the disc size. */
internal data class FullLayoutPlan(val showAge: Boolean, val discDp: Int)

private const val FULL_PADDING_DP = 8
private const val DISC_MAX_DP = 56
private const val DISC_MIN_DP = 32

// A text line is about 1.25x its sp size; sp scales with the user's font size, dp doesn't.
private const val LINE_HEIGHT_FACTOR = 1.25f
private const val PHASE_SP = 15
private const val ILLUMINATION_SP = 13
private const val DETAIL_SP = 12
private const val PHASE_TOP_GAP_DP = 4
private const val FIRST_TIME_ROW_GAP_DP = 2

/**
 * Decides what fits in [heightDp] at [fontScale] with [timeRows] rise/set lines. The disc is the
 * flexible part (shrinks to [DISC_MIN_DP]); the age line is the first thing dropped when even a
 * reduced disc doesn't leave room. Rise/set stay, since they are what the widget is for.
 */
internal fun fullLayoutPlan(
    heightDp: Float,
    fontScale: Float,
    timeRows: Int,
): FullLayoutPlan {
    fun line(sp: Int) = sp * LINE_HEIGHT_FACTOR * fontScale
    val textWithoutAge =
        2 * FULL_PADDING_DP + PHASE_TOP_GAP_DP + line(PHASE_SP) + line(ILLUMINATION_SP) +
            timeRows * line(DETAIL_SP) + if (timeRows > 0) FIRST_TIME_ROW_GAP_DP else 0
    val withAge = heightDp - (textWithoutAge + line(DETAIL_SP))
    if (withAge >= DISC_MIN_DP) {
        return FullLayoutPlan(showAge = true, discDp = withAge.toInt().coerceAtMost(DISC_MAX_DP))
    }
    val withoutAge = heightDp - textWithoutAge
    return FullLayoutPlan(
        showAge = false,
        discDp = withoutAge.toInt().coerceIn(DISC_MIN_DP, DISC_MAX_DP),
    )
}

internal enum class MoonWidgetLayout { COMPACT, FULL }

/** The full layout needs a 2x2 footprint; anything narrower or shorter gets the compact one. */
internal fun moonWidgetLayoutFor(size: DpSize): MoonWidgetLayout =
    if (size.width >= MoonWidget.FULL_SIZE.width && size.height >= MoonWidget.FULL_SIZE.height) {
        MoonWidgetLayout.FULL
    } else {
        MoonWidgetLayout.COMPACT
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

// 56 dp at xxhdpi; Glance scales the bitmap to the Image's dp size.
private const val DISC_SIZE_PX = 168
private val COMPACT_DISC_DP = 34.dp
private const val COMPACT_DISC_SIZE_PX = 102
