/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelDecluttererTest {
    // ---- magnitudeThreshold ---------------------------------------------------------

    @Test
    fun `threshold equals base limit at reference FOV`() {
        assertThat(LabelDeclutterer.magnitudeThreshold(45.0)).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `smaller FOV yields higher threshold`() {
        assertThat(LabelDeclutterer.magnitudeThreshold(25.0)).isWithin(1e-9).of(6.0)
        assertThat(LabelDeclutterer.magnitudeThreshold(35.0)).isWithin(1e-9).of(5.0)
    }

    @Test
    fun `threshold is clamped at base limit for FOV larger than reference`() {
        assertThat(LabelDeclutterer.magnitudeThreshold(90.0)).isWithin(1e-9).of(4.0)
        assertThat(LabelDeclutterer.magnitudeThreshold(60.0)).isWithin(1e-9).of(4.0)
    }

    // ---- passesPreFilter ------------------------------------------------------------

    @Test
    fun `label directly behind viewer is rejected`() {
        val pos = Vector3.UNIT_X
        val lookDir = -Vector3.UNIT_X // looking away from pos
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.0f, null, 10.0),
        ).isFalse()
    }

    @Test
    fun `label directly in front of viewer is accepted`() {
        val pos = Vector3.UNIT_X
        val lookDir = Vector3.UNIT_X
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.5f, null, 10.0),
        ).isTrue()
    }

    @Test
    fun `label exactly at threshold boundary passes`() {
        // dot(pos, lookDir) = 0.5, threshold = 0.5 → condition is `dot < threshold`, so
        // equality passes the filter (not rejected).
        val pos = Vector3(0.5, sqrt3over2, 0.0) // 60° from UNIT_X, dot = 0.5
        val lookDir = Vector3.UNIT_X
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.5f, null, 10.0),
        ).isTrue()
    }

    @Test
    fun `label with magnitude below limit passes`() {
        val pos = Vector3.UNIT_X
        val lookDir = Vector3.UNIT_X
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.0f, 3.5, 4.0),
        ).isTrue()
    }

    @Test
    fun `label with magnitude above limit is rejected`() {
        val pos = Vector3.UNIT_X
        val lookDir = Vector3.UNIT_X
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.0f, 5.0, 4.0),
        ).isFalse()
    }

    @Test
    fun `label with null magnitude always passes magnitude test`() {
        val pos = Vector3.UNIT_X
        val lookDir = Vector3.UNIT_X
        assertThat(
            LabelDeclutterer.passesPreFilter(pos, lookDir, 0.0f, null, -99.0),
        ).isTrue()
    }

    // ---- declutter ------------------------------------------------------------------

    @Test
    fun `empty input returns empty result`() {
        val result = declutter(emptyList())
        assertThat(result.size).isEqualTo(0)
    }

    @Test
    fun `single label is always kept`() {
        val result = declutter(listOf(candidate(0f, 0f, 100, 20, 1)))
        assertThat(result[0]).isTrue()
    }

    @Test
    fun `non-overlapping labels are all kept`() {
        val candidates =
            listOf(
                candidate(0f, 0f, 100, 20, priority = 1),
                candidate(200f, 0f, 100, 20, priority = 2),
                candidate(400f, 0f, 100, 20, priority = 3),
            )
        val result = declutter(candidates)
        assertThat(result.all { it }).isTrue()
    }

    @Test
    fun `on overlap higher priority wins`() {
        val candidates =
            listOf(
                // lower priority, placed first
                candidate(100f, 100f, 100, 20, priority = 5),
                // higher priority, overlaps
                candidate(110f, 100f, 100, 20, priority = 10),
            )
        val result = declutter(candidates)
        assertThat(result[0]).isFalse()
        assertThat(result[1]).isTrue()
    }

    @Test
    fun `on overlap equal priority the first evaluated wins`() {
        // When priorities are equal, the one with the lower index in the sorted order wins.
        // Both have priority 5; the one that happens to come first in the list wins.
        val candidates =
            listOf(
                candidate(100f, 100f, 100, 20, priority = 5),
                candidate(110f, 100f, 100, 20, priority = 5),
            )
        val result = declutter(candidates)
        // Exactly one should win (not both, not neither)
        assertThat(result.count { it }).isEqualTo(1)
    }

    @Test
    fun `just-touching labels do not overlap`() {
        // Right edge of first = left edge of second → touching, not overlapping.
        val candidates =
            listOf(
                // right edge at x=50
                candidate(0f, 0f, 100, 20, priority = 1),
                // left edge at x=50
                candidate(100f, 0f, 100, 20, priority = 2),
            )
        val result = declutter(candidates)
        assertThat(result[0]).isTrue()
        assertThat(result[1]).isTrue()
    }

    @Test
    fun `high priority label keeps low priority neighbour out`() {
        // Three labels in a line; the middle one has the highest priority.
        // It should block both neighbours if they overlap it.
        val candidates =
            listOf(
                // overlaps middle
                candidate(0f, 0f, 120, 20, priority = 1),
                // highest priority → kept
                candidate(60f, 0f, 120, 20, priority = 10),
                // overlaps middle
                candidate(120f, 0f, 120, 20, priority = 1),
            )
        val result = declutter(candidates)
        assertThat(result[1]).isTrue() // middle (highest priority) kept
        assertThat(result[0]).isFalse() // overlaps middle → removed
        assertThat(result[2]).isFalse() // overlaps middle → removed
    }

    @Test
    fun `visibleCount matches the number of kept labels`() {
        val candidates =
            listOf(
                candidate(0f, 0f, 100, 20, priority = 1),
                candidate(10f, 0f, 100, 20, priority = 2),
                candidate(400f, 0f, 100, 20, priority = 3),
            )
        val buffer = LabelDeclutterer.Candidates()
        buffer.declutter(candidates)
        assertThat(buffer.visibleCount).isEqualTo(2)
    }

    @Test
    fun `buffer reuse does not leak the previous frame's result`() {
        val buffer = LabelDeclutterer.Candidates()
        // A crowded frame first, so stale `visible` flags and kept rects would show up in the
        // sparse frame that follows if either were carried over.
        buffer.declutter(
            listOf(
                candidate(0f, 0f, 100, 20, priority = 1),
                candidate(10f, 0f, 100, 20, priority = 2),
                candidate(20f, 0f, 100, 20, priority = 3),
            ),
        )
        val result = buffer.declutter(listOf(candidate(0f, 0f, 100, 20, priority = 1)))
        assertThat(result.size).isEqualTo(1)
        assertThat(result[0]).isTrue()
        assertThat(buffer.visibleCount).isEqualTo(1)
    }

    @Test
    fun `candidate arrays grow past their initial capacity`() {
        // 64 is the initial capacity; 200 non-overlapping labels force two growths.
        val candidates = (0 until 200).map { candidate(it * 200f, 0f, 100, 20, priority = it) }
        val result = declutter(candidates)
        assertThat(result.size).isEqualTo(200)
        assertThat(result.all { it }).isTrue()
    }

    // ---- helpers --------------------------------------------------------------------

    private val sqrt3over2 = kotlin.math.sqrt(3.0) / 2.0

    private data class Candidate(
        val x: Float,
        val y: Float,
        val w: Int,
        val h: Int,
        val priority: Int,
    )

    private fun candidate(
        x: Float,
        y: Float,
        w: Int,
        h: Int,
        priority: Int,
    ) = Candidate(x, y, w, h, priority)

    /**
     * Fills a fresh [LabelDeclutterer.Candidates] and returns the visibility flags as a plain
     * array, so each test reads as `input -> which survived`. The production caller reuses one
     * buffer across frames; `buffer reuse` covers that path.
     */
    private fun declutter(candidates: List<Candidate>): BooleanArray {
        val buffer = LabelDeclutterer.Candidates()
        return buffer.declutter(candidates)
    }

    private fun LabelDeclutterer.Candidates.declutter(candidates: List<Candidate>): BooleanArray {
        clear()
        candidates.forEachIndexed { index, c ->
            add(index, c.x, c.y, c.w, c.h, c.priority)
        }
        LabelDeclutterer.declutter(this)
        return BooleanArray(size) { visible[it] }
    }
}
