/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

/** One label's cell in the atlas: which [page] it landed on, and its pixel rect on that page. */
internal data class AtlasCell(val page: Int, val u: Int, val v: Int, val w: Int, val h: Int)

/**
 * The result of [LabelAtlasPacker.pack]: one cell per input size (in input order) and each page's
 * final height in pixels, already rounded up to a power of two (a GLES1 texture requirement).
 */
internal data class AtlasLayout(val cells: List<AtlasCell>, val pageHeightsPx: List<Int>)

/**
 * Pure (no GL, no Android) strip packer for the label atlas.
 *
 * Cells are placed left-to-right; when a row is full the packer wraps to a new row, and when a
 * page reaches [pack]'s `maxPageHeightPx` it spills to a new page. Paging is what keeps every
 * atlas texture within the GL implementation's `GL_MAX_TEXTURE_SIZE` no matter how many labels a
 * scene submits — a single ever-growing bitmap would exceed it on real catalogs and fail the
 * texture upload. `paddingPx` separates neighboring cells so antialiased glyph edges cannot bleed
 * into an adjacent cell's quad.
 */
internal object LabelAtlasPacker {
    /** A label's rasterized pixel size. */
    data class Size(val w: Int, val h: Int)

    /**
     * Packs [sizes] into pages of [pageWidthPx] × at most [maxPageHeightPx] pixels. Cells wider
     * than the page or taller than the page cap are clamped to fit (the drawer clips their text).
     *
     * Cells are returned in input order with **non-decreasing page indices** — [LabelDrawer]'s
     * single-pass draw loop relies on this to bind each page texture at most once.
     */
    fun pack(
        sizes: List<Size>,
        pageWidthPx: Int,
        maxPageHeightPx: Int,
        paddingPx: Int = 0,
    ): AtlasLayout {
        require(pageWidthPx > 0) { "pageWidthPx must be positive, got $pageWidthPx" }
        require(maxPageHeightPx > 0) { "maxPageHeightPx must be positive, got $maxPageHeightPx" }
        require(paddingPx >= 0) { "paddingPx must not be negative, got $paddingPx" }
        if (sizes.isEmpty()) return AtlasLayout(emptyList(), emptyList())

        val cells = ArrayList<AtlasCell>(sizes.size)
        val pageHeights = ArrayList<Int>()
        var page = 0
        var u = 0
        var v = 0
        var rowH = 0
        // Bottom edge of the lowest cell on the current page. Tracked separately from v + rowH so
        // the page's used height excludes trailing padding — otherwise a 64px row with 1px padding
        // reports 65 and the power-of-two rounding doubles the texture to 128.
        var pageMaxY = 0
        for (size in sizes) {
            val w = size.w.coerceAtMost(pageWidthPx)
            val h = size.h.coerceAtMost(maxPageHeightPx)
            if (u > 0 && u + w > pageWidthPx) {
                // Row full: wrap to the next row, one padding gap below the finished row.
                u = 0
                v += rowH + paddingPx
                rowH = 0
            }
            if (v + h > maxPageHeightPx) {
                // Page full: spill to a fresh page. Any earlier cells in the current row stay
                // on the finished page, whose used height pageMaxY already includes them.
                pageHeights.add(pageMaxY)
                page++
                u = 0
                v = 0
                rowH = 0
                pageMaxY = 0
            }
            cells.add(AtlasCell(page, u, v, w, h))
            u += w + paddingPx
            rowH = maxOf(rowH, h)
            pageMaxY = maxOf(pageMaxY, v + h)
        }
        pageHeights.add(pageMaxY)
        // The spill check guarantees pageMaxY never exceeds the cap; the coercion enforces the
        // ≥1 floor GL requires for degenerate zero-height cells.
        return AtlasLayout(
            cells,
            pageHeights.map { nextPowerOfTwo(it.coerceIn(1, maxPageHeightPx)) },
        )
    }

    private fun nextPowerOfTwo(n: Int): Int = if (n <= 1) 1 else (n - 1).takeHighestOneBit() shl 1
}
