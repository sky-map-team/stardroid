/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import kotlin.math.roundToInt

/**
 * The map HUD (map-hud.md, D65): a compact top-right pointing readout — RA/Dec, Alt/Az,
 * FOV, and the drag-to-align correction with its reset. Information chrome: it renders
 * inside the same show/hide container as the rest of the chrome and re-tints through the
 * theme in night mode. Values arrive pre-throttled ([MapViewModel.hudState]); tabular
 * figures keep them from jittering as they tick.
 */
@Composable
fun MapHud(
    state: HudState,
    onResetAlignment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            modifier
                .width(IntrinsicSize.Max)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val (raHours, raMinutes) = HudFormats.raHoursMinutes(state.raDeg)
        HudRow(R.string.hud_ra_label, stringResource(R.string.hud_ra_value, raHours, raMinutes))
        HudRow(
            R.string.hud_dec_label,
            stringResource(R.string.hud_signed_degrees_value, state.decDeg),
        )
        HudRow(
            R.string.hud_alt_label,
            stringResource(R.string.hud_signed_degrees_value, state.altDeg),
        )
        val cardinals = stringArrayResource(R.array.hud_cardinal_directions)
        HudRow(
            R.string.hud_az_label,
            stringResource(
                R.string.hud_azimuth_value,
                HudFormats.azimuthWholeDegrees(state.azDeg),
                cardinals[HudFormats.cardinalIndex(state.azDeg)],
            ),
        )
        HudRow(
            R.string.hud_fov_label,
            when (HudFormats.fovDecimals(state.fovDeg)) {
                0 -> stringResource(R.string.hud_fov_value, state.fovDeg.roundToInt())
                1 -> stringResource(R.string.hud_fov_value_fine, state.fovDeg)
                else -> stringResource(R.string.hud_fov_value_finest, state.fovDeg)
            },
            locked = state.fovLocked,
        )
        if (HudFormats.correctionActive(state.correctionAzDeg, state.correctionAltDeg)) {
            CorrectionRow(state, onResetAlignment)
        }
    }
}

/**
 * One label/value line; the weighted spacer right-aligns values across rows. [locked] marks
 * the value with the FOV-lock glyph while the AR camera owns the zoom (D64/D65).
 */
@Composable
private fun HudRow(
    @StringRes label: Int,
    value: String,
    locked: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (locked) {
            Icon(
                painterResource(R.drawable.ic_hud_lock),
                contentDescription = stringResource(R.string.hud_fov_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).size(11.dp),
            )
        }
        Text(
            value,
            style = hudValueStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = if (locked) 4.dp else 12.dp),
        )
    }
}

/**
 * The correction row (D64/D65): visible only while a drag-to-align correction is in effect,
 * tinted primary to read as "something is adjusting the sensors", with the reset that
 * clears both axes (undo rides the snackbar the caller shows).
 */
@Composable
private fun CorrectionRow(
    state: HudState,
    onResetAlignment: () -> Unit,
) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.hud_correction_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(
                R.string.hud_correction_value,
                state.correctionAzDeg,
                state.correctionAltDeg,
            ),
            style = hudValueStyle(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
        // Default IconButton: the 48 dp touch-target baseline every chrome control keeps
        // (D56) — this is the HUD's only interactive element, so the row pays the height.
        IconButton(onClick = onResetAlignment) {
            Icon(
                painterResource(R.drawable.ic_hud_reset),
                contentDescription = stringResource(R.string.hud_correction_reset),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Tabular figures so ticking values don't jitter the layout (D65). */
@Composable
private fun hudValueStyle() =
    MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
