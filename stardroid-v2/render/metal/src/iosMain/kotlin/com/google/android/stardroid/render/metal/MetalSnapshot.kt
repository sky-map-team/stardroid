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
import platform.Metal.MTLCommandBufferStatusCompleted
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLOriginMake
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLRenderPassDescriptor
import platform.Metal.MTLResourceStorageModeShared
import platform.Metal.MTLSizeMake
import platform.Metal.MTLStorageModePrivate
import platform.Metal.MTLStoreActionStore
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureUsageRenderTarget
import platform.posix.memcpy

/**
 * A frame read back for sharing: BGRA8, top row first, with the alpha exactly as the screen's
 * layer gets it.
 */
class MetalSnapshot(
    val width: Int,
    val height: Int,
    val bgra: ByteArray,
)

/**
 * Draws the current scene offscreen at [width] × [height] and reads it back, without the camera
 * scrim: the share compositor dims the still itself. Call on the render thread; [onDone] comes on
 * a Metal thread, with null if the GPU refused.
 */
@OptIn(ExperimentalForeignApi::class)
fun MetalSkyRenderer.snapshot(
    queue: MTLCommandQueueProtocol,
    width: Int,
    height: Int,
    onDone: (MetalSnapshot?) -> Unit,
) {
    if (width <= 0 || height <= 0) return onDone(null)
    val device = queue.device
    val descriptor =
        MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
            pixelFormat = MTLPixelFormatBGRA8Unorm,
            width = width.toULong(),
            height = height.toULong(),
            mipmapped = false,
        )
    descriptor.usage = MTLTextureUsageRenderTarget
    descriptor.storageMode = MTLStorageModePrivate
    val target = device.newTextureWithDescriptor(descriptor) ?: return onDone(null)
    val rowBytes = width * BYTES_PER_PIXEL
    val byteCount = rowBytes * height
    val readback =
        device.newBufferWithLength(byteCount.toULong(), MTLResourceStorageModeShared)
            ?: return onDone(null)
    val commands = queue.commandBuffer() ?: return onDone(null)

    val pass = MTLRenderPassDescriptor()
    val color = pass.colorAttachments.objectAtIndexedSubscript(0u)
    color.texture = target
    color.storeAction = MTLStoreActionStore
    encode(commands, pass, width, height, drawScrim = false)

    val blit = commands.blitCommandEncoder() ?: return onDone(null)
    blit.copyFromTexture(
        sourceTexture = target,
        sourceSlice = 0u,
        sourceLevel = 0u,
        sourceOrigin = MTLOriginMake(0u, 0u, 0u),
        sourceSize = MTLSizeMake(width.toULong(), height.toULong(), 1u),
        toBuffer = readback,
        destinationOffset = 0u,
        destinationBytesPerRow = rowBytes.toULong(),
        destinationBytesPerImage = byteCount.toULong(),
    )
    blit.endEncoding()
    commands.addCompletedHandler { finished ->
        val contents = readback.contents()
        if (finished?.status != MTLCommandBufferStatusCompleted || contents == null) {
            onDone(null)
        } else {
            val pixels = ByteArray(byteCount)
            pixels.usePinned { memcpy(it.addressOf(0), contents, byteCount.toULong()) }
            onDone(MetalSnapshot(width, height, pixels))
        }
    }
    commands.commit()
}

private const val BYTES_PER_PIXEL = 4
