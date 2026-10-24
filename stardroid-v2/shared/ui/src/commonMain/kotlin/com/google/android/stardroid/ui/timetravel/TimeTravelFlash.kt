/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.timetravel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.google.android.stardroid.ui.common.formatForLocale
import com.google.android.stardroid.ui.common.rememberDateTimeFormatter
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.time_travel_close_message
import com.google.android.stardroid.ui.resources.time_travel_start_message
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * v1's time-travel theatrics: the translucent purple flash over the sky on each trip
 * (`view_mask` + `timetravelflash`, a 0 → 0.7 → 0 alpha pulse over 2 s), and the notice naming
 * where the clock went, or that it came home, handed to [onNotice] (v1 toasted; Android shows a
 * snackbar, which follows the theme and red-shifts in night mode). Place it over the sky and under
 * the controls, filling them; it takes no touches. Shared by both apps (D134).
 */
@Composable
fun TimeTravelFlash(
    effects: Flow<TravelEffect>,
    onNotice: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flashAlpha = remember { Animatable(0f) }
    // Composition sees only this boolean (two recompositions per flash); the per-frame alpha
    // is read in the draw phase below.
    var flashVisible by remember { mutableStateOf(false) }
    val formatter by rememberUpdatedState(rememberDateTimeFormatter())
    val travelTemplate by rememberUpdatedState(stringResource(Res.string.time_travel_start_message))
    val returnMessage by rememberUpdatedState(stringResource(Res.string.time_travel_close_message))
    val notice by rememberUpdatedState(onNotice)
    LaunchedEffect(effects) {
        var flashJob: Job? = null
        effects.collect { effect ->
            val message =
                when (effect) {
                    is TravelEffect.Travel ->
                        formatForLocale(travelTemplate, arrayOf(formatter(effect.destination)))
                    TravelEffect.Return -> returnMessage
                }
            // A child launch: a snackbar's show suspends for its lifetime, and a back-to-back
            // travel must still restart the flash below immediately.
            launch { notice(message) }
            // Restart the pulse from scratch on back-to-back travels: joining the old job first
            // keeps its finally from hiding the overlay under the new pulse.
            flashJob?.cancelAndJoin()
            flashJob =
                launch {
                    flashVisible = true
                    try {
                        flashAlpha.snapTo(0f)
                        flashAlpha.animateTo(
                            FLASH_PEAK_ALPHA,
                            tween(FLASH_RAMP_MS, easing = LinearEasing),
                        )
                        flashAlpha.animateTo(0f, tween(FLASH_RAMP_MS, easing = LinearEasing))
                    } finally {
                        flashVisible = false
                    }
                }
        }
    }
    // Never composed while idle; the alpha is read in the draw phase (graphicsLayer) so the
    // ramp redraws without recomposing the screen.
    if (flashVisible) {
        Box(modifier.graphicsLayer { alpha = flashAlpha.value }.background(FLASH_COLOR))
    }
}

// v1's time-travel flash (`timetravelflash.xml`): pulsed to 0.7 alpha and back, one second each
// way. v1's `view_mask` magenta (#990099) is re-hued to the launcher icon's indigo for D73 —
// it keeps the "something just happened" jolt while staying in the navy family, where gold at
// this alpha would wash the sky out and read as a rendering fault.
private val FLASH_COLOR = Color(0xFF3D2F74)
private const val FLASH_PEAK_ALPHA = 0.7f
private const val FLASH_RAMP_MS = 1000
