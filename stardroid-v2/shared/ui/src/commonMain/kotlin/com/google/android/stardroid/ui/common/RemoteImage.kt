/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale

/**
 * An image fetched over HTTPS: [loading] in its place until it arrives, [error] if it can't be
 * fetched or decoded. Coil on Android, with the app's memory and disk caches; Foundation's URL
 * loading on iOS, with the shared URL cache.
 */
@Composable
internal expect fun RemoteImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    loading: @Composable () -> Unit,
    error: @Composable () -> Unit,
    modifier: Modifier = Modifier,
)
