/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * One frame's screen-space quads — icons, label glyph runs and their underlines — as the
 * instance data a backend's sprite shader reads, grouped into **runs** that share a texture.
 * The backend uploads [data] once and issues one instanced draw per run, binding that run's
 * [runTexture] (an index the producer of the quads defines: an atlas page, an icon ref).
 *
 * Layout per instance, as `:render:gles3`'s SpriteBatch: centre px 2, size px 2, uv0 2, uv1 2,
 * tint 4 (alpha already multiplied in), mode 1. Pixels are bottom-left-origin, GL's convention,
 * which Metal's clip space shares. `uv0` is the quad's lower-left corner and `uv1` its upper
 * right, in the texture's top-row-first convention, so a glyph's lower-left takes the *larger* v.
 *
 * Grows to a high-water mark and is reused frame to frame, so a steady scene allocates nothing
 * (audit-2026-08 M2). Not thread-safe: the render thread's.
 */
class SpriteInstances {
    var data = FloatArray(INITIAL_INSTANCES * FLOATS_PER_INSTANCE)
        private set

    /** Instances added since [clear]. */
    var size = 0
        private set

    /** Runs since [clear]. [runTexture], [runFirst] and [runSize] hold them, [runCount] long. */
    var runCount = 0
        private set
    var runTexture = IntArray(INITIAL_RUNS)
        private set
    var runFirst = IntArray(INITIAL_RUNS)
        private set
    var runSize = IntArray(INITIAL_RUNS)
        private set

    fun clear() {
        size = 0
        runCount = 0
    }

    /** Starts a run of quads sampling [texture]; [add] appends to the latest run. */
    fun beginRun(texture: Int) {
        if (runCount == runTexture.size) {
            val capacity = runCount * 2
            runTexture = runTexture.copyOf(capacity)
            runFirst = runFirst.copyOf(capacity)
            runSize = runSize.copyOf(capacity)
        }
        runTexture[runCount] = texture
        runFirst[runCount] = size
        runSize[runCount] = 0
        runCount++
    }

    /** Queues one quad in the current run. */
    @Suppress("LongParameterList")
    fun add(
        centerXPx: Float,
        centerYPx: Float,
        widthPx: Float,
        heightPx: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        tint: Rgba,
        alpha: Float,
        mode: Int,
    ) {
        check(runCount > 0) { "add() before beginRun()" }
        if ((size + 1) * FLOATS_PER_INSTANCE > data.size) data = data.copyOf(data.size * 2)
        var i = size * FLOATS_PER_INSTANCE
        data[i++] = centerXPx
        data[i++] = centerYPx
        data[i++] = widthPx
        data[i++] = heightPx
        data[i++] = u0
        data[i++] = v0
        data[i++] = u1
        data[i++] = v1
        data[i++] = tint.r
        data[i++] = tint.g
        data[i++] = tint.b
        data[i++] = tint.a * alpha
        data[i] = mode.toFloat()
        size++
        runSize[runCount - 1]++
    }

    companion object {
        const val FLOATS_PER_INSTANCE = 13

        /** An RGBA texture modulated by a tint: deep-sky markers, shower radiants. */
        const val MODE_ICON = 0

        /** An 8-bit coverage mask: one label's glyph run, with a halo under it. */
        const val MODE_GLYPH = 1

        /** A solid bar: the underline marking a label whose object has an info card. */
        const val MODE_RULE = 2

        private const val INITIAL_INSTANCES = 128
        private const val INITIAL_RUNS = 8
    }
}
