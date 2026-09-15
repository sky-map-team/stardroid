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
import com.google.android.stardroid.render.api.EclipseShadow
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.Terminator
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * An [EclipseShadow] quantised the same way [PhaseKey] quantises the rest of the phase (D106):
 * 1% of a Moon radius for the two shadow radii and the offset, one degree for the direction. An
 * eclipse's geometry moves fast enough — totality alone can be over in under an hour — that this
 * still recomposites several times as one plays out, unlike the ordinary phase steps above, which
 * barely move within a single night.
 */
internal data class EclipseKey(
    val umbraSteps: Int,
    val penumbraSteps: Int,
    val offsetSteps: Int,
    val directionSteps: Int,
) {
    val shadow: EclipseShadow
        get() =
            EclipseShadow(
                umbraSteps * RADIUS_STEP,
                penumbraSteps * RADIUS_STEP,
                offsetSteps * RADIUS_STEP,
                directionSteps * ANGLE_STEP,
            )

    companion object {
        const val RADIUS_STEP = 0.01
        const val ANGLE_STEP = 1.0

        fun of(shadow: EclipseShadow?): EclipseKey? {
            if (shadow == null) return null
            return EclipseKey(
                (shadow.umbraRadius / RADIUS_STEP).roundToInt(),
                (shadow.penumbraRadius / RADIUS_STEP).roundToInt(),
                (shadow.offset / RADIUS_STEP).roundToInt(),
                (shadow.directionDeg / ANGLE_STEP).roundToInt().mod(360),
            )
        }
    }
}

/**
 * A phase quantised to the steps the cache keys on (D88): 0.5% of illuminated fraction, and one
 * degree of bright-limb angle.
 *
 * Both are far finer than the eye can resolve on a disc, and both change slowly — the Moon's
 * illumination takes about seven minutes to cross a step, its limb angle about two hours — so in
 * normal use a body recomposites at most once per layer resubmission, which is roughly once a
 * minute anyway. Time travel moves faster and simply produces more cache entries, which the byte
 * budget then evicts in the usual way.
 */
internal data class PhaseKey(
    val fractionSteps: Int,
    val angleSteps: Int,
    val eclipse: EclipseKey? = null,
) {
    val terminator: Terminator
        get() = Terminator(fractionSteps * FRACTION_STEP, angleSteps * ANGLE_STEP, eclipse?.shadow)

    companion object {
        const val FRACTION_STEP = 0.005
        const val ANGLE_STEP = 1.0

        /**
         * The key for [terminator], or null if the body is lit enough that compositing would not
         * change a pixel — which keeps every outer planet on one shared fully-lit texture.
         *
         * An eclipsed Moon is always composited even though it reads as fully lit by
         * [Terminator.illuminatedFraction] alone (an eclipse only happens near full moon): the
         * [EclipseShadow] carries the reason to composite that the fraction check alone would
         * miss.
         */
        fun of(terminator: Terminator?): PhaseKey? {
            if (terminator == null) return null
            val eclipseKey = EclipseKey.of(terminator.eclipse)
            val fraction = terminator.illuminatedFraction.coerceIn(0.0, 1.0)
            if (fraction >= PhaseCompositor.FULLY_LIT && eclipseKey == null) return null
            return PhaseKey(
                (fraction / FRACTION_STEP).roundToInt(),
                (terminator.brightLimbAngleDeg / ANGLE_STEP).roundToInt().mod(360),
                eclipseKey,
            )
        }
    }
}

/** What a drawer retains: the image, plus the phase painted into it if it has one. */
internal data class TextureKey(val ref: ImageRef, val phase: PhaseKey? = null)

/**
 * The renderer-wide [ImageRef]-keyed GL texture cache (D85).
 *
 * Both [ImageDrawer] and [IconDrawer] used to upload their own textures per [LayerScene]
 * instance. Dynamic layers submit a fresh scene on every position update — the solar system does
 * so about once a minute — so every planet texture was re-decoded and re-uploaded on each one.
 * That was tolerable for v1's 32-pixel sprites and is not for the megabyte-scale discs D85
 * introduces. Here the geometry still rebuilds per scene while the textures are keyed on the image
 * itself and survive.
 *
 * Entries are reference-counted by their holders ([ImageDrawer.build]/[IconDrawer.build] retain,
 * their `release` counterparts release) and outlive a refCount of 0, which is what lets a rebuilt
 * scene pick up the same textures. Only when [byteBudget] is exceeded are unreferenced entries
 * dropped, least-recently-used first; see [TextureLedger.evict].
 *
 * Night-mode textures are built lazily on the first night-mode frame that needs one. Building both
 * eagerly doubled texture memory for every user who never opens night mode.
 *
 * GL-thread-only, like every other cache in [GLSkyRenderer].
 *
 * @param loader resolves an [ImageRef] to a [Bitmap], or null if it is unavailable (that image is
 *   then skipped, D24). The returned bitmap is recycled here. It may be called a second time for
 *   the same ref to build the night-mode variant.
 * @param byteBudget soft ceiling on estimated GPU texture bytes. The default holds D85's full
 *   solar-system set in both variants with room to spare, so the steady state never evicts; see
 *   [DEFAULT_BYTE_BUDGET] for the arithmetic, which has to be redone whenever an asset's
 *   resolution changes.
 */
internal class TextureCache(
    private val loader: (ImageRef) -> Bitmap?,
    byteBudget: Long = DEFAULT_BYTE_BUDGET,
) {
    private val ledger = TextureLedger(byteBudget)

    /**
     * Counts one more holder of [key], uploading its day-mode texture if this is the first.
     * Every call must be paired with a [release].
     */
    fun retain(
        gl: GL10,
        key: TextureKey,
    ) {
        val entry = ledger.retain(key)
        if (entry.dayId != 0 || entry.loadFailed) return
        val bmp = phased(key)
        if (bmp == null) {
            entry.loadFailed = true
            return
        }
        entry.dayId = uploadRgbaTexture(gl, bmp)
        ledger.addBytes(entry, textureByteSize(bmp))
        bmp.recycle()
        deleteTextures(gl, ledger.evict(keep = key))
    }

    /**
     * The bitmap for [key]: the loader's image, with the phase composited in if it has one.
     * Returns null when the image is unavailable (D24).
     */
    private fun phased(key: TextureKey): Bitmap? {
        val base = loader(key.ref) ?: return null
        val phase = key.phase ?: return base
        val composited = PhaseCompositor.composite(base, phase.terminator)
        base.recycle()
        return composited
    }

    /** Drops one holder of [key]; its textures stay cached for the next scene that wants them. */
    fun release(key: TextureKey) = ledger.release(key)

    /**
     * The GL texture name to bind for [key], or 0 if it has no texture (an unavailable image,
     * skipped per D24). In night mode the red variant is built on first use, and if that second
     * load fails the day texture is drawn rather than nothing.
     */
    fun textureId(
        gl: GL10,
        key: TextureKey,
        nightMode: Boolean,
    ): Int {
        val entry = ledger.peek(key) ?: return 0
        if (entry.dayId == 0 || !nightMode) return entry.dayId
        if (entry.nightId == 0) {
            val bmp = phased(key) ?: return entry.dayId
            entry.nightId = uploadRedTexture(gl, bmp)
            ledger.addBytes(entry, textureByteSize(bmp))
            bmp.recycle()
            deleteTextures(gl, ledger.evict(keep = key))
        }
        return entry.nightId
    }

    /**
     * Forgets every texture after EGL context loss. The GL runtime has already freed them, so
     * nothing is deleted here (G9 in render-api.md); [GLSkyRenderer] drops the holders in the
     * same pass, so the outstanding reference counts go with them.
     */
    fun onContextLost() = ledger.clear()

    internal companion object {
        /**
         * 40 MB.
         *
         * What has to fit, at [textureByteSize]'s 4 bytes per texel plus a third again for the
         * mipmap chain, in day and night variants: a 1024² Moon (10.7 MB), 512² Sun, Jupiter and
         * Saturn (2.7 MB each), five 256² bodies and a 128² Pluto (0.7 MB each and 0.2 MB), and
         * the ten deep-sky icons (0.7 MB together) — 23.1 MB in steady state.
         *
         * The rest is headroom for phase churn. A composited body is keyed on its quantised
         * phase, so the entry it used a step ago is still cached and unreferenced until eviction
         * reclaims it, and the Moon's entry is the largest single thing here. Budgeting to the
         * steady state exactly would evict the Moon's own previous phase on every step, which is
         * the one thing this cache exists to avoid; 40 MB leaves room for a couple of stale ones.
         *
         * Redo this sum when an asset's resolution changes — `SolarSystemAssetBudgetTest` keeps
         * it honest by adding up the shipped files.
         */
        const val DEFAULT_BYTE_BUDGET = 40L * 1024 * 1024
    }
}
