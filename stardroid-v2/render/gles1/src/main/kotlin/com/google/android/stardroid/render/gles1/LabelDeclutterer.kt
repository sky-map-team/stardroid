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

/**
 * Pure (no GL, no Android) per-frame label visibility filter (render-api.md "Label decluttering").
 *
 * Three passes applied in order:
 * 1. **Frustum test** — labels whose world position is outside the view cone are discarded.
 * 2. **FOV-dependent magnitude threshold** — at large FOV only bright labels show; zooming in
 *    (smaller FOV) reveals fainter ones. Labels without a magnitude always pass.
 * 3. **Greedy screen-space rejection** — candidates sorted by descending
 *    [Candidates.priority];
 *    each is kept only if its bounding rect doesn't overlap any already-kept rect.
 *
 * Passes 1 and 2 are in [passesPreFilter]; pass 3 is in [declutter].
 */
internal object LabelDeclutterer {
    private const val REFERENCE_FOV_DEG = 45.0
    private const val BASE_MAGNITUDE_LIMIT = 4.0
    private const val MAGNITUDE_SCALE = 10.0

    /**
     * Magnitude cutoff for the given [fovDeg]: smaller FOV (zoomed in) → higher cutoff → more
     * labels visible. Clamped so the threshold never drops below [BASE_MAGNITUDE_LIMIT].
     *
     * At [REFERENCE_FOV_DEG] = 45°: threshold = 4.0.
     * At 25°: threshold = 6.0 (reveals mag-5 and mag-6 labels when zoomed in).
     * At 90°: still 4.0 (wide-angle view shows only bright labels).
     */
    fun magnitudeThreshold(fovDeg: Double): Double =
        BASE_MAGNITUDE_LIMIT + (REFERENCE_FOV_DEG - fovDeg).coerceAtLeast(0.0) / MAGNITUDE_SCALE

    /**
     * Returns `true` if the label at [labelPos] passes the frustum and magnitude pre-filters.
     *
     * @param dotProductThreshold `cos(halfFovAngle × aspect-adjusted factor)` — the minimum dot
     *   product of [labelPos] with [lookDir] for the label to be considered potentially on screen.
     *   Computed from the camera FOV and aspect ratio (same formula as v1's `LabelObjectManager`).
     * @param magnitude the labeled object's magnitude, or `null` to always pass the magnitude test.
     * @param magnitudeLimit labels with magnitude > [magnitudeLimit] are discarded.
     */
    fun passesPreFilter(
        labelPos: Vector3,
        lookDir: Vector3,
        dotProductThreshold: Float,
        magnitude: Double?,
        magnitudeLimit: Double,
    ): Boolean {
        if ((labelPos dot lookDir).toFloat() < dotProductThreshold) return false
        if (magnitude != null && magnitude > magnitudeLimit) return false
        return true
    }

    /**
     * One frame's declutter candidates, held as parallel primitive arrays and reused across
     * frames.
     *
     * Struct-of-arrays rather than a `List<Candidate>` because this is rebuilt at up to 60 fps:
     * the old shape allocated an intermediate survivor list, a second candidate list, an object
     * per label in each, a boxed sorted index list and a `Rect` per candidate, every frame. That
     * is GC pressure inside the draw loop, which on Android shows up as frame-time spikes rather
     * than a lower average — the artifact the D19 perf gate exists to catch (audit-2026-08 M2).
     *
     * Owned by the GL thread and not thread-safe. The arrays grow to the frame's high-water mark
     * and are never shrunk; only the first [size] entries of each are meaningful.
     */
    class Candidates {
        var size: Int = 0
            private set

        /** How many entries [declutter] marked visible; 0 means nothing to draw. */
        var visibleCount: Int = 0
            internal set

        /** The caller's own index for each candidate, so the draw pass can map back. */
        var glyphIndex = IntArray(INITIAL_CAPACITY)
            private set

        var screenX = FloatArray(INITIAL_CAPACITY)
            private set

        var screenY = FloatArray(INITIAL_CAPACITY)
            private set

        var widthPx = IntArray(INITIAL_CAPACITY)
            private set

        var heightPx = IntArray(INITIAL_CAPACITY)
            private set

        var priority = IntArray(INITIAL_CAPACITY)
            private set

        /** Parallel output of [declutter]: `true` = this label should be drawn. */
        var visible = BooleanArray(INITIAL_CAPACITY)
            private set

        /** Scratch: candidate indices sorted by descending priority. */
        internal var order = IntArray(INITIAL_CAPACITY)
            private set

        /** Scratch: the kept bounding rects, four floats each (left, top, right, bottom). */
        internal var keptRects = FloatArray(INITIAL_CAPACITY * FLOATS_PER_RECT)
            private set

        /** Drops the previous frame's contents without releasing the arrays. */
        fun clear() {
            size = 0
            visibleCount = 0
        }

        fun add(
            glyphIndex: Int,
            screenX: Float,
            screenY: Float,
            widthPx: Int,
            heightPx: Int,
            priority: Int,
        ) {
            grow(size + 1)
            this.glyphIndex[size] = glyphIndex
            this.screenX[size] = screenX
            this.screenY[size] = screenY
            this.widthPx[size] = widthPx
            this.heightPx[size] = heightPx
            this.priority[size] = priority
            size++
        }

        private fun grow(needed: Int) {
            if (needed <= glyphIndex.size) return
            val capacity = maxOf(needed, glyphIndex.size * 2)
            glyphIndex = glyphIndex.copyOf(capacity)
            screenX = screenX.copyOf(capacity)
            screenY = screenY.copyOf(capacity)
            widthPx = widthPx.copyOf(capacity)
            heightPx = heightPx.copyOf(capacity)
            priority = priority.copyOf(capacity)
            visible = visible.copyOf(capacity)
            order = order.copyOf(capacity)
            keptRects = keptRects.copyOf(capacity * FLOATS_PER_RECT)
        }

        private companion object {
            const val INITIAL_CAPACITY = 64
        }
    }

    /**
     * Greedy screen-space declutter: visit [candidates] in descending priority order, keeping
     * each only if its bounding rect doesn't overlap one already kept.
     *
     * Writes its answer into [Candidates.visible] (parallel to the candidates) and
     * [Candidates.visibleCount]; allocates nothing.
     */
    fun declutter(candidates: Candidates) {
        val count = candidates.size
        val priority = candidates.priority
        val order = candidates.order
        val visible = candidates.visible
        for (i in 0 until count) {
            visible[i] = false
            order[i] = i
        }
        // Insertion sort, which is stable: equal priorities keep input order, exactly as the
        // `sortedByDescending` this replaced did. Quadratic in the worst case, like the overlap
        // scan below — see the batching TODO in LabelDrawer.draw for where both stop being
        // adequate.
        for (i in 1 until count) {
            val index = order[i]
            val key = priority[index]
            var j = i - 1
            while (j >= 0 && priority[order[j]] < key) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = index
        }

        val kept = candidates.keptRects
        var keptCount = 0
        for (position in 0 until count) {
            val i = order[position]
            val halfWidth = candidates.widthPx[i] / 2f
            val halfHeight = candidates.heightPx[i] / 2f
            val left = candidates.screenX[i] - halfWidth
            val top = candidates.screenY[i] - halfHeight
            val right = candidates.screenX[i] + halfWidth
            val bottom = candidates.screenY[i] + halfHeight
            var overlaps = false
            for (rect in 0 until keptCount) {
                val base = rect * FLOATS_PER_RECT
                if (left < kept[base + 2] &&
                    right > kept[base] &&
                    top < kept[base + 3] &&
                    bottom > kept[base + 1]
                ) {
                    overlaps = true
                    break
                }
            }
            if (!overlaps) {
                visible[i] = true
                val base = keptCount * FLOATS_PER_RECT
                kept[base] = left
                kept[base + 1] = top
                kept[base + 2] = right
                kept[base + 3] = bottom
                keptCount++
            }
        }
        candidates.visibleCount = keptCount
    }

    private const val FLOATS_PER_RECT = 4
}
