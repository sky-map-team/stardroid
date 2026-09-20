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
 * Turns [LabelDeclutterer]'s per-frame yes/no answer into a per-label alpha that eases in and
 * out, so labels arrive and leave instead of popping.
 *
 * The declutterer is unchanged and still decides *which* labels should be visible this frame;
 * this only softens the transition. Two consequences are deliberate:
 *
 * - **A label fading out no longer holds its screen space.** The declutterer's kept-rect set
 *   contains only labels it chose this frame, so an incoming label may cross-fade over an
 *   outgoing one and they will briefly overlap. The alternative — reserving space until the fade
 *   finishes — makes every arrival wait out a departure, which reads as lag rather than polish.
 * - **Labels are keyed by text, not by index.** Dynamic producers resubmit a whole `LayerScene`
 *   (the solar system does, roughly once a minute) and glyph indices are not stable across that,
 *   so index-keyed state would re-fade every planet name every minute. Text is stable and unique
 *   within a layer in practice; two labels with the same text in one layer would share a fade,
 *   which is harmless because the declutterer would not keep both anyway.
 *
 * Holds mutable state, is owned by the GL thread, and is **not** thread-safe.
 *
 * @param fadeMillis time for a full 0→1 or 1→0 transition.
 */
class LabelFader(private val fadeMillis: Long = DEFAULT_FADE_MILLIS) {
    private var alphas = HashMap<String, Float>()
    private var seen = HashSet<String>()

    /** True if any label moved this frame — the caller must schedule another frame. */
    var animating: Boolean = false
        private set

    private var lastFrameMillis: Long = Long.MIN_VALUE

    /**
     * Opens a frame. [nowMillis] is a monotonic clock; the first frame, and any frame after a
     * gap, advances by nothing, so a renderer waking from idle does not jump a fade to its end.
     */
    fun beginFrame(nowMillis: Long) {
        deltaMillis =
            when {
                lastFrameMillis == Long.MIN_VALUE -> 0L
                else -> (nowMillis - lastFrameMillis).coerceIn(0L, MAX_STEP_MILLIS)
            }
        lastFrameMillis = nowMillis
        animating = false
        seen.clear()
    }

    private var deltaMillis: Long = 0L

    /**
     * Advances [key]'s alpha toward 1 if [visible], toward 0 if not, and returns it. A label
     * seen for the first time while invisible stays at 0 and never appears; a label seen for the
     * first time while visible fades **in** from 0, which is what makes a newly submitted scene
     * arrive rather than flash.
     *
     * Call once per candidate per frame, between [beginFrame] and [endFrame].
     */
    fun alphaFor(
        key: String,
        visible: Boolean,
    ): Float {
        seen.add(key)
        val current = alphas[key] ?: 0f
        val target = if (visible) 1f else 0f
        if (current == target) return current
        val step = if (fadeMillis <= 0L) 1f else deltaMillis.toFloat() / fadeMillis
        val next =
            if (target > current) {
                (current + step).coerceAtMost(1f)
            } else {
                (current - step).coerceAtLeast(0f)
            }
        alphas[key] = next
        if (next != target) animating = true
        return next
    }

    /**
     * Closes a frame, dropping state for labels not mentioned this time. A label that leaves the
     * frustum entirely is gone rather than fading, which is correct: it is off screen, and
     * keeping its alpha would make it fade *in* from half-way when you pan back to it.
     */
    fun endFrame() {
        alphas.keys.retainAll(seen)
    }

    /** Drops every fade, so the next frame starts from nothing (scene or context replacement). */
    fun reset() {
        alphas.clear()
        seen.clear()
        lastFrameMillis = Long.MIN_VALUE
        animating = false
    }

    companion object {
        /** Long enough to read as a fade, short enough not to lag a pinch. */
        const val DEFAULT_FADE_MILLIS = 200L

        /**
         * Largest frame step a single fade may take. `RENDERMODE_WHEN_DIRTY` means an arbitrary
         * wall-clock gap can separate two frames (a still device draws nothing); without this
         * cap the frame after an idle period would complete every fade in one jump, which is the
         * pop this class exists to remove.
         */
        const val MAX_STEP_MILLIS = 64L
    }
}
