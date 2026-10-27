/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.calibration

import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v1 played `calib.gif` in a WebView to dodge pre-Pie animated-gif gaps; our minSdk (28) lets
 * [ImageDecoder] drive an [AnimatedImageDrawable] directly.
 */
@Composable
internal actual fun FigureEightAnimation(
    nightMode: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val drawable by
        produceState<AnimatedImageDrawable?>(initialValue = null, context) {
            value =
                withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(context.assets, FIGURE_EIGHT_PATH)
                    ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable
                }
            value?.start()
        }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { view ->
            view.setImageDrawable(drawable)
            view.colorFilter =
                if (nightMode) {
                    PorterDuffColorFilter(NightPhotoTint.toArgb(), PorterDuff.Mode.MULTIPLY)
                } else {
                    null
                }
        },
        modifier = modifier,
    )
}
