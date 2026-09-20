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
import com.google.android.stardroid.render.api.ImageRef

/**
 * `ImageRef` → GL texture, reference-counted with an LRU byte budget.
 *
 * Markedly simpler than the GLES1 cache, in two ways that are both consequences of having
 * shaders:
 *
 * - **One variant per image, not two.** GLES1 keeps a second red-shifted copy of every texture
 *   because night mode there is a per-primitive colour bake with nowhere else to live. Here it
 *   is a uniform read by every fragment shader, so an image is uploaded once.
 * - **The key is the `ImageRef` alone.** GLES1 also keys on a quantised phase, because it paints
 *   the terminator *into* the bitmap; here the phase is a uniform and the Moon's texture is the
 *   same texture at every phase.
 *
 * Between them, the steady-state footprint is roughly a quarter of GLES1's for the same assets —
 * which is why the budget below is far smaller despite holding the same set.
 *
 * GL-thread-only.
 *
 * @param loader resolves an [ImageRef] to a [Bitmap], or null if it is unavailable, in which case
 *   the image is silently skipped (D24). The returned bitmap is recycled here.
 * @param byteBudget soft ceiling on estimated GPU texture bytes.
 */
class TextureCache(
    private val loader: (ImageRef) -> Bitmap?,
    private val byteBudget: Long = DEFAULT_BYTE_BUDGET,
) {
    private class Entry {
        var textureId = 0
        var bytes = 0L
        var refCount = 0
        var loadFailed = false

        /** Monotonic tick of the last release, for LRU ordering among unreferenced entries. */
        var lastReleasedAt = 0L
    }

    private val entries = HashMap<ImageRef, Entry>()
    private var totalBytes = 0L
    private var tick = 0L

    /** Counts one more holder of [ref], uploading it if this is the first. Pair with [release]. */
    fun retain(ref: ImageRef) {
        val entry = entries.getOrPut(ref) { Entry() }
        entry.refCount++
        if (entry.textureId != 0 || entry.loadFailed) return
        val bitmap = loader(ref)
        if (bitmap == null) {
            entry.loadFailed = true
            return
        }
        entry.textureId = uploadRgbaTexture(bitmap)
        entry.bytes = textureByteSize(bitmap)
        totalBytes += entry.bytes
        bitmap.recycle()
        evict(keep = ref)
    }

    /** Drops one holder of [ref]; its texture stays cached for the next scene that wants it. */
    fun release(ref: ImageRef) {
        val entry = entries[ref] ?: return
        if (entry.refCount > 0) entry.refCount--
        if (entry.refCount == 0) entry.lastReleasedAt = ++tick
    }

    /** The texture to bind for [ref], or 0 if it has none (an unavailable image, D24). */
    fun textureId(ref: ImageRef): Int = entries[ref]?.textureId ?: 0

    /**
     * Forgets every texture after EGL context loss. The GL runtime has already freed them, so
     * nothing is deleted here (G9); the renderer drops the holders in the same pass, so the
     * outstanding reference counts go with them.
     */
    fun onContextLost() {
        entries.clear()
        totalBytes = 0L
    }

    /** Deletes unreferenced textures, least-recently-released first, until inside the budget. */
    private fun evict(keep: ImageRef?) {
        if (totalBytes <= byteBudget) return
        val candidates =
            entries.entries
                .filter { it.value.refCount == 0 && it.value.textureId != 0 && it.key != keep }
                .sortedBy { it.value.lastReleasedAt }
        for (candidate in candidates) {
            if (totalBytes <= byteBudget) break
            deleteTextures(intArrayOf(candidate.value.textureId))
            totalBytes -= candidate.value.bytes
            entries.remove(candidate.key)
        }
    }

    companion object {
        /**
         * 16 MB.
         *
         * What has to fit, at four bytes per texel and one variant each: a 1024² Moon (4 MB), a
         * 512² Sun, Jupiter and Saturn (1 MB each), five 256² bodies and a 128² Pluto (0.25 MB
         * each and 0.06 MB), and the ten deep-sky icons — about 9 MB in steady state, so nothing
         * is ever evicted in normal use. Revisit whenever an asset's resolution changes.
         */
        const val DEFAULT_BYTE_BUDGET = 16L * 1024 * 1024
    }
}
