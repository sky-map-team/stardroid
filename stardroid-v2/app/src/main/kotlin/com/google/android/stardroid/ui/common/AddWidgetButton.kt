/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.stardroid.R
import com.google.android.stardroid.widget.requestPinWidget

/**
 * "Add widget": launches the system pin dialog for the widget behind [receiver], falling back
 * to a how-to-add-it-by-hand dialog on launchers that can't pin (D75 discovery). Shared by the
 * Moon card's row and the Help screen so both offer exactly the same path. A cancelled pin
 * dialog is silent — the system owns that UI, and we only hear about a successful placement.
 */
@Composable
fun AddWidgetButton(
    receiver: Class<*>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showManualInstructions by remember { mutableStateOf(false) }
    TextButton(
        onClick = {
            if (!requestPinWidget(context, receiver)) showManualInstructions = true
        },
        modifier = modifier,
    ) {
        Text(stringResource(R.string.moon_widget_promo_add))
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
