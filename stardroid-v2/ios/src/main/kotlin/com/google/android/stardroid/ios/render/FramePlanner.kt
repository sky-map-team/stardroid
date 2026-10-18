/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.render

import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import kotlin.math.ln
import kotlin.math.max

/**
 * Projects retained [LayerScene]s into screen-space [DrawOp]s for one frame.
 *
 * This is a deliberately simple 2-D backend for the iOS proof of concept, not a port of the
 * GLES renderer: points, lines and labels are drawn; images (planet discs, phases) and glows are
 * skipped, and the sky gradient is not drawn. It honours the contract's ordering (layers by
 * depth; within a layer lines → points → labels), the magnitude limit, night mode and the label
 * scale. The viewport is in UIKit points, so `dp` maps 1:1 and [Viewport.density] is unused.
 */
object FramePlanner {
    fun plan(
        scenes: Collection<LayerScene>,
        camera: SkyCamera,
        state: RenderState,
        viewport: Viewport,
    ): List<DrawOp> {
        val projection = SkyProjection(camera, viewport)
        val ops = ArrayList<DrawOp>()
        val labels = ArrayList<PlacedLabel>()
        for (scene in scenes.sortedBy { it.depth }) {
            scene.lines.forEach { planLine(it, projection, state, ops) }
            scene.points.forEach { planPoint(it, projection, state, viewport, ops) }
            scene.labels.forEach { planLabel(it, projection, state, camera, viewport, labels) }
        }
        // Labels last, over every layer, after screen-space declutter (higher priority wins).
        ops += declutter(labels).map { it.op }
        return ops
    }

    private fun planLine(
        line: LinePrimitive,
        projection: SkyProjection,
        state: RenderState,
        ops: MutableList<DrawOp>,
    ) {
        val color = state.tint(line.color)
        val width = line.widthDp.toFloat()
        // Split into runs at vertices behind the viewer; each run becomes its own polyline.
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()

        fun flush() {
            if (xs.size >= 2) {
                ops += DrawOp.Polyline(xs.toFloatArray(), ys.toFloatArray(), width, color)
            }
            xs.clear()
            ys.clear()
        }
        for (vertex in line.vertices) {
            val p = projection.worldToScreen(vertex)
            if (p == null) {
                flush()
            } else {
                xs += p.xPx
                ys += p.yPx
            }
        }
        flush()
    }

    private fun planPoint(
        point: PointPrimitive,
        projection: SkyProjection,
        state: RenderState,
        viewport: Viewport,
        ops: MutableList<DrawOp>,
    ) {
        val (radius, color) =
            when (val look = point.appearance) {
                is PointAppearance.Stellar -> {
                    val limit = state.magnitudeLimit
                    if (limit != null && look.magnitude > limit) return
                    stellarRadius(look.magnitude) to stellarColor(look.magnitude)
                }
                is PointAppearance.Fixed -> (look.sizeDp / 2).toFloat() to look.color
                // Icons need image resolution (ImageRef → UIImage), which this backend lacks.
                is PointAppearance.Icon -> return
            }
        val p = projection.worldToScreen(point.pos) ?: return
        if (!viewport.contains(p.xPx, p.yPx, margin = radius)) return
        ops += DrawOp.Dot(p.xPx, p.yPx, radius, state.tint(color))
    }

    private fun planLabel(
        label: LabelPrimitive,
        projection: SkyProjection,
        state: RenderState,
        camera: SkyCamera,
        viewport: Viewport,
        out: MutableList<PlacedLabel>,
    ) {
        val threshold = label.magnitudeForThresholding
        if (threshold != null && threshold > labelMagnitudeLimit(camera.fovDeg, state)) return
        val p = projection.worldToScreen(label.pos) ?: return
        if (!viewport.contains(p.xPx, p.yPx, margin = 0f)) return
        val size = (baseSizePt(label.style.size) * state.labelScaleFactor).toFloat()
        val op =
            DrawOp.Text(
                x = p.xPx,
                y = p.yPx + label.style.offsetDp.toFloat(),
                text = label.text,
                sizePt = size,
                color = state.tint(label.style.color),
            )
        out += PlacedLabel(op, label.priority)
    }

    /**
     * Greedy declutter by priority. Text widths are estimated (CoreGraphics measures the real
     * ones only at draw time), which is plenty to stop the brightest labels piling up.
     */
    private fun declutter(labels: List<PlacedLabel>): List<PlacedLabel> {
        val kept = ArrayList<PlacedLabel>()
        for (label in labels.sortedByDescending { it.priority }) {
            if (kept.none { it.overlaps(label) }) kept += label
        }
        return kept
    }

    private class PlacedLabel(val op: DrawOp.Text, val priority: Int) {
        private val halfWidth = op.text.length * op.sizePt * AVERAGE_GLYPH_WIDTH / 2
        private val left get() = op.x - halfWidth
        private val right get() = op.x + halfWidth
        private val bottom get() = op.y + op.sizePt

        fun overlaps(other: PlacedLabel): Boolean =
            left < other.right && other.left < right && op.y < other.bottom && other.op.y < bottom
    }

    /** Magnitude → dot radius in points: Sirius ≈ 3.6 pt, naked-eye limit ≈ 0.7 pt. */
    internal fun stellarRadius(magnitude: Double): Float =
        (STAR_RADIUS_MAG0 - STAR_RADIUS_PER_MAG * magnitude)
            .coerceIn(MIN_STAR_RADIUS, MAX_STAR_RADIUS)
            .toFloat()

    private fun stellarColor(magnitude: Double): Rgba =
        Rgba(1f, 1f, 1f, (1.1 - 0.12 * magnitude).coerceIn(0.35, 1.0).toFloat())

    /** Zooming in lets fainter objects' labels through: +2.5 mag per halving of the FOV. */
    internal fun labelMagnitudeLimit(
        fovDeg: Double,
        state: RenderState,
    ): Double {
        val zoomBonus = max(0.0, ln(DEFAULT_FOV / fovDeg) / ln(2.0)) * 2.5
        val base = BASE_LABEL_MAGNITUDE + zoomBonus
        return state.magnitudeLimit?.let { minOf(it, base) } ?: base
    }

    private fun baseSizePt(size: LabelSize): Double =
        when (size) {
            LabelSize.TITLE -> 17.0
            LabelSize.STANDARD -> 13.0
            LabelSize.MINOR -> 11.0
        }

    /** Night mode: every colour mapped onto red at its brightest channel's intensity. */
    private fun RenderState.tint(color: Rgba): Rgba =
        if (nightMode) Rgba(maxOf(color.r, color.g, color.b), 0f, 0f, color.a) else color

    private fun Viewport.contains(
        x: Float,
        y: Float,
        margin: Float,
    ): Boolean = x >= -margin && y >= -margin && x <= widthPx + margin && y <= heightPx + margin

    private const val AVERAGE_GLYPH_WIDTH = 0.55f
    private const val DEFAULT_FOV = 70.0
    private const val BASE_LABEL_MAGNITUDE = 2.5
    private const val STAR_RADIUS_MAG0 = 2.8
    private const val STAR_RADIUS_PER_MAG = 0.35
    private const val MIN_STAR_RADIUS = 0.6
    private const val MAX_STAR_RADIUS = 4.0
}
