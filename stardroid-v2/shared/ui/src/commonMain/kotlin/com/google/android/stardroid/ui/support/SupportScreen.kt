/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.common.donationLinksAllowed
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.common.topBarWindowInsets
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.contributors_text
import com.google.android.stardroid.ui.resources.settings_back
import com.google.android.stardroid.ui.resources.sponsors_text
import com.google.android.stardroid.ui.resources.support_bmac_desc
import com.google.android.stardroid.ui.resources.support_bmac_title
import com.google.android.stardroid.ui.resources.support_intro
import com.google.android.stardroid.ui.resources.support_issues_desc
import com.google.android.stardroid.ui.resources.support_issues_title
import com.google.android.stardroid.ui.resources.support_screen_title
import com.google.android.stardroid.ui.resources.support_share_desc
import com.google.android.stardroid.ui.resources.support_share_title
import com.google.android.stardroid.ui.resources.support_star_desc
import com.google.android.stardroid.ui.resources.support_star_title
import com.google.android.stardroid.ui.resources.support_thanks_html
import org.jetbrains.compose.resources.stringResource

private const val GITHUB_URL = "https://github.com/sky-map-team/stardroid"
private const val ISSUES_URL = "$GITHUB_URL/issues"
private const val BMAC_URL =
    "https://buymeacoffee.com/skymapdevs?utm_source=skymap&utm_medium=app&utm_campaign=support"

/**
 * [onShareApp] is null where the host has no share sheet. [sponsorRow] is the host's own
 * sponsoring row (iOS's tip jar, which must be in-app purchase) and is always shown; without
 * one, a Buy Me a Coffee link shows only where [donationLinksAllowed].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    nightMode: Boolean,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
    onShareApp: (() -> Unit)?,
    sponsorRow: (@Composable () -> Unit)? = null,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.support_screen_title)) },
                windowInsets =
                    topBarWindowInsets(stringResource(Res.string.support_screen_title)),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(Res.string.support_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SupportRow(
                stringResource(Res.string.support_star_title),
                stringResource(Res.string.support_star_desc),
            ) {
                onAction(AnalyticsEvents.SUPPORT_ACTION_GITHUB_STAR)
                uriHandler.openUri(GITHUB_URL)
            }
            SupportRow(
                stringResource(Res.string.support_issues_title),
                stringResource(Res.string.support_issues_desc),
            ) {
                onAction(AnalyticsEvents.SUPPORT_ACTION_ISSUES)
                uriHandler.openUri(ISSUES_URL)
            }
            if (onShareApp != null) {
                SupportRow(
                    stringResource(Res.string.support_share_title),
                    stringResource(Res.string.support_share_desc),
                ) {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_SHARE)
                    onShareApp()
                }
            }
            if (sponsorRow != null) {
                sponsorRow()
            } else if (donationLinksAllowed) {
                SupportRow(
                    stringResource(Res.string.support_bmac_title),
                    stringResource(Res.string.support_bmac_desc),
                ) {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_BMAC)
                    uriHandler.openUri(BMAC_URL)
                }
            }
            StyledHtml(
                formattedStringResource(
                    Res.string.support_thanks_html,
                    stringResource(Res.string.sponsors_text),
                    stringResource(Res.string.contributors_text),
                ),
                nightMode = nightMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Public so a host's `sponsorRow` matches the other rows. */
@Composable
fun SupportRow(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
