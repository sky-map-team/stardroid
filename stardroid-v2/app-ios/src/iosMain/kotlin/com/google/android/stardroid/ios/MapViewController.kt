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
import platform.Foundation.NSSelectorFromString
import platform.Metal.MTLCommandQueueProtocol
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.Metal.MTLPixelFormatBGRA8Unorm
import platform.MetalKit.MTKView
import platform.MetalKit.MTKViewDelegateProtocol
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIPinchGestureRecognizer
import platform.UIKit.UIRotationGestureRecognizer
import platform.UIKit.UIScreen
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
 * fading. A still sky costs nothing.
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
            onAnimating = { view.setNeedsDisplay() },
        )
    private val drawLoop = DrawLoop(renderer, device.newCommandQueue() ?: error("no command queue"))
    private val gestures = Gestures()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val viewController: UIViewController = UIViewController()

    init {
        view.colorPixelFormat = MTLPixelFormatBGRA8Unorm
        view.paused = true
        view.enableSetNeedsDisplay = true
        view.delegate = drawLoop
        listOf(
            UIPanGestureRecognizer(gestures, NSSelectorFromString("pan:")),
            UIPinchGestureRecognizer(gestures, NSSelectorFromString("pinch:")),
            UIRotationGestureRecognizer(gestures, NSSelectorFromString("rotate:")),
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

    /** Every change asks the paused view for one frame — iOS's `RenderConnector`. */
    private inner class Redrawing(
        private val renderer: SkyRenderer,
    ) : SkyRenderer {
        override fun submit(
            layerId: LayerId,
            scene: LayerScene?,
        ) {
            renderer.submit(layerId, scene)
            view.setNeedsDisplay()
        }

        override fun setCamera(camera: SkyCamera) {
            renderer.setCamera(camera)
            view.setNeedsDisplay()
        }

        override fun setRenderState(state: RenderState) {
            renderer.setRenderState(state)
            view.setNeedsDisplay()
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

        override fun gestureRecognizer(
            gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer,
        ): Boolean = true
    }
}
