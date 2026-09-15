/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.gles1.LabelAtlasPacker.Size
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelAtlasPackerTest {
    @Test
    fun `empty input packs to an empty layout`() {
        val layout = LabelAtlasPacker.pack(emptyList(), 1024, 1024)
        assertThat(layout.cells).isEmpty()
        assertThat(layout.pageHeightsPx).isEmpty()
    }

    @Test
    fun `cells advance left to right with padding between them`() {
        val layout =
            LabelAtlasPacker.pack(
                listOf(Size(100, 20), Size(50, 20)),
                pageWidthPx = 1024,
                maxPageHeightPx = 1024,
                paddingPx = 1,
            )
        assertThat(layout.cells[0]).isEqualTo(AtlasCell(page = 0, u = 0, v = 0, w = 100, h = 20))
        assertThat(layout.cells[1]).isEqualTo(AtlasCell(page = 0, u = 101, v = 0, w = 50, h = 20))
    }

    @Test
    fun `a full row wraps and the new row clears the tallest-cell height`() {
        val layout =
            LabelAtlasPacker.pack(
                listOf(Size(600, 20), Size(600, 30), Size(600, 10)),
                pageWidthPx = 1024,
                maxPageHeightPx = 1024,
            )
        // Second cell doesn't fit beside the first; third doesn't fit beside the second.
        assertThat(layout.cells[1]).isEqualTo(AtlasCell(page = 0, u = 0, v = 20, w = 600, h = 30))
        assertThat(layout.cells[2]).isEqualTo(AtlasCell(page = 0, u = 0, v = 50, w = 600, h = 10))
    }

    @Test
    fun `a full page spills to the next page`() {
        val layout =
            LabelAtlasPacker.pack(
                // Three full-width rows of 40px in a 100px-high page: the third row spills.
                listOf(Size(64, 40), Size(64, 40), Size(64, 40)),
                pageWidthPx = 64,
                maxPageHeightPx = 100,
            )
        assertThat(layout.cells.map { it.page }).containsExactly(0, 0, 1).inOrder()
        assertThat(layout.cells[2]).isEqualTo(AtlasCell(page = 1, u = 0, v = 0, w = 64, h = 40))
        assertThat(layout.pageHeightsPx).hasSize(2)
    }

    @Test
    fun `page heights are powers of two and never exceed a power-of-two cap`() {
        val layout =
            LabelAtlasPacker.pack(
                List(40) { Size(512, 100) },
                pageWidthPx = 1024,
                maxPageHeightPx = 1024,
                paddingPx = 2,
            )
        for (h in layout.pageHeightsPx) {
            assertThat(h.countOneBits()).isEqualTo(1)
            assertThat(h).isAtMost(1024)
        }
        // 512+2 padding leaves no room for a second 512px cell per row, so: one cell per
        // 102px row, 10 rows per 1024px page, 40 cells → 4 pages.
        assertThat(layout.pageHeightsPx.size).isEqualTo(4)
    }

    @Test
    fun `trailing padding does not inflate the page height past a power-of-two boundary`() {
        val layout =
            LabelAtlasPacker.pack(
                listOf(Size(30, 64)),
                pageWidthPx = 1024,
                maxPageHeightPx = 1024,
                paddingPx = 1,
            )
        // The used height is exactly 64; padding below the last row must not push it to 65,
        // which would round up to a 128px texture and double the GPU memory.
        assertThat(layout.pageHeightsPx.single()).isEqualTo(64)
    }

    @Test
    fun `oversized cells are clamped to the page`() {
        val layout =
            LabelAtlasPacker.pack(
                listOf(Size(5000, 3000)),
                pageWidthPx = 1024,
                maxPageHeightPx = 512,
            )
        assertThat(layout.cells.single())
            .isEqualTo(AtlasCell(page = 0, u = 0, v = 0, w = 1024, h = 512))
        assertThat(layout.pageHeightsPx.single()).isEqualTo(512)
    }

    @Test
    fun `cells stay in input order`() {
        val sizes = List(100) { Size(200 + it, 20) }
        val layout = LabelAtlasPacker.pack(sizes, 1024, 64)
        assertThat(layout.cells.map { it.w }).isEqualTo(sizes.map { it.w })
    }

    @Test
    fun `page indices are non-decreasing in input order`() {
        // LabelDrawer's single-pass draw loop relies on this to bind each page at most once.
        val sizes = List(100) { Size(200 + it, 20) }
        val layout = LabelAtlasPacker.pack(sizes, 1024, 64)
        assertThat(layout.cells.map { it.page })
            .isInOrder()
        assertThat(layout.cells.last().page).isGreaterThan(0)
    }
}
