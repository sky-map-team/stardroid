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
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Metal.MTLBlitCommandEncoderProtocol
import platform.Metal.MTLBufferProtocol
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLOriginMake
import platform.Metal.MTLPixelFormatR8Unorm
import platform.Metal.MTLPixelFormatRGBA8Unorm
import platform.Metal.MTLResourceStorageModeShared
import platform.Metal.MTLSizeMake
import platform.Metal.MTLStorageModePrivate
import platform.Metal.MTLTextureDescriptor
import platform.Metal.MTLTextureProtocol
import platform.Metal.MTLTextureUsageShaderRead
import platform.UIKit.UIImage

/**
 * Turns images into GPU textures. Each upload happens in two halves: [stage] decodes the image
 * into a CPU-visible buffer now, and [flush] encodes the copies into the texture on the next
 * frame's command buffer, ahead of the render pass that samples it — so uploading needs no
 * command queue of its own, and a texture is never drawn before its pixels arrive.
 *
 * Textures are private storage (the simulator allows nothing else for textures). Images are
 * premultiplied RGBA — what CoreGraphics decodes to, and what Android's `GLUtils.texImage2D`
 * uploads for `:render:gles3`, so the two blend identically at a disc's anti-aliased edge. Label
 * atlas pages are single-channel coverage ([stageCoverage]), as GLES3's `R8` pages are.
 */
@OptIn(ExperimentalForeignApi::class)
internal class MetalTextures(
    private val device: MTLDeviceProtocol,
) {
    private class Pending(
        val staging: MTLBufferProtocol,
        val texture: MTLTextureProtocol,
        val width: Int,
        val height: Int,
        val bytesPerPixel: Int,
    )

    private val pending = ArrayList<Pending>()

    /** A texture that will hold [image] once [flush] runs, or null if it cannot be decoded. */
    fun stage(image: UIImage): MTLTextureProtocol? {
        val cgImage = image.CGImage ?: return null
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        if (width == 0 || height == 0) return null
        val bytesPerRow = width * 4
        val staging =
            device.newBufferWithLength(
                (bytesPerRow * height).toULong(),
                MTLResourceStorageModeShared,
            ) ?: return null

        // Decode straight into the staging buffer: RGBA byte order, premultiplied, top row first.
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context =
            CGBitmapContextCreate(
                staging.contents(),
                width.toULong(),
                height.toULong(),
                8u,
                bytesPerRow.toULong(),
                colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
            )
        CGColorSpaceRelease(colorSpace)
        if (context == null) return null
        CGContextDrawImage(
            context,
            CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
            cgImage,
        )
        CGContextRelease(context)

        val descriptor =
            MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
                MTLPixelFormatRGBA8Unorm,
                width.toULong(),
                height.toULong(),
                false,
            )
        descriptor.usage = MTLTextureUsageShaderRead
        descriptor.storageMode = MTLStorageModePrivate
        val texture = device.newTextureWithDescriptor(descriptor) ?: return null
        pending += Pending(staging, texture, width, height, bytesPerPixel = 4)
        return texture
    }

    /**
     * A single-channel texture that will hold [coverage] — one byte per pixel, top row first —
     * once [flush] runs, or null if it cannot be allocated.
     */
    fun stageCoverage(
        coverage: ByteArray,
        width: Int,
        height: Int,
    ): MTLTextureProtocol? {
        if (width == 0 || height == 0) return null
        val staging =
            coverage.usePinned {
                device.newBufferWithBytes(
                    it.addressOf(0),
                    coverage.size.toULong(),
                    MTLResourceStorageModeShared,
                )
            } ?: return null
        val descriptor =
            MTLTextureDescriptor.texture2DDescriptorWithPixelFormat(
                MTLPixelFormatR8Unorm,
                width.toULong(),
                height.toULong(),
                false,
            )
        descriptor.usage = MTLTextureUsageShaderRead
        descriptor.storageMode = MTLStorageModePrivate
        val texture = device.newTextureWithDescriptor(descriptor) ?: return null
        pending += Pending(staging, texture, width, height, bytesPerPixel = 1)
        return texture
    }

    /** Encodes every staged upload into [blit]; call once per frame, before rendering. */
    fun flush(blit: MTLBlitCommandEncoderProtocol) {
        for (upload in pending) {
            val bytesPerRow = upload.width * upload.bytesPerPixel
            blit.copyFromBuffer(
                sourceBuffer = upload.staging,
                sourceOffset = 0u,
                sourceBytesPerRow = bytesPerRow.toULong(),
                sourceBytesPerImage = (bytesPerRow * upload.height).toULong(),
                sourceSize = MTLSizeMake(upload.width.toULong(), upload.height.toULong(), 1u),
                toTexture = upload.texture,
                destinationSlice = 0u,
                destinationLevel = 0u,
                destinationOrigin = MTLOriginMake(0u, 0u, 0u),
            )
        }
        pending.clear()
    }

    val hasPending: Boolean get() = pending.isNotEmpty()
}

/** A texture's size as the image cache budgets it: four bytes per texel. */
internal val MTLTextureProtocol.byteSize: Long
    get() = width.toLong() * height.toLong() * 4L
