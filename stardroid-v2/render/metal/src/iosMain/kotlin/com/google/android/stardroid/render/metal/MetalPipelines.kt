/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Metal.MTLBlendFactorOne
import platform.Metal.MTLBlendFactorOneMinusSourceAlpha
import platform.Metal.MTLBlendFactorSourceAlpha
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLLibraryProtocol
import platform.Metal.MTLPixelFormat
import platform.Metal.MTLRenderPipelineDescriptor
import platform.Metal.MTLRenderPipelineStateProtocol

/**
 * The shader library, compiled at runtime from [MetalShaderSource], and one pipeline state per
 * program. Compiling fails loudly: a pipeline that silently failed to build is a black sky with
 * no log, the field failure GLES3's shader-compilation gate exists to prevent.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class MetalPipelines(
    device: MTLDeviceProtocol,
    pixelFormat: MTLPixelFormat,
) {
    /** The blend modes :render:gles3 uses, with the same factors for colour and alpha. */
    private enum class Blend { NONE, ALPHA, ADDITIVE }

    private val library: MTLLibraryProtocol =
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            device.newLibraryWithSource(MetalShaderSource.SOURCE, null, error.ptr)
                ?: error("Metal shader library failed to compile: ${error.value?.description}")
        }

    val point = pipeline(device, pixelFormat, "point", Blend.ALPHA)
    val line = pipeline(device, pixelFormat, "line", Blend.ALPHA)

    /** Additive, so the glow adds light to whatever is behind it instead of blending toward it. */
    val glow = pipeline(device, pixelFormat, "glow", Blend.ADDITIVE)

    /** The backdrop: it writes every pixel it covers, so it needs no blending. */
    val sky = pipeline(device, pixelFormat, "sky", Blend.NONE)
    val scrim = pipeline(device, pixelFormat, "scrim", Blend.ALPHA)

    private fun pipeline(
        device: MTLDeviceProtocol,
        pixelFormat: MTLPixelFormat,
        program: String,
        blend: Blend,
    ): MTLRenderPipelineStateProtocol {
        val descriptor = MTLRenderPipelineDescriptor()
        descriptor.label = program
        descriptor.vertexFunction =
            library.newFunctionWithName("${program}_vertex")
                ?: error("Metal shader library has no ${program}_vertex")
        descriptor.fragmentFunction =
            library.newFunctionWithName("${program}_fragment")
                ?: error("Metal shader library has no ${program}_fragment")
        val color = descriptor.colorAttachments.objectAtIndexedSubscript(0u)
        color.pixelFormat = pixelFormat
        if (blend != Blend.NONE) {
            val destination =
                when (blend) {
                    Blend.ADDITIVE -> MTLBlendFactorOne
                    else -> MTLBlendFactorOneMinusSourceAlpha
                }
            color.blendingEnabled = true
            color.sourceRGBBlendFactor = MTLBlendFactorSourceAlpha
            color.sourceAlphaBlendFactor = MTLBlendFactorSourceAlpha
            color.destinationRGBBlendFactor = destination
            color.destinationAlphaBlendFactor = destination
        }
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            device.newRenderPipelineStateWithDescriptor(descriptor, error.ptr)
                ?: error("Metal pipeline '$program' failed to build: ${error.value?.description}")
        }
    }
}
