/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LabelAtlasPacker
import com.google.android.stardroid.render.api.LabelDeclutterer
import com.google.android.stardroid.render.api.LabelFader
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SizeFloor
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.Viewport
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Rasterized glyph data (atlas cell + semantic info) for one label. */
data class LabelGlyph(
    val text: String,
    val widthPx: Int,
    val heightPx: Int,
    val page: Int,
    /** Lower-left texture coordinate; v is the *larger* of the pair (the atlas is unflipped). */
    val uv0: FloatArray,
    /** Upper-right texture coordinate. */
    val uv1: FloatArray,
    val pos: Vector3,
    val priority: Int,
    val magnitude: Double?,
    val color: Rgba,
    val hasDetail: Boolean,
    val offsetDp: Double,
    val clearanceDeg: Double,
    val clearanceMinScreenFraction: Double,
    val clearanceMinSizeDp: Double,
) {
    // Arrays make the generated equals/hashCode identity-based; nothing compares glyphs, and
    // the alternative is copying two floats into objects for every label in the catalog.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** R8 atlas page textures, their pixel sizes, and per-glyph data for one layer's labels. */
class LabelGpuData(
    val pageTextureIds: IntArray,
    val pageSizesPx: List<Pair<Int, Int>>,
    val glyphs: List<LabelGlyph>,
)

/**
 * Sky labels: a Canvas-rasterized atlas drawn as instanced screen-space quads.
 *
 * Four things differ from GLES1, three of them on purpose.
 *
 * 1. **The atlas is `R8`.** It is a coverage mask, not colour; GLES1 stores it as ARGB_8888 only
 *    because fixed-function GL has no single-channel format. This is a quarter of the memory and
 *    the only reason a catalog-scale label set is affordable.
 * 2. **One instanced draw per atlas page**, rather than a matrix push and a draw per label —
 *    which is the TODO GLES1's `LabelDrawer` carries.
 * 3. **Every label gets a halo.** Sampling the coverage mask a few times at an offset and
 *    compositing an outline under the fill costs almost nothing here, and it is the real fix for
 *    issue #1014 (a green horizon label against the horizon glow's own green). On GLES1 a halo
 *    means rasterizing and drawing a second, wider glyph per label, which is why it was not done.
 * 4. **Labels say whether tapping them will do anything.** A tap only identifies objects that
 *    have an info card, and most star labels do not have one — a distinction that is currently
 *    invisible until you tap and nothing happens. Carded labels draw at full strength with a
 *    small dot beside them; the rest are dimmed to [NO_DETAIL_ALPHA].
 *
 * And one thing that is the same by design: [LabelDeclutterer] still decides what is visible,
 * on the CPU, unit-tested. [LabelFader] only softens the transition.
 */
object LabelDrawer {
    private const val ATLAS_WIDTH_PX = 1024

    /** Power of two; 1024×1024 R8 is 1 MB, against GLES1's 4 MB for the same page. */
    private const val MAX_PAGE_HEIGHT_PX = 1024

    /** Gutter between atlas cells so antialiased glyph edges cannot bleed into a neighbour. */
    private const val GLYPH_PADDING_PX = 1

    private const val LABEL_TITLE_SP = 15
    private const val LABEL_STANDARD_SP = 10
    private const val LABEL_MINOR_SP = 8

    /** Opacity of a label naming something with no info card behind it. */
    const val NO_DETAIL_ALPHA = 0.7f

    /** Halo width in atlas texels — about one pixel of outline at the sizes labels are drawn. */
    const val HALO_TEXELS = 1.3f

    /**
     * Outline colour. Near-black rather than black, and not fully opaque, so the outline reads as
     * a shadow the text sits on rather than a cartoon stroke around it.
     */
    val HALO_COLOR = Rgba(0.02f, 0.02f, 0.04f, 0.85f)

    /** Diameter of the has-an-info-card marker, in dp. */
    const val MARKER_SIZE_DP = 3.0

    /** Gap between the marker and the start of the text, in dp. */
    const val MARKER_GAP_DP = 3.0

    /**
     * Rasterizes label text into an R8 atlas and uploads it. Must be called on the GL thread.
     */
    fun build(
        labels: List<LabelPrimitive>,
        state: RenderState,
        density: Float,
    ): LabelGpuData {
        if (labels.isEmpty()) return LabelGpuData(IntArray(0), emptyList(), emptyList())

        val maxTextureSize = queryMaxTextureSize()
        val pageWidth = ATLAS_WIDTH_PX.coerceAtMost(maxTextureSize)
        val maxPageHeight = MAX_PAGE_HEIGHT_PX.coerceAtMost(maxTextureSize)

        val paint =
            Paint().apply {
                isAntiAlias = true
                typeface = Typeface.SANS_SERIF
                // The atlas is a coverage mask: white text on transparent, so the alpha channel
                // carries the shape and the colour comes from the instance tint at draw time.
                // That is also why night mode never needs a rebuild.
                color = 0xffffffff.toInt()
            }

        data class Measurement(val widthPx: Int, val heightPx: Int, val ascentPx: Int)
        val measurements =
            labels.map { label ->
                paint.textSize = fontSizePx(label.style.size, state.labelScaleFactor, density)
                val ascent = ceil(-paint.ascent()).toInt()
                val descent = ceil(paint.descent()).toInt()
                val w = ceil(paint.measureText(label.text)).toInt().coerceAtMost(pageWidth)
                Measurement(w, ascent + descent, ascent)
            }

        val layout =
            LabelAtlasPacker.pack(
                measurements.map { LabelAtlasPacker.Size(it.widthPx, it.heightPx) },
                pageWidth,
                maxPageHeight,
                GLYPH_PADDING_PX,
            )

        // ALPHA_8 is exactly the one-byte-per-pixel coverage the R8 texture wants, so the
        // rasterized page can be copied straight out with no channel shuffling.
        val pageBitmaps =
            layout.pageHeightsPx.map { h ->
                Bitmap.createBitmap(pageWidth, h, Bitmap.Config.ALPHA_8).apply { eraseColor(0) }
            }
        val pageCanvases = pageBitmaps.map { Canvas(it) }
        for ((i, label) in labels.withIndex()) {
            val m = measurements[i]
            val cell = layout.cells[i]
            paint.textSize = fontSizePx(label.style.size, state.labelScaleFactor, density)
            val canvas = pageCanvases[cell.page]
            canvas.save()
            // Clip each glyph to its cell so an over-measured neighbour cannot paint into it.
            canvas.clipRect(
                cell.u.toFloat(),
                cell.v.toFloat(),
                (cell.u + cell.w).toFloat(),
                (cell.v + cell.h).toFloat(),
            )
            canvas.drawText(label.text, cell.u.toFloat(), (cell.v + m.ascentPx).toFloat(), paint)
            canvas.restore()
        }

        val pageTexIds = IntArray(pageBitmaps.size)
        val pageSizes = ArrayList<Pair<Int, Int>>(pageBitmaps.size)
        for (p in pageBitmaps.indices) {
            val bitmap = pageBitmaps[p]
            val pixels = ByteBuffer.allocateDirect(bitmap.width * bitmap.height)
            bitmap.copyPixelsToBuffer(pixels)
            pageTexIds[p] = uploadR8Texture(bitmap.width, bitmap.height, pixels)
            pageSizes.add(bitmap.width to bitmap.height)
            bitmap.recycle()
        }

        val tw = 1f / pageWidth
        val glyphs =
            labels.mapIndexed { i, label ->
                val cell = layout.cells[i]
                val th = 1f / layout.pageHeightsPx[cell.page]
                // Android bitmaps are top-to-bottom and GL textures bottom-to-top, and the upload
                // does not flip, so the lower-left corner takes the *larger* v — the same
                // "negative crop height" convention GLES1 and v1's LabelMaker use.
                LabelGlyph(
                    text = label.text,
                    widthPx = cell.w,
                    heightPx = cell.h,
                    page = cell.page,
                    uv0 = floatArrayOf(cell.u * tw, (cell.v + cell.h) * th),
                    uv1 = floatArrayOf((cell.u + cell.w) * tw, cell.v * th),
                    pos = label.pos,
                    priority = label.priority,
                    magnitude = label.magnitudeForThresholding,
                    color = label.style.color,
                    hasDetail = label.hasDetail,
                    offsetDp = label.style.offsetDp,
                    clearanceDeg = label.style.clearanceDeg,
                    clearanceMinScreenFraction = label.style.clearanceMinScreenFraction,
                    clearanceMinSizeDp = label.style.clearanceMinSizeDp,
                )
            }
        return LabelGpuData(pageTexIds, pageSizes, glyphs)
    }

    private fun fontSizePx(
        size: LabelSize,
        labelScaleFactor: Double,
        density: Float,
    ): Float = labelSizeSp(size) * labelScaleFactor.toFloat() * density

    /** `GL_MAX_TEXTURE_SIZE`, with a conservative fallback if the query returns nonsense. */
    private fun queryMaxTextureSize(): Int {
        val result = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, result, 0)
        // The GL ES 3.0 spec floor is 2048; anything below 64 is not credible.
        return if (result[0] >= 64) result[0] else 2048
    }

    /**
     * Per-frame: pre-filter, project, declutter, fade, and draw the survivors.
     *
     * The [fader] is advanced for every candidate — including the ones the declutterer rejected,
     * which is what lets them fade *out* rather than vanish. A label that leaves the frustum
     * entirely is never offered to the fader at all and is simply forgotten, because fading in
     * from half-lit when you pan back to it would be worse than not fading.
     */
    @Suppress("LongParameterList")
    fun draw(
        gl: GlState,
        batch: SpriteBatch,
        candidates: LabelDeclutterer.Candidates,
        fader: LabelFader,
        gpu: LabelGpuData,
        camera: SkyCamera,
        projection: SkyProjection,
        viewport: Viewport,
        state: RenderState,
    ) {
        if (gpu.pageTextureIds.isEmpty() || gpu.glyphs.isEmpty()) return

        val lookDir = camera.lineOfSight
        val dotThreshold = frustumDotThreshold(camera, viewport)
        val magLimit = LabelDeclutterer.magnitudeThreshold(camera.fovDeg)
        val pxPerDeg = pixelsPerDegree(camera, viewport)
        val markerSizePx = (MARKER_SIZE_DP * viewport.density).toFloat()
        val markerGapPx = (MARKER_GAP_DP * viewport.density).toFloat()

        candidates.clear()
        for (glyphIndex in gpu.glyphs.indices) {
            val glyph = gpu.glyphs[glyphIndex]
            // The frustum test is a hard cull — an off-screen label has nowhere to fade — but
            // the magnitude test is not. A label that a zoom-out has just made too faint is
            // still on screen, and should be seen to leave rather than blinking off, so it
            // stays a candidate and is simply not eligible to win space.
            if (!LabelDeclutterer.passesFrustum(glyph.pos, lookDir, dotThreshold)) continue
            val screen = projection.worldToScreen(glyph.pos) ?: continue
            // The dp gap measures to the label's top edge, the angular clearance to its centre.
            // The disc this label names is floored at draw time (D86), so the clearance is
            // floored by the same rule — otherwise the name sits inside the disc at every zoom
            // where the floor is doing any work.
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
            // The marker rides to the left of the text, so the space it needs is part of what
            // the declutterer has to keep clear — otherwise it would overlap a neighbour the
            // declutterer believes it separated.
            val markerAllowancePx =
                if (glyph.hasDetail) markerSizePx + markerGapPx else 0f
            val glX = screen.xPx
            val glY = viewport.heightPx - (screen.yPx + offsetPx.toFloat())
            candidates.add(
                glyphIndex,
                glX,
                glY,
                glyph.widthPx + markerAllowancePx.toInt(),
                glyph.heightPx,
                glyph.priority,
                eligible = LabelDeclutterer.passesMagnitude(glyph.magnitude, magLimit),
            )
        }
        if (candidates.size == 0) return

        LabelDeclutterer.declutter(candidates)

        val width = viewport.widthPx.toFloat()
        val height = viewport.heightPx.toFloat()
        // Glyphs are emitted in candidate order, which the packer guarantees is non-decreasing in
        // page index, so each page is batched once.
        var activePage = -1
        for (i in 0 until candidates.size) {
            val glyph = gpu.glyphs[candidates.glyphIndex[i]]
            val alpha = fader.alphaFor(glyph.text, candidates.visible[i])
            if (alpha <= 0f) continue
            if (glyph.page != activePage) {
                if (activePage >= 0) {
                    flushPage(gl, batch, gpu, activePage, width, height, state.nightMode)
                }
                activePage = glyph.page
            }
            val detailAlpha = if (glyph.hasDetail) 1f else NO_DETAIL_ALPHA
            val markerAllowancePx =
                if (glyph.hasDetail) markerSizePx + markerGapPx else 0f
            // Pixel-snap to reduce texture aliasing (v1's MAGIC_OFFSET). floor, not toInt():
            // truncation toward zero would snap negative edge coordinates upward.
            val snappedX = floor(candidates.screenX[i]) + IconDrawer.PIXEL_SNAP
            val snappedY = floor(candidates.screenY[i]) + IconDrawer.PIXEL_SNAP
            // The text keeps its own width; the allowance shifts it right so the pair stays
            // centred on the anchor.
            val textCenterX = snappedX + markerAllowancePx * 0.5f
            batch.add(
                centerXPx = textCenterX,
                centerYPx = snappedY,
                widthPx = glyph.widthPx.toFloat(),
                heightPx = glyph.heightPx.toFloat(),
                uv0 = glyph.uv0,
                uv1 = glyph.uv1,
                tint = glyph.color,
                alpha = alpha * detailAlpha,
                mode = SpriteBatch.MODE_GLYPH,
            )
            if (glyph.hasDetail) {
                batch.add(
                    centerXPx = textCenterX - glyph.widthPx * 0.5f - markerGapPx -
                        markerSizePx * 0.5f,
                    centerYPx = snappedY,
                    widthPx = markerSizePx,
                    heightPx = markerSizePx,
                    uv0 = SpriteBatch.FULL_UV0,
                    uv1 = SpriteBatch.FULL_UV1,
                    tint = glyph.color,
                    alpha = alpha,
                    mode = SpriteBatch.MODE_MARKER,
                )
            }
        }
        if (activePage >= 0) {
            flushPage(gl, batch, gpu, activePage, width, height, state.nightMode)
        }
    }

    private fun flushPage(
        gl: GlState,
        batch: SpriteBatch,
        gpu: LabelGpuData,
        page: Int,
        widthPx: Float,
        heightPx: Float,
        nightMode: Boolean,
    ) {
        val (pageWidth, pageHeight) = gpu.pageSizesPx[page]
        batch.flush(
            gl = gl,
            textureId = gpu.pageTextureIds[page],
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            texelSize = floatArrayOf(1f / pageWidth, 1f / pageHeight),
            nightMode = nightMode,
            haloColor = HALO_COLOR,
            haloTexels = HALO_TEXELS,
        )
    }

    /** Deletes the atlas page textures. Must be called on the GL thread. */
    fun release(gpu: LabelGpuData) {
        deleteTextures(gpu.pageTextureIds)
    }

    private fun labelSizeSp(size: LabelSize): Int =
        when (size) {
            LabelSize.TITLE -> LABEL_TITLE_SP
            LabelSize.STANDARD -> LABEL_STANDARD_SP
            LabelSize.MINOR -> LABEL_MINOR_SP
        }
}
