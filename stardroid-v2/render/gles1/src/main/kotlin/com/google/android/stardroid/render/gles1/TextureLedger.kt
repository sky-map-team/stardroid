/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

/**
 * The bookkeeping half of [TextureCache]: which images hold GL textures, how many bytes they
 * are estimated to cost, how many live [ImageGpuData]/[IconGpuData] instances still use each one,
 * and which entries may be evicted when the budget is exceeded.
 *
 * Texture names are plain ints, so none of this needs GL or Android — which is the point: the
 * eviction policy is the part worth testing, and [TextureCache] is untestable on the JVM.
 *
 * Not thread-safe; like the rest of the backend's caches it is GL-thread-only.
 */
internal class TextureLedger(private val byteBudget: Long) {
    /** One cached image: its two texture names, its size estimate, and its live users. */
    internal class Entry {
        /** GL texture name for the day-mode variant; 0 until uploaded. */
        var dayId = 0

        /**
         * GL texture name for the night-mode (red) variant; 0 until the first night-mode frame
         * asks for it. Users who never enter night mode never pay for it.
         */
        var nightId = 0

        /** Estimated GPU bytes for whichever variants have been uploaded. */
        var bytes = 0L

        /** Live [ImageGpuData]/[IconGpuData] holders; an entry above 0 is never evicted. */
        var refCount = 0

        /** Set once the loader returns null, so a missing asset is not re-decoded per rebuild. */
        var loadFailed = false
    }

    // Access-ordered, so iteration visits least-recently-used first and eviction is LRU.
    private val entries = LinkedHashMap<TextureKey, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    private var bytes = 0L

    val entryCount: Int get() = entries.size

    val totalBytes: Long get() = bytes

    /**
     * Returns [key]'s entry — creating an empty one if this is the first user — and counts one
     * more holder against it. Every call must be paired with a [release].
     */
    fun retain(key: TextureKey): Entry = entries.getOrPut(key) { Entry() }.also { it.refCount++ }

    /** Drops one holder of [key]. The entry stays cached, which is what makes reuse possible. */
    fun release(key: TextureKey) {
        val entry = entries[key] ?: return
        if (entry.refCount > 0) entry.refCount--
    }

    /** [key]'s entry, marked most-recently-used, or null if it was never retained (or was evicted). */
    fun peek(key: TextureKey): Entry? = entries[key]

    /** Records [delta] more bytes against [entry] and the running total. */
    fun addBytes(
        entry: Entry,
        delta: Long,
    ) {
        entry.bytes += delta
        bytes += delta
    }

    /**
     * Evicts least-recently-used entries until the budget is met, returning their GL texture names
     * for the caller to delete. Entries with live holders are skipped — a texture in use is never
     * pulled out from under a drawer, so exceeding the budget is possible and is the safe failure
     * — as is [keep], the entry the caller is in the middle of uploading.
     */
    fun evict(keep: TextureKey? = null): IntArray {
        if (bytes <= byteBudget) return IntArray(0)
        val doomed = mutableListOf<TextureKey>()
        val ids = mutableListOf<Int>()
        var freed = 0L
        for ((entryKey, entry) in entries) {
            if (bytes - freed <= byteBudget) break
            if (entry.refCount > 0 || entryKey == keep) continue
            doomed += entryKey
            if (entry.dayId != 0) ids += entry.dayId
            if (entry.nightId != 0) ids += entry.nightId
            freed += entry.bytes
        }
        doomed.forEach { entries.remove(it) }
        bytes -= freed
        return ids.toIntArray()
    }

    /**
     * Forgets every entry without returning anything to delete. For EGL context loss, where the
     * textures are already gone and the names are meaningless (G9 in render-api.md).
     */
    fun clear() {
        entries.clear()
        bytes = 0L
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
