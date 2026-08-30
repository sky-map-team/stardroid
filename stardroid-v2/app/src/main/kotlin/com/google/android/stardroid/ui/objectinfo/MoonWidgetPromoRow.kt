/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.widget.requestPinMoonWidget

/**
 * The Moon card's add-a-widget offer (D75 discovery). "Add" launches the system one-tap pin
 * dialog ([requestPinMoonWidget]) — and never consumes the offer, so a cancelled or failed
 * pin keeps the row; it disappears on its own once a moon widget actually exists
 * (`ObjectInfoViewModel.moonWidgetPromo`). Only the explicit close button calls [onDone],
 * which persists the never-show-again bit.
 */
@Composable
fun MoonWidgetPromoRow(onDone: () -> Unit) {
    val context = LocalContext.current
    var showManualInstructions by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.moon_widget_promo_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.moon_widget_promo_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        if (!requestPinMoonWidget(context)) showManualInstructions = true
                    },
                ) {
                    Text(stringResource(R.string.moon_widget_promo_add))
                }
            }
            IconButton(onClick = onDone) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.moon_widget_promo_dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showManualInstructions) {
        AlertDialog(
            onDismissRequest = { showManualInstructions = false },
            title = { Text(stringResource(R.string.moon_widget_pin_unsupported_title)) },
            text = { Text(stringResource(R.string.moon_widget_pin_unsupported_body)) },
            confirmButton = {
                TextButton(onClick = { showManualInstructions = false }) {
                    Text(stringResource(R.string.moon_widget_pin_unsupported_ok))
                }
            },
        )
    }
}
