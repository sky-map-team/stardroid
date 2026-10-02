/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.testing.assertThat
import kotlin.test.Test

class ImageCacheTest {
    private val loads = mutableListOf<String>()
    private val disposed = mutableListOf<String>()

    /** Loads every ref except "missing"; each resource is its key, sized 10 bytes. */
    private fun cache(budget: Long = 100) =
        ImageCache(::load, bytes = { 10L }, dispose = { disposed += it }, byteBudget = budget)

    private fun load(ref: ImageRef): String? {
        loads += ref.key
        return ref.key.takeIf { it != "missing" }
    }

    @Test
    fun loadsOnFirstRetainOnly() {
        val cache = cache()
        cache.retain(ImageRef("a"))
        cache.retain(ImageRef("a"))
        assertThat(loads).containsExactly("a")
        assertThat(cache[ImageRef("a")]).isEqualTo("a")
        assertThat(cache.heldBytes).isEqualTo(10L)
    }

    @Test
    fun aMissingImageIsTriedOnce() {
        val cache = cache()
        cache.retain(ImageRef("missing"))
        cache.retain(ImageRef("missing"))
        assertThat(loads).containsExactly("missing")
        assertThat(cache[ImageRef("missing")]).isNull()
    }

    @Test
    fun releasedImagesStayCachedWithinBudget() {
        val cache = cache()
        cache.retain(ImageRef("a"))
        cache.release(ImageRef("a"))
        cache.retain(ImageRef("a"))
        assertThat(loads).containsExactly("a")
        assertThat(disposed).isEmpty()
    }

    @Test
    fun overBudgetEvictsUnreferencedLeastRecentlyReleasedFirst() {
        val cache = cache(budget = 20)
        cache.retain(ImageRef("a"))
        cache.retain(ImageRef("b"))
        cache.release(ImageRef("b"))
        cache.release(ImageRef("a"))
        cache.retain(ImageRef("held"))
        // Over budget (30 > 20): b was released before a, so b goes.
        assertThat(disposed).containsExactly("b")
        assertThat(cache[ImageRef("a")]).isEqualTo("a")
        assertThat(cache.heldBytes).isEqualTo(20L)
    }

    @Test
    fun referencedImagesAreNeverEvicted() {
        val cache = cache(budget = 10)
        cache.retain(ImageRef("a"))
        cache.retain(ImageRef("b"))
        // Both are referenced, so the cache runs over budget rather than drop one in use.
        assertThat(disposed).isEmpty()
        assertThat(cache.heldBytes).isEqualTo(20L)
    }
}
