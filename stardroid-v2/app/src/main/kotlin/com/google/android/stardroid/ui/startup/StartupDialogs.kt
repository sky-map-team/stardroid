/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.common.StyledHtml

/**
 * The terms' reading measure. A portrait phone is narrower than this, so the cap does nothing
 * there; it bites in landscape and on tablets, where the full window width would otherwise run
 * the paragraphs to well over a hundred characters a line.
 */
private val EULA_MAX_WIDTH: Dp = 560.dp

/** The manifest version name, for the What's New and Help headings (v1 `getVersionName`). */
@Composable
fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}

/**
 * v1 `EulaDialogFragment` in its gating form, grown into a full-screen destination (the
 * cramped AlertDialog scrolled poorly for a document this long): Accept proceeds, No Thanks
 * (or BACK — v1 routed cancel to reject) exits the app. The HTML terms render natively via
 * [StyledHtml] instead of a WebView (D48).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EulaScreen(
    nightMode: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    BackHandler(onBack = onDecline)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.eula_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    TextButton(onClick = onDecline) {
                        Text(stringResource(R.string.dialog_decline))
                    }
                    Button(onClick = onAccept) {
                        Text(stringResource(R.string.dialog_accept))
                    }
                }
            }
        },
    ) { padding ->
        // The access-permission notice rides on the terms screen so it is disclosed before any
        // permission is requested (Korean Network Act art. 22-2 items 1-3; see eula.xml). Help
        // renders the same key, so a user who has already accepted can still read it.
        // The scroll lives on the full-width box, not on the capped column: a narrower
        // scrollable would leave the surplus width beside it inert, and a drag started there
        // — the natural place to put a thumb on a wide screen — would do nothing.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            // Start-aligned, not centred: the top app bar's title sits at the leading edge,
            // and a centred column would start well inboard of it — and land on the centred
            // Sky Map watermark behind the screen.
            contentAlignment = Alignment.TopStart,
        ) {
            StyledHtml(
                stringResource(R.string.eula_text) +
                    stringResource(R.string.permissions_notice) +
                    stringResource(R.string.eula_agree_line),
                nightMode = nightMode,
                modifier =
                    Modifier
                        .widthIn(max = EULA_MAX_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * v1 `WhatsNewDialogFragment`: the support ask and beta-feedback note lead, ahead of the
 * per-release feature list, so a long feature list can't push the support ask below the fold.
 * Shown on upgrades (never fresh installs — the warm welcome marks it seen). Any dismissal marks
 * the current version seen, as v1's single OK/close path did.
 */
@Composable
fun WhatsNewDialog(
    nightMode: Boolean,
    onDismiss: () -> Unit,
) {
    // The default AlertDialog wraps its content width, so a release with a lot of text ends up
    // tall and narrow. Pin a fixed width and cap the height to a fraction of the screen so the
    // dialog keeps a pleasing aspect ratio and scrolls internally instead of stretching.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whats_new_dialog_title)) },
        text = {
            // The "New in version X" heading is dropped for the 2.0 launch copy, which opens
            // with its own splash line instead — restore it for later point releases once the
            // launch announcement has aged out.
            val html =
                stringResource(R.string.whats_new_support) +
                    stringResource(R.string.beta_user_help_text) +
                    stringResource(R.string.whats_new_content)
            StyledHtml(
                html,
                nightMode = nightMode,
                modifier =
                    Modifier
                        .width(320.dp)
                        .heightIn(max = screenHeight * 0.6f)
                        .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok_button))
            }
        },
    )
}
