/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * The one platform-specific step in drawing labels: turning text into pixels. Everything around
 * it — measuring into an atlas, packing, decluttering, fading, placing — is shared
 * ([LabelAtlas], [LabelFrame]), so a backend implements only this (render-gles3.md §6.5, D128):
 * Android's `Canvas` on one side, UIKit's string drawing on the other. It is also the seam a
 * signed-distance-field atlas would plug into.
 */
interface GlyphRasterizer {
    /** The pixel extent of [text] in the platform's sans-serif face at [fontSizePx]. */
    fun measure(
        text: String,
        fontSizePx: Float,
    ): GlyphMetrics

    /**
     * Draws every [glyphs] entry white-on-transparent into one [widthPx] × [heightPx] page and
     * returns its coverage: one byte per pixel, row-major, **top row first**. Each entry's text
     * starts at its cell's left edge with its baseline [PlacedText.ascentPx] below the cell's
     * top, and is clipped to the cell so an over-measured neighbour cannot paint into it.
     */
    fun rasterizePage(
        widthPx: Int,
        heightPx: Int,
        glyphs: List<PlacedText>,
    ): ByteArray
}

/** Rounded-up pixel metrics of one line of text. */
data class GlyphMetrics(
    val widthPx: Int,
    val ascentPx: Int,
    val descentPx: Int,
) {
    val heightPx: Int get() = ascentPx + descentPx
}

/** One label's text, where it goes in its atlas page, and where its baseline sits. */
data class PlacedText(
    val text: String,
    val fontSizePx: Float,
    val cell: AtlasCell,
    val ascentPx: Int,
)
