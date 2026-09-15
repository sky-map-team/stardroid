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
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.render.api.EclipseGeometry
import com.google.android.stardroid.render.api.PhaseGeometry
import com.google.android.stardroid.render.api.Terminator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Paints a [Terminator] into a fully-lit disc texture, CPU-side (D88).
 *
 * `render/gles1` is fixed-function GL10 — there are no shaders — so the phase is composited into
 * the bitmap before upload and cached against the quantised phase. The arithmetic below is
 * deliberately per-pixel and branch-light: a GLES3 backend evaluates the same function in a
 * fragment shader, drops this file, and gains an exactly continuous terminator for free.
 *
 * Everything is a *multiplier on the source pixel*, never a flat fill, so the body's own surface
 * stays visible through the shadow and the night-mode red transform still applies afterwards.
 */
internal object PhaseCompositor {
    /**
     * Illumination above which the phase is not worth compositing. The outer planets never drop
     * below this from Earth, so they keep sharing one fully-lit texture (D88).
     */
    const val FULLY_LIT = 0.995

    /**
     * What the shadowed hemisphere keeps of its lit brightness. A true New Moon painted black is
     * invisible against a black sky and reads as the Moon having vanished, so the dark side stays
     * a dark grey sphere with its maria faintly legible (D88 §4.2).
     */
    private const val DARK_FLOOR = 0.10

    /**
     * Peak earthshine, added across the shadowed side and scaled by `1 − illuminatedFraction`.
     * Physically real — sunlight off the Earth — and brightest exactly when it is needed most,
     * at the thin crescent and New.
     */
    private const val EARTHSHINE = 0.13

    /**
     * Brightening applied in a thin ring around the whole limb, at every phase, so the disc's
     * extent stays locatable even when almost all of it is in shadow.
     */
    private const val LIMB_RING = 0.22

    /** Width of that ring, and of the terminator's softening, as a fraction of the radius. */
    private const val LIMB_RING_WIDTH = 0.04
    private const val TERMINATOR_SOFTNESS = 0.012

    /**
     * Returns a new bitmap: [src] with [terminator] applied. [src] is left untouched, since the
     * texture cache may still want the fully-lit original for another phase.
     *
     * The disc is assumed to fill the texture, which is what the asset pipeline guarantees
     * (`assets/planets/SOURCES.md`).
     */
    fun composite(
        src: Bitmap,
        terminator: Terminator,
    ): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        // The lit limb's direction in texture space. rotationDeg has already turned the quad so
        // that +y is the body's north pole, and the texture frame is a view of the sky from
        // inside, so east runs to −x. A position angle measured east of north is therefore
        // (−sin, cos) here.
        val chi = terminator.brightLimbAngleDeg * DEGREES_TO_RADIANS
        val dirX = -sin(chi)
        val dirY = cos(chi)
        val fraction = terminator.illuminatedFraction.coerceIn(0.0, 1.0)
        val darkness = 1.0 - fraction
        val eclipse = terminator.eclipse

        for (row in 0 until h) {
            // +y up, unlike the row index.
            val ny = 1.0 - (row + 0.5) / h * 2.0
            for (col in 0 until w) {
                val index = row * w + col
                val argb = pixels[index]
                if (argb ushr 24 == 0) continue
                val nx = (col + 0.5) / w * 2.0 - 1.0

                // Rotate so the lit limb faces +u, the frame PhaseGeometry works in.
                val u = nx * dirX + ny * dirY
                val v = nx * dirY - ny * dirX
                val offset = PhaseGeometry.litOffset(u, v, fraction)
                val lit = smoothstep(offset / TERMINATOR_SOFTNESS)

                var scale = DARK_FLOOR + (1.0 - DARK_FLOOR) * lit
                scale += EARTHSHINE * darkness * (1.0 - lit)
                val radius = hypot(nx, ny)
                if (radius > 1.0 - LIMB_RING_WIDTH) {
                    scale += LIMB_RING * (radius - (1.0 - LIMB_RING_WIDTH)) / LIMB_RING_WIDTH
                }

                val tint =
                    eclipse?.let { EclipseGeometry.tint(nx, ny, it) } ?: EclipseGeometry.Tint.NONE
                pixels[index] =
                    scaleRgb(argb, scale * tint.red, scale * tint.green, scale * tint.blue)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Smooth 0→1 ramp over `[-1, 1]`, so the terminator is soft rather than a hard cut. */
    private fun smoothstep(t: Double): Double {
        val x = ((t + 1.0) * 0.5).coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    /**
     * Multiplies [argb]'s red/green/blue channels independently, leaving alpha alone — the same
     * scale for all three outside an eclipse, and different ones inside the umbra to redden it.
     */
    private fun scaleRgb(
        argb: Int,
        redScale: Double,
        greenScale: Double,
        blueScale: Double,
    ): Int {
        val a = argb and -0x1000000
        val r = (((argb shr 16) and 0xFF) * redScale).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * greenScale).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * blueScale).toInt().coerceIn(0, 255)
        return a or (r shl 16) or (g shl 8) or b
    }
}
