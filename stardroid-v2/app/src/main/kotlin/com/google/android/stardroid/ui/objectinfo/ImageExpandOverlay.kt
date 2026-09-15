/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.stardroid.R
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * v1's `ImageExpandDialogFragment` as a Compose dialog: the full-resolution photo on black,
 * with the credit line and a tap-to-close hint that fades after two seconds. The v2 upgrade
 * per screens-and-startup.md: pinch zooms and drags the photo. A tap anywhere closes, as in
 * v1. Rendered as a [Dialog] so it stacks above the info card's own dialog window.
 */
@Composable
fun ImageExpandOverlay(
    imageRef: String,
    name: String,
    credit: String?,
    nightMode: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val assets = LocalContext.current.assets
        val bitmap by produceState<ImageBitmap?>(initialValue = null, imageRef) {
            value = null
            value =
                withContext(Dispatchers.IO) {
                    try {
                        assets.open("celestial_images/$imageRef").use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
        }
        var scale by remember(imageRef) { mutableFloatStateOf(1f) }
        var offset by remember(imageRef) { mutableStateOf(Offset.Zero) }
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        val transformState =
            rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 8f)
                // ContentScale.Fit letterboxes/pillarboxes, so the displayed image can be
                // smaller than the container — bound the pan to its actual displayed size.
                val image = bitmap
                val (maxX, maxY) =
                    if (image != null && containerSize.width > 0 && containerSize.height > 0) {
                        val imageAspect = image.width.toFloat() / image.height
                        val containerAspect =
                            containerSize.width.toFloat() / containerSize.height
                        val (displayedWidth, displayedHeight) =
                            if (imageAspect > containerAspect) {
                                containerSize.width.toFloat() to
                                    containerSize.width / imageAspect
                            } else {
                                containerSize.height * imageAspect to
                                    containerSize.height.toFloat()
                            }
                        (scale * displayedWidth - containerSize.width).coerceAtLeast(0f) / 2f to
                            (scale * displayedHeight - containerSize.height).coerceAtLeast(0f) / 2f
                    } else {
                        0f to 0f
                    }
                offset =
                    if (scale > 1f) {
                        Offset(
                            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
                        )
                    } else {
                        Offset.Zero
                    }
            }
        var hintVisible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            // v1: the hint starts fading after two seconds.
            delay(2_000)
            hintVisible = false
        }
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    // pointerInput is outer and transformable inner: the Main pass runs
                    // inner-to-outer, so transformable consumes pan/pinch first and this tap
                    // detector — checking isConsumed — sees it before firing. Only dismiss on
                    // an unconsumed tap that never saw a second pointer (else lifting one
                    // finger mid-pinch would dismiss).
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isMultiTouch = false
                        var up: PointerInputChange? = null
                        while (up == null) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            if (event.changes.size > 1) isMultiTouch = true
                            up = event.changes.firstOrNull { it.changedToUp() }
                        }
                        val slop = viewConfiguration.touchSlop
                        val isTap = up != null && (up.position - down.position).getDistance() < slop
                        if (isTap && !isMultiTouch) {
                            currentOnDismiss()
                        }
                    }
                }
                .transformable(transformState)
                .onSizeChanged { containerSize = it },
        ) {
            bitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    colorFilter =
                        if (nightMode) {
                            ColorFilter.tint(NightPhotoTint, BlendMode.Modulate)
                        } else {
                            null
                        },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                val textColor = if (nightMode) NightPhotoTint else Color.White
                credit?.let {
                    Text(
                        stringResource(R.string.object_info_image_credit, it),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    )
                }
                AnimatedVisibility(hintVisible, exit = fadeOut(tween(500))) {
                    Text(
                        stringResource(R.string.object_info_tap_to_close),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
