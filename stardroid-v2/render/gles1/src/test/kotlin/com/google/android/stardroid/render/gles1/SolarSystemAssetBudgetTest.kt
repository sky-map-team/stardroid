/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * [TextureCache.DEFAULT_BYTE_BUDGET] against the assets actually shipped.
 *
 * The budget's doc comment used to enumerate the solar-system set — "a 1024² Moon, a 512² Sun,
 * seven 256² planets" — and nothing checked it. Raising Jupiter and Saturn to 512² took the real
 * total from 18.2 MB to 23.1 MB against a 24 MB budget, which nothing would have reported: the
 * cache does not fail when it cannot hold its working set, it just evicts and re-uploads, so the
 * symptom would have been the Moon re-compositing every phase step on a hot GL thread.
 *
 * So this reads the shipped files rather than a list written down here — a list would go stale in
 * exactly the same way the doc comment did.
 */
class SolarSystemAssetBudgetTest {
    @Test
    fun `every disc is a power of two, as mipmapping requires`() {
        for ((file, size) in discs()) {
            assertThat(size.width).isEqualTo(size.height)
            assertThat(size.width and (size.width - 1)).isEqualTo(0)
            assertThat(file.name).isNotEmpty()
        }
    }

    @Test
    fun `the shipped set fits the budget in both variants, with room for phase churn`() {
        val discs = discs()
        // Both variants of everything, plus the catalog icons.
        val steadyState = 2 * (discs.values.sumOf { textureBytes(it) } + iconBytes())

        assertThat(steadyState).isLessThan(TextureCache.DEFAULT_BYTE_BUDGET)

        // A composited body keeps its previous phase's entry until eviction reclaims it, and the
        // Moon's is the biggest single entry in the cache. If the budget cannot absorb one stale
        // copy of it, every phase step evicts something that is about to be wanted again.
        val staleMoon = 2 * textureBytes(discs.entries.single { it.key.name == "moon.webp" }.value)
        assertThat(steadyState + staleMoon).isLessThan(TextureCache.DEFAULT_BYTE_BUDGET)
    }

    private data class Size(val width: Int, val height: Int)

    /** 4 bytes per texel, plus a third again for the mipmap chain — as [textureByteSize] does. */
    private fun textureBytes(size: Size): Long =
        size.width.toLong() * size.height.toLong() * 4L * 4L / 3L

    /**
     * The deep-sky icons, which share the cache. They are 80² and 128², and the 80² ones are not
     * powers of two, so they carry no mipmap chain.
     */
    private fun iconBytes(): Long =
        assets("catalog/icons").sumOf { file ->
            val size = webpSize(file)
            val base = size.width.toLong() * size.height.toLong() * 4L
            if (size.width and (size.width - 1) == 0) base * 4L / 3L else base
        }

    private fun discs(): Map<File, Size> = assets("planets").associateWith(::webpSize)

    private fun assets(dir: String): List<File> {
        // Gradle runs unit tests with the module directory as the working directory.
        val root = File("../../app/src/main/assets/$dir")
        check(root.isDirectory) { "no asset directory at ${root.absolutePath}" }
        return root.listFiles()!!.filter { it.extension == "webp" }.sorted()
    }

    /**
     * A WebP's canvas size, straight out of the header. The discs are `VP8X` (the extended form,
     * carrying a separate lossless alpha chunk) and the icons are `VP8L` (lossless throughout),
     * which store it differently.
     */
    private fun webpSize(file: File): Size {
        val head = file.inputStream().use { it.readNBytes(30) }

        fun byteAt(at: Int) = head[at].toInt() and 0xFF
        return when (val fourCc = String(head, 12, 4, Charsets.US_ASCII)) {
            // Two 24-bit little-endian width-1/height-1 fields.
            "VP8X" -> {
                fun int24(at: Int) = byteAt(at) or (byteAt(at + 1) shl 8) or (byteAt(at + 2) shl 16)
                Size(int24(24) + 1, int24(27) + 1)
            }
            // Two 14-bit width-1/height-1 fields packed into one little-endian word.
            "VP8L" -> {
                val packed =
                    byteAt(21) or (byteAt(22) shl 8) or (byteAt(23) shl 16) or (byteAt(24) shl 24)
                Size((packed and 0x3FFF) + 1, ((packed shr 14) and 0x3FFF) + 1)
            }
            else -> error("${file.name} is $fourCc, which this test cannot measure")
        }
    }
}
