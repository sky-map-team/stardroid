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
import coil.compose.SubcomposeAsyncImage

// The app's ImageLoader (SkyMapApplication) supplies the caches.
@Composable
internal actual fun RemoteImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    loading: @Composable () -> Unit,
    error: @Composable () -> Unit,
    modifier: Modifier,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = colorFilter,
        loading = { loading() },
        error = { error() },
        modifier = modifier,
    )
}
