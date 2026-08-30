/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLUtils
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SizeFloor
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Rasterized glyph data (atlas position + semantic info) for one label. */
internal data class LabelGlyph(
    val widthPx: Int,
    val heightPx: Int,
    /** Index into [LabelGpuData.pageTextureIds] of the atlas page holding this glyph. */
    val page: Int,
    /** 8 floats in GL_TRIANGLE_STRIP order: (u, v) per corner LL→UL→LR→UR. */
    val texCoords: FloatBuffer,
    val pos: Vector3,
    val priority: Int,
    val magnitude: Double?,
    val color: Rgba,
    /** [color] with the night-mode transform pre-applied, so drawing allocates no [Rgba]. */
    val nightColor: Rgba,
    val offsetDp: Double,
    val clearanceDeg: Double,
    /** The same size floors the named image carries, so clearance follows the *drawn* disc. */
    val clearanceMinScreenFraction: Double,
    val clearanceMinSizeDp: Double,
)

/** GL atlas page textures + per-glyph data for one layer's labels. Empty pages = no labels. */
internal class LabelGpuData(
    val pageTextureIds: IntArray,
    val glyphs: List<LabelGlyph>,
)

/**
 * Builds a Canvas-rasterized texture atlas and draws [LabelPrimitive]s as textured quads in
 * screen space.
 *
 * **Atlas:** labels are rasterized white-on-transparent onto ARGB_8888 Bitmap pages (width capped
 * at [ATLAS_WIDTH_PX], height at [MAX_PAGE_HEIGHT_PX] — both further clamped to the queried
 * `GL_MAX_TEXTURE_SIZE` so a large catalog's label set can never exceed what the GL
 * implementation can upload; [LabelAtlasPacker] spills onto additional pages instead). Uploading
 * with `GLUtils.texImage2D` transfers each Bitmap row-by-row; since Android bitmaps are
 * top-to-bottom and GL textures are bottom-to-top the per-glyph tex coords use a "negative
 * crop-height" convention (lower-left vertex gets the larger V, upper-left the smaller V) to
 * compensate, mirroring v1's `LabelMaker`. White text in the atlas means night-mode tinting is
 * fully done by `glColor4f` at draw time via `GL_MODULATE`, so the atlas never needs to be rebuilt
 * for night mode.
 *
 * **Per-frame declutter:** [LabelDeclutterer] runs a frustum test, a FOV-dependent magnitude
 * threshold, and a greedy screen-space priority sort before each draw.
 *
 * **Label rotation:** deferred — labels are always drawn upright relative to the screen
 * (upAngle = 0) until the sky model (porting-order step 4) wires in the actual `up` angle.
 *
 * The atlas is rebuilt when the [LayerScene], [RenderState.labelScaleFactor], or [Viewport.density]
 * changes; [RenderState.nightMode] does not require a rebuild.
 */
internal object LabelDrawer {
    /**
     * GL-thread-only scratch for [draw]'s declutter pass, reused across frames and layers so the
     * label path allocates nothing per frame (audit-2026-08 M2). Each `draw` call refills it
     * before use, and layers are drawn one at a time, so sharing one buffer is safe.
     */
    private val candidateBuffer = LabelDeclutterer.Candidates()

    private const val ATLAS_WIDTH_PX = 1024

    /** Power of two; 1024×1024 ARGB (4 MB) keeps single page allocations moderate. */
    private const val MAX_PAGE_HEIGHT_PX = 1024

    /** Gutter between atlas cells so antialiased glyph edges cannot bleed into a neighbor. */
    private const val GLYPH_PADDING_PX = 1

    private const val LABEL_TITLE_SP = 15
    private const val LABEL_STANDARD_SP = 10
    private const val LABEL_MINOR_SP = 8

    /** Unit quad vertices (2D), GL_TRIANGLE_STRIP: LL, UL, LR, UR. */
    private val UNIT_QUAD: FloatBuffer =
        directFloatBuffer(8).apply {
            put(-0.5f).put(-0.5f)
            put(-0.5f).put(0.5f)
            put(0.5f).put(-0.5f)
            put(0.5f).put(0.5f)
            rewind()
        }

    /**
     * Rasterizes label text to a Canvas atlas and uploads it as a GL texture. Must be called on
     * the GL thread (calls [GL10.glGenTextures] and [GLUtils.texImage2D]).
     */
    fun build(
        gl: GL10,
        labels: List<LabelPrimitive>,
        state: RenderState,
        density: Float,
    ): LabelGpuData {
        if (labels.isEmpty()) return LabelGpuData(IntArray(0), emptyList())

        // Page dimensions: our own caps, further clamped to what this GL implementation accepts.
        val maxTextureSize = queryMaxTextureSize(gl)
        val pageWidth = ATLAS_WIDTH_PX.coerceAtMost(maxTextureSize)
        val maxPageHeight = MAX_PAGE_HEIGHT_PX.coerceAtMost(maxTextureSize)

        val paint =
            Paint().apply {
                isAntiAlias = true
                typeface = Typeface.SANS_SERIF
                color = 0xffffffff.toInt()
            }

        // First pass: measure each label (width, height, ascent) in pixels.
        data class Measurement(val widthPx: Int, val heightPx: Int, val ascentPx: Int)
        val measurements =
            labels.map { label ->
                val fontSizePx =
                    labelSizeSp(label.style.size) * state.labelScaleFactor.toFloat() * density
                paint.textSize = fontSizePx
                val ascent = kotlin.math.ceil(-paint.ascent()).toInt()
                val descent = kotlin.math.ceil(paint.descent()).toInt()
                val w =
                    kotlin.math.ceil(paint.measureText(label.text)).toInt()
                        .coerceAtMost(pageWidth)
                Measurement(w, ascent + descent, ascent)
            }

        val layout =
            LabelAtlasPacker.pack(
                measurements.map { LabelAtlasPacker.Size(it.widthPx, it.heightPx) },
                pageWidth,
                maxPageHeight,
                GLYPH_PADDING_PX,
            )

        // Rasterize each page's labels onto its own white-on-transparent Bitmap, clipping each
        // glyph to its cell so an over-measured neighbor can never paint into another cell.
        val pageBitmaps =
            layout.pageHeightsPx.map { h ->
                Bitmap.createBitmap(pageWidth, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0) }
            }
        val pageCanvases = pageBitmaps.map { Canvas(it) }
        for ((i, label) in labels.withIndex()) {
            val m = measurements[i]
            val cell = layout.cells[i]
            val fontSizePx =
                labelSizeSp(label.style.size) * state.labelScaleFactor.toFloat() * density
            paint.textSize = fontSizePx
            val canvas = pageCanvases[cell.page]
            canvas.save()
            // The int overload of clipRect is deprecated since API 30; use the float one.
            canvas.clipRect(
                cell.u.toFloat(),
                cell.v.toFloat(),
                (cell.u + cell.w).toFloat(),
                (cell.v + cell.h).toFloat(),
            )
            canvas.drawText(label.text, cell.u.toFloat(), (cell.v + m.ascentPx).toFloat(), paint)
            canvas.restore()
        }

        // Upload every page as a GL texture (NEAREST filter for crisp text at 1:1). All page
        // texture IDs are generated with a single glGenTextures call.
        val pageTexIds = IntArray(pageBitmaps.size)
        gl.glGenTextures(pageTexIds.size, pageTexIds, 0)
        for (p in pageBitmaps.indices) {
            gl.glBindTexture(GL10.GL_TEXTURE_2D, pageTexIds[p])
            setNearestClampParams(gl)
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, pageBitmaps[p], 0)
            pageBitmaps[p].recycle()
        }

        // Build per-glyph tex coords using the negative-crop-height convention so text appears
        // right-side-up despite GLUtils.texImage2D uploading the Bitmap without flipping.
        val tw = 1f / pageWidth
        val glyphs =
            labels.mapIndexed { i, label ->
                val cell = layout.cells[i]
                val th = 1f / layout.pageHeightsPx[cell.page]
                val u0 = cell.u * tw
                val u1 = (cell.u + cell.w) * tw
                val vTop = cell.v * th // top of label in bitmap → smaller GL v
                val vBot = (cell.v + cell.h) * th // bottom of label in bitmap → larger GL v
                val texBuf =
                    directFloatBuffer(8).apply {
                        put(u0).put(vBot) // lower left  → bottom of text
                        put(u0).put(vTop) // upper left  → top of text
                        put(u1).put(vBot) // lower right → bottom of text
                        put(u1).put(vTop) // upper right → top of text
                        rewind()
                    }
                LabelGlyph(
                    widthPx = cell.w,
                    heightPx = cell.h,
                    page = cell.page,
                    texCoords = texBuf,
                    pos = label.pos,
                    priority = label.priority,
                    magnitude = label.magnitudeForThresholding,
                    color = label.style.color,
                    nightColor = StellarStyler.applyNightMode(label.style.color, true),
                    offsetDp = label.style.offsetDp,
                    clearanceDeg = label.style.clearanceDeg,
                    clearanceMinScreenFraction = label.style.clearanceMinScreenFraction,
                    clearanceMinSizeDp = label.style.clearanceMinSizeDp,
                )
            }

        return LabelGpuData(pageTexIds, glyphs)
    }

    /** `GL_MAX_TEXTURE_SIZE`, with a conservative fallback if the query returns nonsense. */
    private fun queryMaxTextureSize(gl: GL10): Int {
        val result = IntArray(1)
        gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, result, 0)
        // The GLES1 spec floor is 64; anything below that (0 on a broken query) is not credible.
        return if (result[0] >= 64) result[0] else 1024
    }

    private fun setNearestClampParams(gl: GL10) {
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_MIN_FILTER,
            GL10.GL_NEAREST.toFloat(),
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_MAG_FILTER,
            GL10.GL_NEAREST.toFloat(),
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_WRAP_S,
            GL10.GL_CLAMP_TO_EDGE.toFloat(),
        )
        gl.glTexParameterf(
            GL10.GL_TEXTURE_2D,
            GL10.GL_TEXTURE_WRAP_T,
            GL10.GL_CLAMP_TO_EDGE.toFloat(),
        )
    }

    /**
     * Per-frame draw: filter + declutter, then draw surviving labels as screen-space
     * textured quads.
     *
     * Uses a bottom-left-origin orthographic projection (same as v1's `LabelObjectManager`) and
     * converts [SkyProjection]'s top-left-origin screen points with `glScreenY = height − rawY`.
     * Label rotation (sky-up angle) is deferred to step 4; labels are always drawn upright.
     *
     * Must be called on the GL thread.
     */
    fun draw(
        gl: GL10,
        gpu: LabelGpuData,
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        state: RenderState,
    ) {
        if (gpu.pageTextureIds.isEmpty() || gpu.glyphs.isEmpty()) return

        val lookDir = camera.lineOfSight
        // Frustum dot-product threshold, from v1's LabelObjectManager.beginDrawing — but
        // scaled by the long/short side ratio: fovDeg spans the *short* viewport side, so v1's
        // raw width/height aspect under-covered the long axis in portrait and labels vanished
        // approaching the top/bottom edges.
        val longSideRatio =
            max(viewport.widthPx, viewport.heightPx).toFloat() /
                min(viewport.widthPx, viewport.heightPx).coerceAtLeast(1)
        val halfFovRad = camera.fovDeg * DEGREES_TO_RADIANS * 0.5
        val thresholdAngleRad = (halfFovRad * (1.0 + longSideRatio)).coerceAtMost(Math.PI)
        val dotThreshold = Math.cos(thresholdAngleRad).toFloat()
        val magLimit = LabelDeclutterer.magnitudeThreshold(camera.fovDeg)

        // Labels hang below their object in screen space (D68): a fixed offsetDp-derived
        // pixel gap that does NOT scale with zoom, floored by the projected clearanceDeg for
        // labels naming angularly-sized images (Sun/Moon/planets) so they clear the disc.
        // Pixels-per-degree at the screen centre: fovDeg spans the short viewport side.
        val pxPerDeg =
            (min(viewport.widthPx, viewport.heightPx) * 0.5 / Math.tan(halfFovRad)) *
                DEGREES_TO_RADIANS

        // Pre-filter by frustum and magnitude; project survivors to screen. The candidate
        // buffer is reused frame to frame (audit-2026-08 M2) — see LabelDeclutterer.Candidates.
        val candidates = candidateBuffer
        candidates.clear()
        for (glyphIndex in gpu.glyphs.indices) {
            val glyph = gpu.glyphs[glyphIndex]
            if (!LabelDeclutterer.passesPreFilter(
                    glyph.pos,
                    lookDir,
                    dotThreshold,
                    glyph.magnitude,
                    magLimit,
                )
            ) {
                continue
            }
            val sp = projection.worldToScreen(glyph.pos) ?: continue
            // Anchor-to-label-centre distance: the dp gap measures to the label's top edge,
            // the angular clearance to its centre (matching the old world-space offset).
            // The disc this label names is floored at draw time (D86), so the clearance is
            // floored by exactly the same rule -- otherwise the name sits inside the disc at
            // every zoom where the floor is doing any work.
            val clearanceDeg =
                SizeFloor.drawnDiameterDeg(
                    glyph.clearanceDeg,
                    glyph.clearanceMinScreenFraction,
                    glyph.clearanceMinSizeDp,
                    camera.fovDeg,
                    min(viewport.widthPx, viewport.heightPx),
                    viewport.density,
                )
            val offsetPx =
                max(
                    glyph.offsetDp * viewport.density + glyph.heightPx * 0.5,
                    clearanceDeg * pxPerDeg,
                )
            // Flip to GL bottom-left-origin; screen-down (+y) is below the object.
            val glX = sp.xPx
            val glY = viewport.heightPx - (sp.yPx + offsetPx.toFloat())
            candidates.add(glyphIndex, glX, glY, glyph.widthPx, glyph.heightPx, glyph.priority)
        }
        if (candidates.size == 0) return

        LabelDeclutterer.declutter(candidates)
        if (candidates.visibleCount == 0) return

        // Configure GL for screen-space textured quads.
        gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE)
        gl.glEnable(GL10.GL_TEXTURE_2D)
        gl.glActiveTexture(GL10.GL_TEXTURE0)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY)

        // Switch to 2D orthographic projection (y = 0 at screen bottom, matching v1).
        val w = viewport.widthPx.toFloat()
        val h = viewport.heightPx.toFloat()
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPushMatrix()
        gl.glLoadIdentity()
        gl.glOrthof(0f, w, 0f, h, -1f, 1f)
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPushMatrix()
        gl.glLoadIdentity()

        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, UNIT_QUAD)

        // TODO(catalog slices): one matrix push + draw call per label is fine at tens of labels;
        //   once catalog layers submit hundreds, batch surviving glyphs per page into a single
        //   vertex/texcoord buffer and draw each page with one glDrawArrays.
        //
        // Single linear pass: the packer guarantees non-decreasing page indices in input order
        // (see LabelAtlasPacker.pack), an order survivors preserves, so binding on page change
        // still binds each page at most once. The active color is cached too — most labels share
        // a color, and redundant glColor4f calls are wasted GL state changes. Draw order between
        // surviving labels is irrelevant: the declutterer guarantees they don't overlap on screen.
        var activePage = -1
        var activeColor: Rgba? = null
        for (i in 0 until candidates.size) {
            if (!candidates.visible[i]) continue
            val glyph = gpu.glyphs[candidates.glyphIndex[i]]
            if (glyph.page != activePage) {
                gl.glBindTexture(GL10.GL_TEXTURE_2D, gpu.pageTextureIds[glyph.page])
                activePage = glyph.page
            }
            val c = if (state.nightMode) glyph.nightColor else glyph.color
            if (c != activeColor) {
                gl.glColor4f(c.r, c.g, c.b, c.a)
                activeColor = c
            }
            // Pixel-snap to reduce texture aliasing (v1's MAGIC_OFFSET = 0.25). floor, not
            // toInt(): truncation toward zero would snap negative edge coordinates upward.
            val snappedX = floor(candidates.screenX[i]) + 0.25f
            val snappedY = floor(candidates.screenY[i]) + 0.25f
            gl.glPushMatrix()
            gl.glTranslatef(snappedX, snappedY, 0f)
            gl.glScalef(glyph.widthPx.toFloat(), glyph.heightPx.toFloat(), 1f)
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, glyph.texCoords)
            gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4)
            gl.glPopMatrix()
        }

        // Restore GL state.
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glPopMatrix()
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glPopMatrix()
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY)
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glDisable(GL10.GL_TEXTURE_2D)
        gl.glColor4f(1f, 1f, 1f, 1f)
    }

    /** Deletes the atlas page GL textures. Must be called on the GL thread. */
    fun release(
        gl: GL10,
        gpu: LabelGpuData,
    ) {
        if (gpu.pageTextureIds.isNotEmpty()) {
            gl.glDeleteTextures(gpu.pageTextureIds.size, gpu.pageTextureIds, 0)
        }
    }

    private fun labelSizeSp(size: LabelSize): Int =
        when (size) {
            LabelSize.TITLE -> LABEL_TITLE_SP
            LabelSize.STANDARD -> LABEL_STANDARD_SP
            LabelSize.MINOR -> LABEL_MINOR_SP
        }
}
