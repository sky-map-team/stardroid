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
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.beta_user_help_text
import com.google.android.stardroid.ui.resources.credits_body
import com.google.android.stardroid.ui.resources.settings_back
import com.google.android.stardroid.ui.resources.whats_new_content
import com.google.android.stardroid.ui.resources.whats_new_screen_title
import org.jetbrains.compose.resources.stringResource

/**
 * The What's New & Credits destination (D74): the release notes and the credits that used
 * to be appended to the bottom of the Help document, now first-class and reachable from the
 * overflow sheet. The startup `WhatsNewDialog` still shows the short release notes once per
 * upgrade; this screen is where they stay findable afterwards.
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
                title = { Text(stringResource(Res.string.whats_new_screen_title)) },
                windowInsets =
                    topBarWindowInsets(stringResource(Res.string.whats_new_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // The "New in version X" heading is dropped for the 2.0 launch copy, which opens with
        // its own splash line instead — restore it for later point releases once the launch
        // announcement has aged out.
        // beta_user_help_text leads for the same reason as WhatsNewDialog: it shouldn't get
        // buried below a long feature list.
        val html =
            stringResource(Res.string.beta_user_help_text) +
                stringResource(Res.string.whats_new_content) +
                stringResource(Res.string.credits_body)
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
