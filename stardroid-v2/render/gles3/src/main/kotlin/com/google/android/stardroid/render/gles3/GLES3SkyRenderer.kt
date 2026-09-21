/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.gles3

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LabelDeclutterer
import com.google.android.stardroid.render.api.LabelFader
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

/**
 * The GL ES 3.0 [SkyRenderer] backend — a sibling of `:render:gles1`, not a replacement.
 *
 * Both backends ship and a setting picks between them, which is what makes every visual
 * difference an A/B you can flip on a real device rather than an argument. The contract, the
 * thread model and the resource lifecycle are identical:
 *
 * - **Painter's algorithm, depth test off** (D18). Layer order by [LayerScene.depth]; within a
 *   layer, glows → lines → images → points → icons → labels. GL ES 3.0 makes a depth
 *   buffer cheap and we still do not want one: the sky is a sphere at unit distance and the
 *   ordering is semantic, not geometric.
 * - **`RENDERMODE_WHEN_DIRTY`** (D23). A still device draws nothing. The one new exception is
 *   label fading, which asks for the next frame through [onAnimating] and only while a fade is
 *   actually in flight.
 * - **G9 resource lifecycle.** CPU-retained [LayerScene]s are the source of truth; GPU resources
 *   are a derived cache rebuilt after EGL context loss with no producer involvement.
 *
 * Where it differs from GLES1 is in what the CPU stops doing: no per-frame quad rebuilds, no
 * per-size draw-call runs, no CPU phase compositor, no second red-shifted copy of every texture.
 *
 * @param assets source of the shader sources under `assets/shaders`.
 * @param density the display density (dp→px multiplier) of the surface.
 * @param imageLoader resolves an [ImageRef] to a [Bitmap]; `null` skips that image (D24). Called
 *   on the GL thread, once per ref — unlike GLES1, never a second time for a night-mode variant.
 * @param onRendererInfo invoked on the GL thread once per surface creation with what the GPU and
 *   driver turned out to be. Called again after EGL context loss.
 * @param onAnimating invoked on the GL thread at the end of any frame that left an animation in
 *   flight, to ask for another. Wire it to `GLSurfaceView.requestRender`.
 */
class GLES3SkyRenderer(
    private val assets: AssetManager,
    private val density: Float,
    private val imageLoader: (ImageRef) -> Bitmap?,
    private val onRendererInfo: (RendererInfo) -> Unit = {},
    private val onAnimating: () -> Unit = {},
) : GLSurfaceView.Renderer, SkyRenderer {
    @Volatile private var camera: SkyCamera? = null

    @Volatile private var renderState: RenderState = RenderState()

    @Volatile private var viewport: Viewport? = null
    private val scenes = ConcurrentHashMap<LayerId, LayerScene>()

    /** Bumped after every [scenes] mutation so the GL thread knows to rebuild [drawOrder]. */
    private val scenesVersion = AtomicLong()

    // ---- GL-thread-only state -------------------------------------------------------------

    private val gl = GlState()
    private var programs = emptyMap<String, ShaderProgram>()
    private var spriteBatch: SpriteBatch? = null
    private var emptyVao = 0

    private var drawOrder: List<Pair<LayerId, LayerScene>> = emptyList()
    private var drawOrderVersion = -1L

    private val textureCache = TextureCache(imageLoader)

    private val pointCache = HashMap<LayerId, MeshCache>()
    private val lineCache = HashMap<LayerId, LineCache>()
    private val glowCache = HashMap<LayerId, MeshCache>()
    private val imageCache = HashMap<LayerId, ImageLayerCache>()
    private val iconCache = HashMap<LayerId, IconLayerCache>()
    private val labelCache = HashMap<LayerId, LabelLayerCache>()

    /**
     * Declutter scratch, reused across frames and layers so the label path allocates nothing per
     * frame (audit-2026-08 M2). Layers are drawn one at a time, so one buffer is safe.
     */
    private val candidates = LabelDeclutterer.Candidates()

    /**
     * Per-layer fade state. Keyed per layer because label texts are only unique within one, and
     * because a layer being switched off should take its fades with it.
     */
    private val faders = HashMap<LayerId, LabelFader>()

    private class MeshCache(val scene: LayerScene, val mesh: Mesh)

    private class LineCache(
        val scene: LayerScene,
        val density: Float,
        val mesh: Mesh,
        val segments: List<LineBuffers.Segment>,
    )

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

    // ---- SkyRenderer ------------------------------------------------------------------------

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

    // ---- GLSurfaceView.Renderer -------------------------------------------------------------

    override fun onSurfaceCreated(
        gl10: GL10,
        config: EGLConfig?,
    ) {
        // The EGL context is new, so every GL name we hold refers to something that no longer
        // exists. Drop them all; the retained scenes rebuild everything on the next frame (G9).
        // Nothing is deleted here — the context loss already freed it.
        textureCache.onContextLost()
        pointCache.clear()
        lineCache.clear()
        glowCache.clear()
        imageCache.clear()
        iconCache.clear()
        labelCache.clear()
        faders.clear()
        gl.invalidate()
        spriteBatch?.onContextLost()
        emptyVao = Mesh.genVertexArray()

        programs =
            ShaderProgram.PROGRAM_NAMES.associateWith { ShaderProgram.fromAssets(assets, it) }
        spriteBatch = SpriteBatch(program("sprite"))

        // No depth buffer: ordering is semantic (D18), not geometric.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        onRendererInfo(queryRendererInfo())
    }

    private fun program(name: String): ShaderProgram =
        programs[name] ?: error("Shader program '$name' was not compiled")

    /** Reads the GL implementation's identity and its observable capability ranges. */
    private fun queryRendererInfo(): RendererInfo {
        val maxTexture = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
        return RendererInfo(
            backend = BACKEND_NAME,
            vendor = GLES30.glGetString(GLES30.GL_VENDOR).orEmpty(),
            renderer = GLES30.glGetString(GLES30.GL_RENDERER).orEmpty(),
            version = GLES30.glGetString(GLES30.GL_VERSION).orEmpty(),
            maxTextureSizePx = maxTexture[0],
            lineWidthRange = queryRange(GLES30.GL_ALIASED_LINE_WIDTH_RANGE),
            // The point-size range is reported for completeness, but unlike GLES1 nothing here
            // depends on it: the fragment shader draws the disc, so a driver with a narrow range
            // cannot collapse every star to one aliased pixel (the D31 hazard).
            pointSizeRange = queryRange(GLES30.GL_ALIASED_POINT_SIZE_RANGE),
        )
    }

    private fun queryRange(name: Int): RendererInfo.Range? {
        val values = floatArrayOf(0f, 0f)
        GLES30.glGetFloatv(name, values, 0)
        if (values[1] <= 0f) return null
        return RendererInfo.Range(values[0], values[1])
    }

    override fun onSurfaceChanged(
        gl10: GL10,
        width: Int,
        height: Int,
    ) {
        GLES30.glViewport(0, 0, width, height)
        viewport = Viewport(width, height, density)
    }

    override fun onDrawFrame(gl10: GL10) {
        val state = renderState
        // Transparent background (D64): an alpha-0 clear lets the composited camera plane below
        // the surface show through the empty sky.
        GLES30.glClearColor(0f, 0f, 0f, if (state.transparentBackground) 0f else 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val camera = camera ?: return
        val viewport = viewport ?: return
        val batch = spriteBatch ?: return

        val projection = SkyProjection(camera, viewport)
        val viewProj = projection.viewProjection.toFloatArray()

        if (state.transparentBackground && state.cameraScrim > 0.0) {
            CameraScrimDrawer.draw(gl, program("scrim"), state.cameraScrim.toFloat(), emptyVao)
        }

        // 0. The sky behind everything, skipped in night mode (as v1's SkyBox was) and while the
        // background is transparent, where an opaque sky would wall off the camera.
        val gradient = state.skyGradient
        if (gradient != null && !state.nightMode && !state.transparentBackground) {
            SkyGradientDrawer.draw(
                gl = gl,
                program = program("sky"),
                gradient = gradient,
                camera = camera,
                viewport = viewport,
                emptyVao = emptyVao,
                frameSeed = (SystemClock.uptimeMillis() % DITHER_SEED_PERIOD_MS).toFloat(),
            )
        }

        refreshDrawOrder()

        val nowMillis = SystemClock.uptimeMillis()
        var animating = false
        for (i in drawOrder.indices) {
            val (layerId, scene) = drawOrder[i]

            // 0. Glows, behind the layer's own lines so the crisp horizon line draws on top.
            if (scene.glows.isNotEmpty()) {
                val mesh =
                    cachedMesh(glowCache, layerId, scene) {
                        GlowDrawer.upload(gl, GlowDrawer.build(scene.glows), program("glow"))
                    }
                GlowDrawer.draw(gl, program("glow"), mesh, viewProj, state.nightMode)
            }

            // 1. Lines.
            val lines = cachedLines(layerId, scene, viewport.density)
            LineDrawer.draw(
                gl,
                program("line"),
                lines.mesh,
                lines.segments,
                viewProj,
                state.nightMode,
            )

            // 2. Images.
            val imageGpu = cachedImage(layerId, scene)
            ImageDrawer.draw(
                gl = gl,
                program = program("skyquad"),
                gpu = imageGpu,
                cache = textureCache,
                camera = camera,
                viewport = viewport,
                viewProj = viewProj,
                nightMode = state.nightMode,
                emptyVao = emptyVao,
            )

            // 3. Points. Neither magnitudeLimit nor night mode is baked in, so unlike GLES1
            // neither invalidates this buffer — they are uniforms.
            val points =
                cachedMesh(pointCache, layerId, scene) {
                    PointDrawer.upload(gl, PointDrawer.build(scene.points), program("point"))
                }
            PointDrawer.draw(
                gl = gl,
                program = program("point"),
                mesh = points,
                viewProj = viewProj,
                density = viewport.density,
                magnitudeLimit = state.magnitudeLimit,
                nightMode = state.nightMode,
            )

            // 3b. Icon points: screen-space textured quads, above the plain dots.
            val iconGpu = cachedIcon(layerId, scene, viewport.density)
            IconDrawer.draw(
                gl = gl,
                batch = batch,
                gpu = iconGpu,
                cache = textureCache,
                camera = camera,
                projection = projection,
                viewport = viewport,
                nightMode = state.nightMode,
            )

            // 4. Labels.
            val labelGpu = cachedLabel(layerId, scene, state, viewport.density)
            val fader = faders.getOrPut(layerId) { LabelFader() }
            fader.beginFrame(nowMillis)
            LabelDrawer.draw(
                gl = gl,
                batch = batch,
                candidates = candidates,
                fader = fader,
                gpu = labelGpu,
                camera = camera,
                projection = projection,
                viewport = viewport,
                state = state,
            )
            fader.endFrame()
            animating = animating || fader.animating
        }

        // The one place RENDERMODE_WHEN_DIRTY yields, and only for as long as something is
        // actually moving: a settled label set asks for nothing and the device goes quiet again.
        if (animating) onAnimating()
    }

    // ---- cache helpers (all GL-thread-only) -------------------------------------------------

    /** Re-sorts layers and releases resources for any removed by `submit(id, null)`. */
    private fun refreshDrawOrder() {
        // Read the version before snapshotting the map: a submit that races with the sort bumps
        // it again, so the next frame re-sorts.
        val version = scenesVersion.get()
        if (version == drawOrderVersion) return
        pruneCache(imageCache) { ImageDrawer.release(textureCache, it.gpuData) }
        pruneCache(iconCache) { IconDrawer.release(textureCache, it.gpuData) }
        pruneCache(labelCache) { LabelDrawer.release(it.gpuData) }
        pruneCache(pointCache) { it.mesh.release() }
        pruneCache(glowCache) { it.mesh.release() }
        pruneCache(lineCache) { it.mesh.release() }
        faders.keys.retainAll(scenes.keys)
        drawOrder = scenes.entries.sortedBy { it.value.depth }.map { it.key to it.value }
        drawOrderVersion = version
    }

    private inline fun cachedMesh(
        cache: HashMap<LayerId, MeshCache>,
        layerId: LayerId,
        scene: LayerScene,
        build: () -> Mesh,
    ): Mesh {
        val existing = cache[layerId]
        if (existing != null && existing.scene === scene) return existing.mesh
        existing?.mesh?.release()
        val mesh = build()
        cache[layerId] = MeshCache(scene, mesh)
        return mesh
    }

    private fun cachedLines(
        layerId: LayerId,
        scene: LayerScene,
        density: Float,
    ): LineCache {
        val existing = lineCache[layerId]
        if (existing != null && existing.scene === scene && existing.density == density) {
            return existing
        }
        existing?.mesh?.release()
        val buffers = LineDrawer.build(scene.lines, density)
        val cache =
            LineCache(
                scene,
                density,
                LineDrawer.upload(gl, buffers, program("line")),
                buffers.segments,
            )
        lineCache[layerId] = cache
        return cache
    }

    /**
     * Rebuilds image texture claims on scene change. The new data claims its textures *before*
     * the old data lets go, so a ref both scenes use is never dropped to zero holders in between.
     */
    private fun cachedImage(
        layerId: LayerId,
        scene: LayerScene,
    ): ImageGpuData {
        val existing = imageCache[layerId]
        if (existing != null && existing.scene === scene) return existing.gpuData
        val gpu = ImageDrawer.build(scene.images, textureCache)
        existing?.let { ImageDrawer.release(textureCache, it.gpuData) }
        imageCache[layerId] = ImageLayerCache(scene, gpu)
        return gpu
    }

    private fun cachedIcon(
        layerId: LayerId,
        scene: LayerScene,
        density: Float,
    ): IconGpuData {
        val existing = iconCache[layerId]
        if (existing != null && existing.scene === scene && existing.density == density) {
            return existing.gpuData
        }
        val gpu = IconDrawer.build(scene.points, textureCache, density)
        existing?.let { IconDrawer.release(textureCache, it.gpuData) }
        iconCache[layerId] = IconLayerCache(scene, density, gpu)
        return gpu
    }

    private fun cachedLabel(
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
        existing?.let { LabelDrawer.release(it.gpuData) }
        val gpu = LabelDrawer.build(scene.labels, state, density)
        labelCache[layerId] = LabelLayerCache(scene, state.labelScaleFactor, density, gpu)
        return gpu
    }

    /** Releases resources for every [layerId] in [cache] that is no longer in [scenes]. */
    private fun <T> pruneCache(
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
        /** Identifies this backend in [RendererInfo.backend] and in the diagnostics screen. */
        const val BACKEND_NAME = "gles3"

        /** Wraps the dither seed often enough to animate, slowly enough to stay float-precise. */
        private const val DITHER_SEED_PERIOD_MS = 10_000L
    }
}
