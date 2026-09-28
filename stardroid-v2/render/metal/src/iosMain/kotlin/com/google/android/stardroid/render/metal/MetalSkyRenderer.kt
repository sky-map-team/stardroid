/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.render.api.GlowMesh
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LineStrips
import com.google.android.stardroid.render.api.PointVertices
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyGradient
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.api.Viewport
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Metal.MTLBufferProtocol
import platform.Metal.MTLClearColorMake
import platform.Metal.MTLCommandBufferProtocol
import platform.Metal.MTLDeviceProtocol
import platform.Metal.MTLIndexTypeUInt32
import platform.Metal.MTLLoadActionClear
import platform.Metal.MTLPixelFormat
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.Metal.MTLPrimitiveTypePoint
import platform.Metal.MTLPrimitiveTypeTriangle
import platform.Metal.MTLPrimitiveTypeTriangleStrip
import platform.Metal.MTLRenderCommandEncoderProtocol
import platform.Metal.MTLRenderPassDescriptor
import platform.Metal.MTLRenderPipelineStateProtocol
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min
import kotlin.math.tan

/**
 * The Metal [SkyRenderer] backend (D117): Kotlin/Native calling Metal, drawing what
 * `:render:gles3` draws with the same contract, thread model and draw order.
 *
 * - **Painter's algorithm, no depth buffer** (D18). Layers by [LayerScene.depth]; within a layer,
 *   glows → lines → points (images, icons and labels arrive in later slices).
 * - **Retained scenes, derived GPU data.** Producers publish immutable [LayerScene]s from any
 *   thread; [encode] builds a layer's buffers from the shared `:render:api` builders the first
 *   time it sees that scene instance, and reuses them until the layer is resubmitted.
 * - **Everything per-frame is a uniform**: camera, magnitude limit, night mode.
 *
 * The host owns the drawable and the frame loop: an `MTKView` delegate calls [encode] with the
 * view's pass descriptor, and the offscreen tests call it with their own texture. [encode] must
 * be called from one thread at a time — it is the render thread's; the publishing methods may be
 * called from anywhere.
 *
 * @param density the display density (dp → px) of the surface being drawn.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class MetalSkyRenderer(
    private val device: MTLDeviceProtocol,
    private val density: Float,
    pixelFormat: MTLPixelFormat = MTLPixelFormatBGRA8Unorm,
) : SkyRenderer {
    @Volatile private var camera: SkyCamera? = null

    @Volatile private var renderState: RenderState = RenderState()

    /** Replaced wholesale on every submit, so the render thread always reads a consistent set. */
    private val scenes = AtomicReference(emptyMap<LayerId, LayerScene>())

    // ---- render-thread-only state -----------------------------------------------------------

    private val pipelines = MetalPipelines(device, pixelFormat)
    private val layers = HashMap<LayerId, LayerGpu>()
    private var drawOrderSource: Map<LayerId, LayerScene>? = null
    private var drawOrder: List<Pair<LayerId, LayerScene>> = emptyList()

    /** One layer's buffers, valid for exactly the [scene] instance they were built from. */
    private class LayerGpu(
        val scene: LayerScene,
        val points: Mesh?,
        val lines: Mesh?,
        val glows: Mesh?,
    )

    /** Vertex data plus, for indexed geometry, its 32-bit index buffer. */
    private class Mesh(
        val vertices: MTLBufferProtocol,
        val indices: MTLBufferProtocol?,
        val count: Int,
    )

    // ---- SkyRenderer ------------------------------------------------------------------------

    override fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    ) {
        while (true) {
            val current = scenes.load()
            val next = if (scene == null) current - layerId else current + (layerId to scene)
            if (scenes.compareAndSet(current, next)) return
        }
    }

    override fun setCamera(camera: SkyCamera) {
        this.camera = camera
    }

    override fun setRenderState(state: RenderState) {
        renderState = state
    }

    // ---- frame ------------------------------------------------------------------------------

    /**
     * Encodes one frame into [commandBuffer], drawing into [pass]'s first colour attachment,
     * which is [widthPx] × [heightPx]. Clears it first — to transparent when the render state
     * asks for a see-through background (D64), otherwise to opaque black.
     */
    fun encode(
        commandBuffer: MTLCommandBufferProtocol,
        pass: MTLRenderPassDescriptor,
        widthPx: Int,
        heightPx: Int,
    ) {
        val state = renderState
        val color = pass.colorAttachments.objectAtIndexedSubscript(0u)
        color.loadAction = MTLLoadActionClear
        val clearAlpha = if (state.transparentBackground) 0.0 else 1.0
        color.clearColor = MTLClearColorMake(0.0, 0.0, 0.0, clearAlpha)
        val encoder = commandBuffer.renderCommandEncoderWithDescriptor(pass) ?: return
        try {
            val camera = camera
            if (camera != null && widthPx > 0 && heightPx > 0) {
                draw(encoder, camera, state, Viewport(widthPx, heightPx, density))
            }
        } finally {
            encoder.endEncoding()
        }
    }

    private fun draw(
        encoder: MTLRenderCommandEncoderProtocol,
        camera: SkyCamera,
        state: RenderState,
        viewport: Viewport,
    ) {
        if (state.transparentBackground && state.cameraScrim > 0.0) {
            encoder.setRenderPipelineState(pipelines.scrim)
            encoder.setFragmentFloats(floatArrayOf(state.cameraScrim.toFloat(), 0f, 0f, 0f), 0)
            encoder.drawPrimitives(MTLPrimitiveTypeTriangleStrip, 0u, 4u)
        }

        // The sky behind everything, skipped in night mode and while the background is
        // transparent, where an opaque sky would wall off the camera — as in GLES3.
        val gradient = state.skyGradient
        if (gradient != null && !state.nightMode && !state.transparentBackground) {
            encoder.setRenderPipelineState(pipelines.sky)
            encoder.setFragmentFloats(skyUniforms(gradient, camera, viewport), 0)
            encoder.drawPrimitives(MTLPrimitiveTypeTriangleStrip, 0u, 4u)
        }

        val frame = frameUniforms(camera, state, viewport)
        for ((layerId, scene) in refreshDrawOrder()) {
            val gpu = layerGpu(layerId, scene)
            gpu.glows?.let { drawIndexed(encoder, pipelines.glow, it, frame) }
            gpu.lines?.let { drawIndexed(encoder, pipelines.line, it, frame) }
            gpu.points?.let { mesh ->
                encoder.setRenderPipelineState(pipelines.point)
                encoder.setVertexBuffer(mesh.vertices, 0u, 0u)
                encoder.setVertexFloats(frame, 1)
                encoder.drawPrimitives(MTLPrimitiveTypePoint, 0u, mesh.count.toULong())
            }
        }
    }

    private fun drawIndexed(
        encoder: MTLRenderCommandEncoderProtocol,
        pipeline: MTLRenderPipelineStateProtocol,
        mesh: Mesh,
        frame: FloatArray,
    ) {
        val indices = mesh.indices ?: return
        encoder.setRenderPipelineState(pipeline)
        encoder.setVertexBuffer(mesh.vertices, 0u, 0u)
        encoder.setVertexFloats(frame, 1)
        encoder.drawIndexedPrimitives(
            MTLPrimitiveTypeTriangle,
            mesh.count.toULong(),
            MTLIndexTypeUInt32,
            indices,
            0u,
        )
    }

    /** Re-sorts layers when the published set changed, and drops removed layers' buffers. */
    private fun refreshDrawOrder(): List<Pair<LayerId, LayerScene>> {
        val current = scenes.load()
        if (current !== drawOrderSource) {
            layers.keys.retainAll(current.keys)
            drawOrder = current.entries.sortedBy { it.value.depth }.map { it.key to it.value }
            drawOrderSource = current
        }
        return drawOrder
    }

    private fun layerGpu(
        layerId: LayerId,
        scene: LayerScene,
    ): LayerGpu {
        layers[layerId]?.let { if (it.scene === scene) return it }
        // Replacing the old buffers is safe mid-flight: a command buffer retains every buffer
        // it references until the GPU is done with it.
        val points = PointVertices.build(scene.points)
        val lines = LineStrips.build(scene.lines)
        val glows = GlowMesh.build(scene.glows)
        val gpu =
            LayerGpu(
                scene = scene,
                points =
                    device.bufferOf(points.data)?.let { Mesh(it, null, points.vertexCount) },
                lines = indexedMesh(lines.vertices, lines.indices),
                glows = indexedMesh(glows.vertices, glows.indices),
            )
        layers[layerId] = gpu
        return gpu
    }

    private fun indexedMesh(
        vertices: FloatArray,
        indices: IntArray,
    ): Mesh? {
        val vertexBuffer = device.bufferOf(vertices) ?: return null
        val indexBuffer = device.bufferOf(indices) ?: return null
        return Mesh(vertexBuffer, indexBuffer, indices.size)
    }

    // ---- uniforms (layouts match common.metal / sky.metal) ----------------------------------

    private fun frameUniforms(
        camera: SkyCamera,
        state: RenderState,
        viewport: Viewport,
    ): FloatArray {
        val out = FloatArray(FRAME_UNIFORM_FLOATS)
        SkyProjection(camera, viewport).viewProjection.toFloatArray().copyInto(out)
        out[16] = viewport.widthPx.toFloat()
        out[17] = viewport.heightPx.toFloat()
        out[18] = viewport.density
        out[19] = if (state.nightMode) 1f else 0f
        out[20] = state.magnitudeLimit?.toFloat() ?: PointVertices.NO_MAGNITUDE_LIMIT
        return out
    }

    /** The per-pixel sky's inputs, built exactly as GLES3's SkyGradientDrawer builds them. */
    private fun skyUniforms(
        gradient: SkyGradient,
        camera: SkyCamera,
        viewport: Viewport,
    ): FloatArray {
        // Mirrors Matrix4.view: right = forward × up, then up re-orthogonalized as right × forward.
        val forward = camera.lineOfSight.normalized()
        val right = (forward cross camera.up).normalized()
        val up = right cross forward
        val sun = gradient.sunDirection.normalized()
        val zenith = gradient.zenithDirection.normalized()
        // fovDeg spans the short side (Matrix4.perspective), so the long side's tangent scales up.
        val tanHalfFov = tan(camera.fovDeg * DEGREES_TO_RADIANS * 0.5)
        val shortSide = min(viewport.widthPx, viewport.heightPx).toDouble()
        val seed = (NSProcessInfo.processInfo.systemUptime * 1000.0) % DITHER_SEED_PERIOD_MS
        return floatArrayOf(
            right.x.toFloat(), right.y.toFloat(), right.z.toFloat(), 0f,
            up.x.toFloat(), up.y.toFloat(), up.z.toFloat(), 0f,
            forward.x.toFloat(), forward.y.toFloat(), forward.z.toFloat(), 0f,
            sun.x.toFloat(), sun.y.toFloat(), sun.z.toFloat(), 0f,
            zenith.x.toFloat(), zenith.y.toFloat(), zenith.z.toFloat(), 0f,
            (tanHalfFov * viewport.widthPx / shortSide).toFloat(),
            (tanHalfFov * viewport.heightPx / shortSide).toFloat(),
            gradient.turbidity.toFloat(),
            seed.toFloat(),
        )
    }

    private companion object {
        /** float4x4 + float4 viewport + float4 params. */
        const val FRAME_UNIFORM_FLOATS = 24

        /** Wraps the dither seed often enough to animate, slowly enough to stay float-precise. */
        const val DITHER_SEED_PERIOD_MS = 10_000.0
    }
}
