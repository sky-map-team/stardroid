/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LabelFaderTest {
    private val fadeMillis = 200L

    /**
     * A contiguous frame clock, so tests exercise fading rather than [LabelFader.MAX_STEP_MILLIS]
     * (which the idle-gap test covers on its own).
     */
    private class Clock(private val fader: LabelFader) {
        private var now = 0L

        /** Runs one 50 ms frame in which [keys] are the visible labels; returns their alphas. */
        fun frame(vararg keys: Pair<String, Boolean>): Map<String, Float> {
            fader.beginFrame(now)
            now += 50L
            val result = keys.associate { (key, visible) -> key to fader.alphaFor(key, visible) }
            fader.endFrame()
            return result
        }

        fun frames(
            count: Int,
            vararg keys: Pair<String, Boolean>,
        ): Map<String, Float> {
            var last = emptyMap<String, Float>()
            repeat(count) { last = frame(*keys) }
            return last
        }
    }

    @Test
    fun `a newly visible label starts at zero and fades in`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        // The first frame has no elapsed time, so nothing has moved yet.
        assertThat(clock.frame("Vega" to true)["Vega"]).isEqualTo(0f)
        assertThat(clock.frame("Vega" to true)["Vega"]).isWithin(1e-4f).of(0.25f)
        assertThat(clock.frame("Vega" to true)["Vega"]).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `fading in reaches exactly one and stops animating`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        assertThat(clock.frames(10, "Vega" to true)["Vega"]).isEqualTo(1f)
        assertThat(fader.animating).isFalse()
    }

    @Test
    fun `fading out reaches exactly zero`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frames(10, "Vega" to true)
        assertThat(clock.frames(10, "Vega" to false)["Vega"]).isEqualTo(0f)
        assertThat(fader.animating).isFalse()
    }

    @Test
    fun `alpha is monotone while the target is unchanged`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        var previous = -1f
        repeat(8) {
            val alpha = clock.frame("Vega" to true).getValue("Vega")
            assertThat(alpha).isAtLeast(previous)
            previous = alpha
        }
    }

    @Test
    fun `a label re-entering mid-fade-out reverses instead of restarting`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frames(10, "Vega" to true)

        val partlyFaded = clock.frame("Vega" to false).getValue("Vega")
        assertThat(partlyFaded).isWithin(1e-4f).of(0.75f)

        // Back up from 0.75 — neither down from 1.0 nor restarted from 0.
        val reversed = clock.frame("Vega" to true).getValue("Vega")
        assertThat(reversed).isWithin(1e-4f).of(1f)
    }

    @Test
    fun `an outgoing and an incoming label cross-fade`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frames(10, "Vega" to true)

        val alphas = clock.frame("Vega" to false, "Deneb" to true)
        assertThat(alphas.getValue("Vega")).isWithin(1e-4f).of(0.75f)
        assertThat(alphas.getValue("Deneb")).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `animating is true only while something is in flight`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frame("Vega" to true)
        clock.frame("Vega" to true)
        assertThat(fader.animating).isTrue()

        clock.frames(10, "Vega" to true)
        assertThat(fader.animating).isFalse()
    }

    @Test
    fun `a long idle gap does not complete a fade in one jump`() {
        val fader = LabelFader(fadeMillis)
        fader.beginFrame(0L)
        fader.alphaFor("Vega", visible = true)
        fader.endFrame()

        // RENDERMODE_WHEN_DIRTY: a still device can leave minutes between frames.
        fader.beginFrame(60_000L)
        val alpha = fader.alphaFor("Vega", visible = true)
        fader.endFrame()
        assertThat(alpha).isWithin(1e-4f).of(LabelFader.MAX_STEP_MILLIS / fadeMillis.toFloat())
    }

    @Test
    fun `a label that leaves the frame is forgotten rather than left mid-fade`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frames(3, "Vega" to true)

        // Vega is off screen entirely: not a candidate, so never offered to alphaFor.
        clock.frames(3, "Deneb" to true)

        // On return it starts from nothing: one step up from 0, not the 0.75 it would be
        // sitting at had the forgotten state survived.
        assertThat(clock.frame("Vega" to true).getValue("Vega")).isWithin(1e-4f).of(0.25f)
    }

    @Test
    fun `an invisible label never appears`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        assertThat(clock.frames(5, "Vega" to false)["Vega"]).isEqualTo(0f)
        assertThat(fader.animating).isFalse()
    }

    @Test
    fun `reset drops every fade`() {
        val fader = LabelFader(fadeMillis)
        val clock = Clock(fader)
        clock.frames(10, "Vega" to true)
        fader.reset()
        assertThat(clock.frame("Vega" to true)["Vega"]).isEqualTo(0f)
    }

    @Test
    fun `a zero fade duration is an immediate switch`() {
        val fader = LabelFader(fadeMillis = 0L)
        val clock = Clock(fader)
        assertThat(clock.frames(2, "Vega" to true)["Vega"]).isEqualTo(1f)
        assertThat(clock.frame("Vega" to false)["Vega"]).isEqualTo(0f)
    }
}
