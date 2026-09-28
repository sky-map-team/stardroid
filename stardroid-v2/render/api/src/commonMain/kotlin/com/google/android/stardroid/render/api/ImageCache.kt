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
 * A backend's resolved images — textures, in practice — held by reference count and kept under
 * a byte budget: the bookkeeping half of `:render:gles3`'s `TextureCache`, with the GPU calls
 * passed in, so every backend shares it (D117).
 *
 * A layer [retain]s each [ImageRef] it draws and [release]s them when its scene is replaced, so
 * an image shared by consecutive scenes is never dropped in between. Released images stay cached
 * for the next scene that wants them; only once the cache is over [byteBudget] are unreferenced
 * ones disposed, least recently released first. A ref that failed to [load] is remembered, so a
 * missing image costs one attempt, not one per frame (D24). Not thread-safe: the render thread's.
 *
 * @param load resolves a ref to a resource, or null if it has none.
 * @param bytes the resource's size, for the budget.
 * @param dispose frees a resource being evicted.
 */
class ImageCache<T : Any>(
    private val load: (ImageRef) -> T?,
    private val bytes: (T) -> Long,
    private val dispose: (T) -> Unit = {},
    private val byteBudget: Long = DEFAULT_BYTE_BUDGET,
) {
    private class Entry<T> {
        var resource: T? = null
        var bytes = 0L
        var refCount = 0
        var loadFailed = false

        /** Monotonic tick of the last release, for LRU ordering among unreferenced entries. */
        var lastReleasedAt = 0L
    }

    private val entries = HashMap<ImageRef, Entry<T>>()
    private var totalBytes = 0L
    private var tick = 0L

    /** Bytes currently held, referenced or not. */
    val heldBytes: Long get() = totalBytes

    /** Counts one more holder of [ref], loading it if this is the first. Pair with [release]. */
    fun retain(ref: ImageRef) {
        val entry = entries.getOrPut(ref) { Entry() }
        entry.refCount++
        if (entry.resource != null || entry.loadFailed) return
        val resource = load(ref)
        if (resource == null) {
            entry.loadFailed = true
            return
        }
        entry.resource = resource
        entry.bytes = bytes(resource)
        totalBytes += entry.bytes
        evict(keep = ref)
    }

    /** Drops one holder of [ref]; the resource stays cached for the next scene that wants it. */
    fun release(ref: ImageRef) {
        val entry = entries[ref] ?: return
        if (entry.refCount > 0) entry.refCount--
        if (entry.refCount == 0) entry.lastReleasedAt = ++tick
    }

    /** The resource for [ref], or null if it has none (an unavailable image, D24). */
    operator fun get(ref: ImageRef): T? = entries[ref]?.resource

    /** Disposes unreferenced resources, least recently released first, until within budget. */
    private fun evict(keep: ImageRef) {
        if (totalBytes <= byteBudget) return
        // Key-value pairs, not the map's own entries: reading a map entry after the map has been
        // modified throws on Kotlin/Native (the JVM happens to allow it), and this loop removes.
        val candidates =
            entries.entries
                .filter { it.value.refCount == 0 && it.value.resource != null && it.key != keep }
                .map { it.key to it.value }
                .sortedBy { (_, entry) -> entry.lastReleasedAt }
        for ((ref, entry) in candidates) {
            if (totalBytes <= byteBudget) break
            entry.resource?.let(dispose)
            totalBytes -= entry.bytes
            entries.remove(ref)
        }
    }

    companion object {
        /**
         * 16 MB, as in `:render:gles3`. What has to fit, at four bytes per texel: a 1024² Moon
         * (4 MB), a 512² Sun, Jupiter and Saturn (1 MB each), five 256² bodies, a 128² Pluto and
         * the ten deep-sky icons — about 9 MB in steady state, so nothing is evicted in normal
         * use. Revisit whenever an asset's resolution changes.
         */
        const val DEFAULT_BYTE_BUDGET = 16L * 1024 * 1024
    }
}
