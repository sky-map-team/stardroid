/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios

import com.google.android.stardroid.astronomy.SkyModel
import com.google.android.stardroid.ios.scene.LookDirection
import com.google.android.stardroid.ios.scene.SkyData
import com.google.android.stardroid.ios.scene.SkyScenes
import com.google.android.stardroid.math.LatLong
import kotlinx.datetime.Clock
import org.robovm.apple.foundation.NSBundle
import org.robovm.apple.uikit.UIGestureRecognizerState
import org.robovm.apple.uikit.UIPanGestureRecognizer
import org.robovm.apple.uikit.UIPinchGestureRecognizer
import org.robovm.apple.uikit.UIViewController
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * The single screen of the iOS proof of concept: the sky around a fixed observer, dragged with
 * one finger and zoomed with a pinch.
 *
 * Scene production mirrors the Android split: static catalog layers are submitted once, the
 * time-dependent ones (solar system, horizon) are recomputed off the main thread once a minute,
 * and the renderer contract takes it from there. Location is fixed until CoreLocation is wired.
 */
class SkyViewController : UIViewController() {
    private val skyView = SkyView()
    private val producer = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var look = LookDirection()

    override fun loadView() {
        view = skyView
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        skyView.addGestureRecognizer(
            UIPanGestureRecognizer { gesture ->
                val pan = gesture as UIPanGestureRecognizer
                val delta = pan.getTranslation(skyView)
                val bounds = skyView.bounds
                look = look.dragged(delta.x, delta.y, min(bounds.width, bounds.height))
                pan.setTranslation(ORIGIN, skyView)
                updateCamera()
            },
        )
        skyView.addGestureRecognizer(
            UIPinchGestureRecognizer { gesture ->
                val pinch = gesture as UIPinchGestureRecognizer
                if (pinch.state == UIGestureRecognizerState.Changed) {
                    look = look.zoomed(pinch.scale)
                    pinch.scale = 1.0
                    updateCamera()
                }
            },
        )
        producer.execute(::submitCatalogLayers)
        producer.scheduleAtFixedRate(::submitTimeDependentLayers, 0, 1, TimeUnit.MINUTES)
    }

    override fun prefersStatusBarHidden(): Boolean = true

    private fun submitCatalogLayers() {
        val renderer = skyView.renderer
        renderer.submit(
            SkyScenes.STARS,
            SkyScenes.stars(SkyData.parseStars(resourceLines("stars"))),
        )
        renderer.submit(
            SkyScenes.CONSTELLATIONS,
            SkyScenes.constellations(SkyData.parseConstellations(resourceLines("constellations"))),
        )
    }

    private fun submitTimeDependentLayers() {
        val now = Clock.System.now()
        val renderer = skyView.renderer
        renderer.submit(SkyScenes.SOLAR_SYSTEM, SkyScenes.solarSystem(now, OBSERVER))
        renderer.submit(SkyScenes.HORIZON, SkyScenes.horizon(SkyModel.localFrame(now, OBSERVER)))
        updateCamera()
    }

    /** Re-derives the camera: the look direction is local, the sky turns beneath it. */
    private fun updateCamera() {
        val frame = SkyModel.localFrame(Clock.System.now(), OBSERVER)
        skyView.renderer.setCamera(look.toCamera(frame))
    }

    private fun resourceLines(name: String): Sequence<String> {
        val path =
            NSBundle.getMainBundle().findResourcePath(name, "csv")
                ?: error("$name.csv missing from the app bundle")
        return File(path).readLines().asSequence()
    }

    private companion object {
        /** Placeholder observer (Greenwich) until location services are wired in. */
        val OBSERVER = LatLong(51.4769, 0.0)
        val ORIGIN = org.robovm.apple.coregraphics.CGPoint(0.0, 0.0)
    }
}
