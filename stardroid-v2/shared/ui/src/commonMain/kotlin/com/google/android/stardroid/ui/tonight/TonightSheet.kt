/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.tonight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.surfaces.HighlightDot
import com.google.android.stardroid.surfaces.WidgetColors
import com.google.android.stardroid.surfaces.highlightDot
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.countdown_full_moon
import com.google.android.stardroid.ui.resources.countdown_new_moon
import com.google.android.stardroid.ui.resources.countdown_shower_peak
import com.google.android.stardroid.ui.resources.search_button
import com.google.android.stardroid.ui.resources.tonight_announcements_heading
import com.google.android.stardroid.ui.resources.tonight_quiet
import com.google.android.stardroid.ui.resources.tonight_quiet_next
import com.google.android.stardroid.ui.resources.tonight_sheet_title
import com.google.android.stardroid.ui.resources.tonight_widget_sun_times
import com.google.android.stardroid.ui.resources.tonight_widget_sunset_only
import org.jetbrains.compose.resources.stringResource

/**
 * The Tonight sheet (D149): sun times, active announcements and every highlight, wrapped rather
 * than cut off. A row opens its object's card via [onOpenObject]; an announcement that carries a
 * search runs it via [onSearch].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonightSheet(
    viewModel: TonightViewModel,
    onOpenObject: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val visible by viewModel.visible.collectAsState()
    val state by viewModel.state.collectAsState()
    // Held back until loaded, so it doesn't open as a title-only sliver and then jump.
    if (!visible || state == null) return
    ModalBottomSheet(onDismissRequest = viewModel::close) {
        TonightContent(
            state = state,
            onOpenObject = {
                viewModel.close()
                onOpenObject(it)
            },
            onSearch = {
                viewModel.close()
                onSearch(it)
            },
        )
    }
}

@Composable
internal fun TonightContent(
    state: TonightState?,
    onOpenObject: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(Res.string.tonight_sheet_title),
            style = MaterialTheme.typography.titleLarge,
        )
        if (state == null) return@Column
        SunTimes(state)
        if (state.notices.isNotEmpty()) {
            Text(
                stringResource(Res.string.tonight_announcements_heading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.notices.forEach { Notice(it, onSearch) }
        }
        if (state.rows.isEmpty()) {
            Quiet(state)
        } else {
            state.rows.forEach { Highlight(it, onOpenObject) }
        }
    }
}

@Composable
private fun SunTimes(state: TonightState) {
    val sunset = state.sunset ?: return
    val dark = state.darkness
    Text(
        if (dark != null) {
            formattedStringResource(Res.string.tonight_widget_sun_times, sunset, dark)
        } else {
            formattedStringResource(Res.string.tonight_widget_sunset_only, sunset)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Notice(
    notice: TonightNotice,
    onSearch: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            notice.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (notice.body.isNotBlank()) {
            Text(notice.body, style = MaterialTheme.typography.bodyMedium)
        }
        notice.searchQuery?.let { query ->
            TextButton(onClick = { onSearch(query) }) {
                Text(stringResource(Res.string.search_button))
            }
        }
    }
}

@Composable
private fun Highlight(
    row: TonightRow,
    onOpenObject: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenObject(row.targetId) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor(row.quality)),
        )
        Column(Modifier.weight(1f, fill = true)) {
            Text(row.text, style = MaterialTheme.typography.bodyLarge)
            if (row.trailing.isNotBlank()) {
                Text(
                    row.trailing,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Quiet(state: TonightState) {
    Text(stringResource(Res.string.tonight_quiet), style = MaterialTheme.typography.bodyLarge)
    val next = state.next ?: return
    Text(
        formattedStringResource(
            Res.string.tonight_quiet_next,
            countdownName(next),
            state.daysToNext,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun countdownName(next: CountdownTarget): String =
    when (next) {
        is CountdownTarget.ToShowerPeak ->
            formattedStringResource(Res.string.countdown_shower_peak, next.shower.name)
        is CountdownTarget.ToMoonPhase ->
            stringResource(
                if (next.phase == LunarPhase.NEW) {
                    Res.string.countdown_new_moon
                } else {
                    Res.string.countdown_full_moon
                },
            )
    }

private fun dotColor(quality: Double): Color =
    Color(
        when (highlightDot(quality)) {
            HighlightDot.GOLD -> WidgetColors.STAR_GOLD
            HighlightDot.BLUE -> WidgetColors.DOT_BLUE
            HighlightDot.DIM -> WidgetColors.DOT_DIM
        },
    )
