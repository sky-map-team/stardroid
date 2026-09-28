/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3

/**
 * One layer's labels rasterized into coverage-mask atlas pages, plus each label's cell and
 * placement data: the `build` half of a label drawer, shared by every backend (D117). The
 * backend uploads [pages] as single-channel textures and hands the glyphs to [LabelFrame] each
 * frame. Transcribed from `:render:gles3`'s LabelDrawer.build, with the text rasterizing behind
 * [GlyphRasterizer].
 *
 * The atlas is a coverage mask, not colour: text is drawn white on transparent and coloured by
 * the instance tint, which is also why night mode never needs a rebuild.
 */
class LabelAtlas private constructor(
    val pages: List<Page>,
    val glyphs: List<LabelGlyph>,
) {
    /** One atlas page: [coverage] is one byte per pixel, row-major, top row first. */
    class Page(
        val widthPx: Int,
        val heightPx: Int,
        val coverage: ByteArray,
    )

    companion object {
        private const val ATLAS_WIDTH_PX = 1024

        /** Power of two; a 1024×1024 single-channel page is 1 MB. */
        private const val MAX_PAGE_HEIGHT_PX = 1024

        /** Gutter between atlas cells so antialiased glyph edges cannot bleed into a neighbour. */
        private const val GLYPH_PADDING_PX = 1

        private const val LABEL_TITLE_SP = 15f
        private const val LABEL_STANDARD_SP = 10f
        private const val LABEL_MINOR_SP = 8f

        val EMPTY = LabelAtlas(emptyList(), emptyList())

        /**
         * Measures, packs and rasterizes [labels] at the accessibility [labelScaleFactor] and
         * display [density]. [maxTextureSizePx] is the GPU's limit, which caps the page size.
         */
        fun build(
            labels: List<LabelPrimitive>,
            labelScaleFactor: Double,
            density: Float,
            rasterizer: GlyphRasterizer,
            maxTextureSizePx: Int,
        ): LabelAtlas {
            if (labels.isEmpty()) return EMPTY
            val pageWidth = ATLAS_WIDTH_PX.coerceAtMost(maxTextureSizePx)
            val maxPageHeight = MAX_PAGE_HEIGHT_PX.coerceAtMost(maxTextureSizePx)

            val fontSizes = labels.map { fontSizePx(it.style.size, labelScaleFactor, density) }
            val metrics =
                labels.mapIndexed { i, label -> rasterizer.measure(label.text, fontSizes[i]) }
            val layout =
                LabelAtlasPacker.pack(
                    metrics.map {
                        LabelAtlasPacker.Size(it.widthPx.coerceAtMost(pageWidth), it.heightPx)
                    },
                    pageWidth,
                    maxPageHeight,
                    GLYPH_PADDING_PX,
                )

            val pages =
                layout.pageHeightsPx.mapIndexed { page, heightPx ->
                    val placed =
                        labels.indices
                            .filter { layout.cells[it].page == page }
                            .map { i ->
                                PlacedText(
                                    labels[i].text,
                                    fontSizes[i],
                                    layout.cells[i],
                                    metrics[i].ascentPx,
                                )
                            }
                    Page(pageWidth, heightPx, rasterizer.rasterizePage(pageWidth, heightPx, placed))
                }

            val texelWidth = 1f / pageWidth
            val glyphs =
                labels.mapIndexed { i, label ->
                    val cell = layout.cells[i]
                    val texelHeight = 1f / layout.pageHeightsPx[cell.page]
                    // Pages are top row first, so the quad's lower-left corner takes the *larger*
                    // v — the convention GLES1 and v1's LabelMaker use.
                    LabelGlyph(
                        text = label.text,
                        widthPx = cell.w,
                        heightPx = cell.h,
                        page = cell.page,
                        u0 = cell.u * texelWidth,
                        v0 = (cell.v + cell.h) * texelHeight,
                        u1 = (cell.u + cell.w) * texelWidth,
                        v1 = cell.v * texelHeight,
                        label = label,
                    )
                }
            return LabelAtlas(pages, glyphs)
        }

        private fun fontSizePx(
            size: LabelSize,
            labelScaleFactor: Double,
            density: Float,
        ): Float {
            val sp =
                when (size) {
                    LabelSize.TITLE -> LABEL_TITLE_SP
                    LabelSize.STANDARD -> LABEL_STANDARD_SP
                    LabelSize.MINOR -> LABEL_MINOR_SP
                }
            return sp * labelScaleFactor.toFloat() * density
        }
    }
}

/**
 * One label's atlas cell (in [page], texture coordinates `u0..u1` × `v1..v0`) and the primitive
 * it draws. Compared by identity: nothing compares glyphs by value.
 */
class LabelGlyph(
    val text: String,
    val widthPx: Int,
    val heightPx: Int,
    val page: Int,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
    val label: LabelPrimitive,
) {
    val pos: Vector3 get() = label.pos
}
