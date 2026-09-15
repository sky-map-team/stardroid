/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import kotlin.math.roundToInt

/**
 * The in-AR exposure and dimmer controls (camera-ar-mode.md/D64, exposure levers 1 + 2):
 * shown only while the camera layer is on, riding the chrome show/hide like everything
 * else. The dimmer is the GL scrim ("how bright is the video"); exposure is AE
 * compensation ("what did the sensor capture") — the row hides when the camera doesn't
 * support compensation.
 */
@Composable
fun ArControls(
    state: ArUiState,
    specs: ArCameraSpecs?,
    onScrimChange: (Double) -> Unit,
    onExposureChange: (Int) -> Unit,
    onIsoFractionChange: (Double) -> Unit,
    onShutterFractionChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
                .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        if (state.exposureSupported) {
            ArSliderRow(
                icon = R.drawable.ic_sun,
                description = stringResource(R.string.ar_exposure_slider),
                value = state.exposureIndex.toFloat(),
                range = state.exposureMin.toFloat()..state.exposureMax.toFloat(),
                onChange = { onExposureChange(it.roundToInt()) },
            )
        }
        ArSliderRow(
            icon = R.drawable.ic_ar_dimmer,
            description = stringResource(R.string.ar_dimmer_slider),
            value = state.scrim.toFloat(),
            range = 0f..MapViewModel.MAX_AR_SCRIM.toFloat(),
            onChange = { onScrimChange(it.toDouble()) },
        )
        // Temporary dev controls (exposure lever 4) while the night behavior is tuned:
        // manual ISO and shutter, log-scaled, far left = auto.
        if (state.manualExposureSupported && specs?.isoRange != null) {
            ManualExposureRow(
                label = stringResource(R.string.ar_iso_slider_label),
                description = stringResource(R.string.ar_iso_slider),
                fraction = state.isoFraction,
                valueText =
                    if (state.isoFraction > 0.0) {
                        ArExposureMath.isoForFraction(specs.isoRange, state.isoFraction)
                            .toString()
                    } else {
                        stringResource(R.string.ar_exposure_auto)
                    },
                onChange = onIsoFractionChange,
            )
        }
        if (state.manualExposureSupported && specs?.exposureTimeRangeNs != null) {
            ManualExposureRow(
                label = stringResource(R.string.ar_shutter_slider_label),
                description = stringResource(R.string.ar_shutter_slider),
                fraction = state.shutterFraction,
                valueText =
                    if (state.shutterFraction > 0.0) {
                        ArExposureMath.formatExposureTime(
                            ArExposureMath.exposureTimeForFraction(
                                specs.exposureTimeRangeNs,
                                state.shutterFraction,
                            ),
                        )
                    } else {
                        stringResource(R.string.ar_exposure_auto)
                    },
                onChange = onShutterFractionChange,
            )
        }
    }
}

@Composable
private fun ManualExposureRow(
    label: String,
    description: String,
    fraction: Double,
    valueText: String,
    onChange: (Double) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Slider(
            value = fraction.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            modifier =
                Modifier
                    .padding(start = 4.dp)
                    .width(112.dp)
                    .semantics { contentDescription = description },
        )
        Text(
            valueText,
            style =
                MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 6.dp).width(44.dp),
        )
    }
}

@Composable
private fun ArSliderRow(
    @DrawableRes icon: Int,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .width(140.dp)
                    .semantics { contentDescription = description },
        )
    }
}
