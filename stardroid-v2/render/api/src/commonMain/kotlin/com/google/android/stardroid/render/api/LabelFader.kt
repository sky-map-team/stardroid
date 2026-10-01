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
    /**
     * One label's fade, mutable in place.
     *
     * A class rather than a `Float` in the map because `HashMap<String, Float>` boxes on every
     * put — and this is written once per candidate label per frame, inside the draw loop. The
     * first version did exactly that, alongside a `HashSet` rebuilt each frame to track which
     * labels were still on screen, and the result was tens of thousands of short-lived
     * allocations a second while panning. That is GC pressure in the render path, which shows
     * up as frame-time spikes rather than a lower average — the artifact the D19 gate exists to
     * catch, and the same defect audit-2026-08 M2 rewrote the declutterer to remove.
     *
     * [lastSeenFrame] replaces that set: a label still on screen is one whose entry was touched
     * this frame, so presence is a field compare instead of a second hash structure.
     */
    private class Entry(
        var alpha: Float,
        var lastSeenFrame: Long,
    )

    private val entries = HashMap<String, Entry>()

    /** Monotonic frame counter, used to tell "touched this frame" from "gone". */
    private var frameId = 0L

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
        frameId++
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
        // Allocates only the first time a label is ever seen, never per frame.
        var entry = entries[key]
        if (entry == null) {
            entry = Entry(alpha = 0f, lastSeenFrame = frameId)
            entries[key] = entry
        }
        entry.lastSeenFrame = frameId
        val current = entry.alpha
        val target = if (visible) 1f else 0f
        if (current == target) return current
        val step = if (fadeMillis <= 0L) 1f else deltaMillis.toFloat() / fadeMillis
        val next =
            if (target > current) {
                (current + step).coerceAtMost(1f)
            } else {
                (current - step).coerceAtLeast(0f)
            }
        entry.alpha = next
        if (next != target) animating = true
        return next
    }

    /**
     * Closes a frame, dropping state for labels not mentioned this time. A label that leaves the
     * frustum entirely is gone rather than fading, which is correct: it is off screen, and
     * keeping its alpha would make it fade *in* from half-way when you pan back to it.
     */
    fun endFrame() {
        if (entries.isEmpty()) return
        // One iterator per frame per layer, against hundreds of allocations for the set this
        // replaced. Removal has to happen: a label that has left the frustum must be forgotten,
        // or it would fade in from half-lit when panned back to.
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().lastSeenFrame != frameId) iterator.remove()
        }
    }

    /**
     * How many labels the fader is currently tracking.
     *
     * Exposed for tests: steady-state growth here is what a per-frame allocation in this class
     * looks like from the outside, and a leak would mean a long pan accumulating an entry for
     * every label ever seen.
     */
    fun trackedLabelCount(): Int = entries.size

    /** Drops every fade, so the next frame starts from nothing (scene or context replacement). */
    fun reset() {
        entries.clear()
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
