/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.testing.environmentVariable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.writeToFile
import platform.Metal.MTLCommandBufferStatusCompleted
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLOriginMake
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLRenderPassDescriptor
import platform.Metal.MTLResourceStorageModeShared
import platform.Metal.MTLSizeMake
import platform.Metal.MTLStorageModePrivate
import platform.Metal.MTLStoreActionStore
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureUsageRenderTarget
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/** The system GPU, or a clear failure — every render test needs one. */
internal fun metalDevice(): MTLDeviceProtocol =
    MTLCreateSystemDefaultDevice() ?: error("no Metal device (is this a simulator without GPU?)")

/** One rendered frame read back to the CPU: BGRA8, top row first. */
internal class Frame(
    val width: Int,
    val height: Int,
    private val bgra: ByteArray,
) {
    fun red(
        x: Int,
        y: Int,
    ): Int = channel(x, y, 2)

    fun green(
        x: Int,
        y: Int,
    ): Int = channel(x, y, 1)

    fun blue(
        x: Int,
        y: Int,
    ): Int = channel(x, y, 0)

    private fun channel(
        x: Int,
        y: Int,
        offset: Int,
    ): Int = bgra[(y * width + x) * 4 + offset].toInt() and 0xFF

    /** Pixels for which [predicate] holds. */
    fun count(predicate: (r: Int, g: Int, b: Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until height) {
            for (x in 0 until width) if (predicate(red(x, y), green(x, y), blue(x, y))) n++
        }
        return n
    }

    /**
     * Writes the frame as `<name>.png` into the directory Gradle passes in `SKYMAP_RENDER_OUT`
     * (build/reports/metal-renders), so a human can look at what the assertions only sample.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun savePng(name: String) {
        val dir = environmentVariable("SKYMAP_RENDER_OUT") ?: return
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        bgra.usePinned { pinned ->
            val context =
                CGBitmapContextCreate(
                    pinned.addressOf(0),
                    width.toULong(),
                    height.toULong(),
                    8u,
                    (width * 4).toULong(),
                    colorSpace,
                    CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst.value or kCGBitmapByteOrder32Little,
                )
            val image = CGBitmapContextCreateImage(context)
            UIImagePNGRepresentation(UIImage.imageWithCGImage(image))
                ?.writeToFile("$dir/$name.png", atomically = true)
            CGImageRelease(image)
            CGContextRelease(context)
        }
        CGColorSpaceRelease(colorSpace)
    }
}

/**
 * Renders one frame of [renderer] into an offscreen [width] × [height] texture and reads it back.
 *
 * The texture is private storage — the simulator does not allow CPU-visible textures — so the
 * pixels come back through a blit into a shared buffer, in the same command buffer.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun renderOffscreen(
    device: MTLDeviceProtocol,
    renderer: MetalSkyRenderer,
    width: Int,
    height: Int,
): Frame {
    val descriptor =
        MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
            MTLPixelFormatBGRA8Unorm,
            width.toULong(),
            height.toULong(),
            false,
        )
    descriptor.usage = MTLTextureUsageRenderTarget
    descriptor.storageMode = MTLStorageModePrivate
    val texture = device.newTextureWithDescriptor(descriptor) ?: error("no render texture")
    val bytesPerRow = width * 4
    val readback =
        device.newBufferWithLength((bytesPerRow * height).toULong(), MTLResourceStorageModeShared)
            ?: error("no readback buffer")

    val queue = device.newCommandQueue() ?: error("no command queue")
    val commands = queue.commandBuffer() ?: error("no command buffer")
    val pass = MTLRenderPassDescriptor()
    val color = pass.colorAttachments.objectAtIndexedSubscript(0u)
    color.texture = texture
    color.storeAction = MTLStoreActionStore
    renderer.encode(commands, pass, width, height)

    val blit = commands.blitCommandEncoder() ?: error("no blit encoder")
    blit.copyFromTexture(
        sourceTexture = texture,
        sourceSlice = 0u,
        sourceLevel = 0u,
        sourceOrigin = MTLOriginMake(0u, 0u, 0u),
        sourceSize = MTLSizeMake(width.toULong(), height.toULong(), 1u),
        toBuffer = readback,
        destinationOffset = 0u,
        destinationBytesPerRow = bytesPerRow.toULong(),
        destinationBytesPerImage = (bytesPerRow * height).toULong(),
    )
    blit.endEncoding()
    commands.commit()
    commands.waitUntilCompleted()
    check(commands.status == MTLCommandBufferStatusCompleted) {
        "GPU frame failed: ${commands.error?.description}"
    }

    val bytes = ByteArray(bytesPerRow * height)
    bytes.usePinned {
        memcpy(it.addressOf(0), readback.contents()?.reinterpret<ByteVar>(), bytes.size.toULong())
    }
    return Frame(width, height, bytes)
}
