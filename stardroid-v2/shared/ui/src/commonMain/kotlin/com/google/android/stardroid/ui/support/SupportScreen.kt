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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.google.android.stardroid.ui.resources.support_rate_desc
import com.google.android.stardroid.ui.resources.support_rate_title
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
 * [onShareApp] is null where the host has no share sheet; [onRateApp] where there is no store
 * listing to review. [sponsorRow] is the host's own
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
    onRateApp: (() -> Unit)? = null,
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
            if (onRateApp != null) {
                SupportRow(
                    stringResource(Res.string.support_rate_title),
                    stringResource(Res.string.support_rate_desc),
                    icon = Icons.Filled.ThumbUp,
                ) {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_RATE)
                    onRateApp()
                }
            }
            if (onShareApp != null) {
                SupportRow(
                    stringResource(Res.string.support_share_title),
                    stringResource(Res.string.support_share_desc),
                    icon = Icons.Filled.Share,
                ) {
                    onAction(AnalyticsEvents.SUPPORT_ACTION_SHARE)
                    onShareApp()
                }
            }
            SupportRow(
                stringResource(Res.string.support_star_title),
                stringResource(Res.string.support_star_desc),
                icon = Icons.Filled.Star,
            ) {
                onAction(AnalyticsEvents.SUPPORT_ACTION_GITHUB_STAR)
                uriHandler.openUri(GITHUB_URL)
            }
            SupportRow(
                stringResource(Res.string.support_issues_title),
                stringResource(Res.string.support_issues_desc),
                icon = Icons.Filled.Create,
            ) {
                onAction(AnalyticsEvents.SUPPORT_ACTION_ISSUES)
                uriHandler.openUri(ISSUES_URL)
            }
            if (sponsorRow != null || donationLinksAllowed) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (sponsorRow != null) {
                sponsorRow()
            } else if (donationLinksAllowed) {
                SupportRow(
                    stringResource(Res.string.support_bmac_title),
                    stringResource(Res.string.support_bmac_desc),
                    icon = Icons.Filled.Favorite,
                    emphasized = true,
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

/**
 * Public so a host's `sponsorRow` matches the other rows. The icon and chevron mark the row as
 * tappable; [emphasized] tints it as the one monetary ask.
 */
@Composable
fun SupportRow(
    title: String,
    description: String,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val colors =
        if (emphasized) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
                supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                trailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            ListItemDefaults.colors(
                leadingIconColor = MaterialTheme.colorScheme.primary,
            )
        }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = colors,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
