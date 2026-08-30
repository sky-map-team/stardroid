/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.sensors.SensorAccuracy
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.theme.NightPhotoTint
import com.google.android.stardroid.ui.theme.statusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The compass-calibration screen — v1's `CompassCalibrationActivity` as a full-screen Compose
 * overlay: the figure-eight animation, the live calibration readout, and (when the low-accuracy
 * monitor opened it) the "don't show again" opt-out. In that auto-opened form it dismisses
 * itself the moment the compass reads HIGH ([onCalibrated]; v1's `AUTO_DISMISSABLE`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassCalibrationScreen(
    viewModel: CompassCalibrationViewModel,
    nightMode: Boolean,
    userInitiated: Boolean,
    onCalibrated: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val accuracy by viewModel.accuracy.collectAsStateWithLifecycle()
    val dontShowAgain by viewModel.dontShowAgain.collectAsStateWithLifecycle()

    if (!userInitiated) {
        val currentOnCalibrated by rememberUpdatedState(onCalibrated)
        LaunchedEffect(accuracy) {
            if (accuracy == SensorAccuracy.HIGH) currentOnCalibrated()
        }
    }

    val barTitle =
        stringResource(
            if (userInitiated) {
                R.string.calibration_heading_user
            } else {
                R.string.calibration_heading_warning
            },
        )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(barTitle) },
                windowInsets = topBarWindowInsets(barTitle),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FigureEightAnimation(nightMode)
                AccuracyReadout(viewModel.hasMagnetometer, accuracy, nightMode)
                StyledHtml(
                    stringResource(
                        if (userInitiated) {
                            R.string.calibration_what_to_do_user
                        } else {
                            R.string.calibration_what_to_do
                        },
                        CALIBRATION_VIDEO_URL,
                    ),
                    nightMode = nightMode,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (!userInitiated) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = viewModel::setDontShowAgain,
                        )
                        Text(stringResource(R.string.calibration_do_not_show_again))
                    }
                }
                Button(
                    onClick = onBack,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_ok))
                }
            }
        }
    }
}

/**
 * v1 played `calib.gif` in a WebView to dodge pre-Pie animated-gif gaps; minSdk 29 lets
 * [ImageDecoder] drive an [AnimatedImageDrawable] directly. Night mode red-multiplies the
 * frames like every other photograph (D46).
 */
@Composable
private fun FigureEightAnimation(nightMode: Boolean) {
    val context = LocalContext.current
    val drawable by
        produceState<AnimatedImageDrawable?>(initialValue = null, context) {
            value =
                withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(context.assets, "calibration/calib.gif")
                    ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable
                }
            value?.start()
        }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            view.setImageDrawable(drawable)
            view.colorFilter =
                if (nightMode) {
                    PorterDuffColorFilter(NightPhotoTint.toArgb(), PorterDuff.Mode.MULTIPLY)
                } else {
                    null
                }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp),
    )
}

@Composable
private fun AccuracyReadout(
    hasMagnetometer: Boolean,
    accuracy: SensorAccuracy?,
    nightMode: Boolean,
) {
    val colors = statusColors(nightMode)
    val text: String
    val color =
        if (!hasMagnetometer) {
            text = stringResource(R.string.diagnostics_sensor_absent)
            colors.absent
        } else {
            text =
                stringResource(
                    when (accuracy) {
                        SensorAccuracy.HIGH -> R.string.calibration_accuracy_high
                        SensorAccuracy.MEDIUM -> R.string.calibration_accuracy_medium
                        SensorAccuracy.LOW -> R.string.calibration_accuracy_low
                        SensorAccuracy.UNRELIABLE -> R.string.calibration_accuracy_unreliable
                        SensorAccuracy.NO_CONTACT -> R.string.calibration_accuracy_no_contact
                        null -> R.string.calibration_accuracy_unknown
                    },
                )
            when (accuracy) {
                SensorAccuracy.HIGH -> colors.good
                SensorAccuracy.MEDIUM -> colors.ok
                SensorAccuracy.LOW -> colors.warning
                SensorAccuracy.UNRELIABLE, SensorAccuracy.NO_CONTACT -> colors.bad
                null -> colors.absent
            }
        }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** v1's linked demonstration video. */
private const val CALIBRATION_VIDEO_URL = "https://www.youtube.com/watch?v=-Uq7AmSAjt8"
