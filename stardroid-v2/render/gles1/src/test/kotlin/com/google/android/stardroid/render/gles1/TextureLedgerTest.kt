/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.android.stardroid.render.api.ImageRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TextureLedgerTest {
    private var nextId = 1

    /** Retains [key] and gives it a texture name and [bytes], as [TextureCache.retain] would. */
    private fun TextureLedger.upload(
        key: TextureKey,
        bytes: Long,
    ): TextureLedger.Entry =
        retain(key).also {
            it.dayId = nextId++
            addBytes(it, bytes)
        }

    @Test
    fun `an unknown ref has no entry`() {
        val ledger = TextureLedger(BUDGET)
        assertThat(ledger.peek(TextureKey(ImageRef("planet/mars")))).isNull()
        assertThat(ledger.entryCount).isEqualTo(0)
    }

    @Test
    fun `retaining the same ref twice reuses the entry`() {
        val ledger = TextureLedger(BUDGET)
        val first = ledger.upload(MARS, 100)
        val second = ledger.retain(MARS)

        assertThat(second).isSameInstanceAs(first)
        assertThat(ledger.entryCount).isEqualTo(1)
        assertThat(ledger.totalBytes).isEqualTo(100)
    }

    @Test
    fun `releasing more often than retaining does not underflow`() {
        val ledger = TextureLedger(BUDGET)
        ledger.upload(MARS, 100)
        repeat(3) { ledger.release(MARS) }

        assertThat(ledger.peek(MARS)!!.refCount).isEqualTo(0)
    }

    @Test
    fun `an unreferenced entry stays cached while under budget`() {
        val ledger = TextureLedger(BUDGET)
        ledger.upload(MARS, 100)
        ledger.release(MARS)

        assertThat(ledger.evict()).isEmpty()
        assertThat(ledger.peek(MARS)).isNotNull()
    }

    @Test
    fun `eviction returns the texture names of both variants`() {
        val ledger = TextureLedger(byteBudget = 10)
        val entry = ledger.upload(MARS, 100)
        entry.nightId = 99
        ledger.release(MARS)

        assertThat(ledger.evict().toList()).containsExactly(entry.dayId, 99)
        assertThat(ledger.peek(MARS)).isNull()
        assertThat(ledger.totalBytes).isEqualTo(0)
    }

    @Test
    fun `eviction never touches a referenced entry`() {
        val ledger = TextureLedger(byteBudget = 10)
        ledger.upload(MARS, 100)

        assertThat(ledger.evict()).isEmpty()
        assertThat(ledger.peek(MARS)).isNotNull()
        assertThat(ledger.totalBytes).isEqualTo(100)
    }

    @Test
    fun `eviction spares the ref being uploaded`() {
        val ledger = TextureLedger(byteBudget = 10)
        ledger.upload(MARS, 100)
        ledger.release(MARS)

        assertThat(ledger.evict(keep = MARS)).isEmpty()
        assertThat(ledger.peek(MARS)).isNotNull()
    }

    @Test
    fun `eviction drops least-recently-used entries first`() {
        // Budget fits two of the three 100-byte entries.
        val ledger = TextureLedger(byteBudget = 250)
        listOf(MARS, MOON, SUN).forEach {
            ledger.upload(it, 100)
            ledger.release(it)
        }
        // Touch Mars, making the Moon the least recently used.
        ledger.peek(MARS)

        ledger.evict()

        assertThat(ledger.peek(MOON)).isNull()
        assertThat(ledger.peek(MARS)).isNotNull()
        assertThat(ledger.peek(SUN)).isNotNull()
        assertThat(ledger.totalBytes).isEqualTo(200)
    }

    @Test
    fun `eviction stops as soon as the budget is met`() {
        val ledger = TextureLedger(byteBudget = 250)
        listOf(MARS, MOON, SUN, JUPITER).forEach {
            ledger.upload(it, 100)
            ledger.release(it)
        }

        assertThat(ledger.evict()).hasLength(2)
        assertThat(ledger.entryCount).isEqualTo(2)
        assertThat(ledger.totalBytes).isEqualTo(200)
    }

    @Test
    fun `a referenced entry over budget blocks eviction rather than being dropped`() {
        // The Moon alone busts the budget but is in use; the rest is freed and it stays.
        val ledger = TextureLedger(byteBudget = 250)
        ledger.upload(MOON, 400)
        ledger.upload(MARS, 100)
        ledger.release(MARS)

        ledger.evict()

        assertThat(ledger.peek(MOON)).isNotNull()
        assertThat(ledger.peek(MARS)).isNull()
        assertThat(ledger.totalBytes).isEqualTo(400)
    }

    @Test
    fun `clear forgets every entry without reporting textures to delete`() {
        val ledger = TextureLedger(BUDGET)
        ledger.upload(MARS, 100)
        ledger.upload(MOON, 100)

        ledger.clear()

        assertThat(ledger.entryCount).isEqualTo(0)
        assertThat(ledger.totalBytes).isEqualTo(0)
        assertThat(ledger.peek(MARS)).isNull()
    }

    private companion object {
        const val BUDGET = 1L shl 20
        val MARS = TextureKey(ImageRef("planet/mars"))
        val MOON = TextureKey(ImageRef("planet/moon"))
        val SUN = TextureKey(ImageRef("planet/sun"))
        val JUPITER = TextureKey(ImageRef("planet/jupiter"))
    }
}
