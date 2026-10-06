/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.render.RenderBinder
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.android.stardroid.render.metal.MetalSkyRenderer
import com.google.android.stardroid.ui.map.MapViewModel
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGPointZero
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSize
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.MetalKit.MTKView
import platform.MetalKit.MTKViewDelegateProtocol
import platform.QuartzCore.CADisplayLink
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIPinchGestureRecognizer
import platform.UIKit.UIRotationGestureRecognizer
import platform.UIKit.UIScreen
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.math.PI
import kotlin.math.min

/**
 * The map: a Metal view drawing [MapViewModel]'s camera and render state and every layer's
 * scenes (through the shared [RenderBinder]), with drag, pinch and twist going to the ViewModel —
 * what Android's `MainActivity` wires around its GLSurfaceView.
 *
 * It draws on demand, as Android's surface does (RENDERMODE_WHEN_DIRTY, D23): the view is paused,
 * and redraws only when a scene, the camera or the render state changes, or while a label is still
 * fading — at most once per display refresh. A still sky costs nothing.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class MapViewController(
    private val graph: IosAppGraph,
    private val mapViewModel: MapViewModel,
) {
    private val device = MTLCreateSystemDefaultDevice() ?: error("this device has no Metal GPU")
    private val view = MTKView(CGRectZero.readValue(), device)
    private val renderer =
        MetalSkyRenderer(
            device,
            density = UIScreen.mainScreen.scale.toFloat(),
            imageLoader = BundleImageLoader::load,
            onAnimating = { requestFrame() },
        )
    private val drawLoop = DrawLoop(renderer, device.newCommandQueue() ?: error("no command queue"))
    private val gestures = Gestures()
    private val frames = FrameRequests()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val viewController: UIViewController = UIViewController()

    /**
     * A still tap on the sky, in points from the view's top-left — Android's onSingleTapUp,
     * which identifies what is there (object info).
     */
    var onTap: ((xPoints: Double, yPoints: Double) -> Unit)? = null

    init {
        view.colorPixelFormat = MTLPixelFormatBGRA8Unorm
        view.paused = true
        view.enableSetNeedsDisplay = false
        view.delegate = drawLoop
        listOf(
            UIPanGestureRecognizer(gestures, NSSelectorFromString("pan:")),
            UIPinchGestureRecognizer(gestures, NSSelectorFromString("pinch:")),
            UIRotationGestureRecognizer(gestures, NSSelectorFromString("rotate:")),
            UITapGestureRecognizer(gestures, NSSelectorFromString("tap:")),
        ).forEach {
            it.delegate = gestures
            view.addGestureRecognizer(it)
        }
        viewController.view = view

        val binder = RenderBinder(Redrawing(renderer))
        binder.bindCamera(scope, mapViewModel.camera)
        binder.bindRenderState(scope, mapViewModel.renderState)
        scope.launch {
            for (layer in graph.layerRegistry().layers) {
                binder.bindLayer(this, layer) { LayerRegistry.layerEnabled(graph.settings, it) }
            }
        }
    }

    /** The view's shorter side, which the camera's field of view spans. */
    private fun shortSide(): Int = view.bounds.useContents { min(size.width, size.height).toInt() }

    /** Asks for one frame, drawn at the display's next refresh however many ask before then. */
    private fun requestFrame() = frames.request()

    /**
     * Draws a requested frame at the next refresh, once, as Android's GL thread does for
     * `requestRender`. Drawing at once instead (`setNeedsDisplay`, on the next run-loop turn)
     * lets a label fade request frames faster than the display shows them, and each extra frame
     * then blocks the main thread waiting for a free drawable — starving the sensors' updates.
     */
    private inner class FrameRequests : NSObject() {
        private var requested = false
        private val link =
            CADisplayLink.displayLinkWithTarget(this, NSSelectorFromString("tick:")).also {
                it.paused = true
                it.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
            }

        fun request() {
            requested = true
            link.paused = false
        }

        @ObjCAction
        fun tick(displayLink: CADisplayLink) {
            if (!requested) {
                link.paused = true
                return
            }
            requested = false
            view.draw()
        }
    }

    /** Every change asks the paused view for one frame — iOS's `RenderConnector`. */
    private inner class Redrawing(
        private val renderer: SkyRenderer,
    ) : SkyRenderer {
        override fun submit(
            layerId: LayerId,
            scene: LayerScene?,
        ) {
            renderer.submit(layerId, scene)
            requestFrame()
        }

        override fun setCamera(camera: SkyCamera) {
            renderer.setCamera(camera)
            requestFrame()
        }

        override fun setRenderState(state: RenderState) {
            renderer.setRenderState(state)
            requestFrame()
        }
    }

    private class DrawLoop(
        private val renderer: MetalSkyRenderer,
        private val queue: MTLCommandQueueProtocol,
    ) : NSObject(), MTKViewDelegateProtocol {
        override fun mtkView(
            view: MTKView,
            drawableSizeWillChange: CValue<CGSize>,
        ) = Unit

        override fun drawInMTKView(view: MTKView) {
            val pass = view.currentRenderPassDescriptor ?: return
            val drawable = view.currentDrawable ?: return
            val commands = queue.commandBuffer() ?: return
            val (width, height) =
                view.drawableSize.useContents { width.toInt() to height.toInt() }
            renderer.encode(commands, pass, width, height)
            commands.presentDrawable(drawable)
            commands.commit()
        }
    }

    /**
     * The map's gestures, in points: the ViewModel scales a drag by the screen's short side, so
     * the unit cancels. Pinch, twist and drag may run together, as on Android.
     */
    private inner class Gestures :
        NSObject(),
        UIGestureRecognizerDelegateProtocol {
        @ObjCAction
        fun pan(recognizer: UIPanGestureRecognizer) {
            when (recognizer.state) {
                UIGestureRecognizerStateBegan -> mapViewModel.stopFling()
                UIGestureRecognizerStateEnded -> {
                    val (vx, vy) = recognizer.velocityInView(view).useContents { x to y }
                    mapViewModel.onFling(vx.toFloat(), vy.toFloat(), shortSide())
                    mapViewModel.onGestureEnd()
                    return
                }
                UIGestureRecognizerStateCancelled -> {
                    mapViewModel.onGestureEnd()
                    return
                }
                else -> Unit
            }
            val (dx, dy) = recognizer.translationInView(view).useContents { x to y }
            recognizer.setTranslation(CGPointZero.readValue(), view)
            mapViewModel.onDrag(
                dx.toFloat(),
                dy.toFloat(),
                shortSide(),
                pointerCount = recognizer.numberOfTouches.toInt(),
            )
        }

        @ObjCAction
        fun pinch(recognizer: UIPinchGestureRecognizer) {
            mapViewModel.onStretch(recognizer.scale.toFloat())
            recognizer.scale = 1.0
            if (recognizer.state == UIGestureRecognizerStateEnded) mapViewModel.onGestureEnd()
        }

        @ObjCAction
        fun rotate(recognizer: UIRotationGestureRecognizer) {
            // UIKit's rotation is clockwise-positive on screen, as the ViewModel expects.
            mapViewModel.onRotate((recognizer.rotation * 180.0 / PI).toFloat())
            recognizer.rotation = 0.0
            if (recognizer.state == UIGestureRecognizerStateEnded) mapViewModel.onGestureEnd()
        }

        @ObjCAction
        fun tap(recognizer: UITapGestureRecognizer) {
            if (recognizer.state != UIGestureRecognizerStateEnded) return
            val (x, y) = recognizer.locationInView(view).useContents { x to y }
            onTap?.invoke(x, y)
        }

        override fun gestureRecognizer(
            gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
        ): Boolean = true
    }
}
