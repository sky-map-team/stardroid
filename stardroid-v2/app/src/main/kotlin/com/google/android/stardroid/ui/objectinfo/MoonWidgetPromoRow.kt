/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.AddWidgetButton
import com.google.android.stardroid.widget.MoonWidgetReceiver

/**
 * The Moon card's permanent add-a-widget row (D75 discovery). "Add" launches the system
 * one-tap pin dialog and never consumes the offer, so a cancelled pin keeps the row.
 *
 * Nothing dismisses it. Instead it recedes on its own: the full title-and-subtitle pitch
 * while no moon widget exists ([placed] false), then a single quiet "Add widget" line once
 * one does — still there for a second widget, no longer selling the first.
 */
@Composable
fun MoonWidgetPromoRow(placed: Boolean) {
    if (placed) {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AddWidgetButton(MoonWidgetReceiver::class.java)
        }
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.moon_widget_promo_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.moon_widget_promo_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddWidgetButton(MoonWidgetReceiver::class.java)
        }
    }
}
