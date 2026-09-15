/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.stardroid.ui.map.CHROME_TOUR_STOPS
import com.google.android.stardroid.ui.map.ChromeTourTarget
import com.google.android.stardroid.ui.map.MapChrome
import com.google.android.stardroid.ui.map.ReferenceFrame
import com.google.android.stardroid.ui.map.chromeTourLabel
import com.google.android.stardroid.ui.map.demoChromeToggles
import com.google.android.stardroid.ui.theme.TourColors
import com.google.android.stardroid.ui.theme.tourColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** How long the spotlight rests on each control (v1 cycled its highlight groups at 1 s). */
private const val TOUR_STEP_MS = 1500L

/**
 * Slide 1's guided tour (D61): the real [MapChrome] — same composables the map shows, with
 * canned all-layers-on state and no-op callbacks — over the welcome slide's sky screenshot,
 * with a v1-style neon ring + arrow + label spotlight cycling through every control. Because
 * the chrome is the real thing, the tour can't drift out of date, localizes through the same
 * string resources, and lays out correctly on every screen. Each control reports its bounds
 * through `tourTargetModifier`; stops whose control isn't on screen — an `ifroom` rail toggle
 * dropped on a short screen, or anything clipped by a small demo box — are skipped, so the
 * tour always shows the chrome this window actually has. The chrome is display-only: its
 * semantics are cleared (TalkBack users get the slide's text) and taps are swallowed.
 */
@Composable
internal fun ChromeTourDemo(
    active: Boolean,
    nightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val targetCoordinates = remember { mutableStateMapOf<ChromeTourTarget, LayoutCoordinates>() }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        step = 0
        while (true) {
            delay(TOUR_STEP_MS)
            step += 1
        }
    }

    // MapChrome insets itself against the window's safe-drawing area, which is right for a
    // full-screen map and wrong for a demo panel that is nowhere near those edges: unconsumed,
    // it would pay the navigation-bar inset the welcome screen has already applied and a
    // landscape display cutout's inset on an edge it does not touch, both out of the height
    // the chrome is shortest of.
    //
    // The top edge is deliberately left unconsumed. This box's top really is the window's top
    // (the welcome screen applies only navigationBarsPadding), so anything MapChrome anchors
    // there would land under the status bar or a cutout. Nothing does today — the tour passes
    // no hudState, and the HUD is the only top-anchored zone — but consuming the whole set
    // would make that a silent dependency on a default argument.
    Box(
        modifier
            .consumeWindowInsets(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .onGloballyPositioned { rootCoordinates = it },
    ) {
        // The chrome is a live rendering, not a working control surface: cleared semantics
        // keep TalkBack from announcing a dozen dead buttons, and the overlay swallows taps
        // so a press can't ripple a control that does nothing (pager swipes still work —
        // the overlay doesn't consume).
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) {
            MapChrome(
                toggles = remember { demoChromeToggles() },
                nightMode = nightMode,
                referenceFrame = ReferenceFrame.SENSOR,
                sensorsAvailable = true,
                onToggleLayer = { _, _ -> },
                onToggleReferenceFrame = {},
                onToggleNightMode = {},
                onOpenSearch = {},
                onOpenTimeTravel = {},
                onOpenLayersSheet = {},
                onOpenOverflow = {},
                tourTargetModifier = { target ->
                    Modifier.onGloballyPositioned { targetCoordinates[target] = it }
                },
                // The tour names each control in its own spotlight chip; rail labels would
                // sit in the same space saying the same words twice. (Null is the default,
                // but stated explicitly — this is a decision, not an omission.)
                railLabels = null,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {},
            )
        }
        // Attachment alone is not enough to earn a stop: a control that overflows a short
        // demo box is still composed and positioned, just clipped, so filtering on isAttached
        // would spend TOUR_STEP_MS spotlighting a ring drawn outside the visible box. Only
        // controls whose bounds land inside the box are on the tour.
        val root = rootCoordinates?.takeIf { it.isAttached }
        val stops =
            if (root == null) {
                emptyList()
            } else {
                CHROME_TOUR_STOPS.mapNotNull { target ->
                    val coordinates =
                        targetCoordinates[target]?.takeIf { it.isAttached }
                            ?: return@mapNotNull null
                    val bounds = root.localBoundingBoxOf(coordinates, clipBounds = false)
                    if (bounds.isVisibleIn(root.size)) target to bounds else null
                }
            }
        val stop = stops.getOrNull(step % max(1, stops.size))
        if (stop != null) {
            TourSpotlight(
                bounds = stop.second,
                label = stringResource(chromeTourLabel(stop.first)),
                colors = tourColors(nightMode),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Whether a control's bounds sit wholly inside a box of [size]. A half-clipped control is not
 * worth a stop — the ring would run off the edge — so this asks for the whole thing, with a
 * pixel of tolerance for controls laid out flush against the box's own padding.
 */
private fun Rect.isVisibleIn(size: IntSize): Boolean =
    !isEmpty &&
        left >= -1f &&
        top >= -1f &&
        right <= size.width + 1f &&
        bottom <= size.height + 1f

/**
 * The v1 neon highlight, generalized: a glowing ring around [bounds], an arrow pointing at it
 * from the direction of the screen center, and the control's name in a glowing chip at the
 * arrow's tail. v1 hand-tuned every arrow angle and label offset in dp
 * (`fragment_welcome_slide_1.xml`'s "hand tuned" comment); here everything derives from the
 * control's measured position. Purely decorative — TalkBack users get the slide's text.
 */
@Composable
private fun TourSpotlight(
    bounds: Rect,
    label: String,
    colors: TourColors,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(color = colors.neon, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Canvas(modifier) {
        drawSpotlight(bounds, label, colors, textMeasurer, labelStyle)
    }
}

private fun DrawScope.drawSpotlight(
    bounds: Rect,
    label: String,
    colors: TourColors,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val neon = colors.neon
    val ringCenter = bounds.center
    val ringRadius = max(bounds.width, bounds.height) / 2f + 8.dp.toPx()

    // Everything hangs off the direction from the control toward the canvas center, so rail
    // items point right, the bottom-right action cluster points up-left, and so on.
    val toCenter = Offset(size.width / 2f, size.height / 2f) - ringCenter
    val length = sqrt(toCenter.x * toCenter.x + toCenter.y * toCenter.y)
    val dir = if (length < 1f) Offset(1f, 0f) else toCenter / length

    val arrowLength = 56.dp.toPx()
    val gap = 6.dp.toPx()
    val arrowTip = ringCenter + dir * (ringRadius + gap)
    val arrowTail = ringCenter + dir * (ringRadius + gap + arrowLength)

    // Label chip beyond the arrow tail, clamped on-screen.
    val text = textMeasurer.measure(label, labelStyle)
    val padH = 10.dp.toPx()
    val padV = 5.dp.toPx()
    val chipSize = Size(text.size.width + 2 * padH, text.size.height + 2 * padV)
    val chipDistance =
        10.dp.toPx() +
            abs(dir.x) * chipSize.width / 2f + abs(dir.y) * chipSize.height / 2f
    val chipCenter = arrowTail + dir * chipDistance
    // coerceAtMost-then-atLeast, not coerceIn: an oversized chip (huge font scale, tiny
    // window) pins to the top-left margin instead of throwing on an empty range.
    val margin = 8.dp.toPx()
    val chipLeft =
        (chipCenter.x - chipSize.width / 2f)
            .coerceAtMost(size.width - chipSize.width - margin)
            .coerceAtLeast(margin)
    val chipTop =
        (chipCenter.y - chipSize.height / 2f)
            .coerceAtMost(size.height - chipSize.height - margin)
            .coerceAtLeast(margin)

    // Ring and arrow, each drawn twice: a wide translucent pass for the glow, then the core.
    fun strokes(width: Float) = listOf(width * 3.2f to 0.25f, width to 1f)
    for ((width, alpha) in strokes(2.5.dp.toPx())) {
        drawCircle(
            color = neon.copy(alpha = alpha),
            radius = ringRadius,
            center = ringCenter,
            style = Stroke(width = width),
        )
        drawLine(
            color = neon.copy(alpha = alpha),
            start = arrowTail,
            end = arrowTip,
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
        // Arrowhead: two barbs splayed off the shaft direction at the tip.
        val barbLength = 10.dp.toPx()
        val back = dir * barbLength
        val side = Offset(-dir.y, dir.x) * (barbLength * 0.6f)
        for (barb in listOf(arrowTip + back + side, arrowTip + back - side)) {
            drawLine(
                color = neon.copy(alpha = alpha),
                start = barb,
                end = arrowTip,
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }

    drawRoundRect(
        color = colors.labelFill,
        topLeft = Offset(chipLeft, chipTop),
        size = chipSize,
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    drawRoundRect(
        color = neon.copy(alpha = 0.8f),
        topLeft = Offset(chipLeft, chipTop),
        size = chipSize,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawText(text, topLeft = Offset(chipLeft + padH, chipTop + padV))
}
