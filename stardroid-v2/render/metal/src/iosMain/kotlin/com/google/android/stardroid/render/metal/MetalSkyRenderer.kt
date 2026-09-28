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
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.GlowMesh
import com.google.android.stardroid.render.api.ImageCache
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageQuad
import com.google.android.stardroid.render.api.ImageRef
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
import platform.Metal.MTLTextureProtocol
import platform.UIKit.UIImage
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
 *   glows → lines → images → points (icons and labels arrive in a later slice).
 * - **Retained scenes, derived GPU data.** Producers publish immutable [LayerScene]s from any
 *   thread; [encode] builds a layer's buffers from the shared `:render:api` builders the first
 *   time it sees that scene instance, and reuses them until the layer is resubmitted.
 * - **Everything per-frame is a uniform**: camera, magnitude limit, night mode, and each image's
 *   size and phase — so a pinch or a changing Moon never rebuilds a buffer.
 *
 * The host owns the drawable and the frame loop: an `MTKView` delegate calls [encode] with the
 * view's pass descriptor, and the offscreen tests call it with their own texture. [encode] must
 * be called from one thread at a time — it is the render thread's; the publishing methods may be
 * called from anywhere.
 *
 * @param density the display density (dp → px) of the surface being drawn.
 * @param imageLoader resolves an [ImageRef] to an image, or null to skip it (D24). Called on the
 *   render thread, at most once per ref while its texture stays cached.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class MetalSkyRenderer(
    private val device: MTLDeviceProtocol,
    private val density: Float,
    imageLoader: (ImageRef) -> UIImage? = { null },
    pixelFormat: MTLPixelFormat = MTLPixelFormatBGRA8Unorm,
) : SkyRenderer {
    @Volatile private var camera: SkyCamera? = null

    @Volatile private var renderState: RenderState = RenderState()

    /** Replaced wholesale on every submit, so the render thread always reads a consistent set. */
    private val scenes = AtomicReference(emptyMap<LayerId, LayerScene>())

    // ---- render-thread-only state -----------------------------------------------------------

    private val pipelines = MetalPipelines(device, pixelFormat)
    private val textures = MetalTextures(device)
    private val images =
        ImageCache<MTLTextureProtocol>(
            load = { ref -> imageLoader(ref)?.let(textures::stage) },
            bytes = { it.byteSize },
        )
    private val layers = HashMap<LayerId, LayerGpu>()
    private var drawOrderSource: Map<LayerId, LayerScene>? = null
    private var drawOrder: List<Pair<LayerId, LayerScene>> = emptyList()

    /**
     * One layer's buffers, valid for exactly the [scene] instance they were built from, and its
     * claims on the textures of the scene's images, which [release] gives back.
     */
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
        // Build what changed first: new layers stage their textures, and the copies into them
        // have to be encoded before the render pass that samples them.
        val order = refreshDrawOrder()
        for ((layerId, scene) in order) layerGpu(layerId, scene)
        if (textures.hasPending) {
            commandBuffer.blitCommandEncoder()?.let { blit ->
                textures.flush(blit)
                blit.endEncoding()
            }
        }

        val state = renderState
        val color = pass.colorAttachments.objectAtIndexedSubscript(0u)
        color.loadAction = MTLLoadActionClear
        val clearAlpha = if (state.transparentBackground) 0.0 else 1.0
        color.clearColor = MTLClearColorMake(0.0, 0.0, 0.0, clearAlpha)
        val encoder = commandBuffer.renderCommandEncoderWithDescriptor(pass) ?: return
        try {
            val camera = camera
            if (camera != null && widthPx > 0 && heightPx > 0) {
                draw(encoder, order, camera, state, Viewport(widthPx, heightPx, density))
            }
        } finally {
            encoder.endEncoding()
        }
    }

    private fun draw(
        encoder: MTLRenderCommandEncoderProtocol,
        order: List<Pair<LayerId, LayerScene>>,
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
        for ((layerId, scene) in order) {
            val gpu = layers[layerId] ?: continue
            gpu.glows?.let { drawIndexed(encoder, pipelines.glow, it, frame) }
            gpu.lines?.let { drawIndexed(encoder, pipelines.line, it, frame) }
            drawImages(encoder, scene.images, frame, camera, viewport)
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

    /**
     * Each image is a quad sized for this frame's field of view — its true size or its floor
     * (SizeFloor, D86) — so the geometry is uniforms, not a buffer, and the list order the
     * producer chose (farthest first, D18) is the draw order.
     */
    private fun drawImages(
        encoder: MTLRenderCommandEncoderProtocol,
        scene: List<ImagePrimitive>,
        frame: FloatArray,
        camera: SkyCamera,
        viewport: Viewport,
    ) {
        if (scene.isEmpty()) return
        encoder.setRenderPipelineState(pipelines.image)
        encoder.setVertexFloats(frame, 1)
        encoder.setFragmentFloats(frame, 1)
        for (image in scene) {
            val drawnDeg = ImageQuad.drawnDiameterDeg(image, camera, viewport) ?: continue
            val texture = images[image.image] ?: continue
            val uniforms = imageUniforms(image, drawnDeg)
            encoder.setVertexFloats(uniforms, 2)
            encoder.setFragmentFloats(uniforms, 2)
            encoder.setFragmentTexture(texture, 0u)
            encoder.drawPrimitives(MTLPrimitiveTypeTriangleStrip, 0u, 4u)
        }
    }

    /** Re-sorts layers when the published set changed, and drops removed layers' resources. */
    private fun refreshDrawOrder(): List<Pair<LayerId, LayerScene>> {
        val current = scenes.load()
        if (current !== drawOrderSource) {
            val removed = layers.keys - current.keys
            for (layerId in removed) layers.remove(layerId)?.let(::release)
            drawOrder = current.entries.sortedBy { it.value.depth }.map { it.key to it.value }
            drawOrderSource = current
        }
        return drawOrder
    }

    private fun layerGpu(
        layerId: LayerId,
        scene: LayerScene,
    ): LayerGpu {
        val existing = layers[layerId]
        if (existing != null && existing.scene === scene) return existing
        // Claim the new scene's images before letting go of the old one's, so an image both use
        // never drops to zero holders in between. Replacing buffers is safe mid-flight: a command
        // buffer retains every resource it references until the GPU is done with it.
        for (image in scene.images) images.retain(image.image)
        existing?.let(::release)
        val points = PointVertices.build(scene.points)
        val lines = LineStrips.build(scene.lines)
        val glows = GlowMesh.build(scene.glows)
        val gpu =
            LayerGpu(
                scene = scene,
                points = device.bufferOf(points.data)?.let { Mesh(it, null, points.vertexCount) },
                lines = indexedMesh(lines.vertices, lines.indices),
                glows = indexedMesh(glows.vertices, glows.indices),
            )
        layers[layerId] = gpu
        return gpu
    }

    private fun release(gpu: LayerGpu) {
        for (image in gpu.scene.images) images.release(image.image)
    }

    private fun indexedMesh(
        vertices: FloatArray,
        indices: IntArray,
    ): Mesh? {
        val vertexBuffer = device.bufferOf(vertices) ?: return null
        val indexBuffer = device.bufferOf(indices) ?: return null
        return Mesh(vertexBuffer, indexBuffer, indices.size)
    }

    // ---- uniforms (layouts match common.metal, sky.metal and image.metal) --------------------

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

    /** One image's quad and shading inputs: ImageUniforms in image.metal. */
    private fun imageUniforms(
        image: ImagePrimitive,
        drawnDeg: Double,
    ): FloatArray {
        val axes = ImageQuad.halfAxes(image, drawnDeg)
        val out = FloatArray(IMAGE_UNIFORM_FLOATS)
        out.putVector(0, image.center)
        out.putVector(4, axes.u)
        out.putVector(8, axes.v)
        val terminator = image.terminator ?: return out
        val lit = ImageQuad.litDirection(terminator)
        out[12] = 1f
        out[13] = terminator.illuminatedFraction.toFloat()
        out[14] = lit.x.toFloat()
        out[15] = lit.y.toFloat()
        val eclipse = terminator.eclipse ?: return out
        val shadow = ImageQuad.shadowCenter(eclipse)
        out[16] = 1f
        out[17] = eclipse.umbraRadius.toFloat()
        out[18] = eclipse.penumbraRadius.toFloat()
        out[20] = shadow.x.toFloat()
        out[21] = shadow.y.toFloat()
        return out
    }

    private fun FloatArray.putVector(
        at: Int,
        v: Vector3,
    ) {
        this[at] = v.x.toFloat()
        this[at + 1] = v.y.toFloat()
        this[at + 2] = v.z.toFloat()
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

        /** Six float4s: centre, half-u, half-v, terminator, eclipse, shadow centre. */
        const val IMAGE_UNIFORM_FLOATS = 24

        /** Wraps the dither seed often enough to animate, slowly enough to stay float-precise. */
        const val DITHER_SEED_PERIOD_MS = 10_000.0
    }
}
