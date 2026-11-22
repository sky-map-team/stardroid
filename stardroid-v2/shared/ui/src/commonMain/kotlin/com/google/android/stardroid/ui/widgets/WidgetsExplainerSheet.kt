/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.widgets_button
import com.google.android.stardroid.ui.resources.widgets_explainer_countdown_desc
import com.google.android.stardroid.ui.resources.widgets_explainer_countdown_title
import com.google.android.stardroid.ui.resources.widgets_explainer_intro
import com.google.android.stardroid.ui.resources.widgets_explainer_lock_screen
import com.google.android.stardroid.ui.resources.widgets_explainer_moon_desc
import com.google.android.stardroid.ui.resources.widgets_explainer_moon_title
import com.google.android.stardroid.ui.resources.widgets_explainer_step_1
import com.google.android.stardroid.ui.resources.widgets_explainer_step_2
import com.google.android.stardroid.ui.resources.widgets_explainer_step_3
import com.google.android.stardroid.ui.resources.widgets_explainer_tonight_desc
import com.google.android.stardroid.ui.resources.widgets_explainer_tonight_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Says what Sky Map's widgets are and how to place them. iOS can't add a widget from inside an
 * app, so this is the whole feature there; Android has its own sheet with Add buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsExplainerSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(Res.string.widgets_button),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(Res.string.widgets_explainer_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WidgetEntry(
                Res.string.widgets_explainer_moon_title,
                Res.string.widgets_explainer_moon_desc,
            )
            WidgetEntry(
                Res.string.widgets_explainer_tonight_title,
                Res.string.widgets_explainer_tonight_desc,
            )
            WidgetEntry(
                Res.string.widgets_explainer_countdown_title,
                Res.string.widgets_explainer_countdown_desc,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Step(1, Res.string.widgets_explainer_step_1)
                Step(2, Res.string.widgets_explainer_step_2)
                Step(3, Res.string.widgets_explainer_step_3)
            }
            Text(
                stringResource(Res.string.widgets_explainer_lock_screen),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetEntry(
    title: StringResource,
    description: StringResource,
) {
    Column {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(
    number: Int,
    text: StringResource,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$number.", style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}
