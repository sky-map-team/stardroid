/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

/**
 * One GL binding point's cached name, and the decision of whether a real bind call is needed.
 *
 * Split out of [GlState] so it can be tested. The decisions here are only *observable* inside
 * [GlState] as "did a `GLES30` call happen or not", which a JVM test cannot see — but as a
 * value that answers [needsBind] they are ordinary logic, and `./gradlew check` covers them
 * with no device and no mocking of Android statics. That matters more than usual for this
 * particular logic: the deletion rule below is subtle, it was got wrong twice during the GLES3
 * port, and the code that depends on it can otherwise only be checked by looking at a screen.
 *
 * Not thread-safe; like everything else in this backend it is GL-thread-only.
 */
class BoundName {
    private var current = 0

    /**
     * Records that [name] is being bound and returns whether a real GL call is needed — `false`
     * when it is already the cached binding and the call would be redundant.
     */
    fun needsBind(name: Int): Boolean {
        if (current == name) return false
        current = name
        return true
    }

    /**
     * Forgets [name] if it is the cached binding, because GL has just implicitly unbound it.
     *
     * Deleting the currently-bound vertex array or texture reverts that binding to zero
     * (GL ES 3.0 §2.10 and §3.8), and the freed name is commonly handed straight back by the
     * next `glGen*`. A cache that kept naming it would then skip the next bind of what looks
     * like "the same" name while something else — the default object — was really bound.
     * Zeroing matches exactly what GL did, rather than invalidating and forcing a redundant
     * call in the common case where the deleted object was not the live one.
     */
    fun onDeleted(name: Int) {
        if (current == name) current = 0
    }

    /** True if any of [names] is the cached binding. */
    fun onAnyDeleted(names: IntArray) {
        if (names.any { it == current }) current = 0
    }

    /**
     * Forgets the binding entirely, so the next [needsBind] always reports `true`.
     *
     * Distinct from [onDeleted]'s zeroing: this means "we no longer know what is bound", which
     * is not the same claim as "zero is bound". Used when switching active texture unit, where
     * the cache describes a different unit than the one now selected.
     */
    fun invalidate() {
        current = UNKNOWN
    }

    /** Resets to GL's own initial state: object zero bound. */
    fun reset() {
        current = 0
    }

    private companion object {
        /** No real GL name, so [needsBind] can never match it. */
        const val UNKNOWN = -1
    }
}
