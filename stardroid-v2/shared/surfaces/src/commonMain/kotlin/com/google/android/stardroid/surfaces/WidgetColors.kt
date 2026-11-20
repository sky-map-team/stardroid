/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.surfaces

/**
 * The widget-world palette (D67, D73) as 0xAARRGGBB, drawn by Android's Glance widgets and the
 * iOS WidgetKit ones alike: Starlight ink, Star Gold for the one key figure per widget, a muted
 * line colour, Planet Green for good-news notes, and the moon disc's own three colours.
 */
object WidgetColors {
    const val STARLIGHT: Long = 0xFFE0E1DD
    const val STAR_GOLD: Long = 0xFFFFC107
    const val MUTED: Long = 0xFFA9B4D0
    const val PLANET_GREEN: Long = 0xFF4DB848

    /** Brand navy at 86% over the wallpaper; Android's `widget_background` drawable. */
    const val SURFACE: Long = 0xDB0E1428

    /** The hairline border round the surface. */
    const val OUTLINE: Long = 0xFF2A3355

    /**
     * The tonight rows' [HighlightDot]s below gold, as Android's `widget_dot_blue` and
     * `widget_dot_dim` drawables draw them.
     */
    const val DOT_BLUE: Long = 0xFF7EC8E3
    const val DOT_DIM: Long = 0xFF5A6486

    const val MOON_LIT: Long = 0xFFE8E4D8

    /** surfaceVariant navy, so the dark limb still reads against the surface. */
    const val MOON_SHADOW: Long = 0xFF1B2340
    const val MOON_MARIA: Long = 0x381B2340
}
