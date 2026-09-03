/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.timetravel

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.time.TimeTravelClock
import com.google.android.stardroid.time.TimeTravelEvent
import com.google.android.stardroid.time.TimeTravelEvents
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.text.DateFormat
import java.util.Date

/**
 * The time-player bar shown while time travel is engaged (v1 `time_player_view`): the
 * simulated date/time readout, the playback-rate label, rate stepping, and the way home.
 * Travelling state is made unmistakable (UX feedback: the player read as a passive caption):
 * the surface turns `tertiaryContainer` while the clock sweeps, direction glyphs grow with
 * the speed step (v1's `<`/`>` labels), and the readout ticks in with a slide.
 */
@Composable
fun TimeTravelPlayer(viewModel: TimeTravelViewModel) {
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val rate by viewModel.rateSecondsPerSecond.collectAsStateWithLifecycle()
    val formatter = rememberDateTimeFormatter()
    val sweeping = rate != 0.0
    Surface(
        color =
            if (sweeping) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            },
        contentColor =
            if (sweeping) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            // The readout slides in per tick while sweeping so the motion itself is
            // visible; frozen time produces one stable string and therefore no animation.
            AnimatedContent(
                targetState = formatter.formatInstant(currentTime),
                transitionSpec = {
                    (
                        slideInVertically(tween(DATE_TICK_MS)) { it / 2 } +
                            fadeIn(tween(DATE_TICK_MS))
                    ).togetherWith(
                        slideOutVertically(tween(DATE_TICK_MS)) { -it / 2 } +
                            fadeOut(tween(DATE_TICK_MS)),
                    )
                },
                label = "timeTravelDateTicker",
            ) { readout ->
                Text(readout, style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sweeping) {
                    Text(
                        if (rate < 0) "◀◀" else "▶▶",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = (10 + 2 * speedStep(rate)).sp,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    stringResource(rateLabel(rate)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                FilledTonalButton(
                    onClick = { viewModel.decelerate() },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(R.string.time_travel_slower)) }
                FilledTonalButton(
                    onClick = { viewModel.pauseTime() },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(R.string.time_travel_pause)) }
                FilledTonalButton(
                    onClick = { viewModel.accelerate() },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(R.string.time_travel_faster)) }
                FilledTonalButton(
                    onClick = { viewModel.returnToRealTime() },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(R.string.time_travel_return)) }
            }
        }
    }
}

/**
 * v1's time-travel dialog in Compose: pick a date, a time, or a popular event, then Go —
 * or just "Start from now" if nothing was touched (v1's two-mode Go button).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimeTravelDialog(
    viewModel: TimeTravelViewModel,
    onSunWontSet: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val context = LocalContext.current
    val formatter = rememberDateTimeFormatter()
    // Saved across configuration changes: rotating the device mustn't discard the date the user
    // has been dialing in. Instant/event ride as their primitive forms (epoch millis, list index).
    var target by
        rememberSaveable(stateSaver = instantSaver) { mutableStateOf(viewModel.pickerSeed()) }
    var userModified by rememberSaveable { mutableStateOf(false) }
    var selectedEvent by
        rememberSaveable(stateSaver = eventSaver) { mutableStateOf<TimeTravelEvent?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_travel_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.time_travel_visiting, formatter.formatInstant(target)),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // FlowRow rather than Row: long translations (e.g. Greek) can outgrow the
                // dialog's width side by side, which squeezed the second button into an
                // unreadably narrow, many-line sliver instead of simply wrapping.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showDatePicker = true },
                    ) { Text(stringResource(R.string.time_travel_pick_date)) }
                    FilledTonalButton(
                        onClick = { showTimePicker = true },
                    ) { Text(stringResource(R.string.time_travel_pick_time)) }
                }
                EventPicker(
                    selectedEvent = selectedEvent,
                    pastAt = viewModel.wallTime(),
                    onSelect = { event ->
                        val resolved = viewModel.resolve(event)
                        if (resolved != null) {
                            selectedEvent = event
                            target = resolved
                            userModified = true
                        } else {
                            // The polar day/night case: no sunrise/sunset within a day. The
                            // destination stays put, as in v1.
                            onSunWontSet()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (userModified) {
                        // The preset's resource entry name keys the analytics funnel;
                        // locale-independent, unlike the display string.
                        viewModel.goTo(
                            target,
                            eventKey =
                                selectedEvent?.let {
                                    context.resources.getResourceEntryName(it.displayNameRes)
                                },
                            searchTarget = selectedEvent?.searchTarget,
                        )
                    } else {
                        viewModel.goToNow()
                    }
                    onDismiss()
                },
            ) {
                val label =
                    if (userModified) R.string.time_travel_go else R.string.time_travel_from_now
                Text(stringResource(label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.time_travel_cancel))
            }
        },
    )

    if (showDatePicker) {
        // The picker works in UTC epoch-day millis; carry the picked calendar date over and
        // keep the previously chosen local wall time (v1: date and time set independently).
        // Seed with the local date at midnight UTC so the picker highlights the local day, not
        // the UTC day of `target` (which can differ near midnight).
        val initialMillis =
            target
                .toLocalDateTime(zone)
                .date
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = initialMillis,
                // The default 1900..2100 is too narrow for a planetarium — open the range so the
                // user can visit historical and far-future skies.
                yearRange = TIME_TRAVEL_YEAR_RANGE,
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date =
                                Instant
                                    .fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC)
                                    .date
                            val time = target.toLocalDateTime(zone).time
                            target = LocalDateTime(date, time).toInstant(zone)
                            selectedEvent = null
                            userModified = true
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.time_travel_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.time_travel_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val local = target.toLocalDateTime(zone)
        val context = LocalContext.current
        val is24Hour =
            remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
        val pickerState =
            rememberTimePickerState(
                initialHour = local.hour,
                initialMinute = local.minute,
                is24Hour = is24Hour,
            )
        AlertDialog(
            // Wrap the wide M3 TimePicker so it isn't clipped inside the dialog's default width.
            modifier = Modifier.width(IntrinsicSize.Min),
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        target =
                            LocalDateTime(
                                local.date,
                                LocalTime(pickerState.hour, pickerState.minute),
                            ).toInstant(zone)
                        selectedEvent = null
                        userModified = true
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.time_travel_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.time_travel_cancel))
                }
            },
        )
    }
}

/** The popular-events dropdown; past fixed events grey out but stay selectable (v1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPicker(
    selectedEvent: TimeTravelEvent?,
    pastAt: Instant,
    onSelect: (TimeTravelEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value =
                selectedEvent?.let { stringResource(it.displayNameRes) }
                    ?: stringResource(R.string.time_travel_select_event),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (event in TimeTravelEvents.ALL) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(event.displayNameRes),
                            color =
                                if (event.isPastAt(pastAt)) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    },
                    onClick = {
                        onSelect(event)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** One step of the readout's slide-in tick — brisk enough for the fastest sweep rates. */
private const val DATE_TICK_MS = 120

/**
 * The magnitude rung of the v1 rate ladder (0 = 1 s/s … 5 = week/s), sizing the player's
 * direction glyphs so faster travel reads bigger.
 */
private fun speedStep(rateSecondsPerSecond: Double): Int =
    when (kotlin.math.abs(rateSecondsPerSecond)) {
        TimeTravelClock.SECONDS_PER_WEEK -> 5
        TimeTravelClock.SECONDS_PER_DAY -> 4
        TimeTravelClock.SECONDS_PER_HOUR -> 3
        TimeTravelClock.SECONDS_PER_10_MINUTES -> 2
        TimeTravelClock.SECONDS_PER_MINUTE -> 1
        else -> 0
    }

/** The DatePicker year span — wide enough for historical and far-future sky-watching. */
private val TIME_TRAVEL_YEAR_RANGE = 1600..3000

/** Persists a picked [Instant] across configuration changes as epoch millis. */
private val instantSaver =
    Saver<Instant, Long>(
        save = { it.toEpochMilliseconds() },
        restore = { Instant.fromEpochMilliseconds(it) },
    )

/** Persists the selected event across configuration changes as its index in [TimeTravelEvents]. */
private val eventSaver =
    Saver<TimeTravelEvent?, Int>(
        save = { event -> event?.let { TimeTravelEvents.ALL.indexOf(it) } ?: -1 },
        restore = { index -> TimeTravelEvents.ALL.getOrNull(index) },
    )

/**
 * A remembered locale formatter (medium date, short time; v1 used an explicit pattern). Held
 * across recompositions so the 30 Hz player readout doesn't reallocate a [DateFormat] per frame.
 */
@Composable
private fun rememberDateTimeFormatter(): DateFormat {
    // Key on the configuration so a runtime locale change re-creates the formatter under the
    // new locale (a bare remember would keep the stale one if the activity isn't recreated).
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
}

/** Locale-formatted date + time using an existing formatter. */
private fun DateFormat.formatInstant(instant: Instant): String =
    format(Date(instant.toEpochMilliseconds()))

/** The v1 speed-label ladder, keyed by the exact rate constants in [TimeTravelClock]. */
@StringRes
private fun rateLabel(rateSecondsPerSecond: Double): Int =
    when (rateSecondsPerSecond) {
        -TimeTravelClock.SECONDS_PER_WEEK -> R.string.time_travel_week_speed_back
        -TimeTravelClock.SECONDS_PER_DAY -> R.string.time_travel_day_speed_back
        -TimeTravelClock.SECONDS_PER_HOUR -> R.string.time_travel_hour_speed_back
        -TimeTravelClock.SECONDS_PER_10_MINUTES -> R.string.time_travel_10minute_speed_back
        -TimeTravelClock.SECONDS_PER_MINUTE -> R.string.time_travel_minute_speed_back
        -1.0 -> R.string.time_travel_second_speed_back
        1.0 -> R.string.time_travel_second_speed
        TimeTravelClock.SECONDS_PER_MINUTE -> R.string.time_travel_minute_speed
        TimeTravelClock.SECONDS_PER_10_MINUTES -> R.string.time_travel_10minute_speed
        TimeTravelClock.SECONDS_PER_HOUR -> R.string.time_travel_hour_speed
        TimeTravelClock.SECONDS_PER_DAY -> R.string.time_travel_day_speed
        TimeTravelClock.SECONDS_PER_WEEK -> R.string.time_travel_week_speed
        else -> R.string.time_travel_stopped
    }
