/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Metal.MTLBufferProtocol
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLRenderCommandEncoderProtocol
import platform.Metal.MTLResourceStorageModeShared

/*
 * Kotlin arrays to Metal. Each call pins the array only for the duration of the copy: Metal
 * copies the bytes into a buffer (or, for setVertexBytes/setFragmentBytes, into the command
 * stream), so nothing keeps pointing into the Kotlin heap afterwards.
 */

/** A shared-storage buffer holding a copy of [data], or null if there is nothing to hold. */
@OptIn(ExperimentalForeignApi::class)
internal fun MTLDeviceProtocol.bufferOf(data: FloatArray): MTLBufferProtocol? =
    if (data.isEmpty()) {
        null
    } else {
        data.usePinned {
            newBufferWithBytes(
                it.addressOf(0),
                (data.size * Float.SIZE_BYTES).toULong(),
                MTLResourceStorageModeShared,
            )
        }
    }

/** A shared-storage buffer holding a copy of [data] as 32-bit indices, or null if empty. */
@OptIn(ExperimentalForeignApi::class)
internal fun MTLDeviceProtocol.bufferOf(data: IntArray): MTLBufferProtocol? =
    if (data.isEmpty()) {
        null
    } else {
        data.usePinned {
            newBufferWithBytes(
                it.addressOf(0),
                (data.size * Int.SIZE_BYTES).toULong(),
                MTLResourceStorageModeShared,
            )
        }
    }

/** Small per-draw data (uniforms), copied into the command stream at vertex-buffer [index]. */
@OptIn(ExperimentalForeignApi::class)
internal fun MTLRenderCommandEncoderProtocol.setVertexFloats(
    data: FloatArray,
    index: Int,
) {
    data.usePinned {
        setVertexBytes(it.addressOf(0), (data.size * Float.SIZE_BYTES).toULong(), index.toULong())
    }
}

/** Small per-draw data (uniforms), copied into the command stream at fragment-buffer [index]. */
@OptIn(ExperimentalForeignApi::class)
internal fun MTLRenderCommandEncoderProtocol.setFragmentFloats(
    data: FloatArray,
    index: Int,
) {
    data.usePinned {
        setFragmentBytes(it.addressOf(0), (data.size * Float.SIZE_BYTES).toULong(), index.toULong())
    }
}
