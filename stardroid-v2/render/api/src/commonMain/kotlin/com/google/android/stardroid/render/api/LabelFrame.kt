/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The per-frame half of a label drawer, shared by every backend (D117): cull, project, offset,
 * declutter, fade, and emit the survivors as [SpriteInstances] — one run per [LabelAtlas] page,
 * each glyph followed by its has-an-info-card underline. Transcribed from `:render:gles3`'s
 * LabelDrawer.draw; the backend only uploads the instances and draws each run with its page.
 *
 * [LabelDeclutterer] still decides what is visible, on the CPU; [LabelFader] only softens the
 * transition. Owns reusable scratch, so a steady frame allocates nothing (audit-2026-08 M2); one
 * instance serves every layer, since layers are laid out one at a time. Not thread-safe.
 */
class LabelFrame {
    private val candidates = LabelDeclutterer.Candidates()

    /**
     * Lays out [atlas]'s labels for this frame into [out] (appending runs whose texture is the
     * atlas page index). The [fader] is advanced for every candidate — including the ones the
     * declutterer rejected, which is what lets them fade *out* rather than vanish. A label that
     * leaves the frustum entirely is never offered to the fader and is simply forgotten.
     */
    fun layout(
        atlas: LabelAtlas,
        fader: LabelFader,
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        out: SpriteInstances,
    ) {
        if (atlas.glyphs.isEmpty()) return
        val lookDir = camera.lineOfSight
        val dotThreshold = ScreenSpace.frustumDotThreshold(camera, viewport)
        val magLimit = LabelDeclutterer.magnitudeThreshold(camera.fovDeg)
        val pxPerDeg = ScreenSpace.pixelsPerDegree(camera, viewport)
        val shortSidePx = min(viewport.widthPx, viewport.heightPx)

        candidates.clear()
        for (glyphIndex in atlas.glyphs.indices) {
            val glyph = atlas.glyphs[glyphIndex]
            val label = glyph.label
            // The frustum test is a hard cull — an off-screen label has nowhere to fade — but the
            // magnitude test is not: a label a zoom-out has just made too faint is still on
            // screen, and should be seen to leave, so it stays a candidate that cannot win space.
            if (!LabelDeclutterer.passesFrustum(glyph.pos, lookDir, dotThreshold)) continue
            val screen = projection.worldToScreen(glyph.pos) ?: continue
            // The dp gap measures to the label's top edge, the angular clearance to its centre.
            // The disc this label names is floored at draw time (D86), so the clearance is floored
            // by the same rule, or the name would sit inside the disc wherever the floor works.
            val clearanceDeg =
                SizeFloor.drawnDiameterDeg(
                    label.style.clearanceDeg,
                    label.style.clearanceMinScreenFraction,
                    label.style.clearanceMinSizeDp,
                    camera.fovDeg,
                    shortSidePx,
                    viewport.density,
                )
            val offsetPx =
                max(
                    label.style.offsetDp * viewport.density + glyph.heightPx * 0.5,
                    clearanceDeg * pxPerDeg,
                )
            candidates.add(
                glyphIndex,
                screen.xPx,
                viewport.heightPx - (screen.yPx + offsetPx.toFloat()),
                glyph.widthPx,
                glyph.heightPx + if (label.hasDetail) underlineAllowance(glyph) else 0,
                label.priority,
                eligible =
                    LabelDeclutterer.passesMagnitude(
                        label.magnitudeForThresholding,
                        magLimit,
                    ),
            )
        }
        if (candidates.size == 0) return

        LabelDeclutterer.declutter(candidates)

        // Candidates are in glyph order, which the packer guarantees is non-decreasing in page,
        // so each page is one run.
        var activePage = -1
        for (i in 0 until candidates.size) {
            val glyph = atlas.glyphs[candidates.glyphIndex[i]]
            val alpha = fader.alphaFor(glyph.text, candidates.visible[i])
            if (alpha <= 0f) continue
            if (glyph.page != activePage) {
                out.beginRun(glyph.page)
                activePage = glyph.page
            }
            val label = glyph.label
            val detailAlpha = if (label.hasDetail) 1f else NO_DETAIL_ALPHA
            // Pixel-snap to reduce texture aliasing. floor, not toInt(): truncation toward zero
            // would snap negative edge coordinates upward.
            val x = floor(candidates.screenX[i]) + ScreenSpace.PIXEL_SNAP
            val y = floor(candidates.screenY[i]) + ScreenSpace.PIXEL_SNAP
            out.add(
                x,
                y,
                glyph.widthPx.toFloat(),
                glyph.heightPx.toFloat(),
                glyph.u0,
                glyph.v0,
                glyph.u1,
                glyph.v1,
                label.style.color,
                alpha * detailAlpha,
                SpriteInstances.MODE_GLYPH,
            )
            if (label.hasDetail) {
                // Under the cell, not through it: the cell's bottom edge already sits below the
                // descender, so the rule clears the text without knowing the baseline.
                val thickness = underlineThickness(glyph)
                val gap = underlineGap(glyph)
                out.add(
                    x,
                    y - glyph.heightPx * 0.5f - gap - thickness * 0.5f,
                    glyph.widthPx * UNDERLINE_WIDTH_FRACTION,
                    thickness,
                    0f,
                    1f,
                    1f,
                    0f,
                    label.style.color,
                    alpha * UNDERLINE_ALPHA,
                    SpriteInstances.MODE_RULE,
                )
            }
        }
    }

    private fun underlineThickness(glyph: LabelGlyph): Float =
        max(1f, glyph.heightPx * UNDERLINE_THICKNESS_FRACTION)

    private fun underlineGap(glyph: LabelGlyph): Float =
        max(
            1f,
            glyph.heightPx * UNDERLINE_GAP_FRACTION,
        )

    /** Room the rule needs below the cell, which the declutterer keeps clear. */
    private fun underlineAllowance(glyph: LabelGlyph): Int =
        (underlineThickness(glyph) + underlineGap(glyph)).toInt()

    companion object {
        /** Opacity of a label naming something with no info card behind it. */
        const val NO_DETAIL_ALPHA = 0.7f

        /** Halo width in atlas texels — about one pixel of outline at the sizes labels draw. */
        const val HALO_TEXELS = 1.3f

        /**
         * Outline colour. Near-black and not fully opaque, so the outline reads as a shadow the
         * text sits on rather than a cartoon stroke around it.
         */
        val HALO_COLOR = Rgba(0.02f, 0.02f, 0.04f, 0.85f)

        /**
         * The info-card underline's thickness as a fraction of the label's cell height:
         * proportional to the text, which keeps it a hairline and tracks the font-size preference.
         */
        const val UNDERLINE_THICKNESS_FRACTION = 1f / 16f

        /** Gap between the bottom of the label's cell and the underline, same units. */
        const val UNDERLINE_GAP_FRACTION = 1f / 14f

        /** How much of the label's width the rule spans, centred: a tick, not a full rule. */
        const val UNDERLINE_WIDTH_FRACTION = 0.6f

        /** Opacity of the rule relative to its label: a hint, not a second piece of text. */
        const val UNDERLINE_ALPHA = 0.45f
    }
}
