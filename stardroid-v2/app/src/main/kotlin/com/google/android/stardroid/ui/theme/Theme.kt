/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.android.stardroid.render.api.Rgba

/**
 * The map chrome is always dark (it floats over the night sky); night mode swaps to a
 * red-shifted scheme so the UI matches `RenderState.nightMode` and preserves dark adaptation.
 * One DataStore preference drives both worlds (layers-and-app.md).
 */
@Composable
fun SkyMapTheme(
    nightMode: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (nightMode) NightColors else DayColors,
        content = content,
    )
}

/**
 * Multiply tint for photographs in night mode (v1's red `PorterDuff.MULTIPLY` filter on
 * gallery thumbnails and expanded images) — theme colors only restyle the chrome; bitmaps
 * must be red-shifted explicitly to preserve dark adaptation.
 */
val NightPhotoTint = Color(0xFFB03030)

/**
 * The two-tier status palette (AGENTS.md): day colors carry meaning by hue; the night set is
 * red-shifted with brightness mirroring the day-mode meaning (brighter = better). Values are
 * v1's `status_*` / `night_status_*` resources.
 */
data class StatusColors(
    val good: Color,
    val ok: Color,
    val warning: Color,
    val bad: Color,
    val absent: Color,
)

fun statusColors(nightMode: Boolean): StatusColors =
    if (nightMode) NightStatusColors else DayStatusColors

/**
 * `ok` and `warning` diverge from v1 (D73). With Star Gold as [DayColors]'s accent, v1's
 * yellow `#FFEB3B` (hue 54°) and orange `#FF9800` (hue 36°) both sat within ~9° of the
 * accent's 45°, so "acceptable" and "degraded" read as the brand color rather than as
 * status. These are separated by hue, not luminance, so WCAG contrast is the wrong metric
 * here — `#DCE775` scores *worse* against gold than `#FFEB3B` while being plainly more
 * distinguishable. `ok` lives between the accent and `good`, so it is placed near the
 * balance point (~21° from gold, ~22° from `good`) rather than pushed hard off the accent.
 */
private val DayStatusColors =
    StatusColors(
        good = Color(0xFF8BC34A),
        ok = Color(0xFFDCE775),
        warning = Color(0xFFFF7043),
        bad = Color(0xFFE91E63),
        absent = Color(0xFFA0A0A0),
    )

private val NightStatusColors =
    StatusColors(
        good = Color(0xFFDD6666),
        ok = Color(0xFFAA4444),
        warning = Color(0xFF883333),
        bad = Color(0xFF662222),
        absent = Color(0xFF552222),
    )

/**
 * The long-form document palette for [StyledHtml]'s renderers (help, EULA, What's New,
 * calibration): v1's `help.css` expressed as roles rather than CSS classes. Kept here with
 * [StatusColors] so the raw hues live in one place — no `Color(0xFF…)` literals in `ui/`.
 */
data class DocumentColors(
    /** `h1`–`h3` accents, indexed by heading level - 1. */
    val headings: List<Color>,
    val calloutBackground: Color,
    val calloutAccent: Color,
    /**
     * Red-shift applied to the help screen's symbol-key glyphs, or null in day mode — there the
     * legend must match what the map actually draws, so it tints from the renderer's own
     * `SkyColors` rather than from a chrome color that merely happens to agree.
     */
    val symbolTintOverride: Color?,
)

fun documentColors(nightMode: Boolean): DocumentColors =
    if (nightMode) NightDocumentColors else DayDocumentColors

/** Bridges a renderer [Rgba] to a Compose [Color] so chrome can reuse the map's own palette. */
fun Rgba.toComposeColor(): Color = Color(red = r, green = g, blue = b, alpha = a)

/**
 * The onboarding tour's spotlight (D46/D61). [neon] shares Lens Blue's hue with the map's
 * markers but is *not* derived from them: it is picked for contrast against the chrome and to
 * stay distinct from the gold accent the spotlight points at, so it tracks the chrome palette
 * rather than the renderer's.
 */
data class TourColors(
    val neon: Color,
    /** The label chip's fill, v1's `neon_text_bg`. */
    val labelFill: Color,
)

fun tourColors(nightMode: Boolean): TourColors = if (nightMode) NightTourColors else DayTourColors

private val DayTourColors =
    TourColors(
        neon = Color(0xFF7EC8E3),
        labelFill = Color(0xDD0B0F19),
    )

private val NightTourColors =
    TourColors(
        neon = Color(0xFFFF5252),
        labelFill = Color(0xDD0B0F19),
    )

/**
 * Headings are v1's `help.css` accents (h1 blue, h2 amber, h3 salmon) snapped onto the brand
 * hues for D73: Lens Blue, Star Gold, and a lightened Planet Red.
 *
 * The callout deliberately does *not* reuse [StatusColors.warning]. Those hues are load-bearing
 * for live state — "this sensor is degraded right now" — and a static prose note is not that.
 * Lens Blue is the secondary accent and reads as "worth reading", leaving the warm end of the
 * palette to mean what it means everywhere else.
 */
private val DayDocumentColors =
    DocumentColors(
        headings = listOf(Color(0xFF7EC8E3), Color(0xFFFFC107), Color(0xFFE8836A)),
        calloutBackground = Color(0xFF1E3A4C),
        calloutAccent = Color(0xFF7EC8E3),
        symbolTintOverride = null,
    )

/** The night palette's brightest red — `h1` accent and the night symbol-key tint. */
private val NightAccentBright = Color(0xFFE06060)

/**
 * Night variants: red-shifted per the status-color rules, heading brightness stepping down with
 * depth so the hierarchy survives the single-hue palette.
 *
 * The callout carries far less background lift than its day counterpart. On the near-black night
 * surface a tonal panel is both hard to see and hostile to dark adaptation, so the accent bar
 * does the work and the fill only just separates from the page.
 */
private val NightDocumentColors =
    DocumentColors(
        headings = listOf(NightAccentBright, Color(0xFFC04848), Color(0xFFA03A3A)),
        calloutBackground = Color(0xFF200000),
        calloutAccent = Color(0xFFC04848),
        // The map's markers are red-shifted by the renderer's night transform; the legend
        // follows with the brightest chrome red so it still reads as the same element.
        symbolTintOverride = NightAccentBright,
    )

/**
 * The "Deep Space / Star Gold" brand scheme (D73): Star Gold accent on a navy surface family,
 * with Lens Blue as the secondary. The hues come from v1's logo palette
 * (`stardroid-v1/docs/design/visual_design.md`) and the v2 launcher icon, so the chrome
 * continues the navy-and-gold the splash and version banner already ship.
 *
 * Amber is also the least dark-adaptation-hostile of the bright hues, so this degrades
 * gracefully toward [NightColors] — both modes are a warm accent on near-black.
 *
 * The `surfaceContainer*` ramp is set explicitly: dialogs, bottom sheets, cards and scrolled
 * top bars resolve their containers from those roles, and left unset they stay the baseline's
 * purple-tinted grey even once the core roles are branded.
 */
private val DayColors =
    darkColorScheme(
        primary = Color(0xFFFFC107),
        onPrimary = Color(0xFF231A00),
        primaryContainer = Color(0xFF5A4200),
        onPrimaryContainer = Color(0xFFFFDF9C),
        inversePrimary = Color(0xFF7A5A00),
        secondary = Color(0xFF7EC8E3),
        onSecondary = Color(0xFF00344A),
        secondaryContainer = Color(0xFF1E3A4C),
        onSecondaryContainer = Color(0xFF9ED4EC),
        tertiary = Color(0xFFF5C77E),
        onTertiary = Color(0xFF3D2A00),
        // The time-travel player's "clock is sweeping" surface (TimeTravelUi); the baseline's
        // mauve container would break the navy/gold palette.
        tertiaryContainer = Color(0xFF4A3510),
        onTertiaryContainer = Color(0xFFF5C77E),
        background = Color(0xFF080C1C),
        onBackground = Color(0xFFE0E1DD),
        surface = Color(0xFF0E1428),
        onSurface = Color(0xFFE0E1DD),
        surfaceVariant = Color(0xFF1B2340),
        onSurfaceVariant = Color(0xFFA9B4D0),
        surfaceDim = Color(0xFF080C1C),
        surfaceBright = Color(0xFF232C4C),
        surfaceContainerLowest = Color(0xFF050813),
        surfaceContainerLow = Color(0xFF0E1428),
        surfaceContainer = Color(0xFF141C36),
        surfaceContainerHigh = Color(0xFF1B2340),
        surfaceContainerHighest = Color(0xFF232C4C),
        // Borders and dividers rather than text, so this sits below the AA text threshold by
        // design; it doubles as the layer rail's "off" tint, always beside an icon shape.
        outline = Color(0xFF5A6486),
        outlineVariant = Color(0xFF2A3355),
        error = Color(0xFFFF7A5C),
        onError = Color(0xFF4A0E00),
        errorContainer = Color(0xFF6E2010),
        onErrorContainer = Color(0xFFFFDAD1),
        // Snackbars draw on the inverse roles; keep them Starlight-on-navy.
        inverseSurface = Color(0xFFE0E1DD),
        inverseOnSurface = Color(0xFF0E1428),
    )

private val NightColors =
    darkColorScheme(
        primary = Color(0xFFC03030),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF400000),
        onPrimaryContainer = Color(0xFFE06060),
        secondary = Color(0xFF904040),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF2A0000),
        onSecondaryContainer = Color(0xFFC05050),
        // The time-travel player's "clock is sweeping" surface; without an override the
        // baseline purple container would break the red palette.
        tertiary = Color(0xFFB05050),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF4A0E0E),
        onTertiaryContainer = Color(0xFFE06868),
        // Snackbars draw on the inverse roles; the baseline's near-white inverseSurface
        // would wreck dark adaptation, so keep them red-on-dark too.
        inverseSurface = Color(0xFF380808),
        inverseOnSurface = Color(0xFFD05050),
        inversePrimary = Color(0xFFE06060),
        background = Color.Black,
        onBackground = Color(0xFFB03030),
        surface = Color(0xFF150000),
        onSurface = Color(0xFFB03030),
        surfaceVariant = Color(0xFF200000),
        onSurfaceVariant = Color(0xFFA03030),
        outline = Color(0xFF702020),
    )
