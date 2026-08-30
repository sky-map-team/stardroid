/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles1

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.RendererInfo
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyProjection
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.api.Viewport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.opengles.GL11

/**
 * The GLES1 [SkyRenderer] backend (Slice 3b-i/ii, icons in 4d): a port of v1's renderer behind
 * the new contract, covering points, lines, images, screen-space icons, and labels.
 *
 * Producers publish whole retained [LayerScene]s (D13) from any thread via [submit]; this class
 * holds the latest one per [LayerId] independently of any GL resource (the CPU-retained scene is
 * the source of truth, G9 in render-api.md). On the GL thread, [onDrawFrame] lazily rebuilds
 * per-type buffers and GPU resources when their scene, state, or density changes, then replays
 * the same data every frame in between.
 *
 * **GL resource lifecycle (G9):** on EGL context loss [onSurfaceCreated] clears all caches;
 * since the retained scenes are never lost, the next [onDrawFrame] re-derives all GPU resources
 * with no producer involvement. Textures lost with the context are not explicitly deleted (the GL
 * runtime frees them). Image and icon textures outlive their scenes in the shared [TextureCache]
 * and are deleted only under its byte budget; label atlases are deleted on scene replacement or
 * removal, since their glyph layout is scene-specific and never reusable.
 *
 * @param density the display density (dp→px multiplier) of the surface.
 * @param imageLoader resolves an [ImageRef] key to a [Bitmap]; returns `null` if the image is
 *   unavailable (that image is silently skipped per D24). Called on the GL thread, once per
 *   [ImageRef] per variant — a second time for the same ref if night mode is ever entered. The
 *   returned [Bitmap] is owned by the renderer and recycled immediately after texture upload —
 *   the loader must not return shared or pooled instances.
 * @param onRendererInfo invoked on the GL thread once per surface creation with what the GPU
 *   and driver turned out to be (diagnostics only — see [RendererInfo]). Called again after
 *   EGL context loss, since the replacement context may not be the same implementation.
 */
class GLSkyRenderer(
    private val density: Float,
    private val imageLoader: (ImageRef) -> Bitmap?,
    private val onRendererInfo: (RendererInfo) -> Unit = {},
) : GLSurfaceView.Renderer, SkyRenderer {
    @Volatile private var camera: SkyCamera? = null

    @Volatile private var renderState: RenderState = RenderState()

    @Volatile private var viewport: Viewport? = null
    private val scenes = ConcurrentHashMap<LayerId, LayerScene>()

    /** Bumped after every [scenes] mutation so the GL thread knows to rebuild [drawOrder]. */
    private val scenesVersion = AtomicLong()

    // GL-thread-only: scenes sorted by depth. Layers are submitted rarely, so the sort is cached
    // and rebuilt only when scenesVersion changes rather than allocating a sorted copy per frame.
    private var drawOrder: List<Pair<LayerId, LayerScene>> = emptyList()
    private var drawOrderVersion = -1L

    // GL-thread-only: the RenderState projection point buffers bake in (see the point-drawing
    // step in onDrawFrame). Reallocated only when those fields change, so a steady-state frame
    // allocates no Pair per layer. The initial value matches RenderState's defaults.
    private var pointKey = Pair(false, null as Double?)

    // GL-thread-only: pure CPU buffers keyed by (scene identity, the state fields the drawer
    // actually consumes, density). Keying on the whole RenderState would rebuild every point
    // buffer — a 100k-vertex re-style and re-sort — when, say, only labelScaleFactor changed.
    private val pointCache = HashMap<LayerId, BuildCache<PointBuffers>>()
    private val lineCache = HashMap<LayerId, BuildCache<LineBuffers>>()
    private val glowCache = HashMap<LayerId, BuildCache<GlowBuffers>>()

    // GL-thread-only: the sky-gradient dome, built on first use. Pure client-side buffers with
    // static geometry — the per-frame sun rotation happens on the modelview stack — so context
    // loss doesn't invalidate it and no per-state rebuild ever happens.
    private var skyGradientBuffers: SkyGradientBuffers? = null

    // GL-thread-only: the ImageRef-keyed texture cache shared by every layer's images and icons
    // (D85). The per-layer caches below hold the geometry and a claim on these textures.
    private val textureCache = TextureCache(imageLoader)

    // GL-thread-only: GPU resources (textures). Keyed by scene identity only for images;
    // by (scene identity, density) for icons; by (scene identity, labelScaleFactor, density)
    // for labels.
    private val imageCache = HashMap<LayerId, ImageLayerCache>()
    private val iconCache = HashMap<LayerId, IconLayerCache>()
    private val labelCache = HashMap<LayerId, LabelLayerCache>()

    private data class BuildCache<T>(
        val scene: LayerScene,
        val stateKey: Any?,
        val density: Float,
        val buffers: T,
    ) {
        fun isStale(
            scene: LayerScene,
            stateKey: Any?,
            density: Float,
        ) = this.scene !== scene || this.stateKey != stateKey || this.density != density
    }

    private class ImageLayerCache(val scene: LayerScene, val gpuData: ImageGpuData)

    private class IconLayerCache(
        val scene: LayerScene,
        val density: Float,
        val gpuData: IconGpuData,
    )

    private class LabelLayerCache(
        val scene: LayerScene,
        val labelScaleFactor: Double,
        val density: Float,
        val gpuData: LabelGpuData,
    )

    override fun submit(
        layerId: LayerId,
        scene: LayerScene?,
    ) {
        if (scene == null) scenes.remove(layerId) else scenes[layerId] = scene
        // Bump after the map write: a GL thread that sees the new version also sees the new map.
        scenesVersion.incrementAndGet()
    }

    override fun setCamera(camera: SkyCamera) {
        this.camera = camera
    }

    override fun setRenderState(state: RenderState) {
        this.renderState = state
    }

    override fun onSurfaceCreated(
        gl: GL10,
        config: EGLConfig?,
    ) {
        // Clear all caches. CPU-side buffers are discarded; GL textures are auto-freed by the
        // context loss — do NOT call ImageDrawer.release / LabelDrawer.release here. Dropping
        // the per-layer caches drops every claim on the texture cache, which forgets its
        // (now meaningless) texture names in the same pass.
        textureCache.onContextLost()
        pointCache.clear()
        lineCache.clear()
        glowCache.clear()
        imageCache.clear()
        iconCache.clear()
        labelCache.clear()

        gl.glDisable(GL10.GL_DEPTH_TEST)
        gl.glDisable(GL10.GL_LIGHTING)
        gl.glEnable(GL10.GL_DITHER)
        gl.glEnable(GL10.GL_BLEND)
        gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA)
        gl.glClearColor(0f, 0f, 0f, 1f)

        onRendererInfo(queryRendererInfo(gl))
    }

    /**
     * Reads the GL implementation's identity and the two capability ranges whose limits we
     * cannot otherwise observe (see [RendererInfo]). Runs on the GL thread.
     */
    private fun queryRendererInfo(gl: GL10): RendererInfo {
        val maxTexture = IntArray(1)
        gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
        return RendererInfo(
            backend = BACKEND_NAME,
            vendor = gl.glGetString(GL10.GL_VENDOR).orEmpty(),
            renderer = gl.glGetString(GL10.GL_RENDERER).orEmpty(),
            version = gl.glGetString(GL10.GL_VERSION).orEmpty(),
            maxTextureSizePx = maxTexture[0],
            lineWidthRange = queryRange(gl, GL10.GL_ALIASED_LINE_WIDTH_RANGE),
            pointSizeRange = queryRange(gl, GL10.GL_SMOOTH_POINT_SIZE_RANGE),
        )
    }

    /**
     * A two-float `glGetFloatv` range, or null if we cannot ask. `glGetFloatv` is declared on
     * [GL11] rather than [GL10]; every Android GL context has satisfied GL11 since well before
     * our minSdk, but the checked cast keeps a hypothetical GL10-only implementation from
     * crashing a diagnostics read.
     */
    private fun queryRange(
        gl: GL10,
        name: Int,
    ): RendererInfo.Range? {
        val gl11 = gl as? GL11 ?: return null
        val values = floatArrayOf(0f, 0f)
        gl11.glGetFloatv(name, values, 0)
        if (values[1] <= 0f) return null
        return RendererInfo.Range(values[0], values[1])
    }

    override fun onSurfaceChanged(
        gl: GL10,
        width: Int,
        height: Int,
    ) {
        gl.glViewport(0, 0, width, height)
        viewport = Viewport(width, height, density)
    }

    override fun onDrawFrame(gl: GL10) {
        val state = renderState
        // Transparent background (camera-ar-mode.md/D64): alpha-0 clear lets the composited
        // camera plane below the surface show through the empty sky.
        gl.glClearColor(0f, 0f, 0f, if (state.transparentBackground) 0f else 1f)
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT)
        val camera = camera ?: return
        val viewport = viewport ?: return

        // The camera dimmer goes under everything the map draws; it runs on identity
        // matrices, loaded fresh by the projection setup just below.
        if (state.transparentBackground && state.cameraScrim > 0.0) {
            CameraScrimDrawer.draw(gl, state.cameraScrim.toFloat())
        }

        val projection = SkyProjection(camera, viewport)
        gl.glMatrixMode(GL10.GL_PROJECTION)
        gl.glLoadMatrixf(projection.viewProjection.toFloatArray(), 0)
        gl.glMatrixMode(GL10.GL_MODELVIEW)
        gl.glLoadIdentity()

        // 0. The sky-gradient dome goes behind everything (v1 gave its SkyBox MIN_VALUE depth)
        // and is skipped entirely in night mode, as v1's SkyBox.drawInternal was — and while
        // the background is transparent, where an opaque dome would wall off the camera.
        val gradient = state.skyGradient
        if (gradient != null && !state.nightMode && !state.transparentBackground) {
            val buffers =
                skyGradientBuffers
                    ?: SkyGradientDrawer.build().also { skyGradientBuffers = it }
            SkyGradientDrawer.draw(gl, buffers, gradient.sunDirection)
        }

        // Draw layers back-to-front; within each layer: glows → lines → images → points → labels
        // (D18 painter's algorithm). Read the version before snapshotting the map: a submit that
        // races with the sort bumps the version again, so the next frame re-sorts.
        val version = scenesVersion.get()
        if (version != drawOrderVersion) {
            // Layers changed: release GPU resources for layers removed via submit(id, null),
            // drop their CPU buffers (direct buffers have no explicit free — the GC reclaims
            // them once unreferenced), and re-sort. Steady-state frames skip all of this.
            pruneGpuCache(imageCache) { ImageDrawer.release(textureCache, it.gpuData) }
            pruneGpuCache(iconCache) { IconDrawer.release(textureCache, it.gpuData) }
            pruneGpuCache(labelCache) { LabelDrawer.release(gl, it.gpuData) }
            pointCache.keys.retainAll(scenes.keys)
            lineCache.keys.retainAll(scenes.keys)
            glowCache.keys.retainAll(scenes.keys)
            drawOrder = scenes.entries.sortedBy { it.value.depth }.map { it.key to it.value }
            drawOrderVersion = version
        }
        if (state.nightMode != pointKey.first || state.magnitudeLimit != pointKey.second) {
            pointKey = Pair(state.nightMode, state.magnitudeLimit)
        }
        for (i in drawOrder.indices) {
            val (layerId, scene) = drawOrder[i]
            // 0. Glows (the horizon's gradient mesh), behind the layer's own lines so the crisp
            // horizon line draws on top. Day and night colors are both baked at build time and
            // picked at draw time, so glow geometry depends on no RenderState field.
            if (scene.glows.isNotEmpty()) {
                val glowBuffers =
                    cachedCpu(glowCache, layerId, scene, null, viewport.density) {
                        GlowDrawer.build(scene.glows)
                    }
                GlowDrawer.draw(gl, glowBuffers, state.nightMode)
            }

            // 1. Lines. Night mode is applied per segment at draw time, so line geometry
            // depends on no RenderState field at all — the state key is a constant.
            val lineBuffers =
                cachedCpu(lineCache, layerId, scene, null, viewport.density) {
                    LineDrawer.build(scene.lines, viewport.density)
                }
            LineDrawer.draw(gl, lineBuffers, state.nightMode)

            // 2. Images
            val imageGpu = cachedImage(gl, layerId, scene)
            ImageDrawer.draw(gl, imageGpu, textureCache, camera, viewport, state.nightMode)

            // 3. Points. Color (night mode, magnitude fade) is baked per vertex, so those two
            // fields are the key (cached in the pointKey field); labelScaleFactor changes must
            // not trigger a rebuild.
            val pointBuffers =
                cachedCpu(pointCache, layerId, scene, pointKey, viewport.density) {
                    PointDrawer.build(scene.points, state, viewport.density)
                }
            PointDrawer.draw(gl, pointBuffers)

            // 3b. Icon points: screen-space textured quads, drawn above the plain dots.
            val iconGpu = cachedIcon(gl, layerId, scene, viewport.density)
            IconDrawer.draw(
                gl,
                iconGpu,
                textureCache,
                camera,
                projection,
                viewport,
                state.nightMode,
            )

            // 4. Labels
            val labelGpu = cachedLabel(gl, layerId, scene, state, viewport.density)
            LabelDrawer.draw(gl, labelGpu, camera, projection, viewport, state)
        }
    }

    // ---- cache helpers (all GL-thread-only) -----------------------------------------

    /**
     * Returns a cached [T] for CPU-side buffers, rebuilding if inputs changed. [stateKey] is the
     * caller's projection of [RenderState] onto the fields its buffers actually bake in (compared
     * by equality); pass a constant for buffers that depend on no state at all.
     */
    private inline fun <T> cachedCpu(
        cache: HashMap<LayerId, BuildCache<T>>,
        layerId: LayerId,
        scene: LayerScene,
        stateKey: Any?,
        density: Float,
        build: () -> T,
    ): T {
        val existing = cache[layerId]
        if (existing != null && !existing.isStale(scene, stateKey, density)) return existing.buffers
        val built = build()
        cache[layerId] = BuildCache(scene, stateKey, density, built)
        return built
    }

    /**
     * Returns cached [ImageGpuData], rebuilding the quad geometry on scene change.
     *
     * Dynamic layers (the solar system) submit a new [LayerScene] on every position update, so
     * this rebuild runs about once a minute. It is cheap because it is only geometry: the new
     * data claims its textures from [textureCache] *before* the old data lets go of them, so a
     * ref that both scenes use is never dropped to zero holders in between.
     */
    private fun cachedImage(
        gl: GL10,
        layerId: LayerId,
        scene: LayerScene,
    ): ImageGpuData {
        val existing = imageCache[layerId]
        if (existing != null && existing.scene === scene) return existing.gpuData
        val gpu = ImageDrawer.build(gl, scene.images, textureCache)
        existing?.let { ImageDrawer.release(textureCache, it.gpuData) }
        imageCache[layerId] = ImageLayerCache(scene, gpu)
        return gpu
    }

    /** Returns cached [IconGpuData], rebuilding the sprite list on scene change. */
    private fun cachedIcon(
        gl: GL10,
        layerId: LayerId,
        scene: LayerScene,
        density: Float,
    ): IconGpuData {
        val existing = iconCache[layerId]
        if (existing != null && existing.scene === scene && existing.density == density) {
            return existing.gpuData
        }
        val gpu = IconDrawer.build(gl, scene.points, textureCache, density)
        existing?.let { IconDrawer.release(textureCache, it.gpuData) }
        iconCache[layerId] = IconLayerCache(scene, density, gpu)
        return gpu
    }

    /**
     * Returns cached [LabelGpuData], rebuilding if scene, [RenderState.labelScaleFactor], or
     * density changed.
     */
    private fun cachedLabel(
        gl: GL10,
        layerId: LayerId,
        scene: LayerScene,
        state: RenderState,
        density: Float,
    ): LabelGpuData {
        val existing = labelCache[layerId]
        if (existing != null &&
            existing.scene === scene &&
            existing.labelScaleFactor == state.labelScaleFactor &&
            existing.density == density
        ) {
            return existing.gpuData
        }
        existing?.let { LabelDrawer.release(gl, it.gpuData) }
        val gpu = LabelDrawer.build(gl, scene.labels, state, density)
        labelCache[layerId] = LabelLayerCache(scene, state.labelScaleFactor, density, gpu)
        return gpu
    }

    /** Releases GPU resources for every [layerId] in [cache] that is no longer in [scenes]. */
    private fun <T> pruneGpuCache(
        cache: HashMap<LayerId, T>,
        release: (T) -> Unit,
    ) {
        val iter = cache.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!scenes.containsKey(entry.key)) {
                release(entry.value)
                iter.remove()
            }
        }
    }

    companion object {
        /** Identifies this backend in [RendererInfo.backend]. */
        const val BACKEND_NAME = "gles1"
    }
}
