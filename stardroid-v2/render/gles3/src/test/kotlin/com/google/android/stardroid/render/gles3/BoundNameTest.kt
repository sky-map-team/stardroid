/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The rules [GlState] relies on to skip redundant binds without ever skipping a needed one.
 *
 * The deletion cases are the point. They encode a GL ES 3.0 behaviour that is easy to forget —
 * deleting a bound object silently reverts the binding to zero — combined with drivers handing
 * a freed name straight back to the next `glGen*`. Get it wrong and the symptom is a draw call
 * quietly using the wrong vertex array on some devices and not others, which is exactly the
 * kind of thing that does not show up in a side-by-side screenshot.
 */
class BoundNameTest {
    @Test
    fun `first bind of a name is needed`() {
        assertThat(BoundName().needsBind(7)).isTrue()
    }

    @Test
    fun `rebinding the same name is skipped`() {
        val bound = BoundName()
        bound.needsBind(7)
        assertThat(bound.needsBind(7)).isFalse()
    }

    @Test
    fun `binding a different name is needed`() {
        val bound = BoundName()
        bound.needsBind(7)
        assertThat(bound.needsBind(8)).isTrue()
    }

    @Test
    fun `zero starts out bound, as it is in GL`() {
        // GL's initial state is object zero bound, so an explicit bind of zero is redundant.
        assertThat(BoundName().needsBind(0)).isFalse()
    }

    @Test
    fun `deleting the bound name forces the next bind, even of the same name`() {
        // The regression this whole class exists for: a driver hands the freed name back, and
        // without forgetting it the next bind would be skipped while zero was really bound.
        val bound = BoundName()
        bound.needsBind(7)
        bound.onDeleted(7)
        assertThat(bound.needsBind(7)).isTrue()
    }

    @Test
    fun `deleting some other name leaves the binding cached`() {
        val bound = BoundName()
        bound.needsBind(7)
        bound.onDeleted(9)
        assertThat(bound.needsBind(7)).isFalse()
    }

    @Test
    fun `deleting the bound name leaves zero bound, not unknown`() {
        // GL reverts to object zero, so a following bind of zero is genuinely redundant.
        val bound = BoundName()
        bound.needsBind(7)
        bound.onDeleted(7)
        assertThat(bound.needsBind(0)).isFalse()
    }

    @Test
    fun `a batch delete containing the bound name forgets it`() {
        val bound = BoundName()
        bound.needsBind(7)
        bound.onAnyDeleted(intArrayOf(4, 7, 9))
        assertThat(bound.needsBind(7)).isTrue()
    }

    @Test
    fun `a batch delete missing the bound name leaves it cached`() {
        val bound = BoundName()
        bound.needsBind(7)
        bound.onAnyDeleted(intArrayOf(4, 9))
        assertThat(bound.needsBind(7)).isFalse()
    }

    @Test
    fun `an empty batch delete changes nothing`() {
        val bound = BoundName()
        bound.needsBind(7)
        bound.onAnyDeleted(IntArray(0))
        assertThat(bound.needsBind(7)).isFalse()
    }

    @Test
    fun `forget forces the next bind of any name, including zero`() {
        // "We don't know what's bound" is a different claim from "zero is bound" — after a
        // texture-unit switch the cache describes a unit we are no longer looking at.
        val bound = BoundName()
        bound.needsBind(0)
        bound.forget()
        assertThat(bound.needsBind(0)).isTrue()
    }

    @Test
    fun `reset restores GL's initial state`() {
        val bound = BoundName()
        bound.needsBind(7)
        bound.reset()
        assertThat(bound.needsBind(0)).isFalse()
        assertThat(bound.needsBind(7)).isTrue()
    }
}
