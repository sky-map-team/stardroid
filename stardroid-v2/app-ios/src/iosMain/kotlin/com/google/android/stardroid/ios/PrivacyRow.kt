/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.support_privacy_desc
import com.google.android.stardroid.ui.resources.support_privacy_title
import com.google.android.stardroid.ui.support.SupportRow
import org.jetbrains.compose.resources.stringResource

// A redirect we control, so the policy can move without a new app release.
private const val PRIVACY_POLICY_URL = "https://privacy.getskymap.app"

/** The in-app link to the privacy policy that App Review guideline 5.1.1(i) asks for. */
@Composable
fun PrivacyRow() {
    val uriHandler = LocalUriHandler.current
    SupportRow(
        stringResource(Res.string.support_privacy_title),
        stringResource(Res.string.support_privacy_desc),
    ) {
        uriHandler.openUri(PRIVACY_POLICY_URL)
    }
}
