/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.options

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.topBarWindowInsets

/**
 * The ⋮ button's full-screen page: the destinations and one-shot actions that don't earn
 * permanent map pixels. Rows are ordered by when a user needs them — Help and Tutorial
 * first, where a lost newcomer finds them without scrolling (Hannah's feedback, 2026-08).
 *
 * Location and Share stay map-anchored (the location sheet and the sky capture live on
 * the map), so their callbacks hand a pending action back to the map before returning;
 * everything else navigates onward from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    onBack: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenTutorial: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenGallery: () -> Unit,
    onShareSky: (() -> Unit)?,
    onOpenWhatsNew: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    // No collapsing scroll behavior: with eight short rows the collapse range would offer
    // scroll where there's nothing to scroll. The column still scrolls on overflow (small
    // screens, landscape, large font scales).
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_button)) },
                windowInsets = topBarWindowInsets(),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            OptionsRow(R.drawable.ic_help, R.string.help_button, onOpenHelp)
            OptionsRow(R.drawable.ic_tutorial, R.string.tutorial_button, onOpenTutorial)
            OptionsRow(R.drawable.ic_settings, R.string.settings_button, onOpenSettings)
            OptionsRow(R.drawable.ic_location, R.string.location_button, onOpenLocation)
            OptionsRow(R.drawable.ic_gallery, R.string.gallery_button, onOpenGallery)
            if (onShareSky != null) {
                OptionsRow(R.drawable.ic_share, R.string.share_button, onShareSky)
            }
            OptionsRow(R.drawable.ic_whats_new, R.string.whats_new_button, onOpenWhatsNew)
            OptionsRow(R.drawable.ic_calibrate, R.string.calibration_button, onOpenCalibration)
        }
    }
}

@Composable
private fun OptionsRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}
