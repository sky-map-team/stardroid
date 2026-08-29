/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.stardroid.R
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The search overlay, v1's `OverlayManager` in Compose: a 60% dim over the sky, the pulsing
 * crosshair on the target, and — until the target centers — the guide circle with the
 * directional arrow, morphing into an expanding "found" circle over v1's one-second fade.
 *
 * Everything projects through the same pure [SkyProjection] the GL backend loads (D21), so the
 * crosshair pins to the drawn sky pixel-for-pixel. The crosshair therefore lives here rather
 * than as the sky-anchored icon scene layers-and-app.md sketched: v1's 1 Hz pulse would need
 * ~30 Hz scene resubmission and an icon tint the render API deliberately lacks, while one
 * Canvas already redraws with every camera frame (D43).
 */
@Composable
fun SearchOverlay(
    camera: SkyCamera,
    target: SearchTarget,
    nightMode: Boolean,
    found: Boolean,
    modifier: Modifier = Modifier,
) {
    // v1 CrosshairOverlay: intensity = 0.7 + 0.3·sin(2πt), period one second.
    val pulsePhase by rememberInfiniteTransition(label = "crosshairPulse")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(PULSE_PERIOD_MILLIS, easing = LinearEasing)),
            label = "crosshairPulsePhase",
        )
    // v1 SearchHelper: the seeking↔found transition sweeps 0..1 in about a second.
    val transition by animateFloatAsState(
        targetValue = if (found) 1f else 0f,
        animationSpec = tween(TRANSITION_MILLIS, easing = LinearEasing),
        label = "searchFocusTransition",
    )

    Canvas(modifier) {
        // v1 OverlayManager's mDarkQuad: dim the whole sky while searching.
        drawRect(Color.Black.copy(alpha = DIM_ALPHA))
        if (size.width < 1f || size.height < 1f) return@Canvas

        val projection =
            SkyProjection(
                camera,
                Viewport(size.width.roundToInt(), size.height.roundToInt(), density),
            )
        val center = Offset(size.width / 2f, size.height / 2f)
        val focusRadius = min(size.width, size.height) - SearchGeometry.FOCUS_RADIUS_INSET_PX

        projection.worldToScreen(target.direction)?.let { point ->
            val intensity = 0.7f + 0.3f * sin(2f * Math.PI.toFloat() * pulsePhase)
            val color =
                if (nightMode) {
                    Color(intensity, 0f, 0f, CROSSHAIR_ALPHA)
                } else {
                    Color(intensity, intensity, 0f, CROSSHAIR_ALPHA)
                }
            drawCrosshair(Offset(point.xPx, point.yPx), CROSSHAIR_RADIUS_DP.dp.toPx(), color)
        }

        val guideRadius = GUIDE_CIRCLE_FRACTION * focusRadius
        if (transition == 0f) {
            // Seeking: v1's red-near → blue-far tint on the guide circle and arrow.
            val distance =
                SearchGeometry.normalizedSeparation(camera, target.direction).toFloat()
            val color =
                if (nightMode) Color(0.6f, 0f, 0f) else Color(1f - distance, 0f, distance)
            drawCircle(color, guideRadius, center, style = Stroke(RING_STROKE_DP.dp.toPx()))
            val bearingDeg =
                SearchGeometry.screenBearingRad(camera, target.direction) * RADIANS_TO_DEGREES
            rotate(degrees = bearingDeg.toFloat(), pivot = center) {
                drawArrow(center, guideRadius, ARROW_FRACTION * min(size.width, size.height), color)
            }
        } else {
            // Locking on: v1's expanding orange circle (red in night mode), no arrow.
            val color = if (nightMode) Color(1f, 0f, 0f) else Color(1f, 0.5f, 0f)
            val radius = guideRadius + (0.5f * focusRadius - guideRadius) * transition
            drawCircle(
                color.copy(alpha = FOUND_CIRCLE_ALPHA),
                radius,
                center,
                style = Stroke(RING_STROKE_DP.dp.toPx()),
            )
        }
    }
}

/** v1's 40 px crosshair texture as strokes: a ring with four ticks, in dp for today's density. */
private fun DrawScope.drawCrosshair(
    at: Offset,
    radius: Float,
    color: Color,
) {
    val stroke = Stroke(RING_STROKE_DP.dp.toPx())
    drawCircle(color, radius * 0.6f, at, style = stroke)
    for ((dx, dy) in CROSSHAIR_TICK_DIRECTIONS) {
        drawLine(
            color,
            start = Offset(at.x + dx * radius * 0.6f, at.y + dy * radius * 0.6f),
            end = Offset(at.x + dx * radius, at.y + dy * radius),
            strokeWidth = stroke.width,
        )
    }
}

/** A solid triangle just outside the guide circle, pointing +x (the rotation supplies bearing). */
private fun DrawScope.drawArrow(
    center: Offset,
    circleRadius: Float,
    length: Float,
    color: Color,
) {
    val base = center.x + circleRadius + ARROW_GAP_DP.dp.toPx()
    val path =
        Path().apply {
            moveTo(base + length, center.y)
            lineTo(base, center.y - length / 2f)
            lineTo(base, center.y + length / 2f)
            close()
        }
    drawPath(path, color)
}

/**
 * v1's `search_control_bar`: the Looking for / Found status, the point-your-phone prompt while
 * seeking, and the cancel button.
 */
@Composable
fun SearchControlBar(
    targetName: String,
    found: Boolean,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        // The translucent surface color is not an exact M3 role, so contentColorFor() can't
        // derive a readable foreground and Text falls back to black. Set it explicitly.
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                stringResource(
                    if (found) {
                        R.string.search_target_found_message
                    } else {
                        R.string.search_target_looking_message
                    },
                    targetName,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!found) {
                Text(
                    stringResource(R.string.search_overlay_prompt),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.search_cancel))
            }
        }
    }
}

/**
 * The in-app search box: v1's system `SearchView` + `SearchTermsProvider` suggestions folded
 * into one Compose dialog. The as-you-type list doubles as v1's "Did you mean?" dialog; the
 * keyboard's search action is v1's query submit (coordinates first, then names).
 */
@Composable
fun SearchDialog(
    viewModel: SearchViewModel,
    onDismiss: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val noResults by viewModel.noResults.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_label)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    // RA/Dec entry was invisible before: show the formats under the field.
                    supportingText = {
                        Text(stringResource(R.string.search_coordinates_hint))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                )
                if (noResults) {
                    Text(
                        stringResource(R.string.search_no_results),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(suggestions, key = { it.id.value }) { hit ->
                        SuggestionRow(hit, onClick = { viewModel.select(hit) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.submit() }) {
                Text(stringResource(R.string.search_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.search_cancel))
            }
        },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun SuggestionRow(
    hit: SearchHit,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(hit.name, style = MaterialTheme.typography.bodyLarge)
        hit.subtext?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** v1 OverlayManager: 60% black dim behind the overlay. */
private const val DIM_ALPHA = 0.6f

/** v1 CrosshairOverlay: one-second pulse, fixed 0.7 alpha, 40 px (here dp) across. */
private const val PULSE_PERIOD_MILLIS = 1000
private const val CROSSHAIR_ALPHA = 0.7f
private const val CROSSHAIR_RADIUS_DP = 20
private val CROSSHAIR_TICK_DIRECTIONS = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)

/** v1 SearchHelper's ±0.001/ms transition factor: a one-second seeking↔found fade. */
private const val TRANSITION_MILLIS = 1000

/** v1 SearchArrow: CIRCLE_SIZE 0.4 of the focus diameter → 0.2 of it as a radius. */
private const val GUIDE_CIRCLE_FRACTION = 0.2f

/** v1 SearchArrow: ARROW_SIZE 0.1 of the short screen side. */
private const val ARROW_FRACTION = 0.1f
private const val ARROW_GAP_DP = 4
private const val RING_STROKE_DP = 3

/** v1 SearchArrow's expandFactor path draws the found circle at 0.7 alpha. */
private const val FOUND_CIRCLE_ALPHA = 0.7f
