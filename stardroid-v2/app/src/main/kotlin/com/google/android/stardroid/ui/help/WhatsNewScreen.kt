/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.help

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.topBarWindowInsets

/**
 * The What's New & Credits destination (D74): the release notes, the support pitch, and the
 * credits that used to be appended to the bottom of the Help document, now first-class and
 * reachable from the overflow sheet. The startup `WhatsNewDialog` still shows the short
 * release notes once per upgrade; this screen is where they stay findable afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    nightMode: Boolean,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.whats_new_screen_title)) },
                windowInsets =
                    topBarWindowInsets(stringResource(R.string.whats_new_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val sponsors = stringResource(R.string.sponsors_text)
        val contributors = stringResource(R.string.contributors_text)
        val creditsText = stringResource(R.string.credits_text, sponsors, contributors)
        // The parse is remembered inside StyledHtml; the concat here is cheap.
        // The "New in version X" heading is dropped for the 2.0 launch copy, which opens with
        // its own splash line instead — restore it for later point releases once the launch
        // announcement has aged out.
        val html =
            stringResource(R.string.whats_new_content) +
                stringResource(R.string.beta_user_help_text) +
                stringResource(R.string.whats_new_support) +
                creditsText
        StyledHtml(
            html,
            nightMode = nightMode,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
