/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.ui.common.StyledHtml
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.dialog_accept
import com.google.android.stardroid.ui.resources.dialog_decline
import com.google.android.stardroid.ui.resources.eula_agree_line
import com.google.android.stardroid.ui.resources.eula_text
import com.google.android.stardroid.ui.resources.eula_title
import com.google.android.stardroid.ui.resources.permissions_notice
import org.jetbrains.compose.resources.stringResource

/**
 * The terms' reading measure. A portrait phone is narrower than this, so the cap does nothing
 * there; it bites in landscape and on tablets, where the full window width would otherwise run
 * the paragraphs to well over a hundred characters a line.
 */
private val EULA_MAX_WIDTH: Dp = 560.dp

/**
 * v1 `EulaDialogFragment` in its gating form, grown into a full-screen destination (the
 * cramped AlertDialog scrolled poorly for a document this long): Accept proceeds, No Thanks
 * declines. The HTML terms render natively via [StyledHtml] instead of a WebView (D48).
 *
 * Shared by both apps (D134). What declining means is each platform's: Android exits, as v1 did,
 * and routes BACK to [onDecline] around this screen. An iOS app may not quit itself, so iOS
 * passes no [onDecline] and the screen offers Accept alone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EulaScreen(
    nightMode: Boolean,
    onAccept: () -> Unit,
    onDecline: (() -> Unit)?,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.eula_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomAppBar {
                // FlowRow rather than Row: a long translation of either label can outgrow the
                // bar's width, which would squeeze the two buttons together instead of
                // wrapping.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    if (onDecline != null) {
                        TextButton(onClick = onDecline) {
                            Text(stringResource(Res.string.dialog_decline))
                        }
                    }
                    Button(onClick = onAccept) {
                        Text(stringResource(Res.string.dialog_accept))
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
                stringResource(Res.string.eula_text) +
                    stringResource(Res.string.permissions_notice) +
                    stringResource(Res.string.eula_agree_line),
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
