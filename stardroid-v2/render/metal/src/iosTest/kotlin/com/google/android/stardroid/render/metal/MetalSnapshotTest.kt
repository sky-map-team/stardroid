/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.metal

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
class MetalSnapshotTest {
    private fun snapshotOf(state: RenderState): MetalSnapshot {
        val device = metalDevice()
        val renderer = MetalSkyRenderer(device, density = 2f)
        renderer.setCamera(SkyCamera(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, 1.0), 45.0))
        renderer.setRenderState(state)
        val finished = dispatch_semaphore_create(0)
        var frame: MetalSnapshot? = null
        renderer.snapshot(device.newCommandQueue()!!, width = 64, height = 96) {
            frame = it
            dispatch_semaphore_signal(finished)
        }
        dispatch_semaphore_wait(finished, DISPATCH_TIME_FOREVER)
        return frame!!
    }

    @Test
    fun overTheCameraTheSnapshotIsClearAndLeavesTheScrimToTheCompositor() {
        val frame = snapshotOf(RenderState(transparentBackground = true, cameraScrim = 0.6))

        assertEquals(64 * 96 * 4, frame.bgra.size)
        assertEquals(0, frame.bgra.count { it != 0.toByte() })
    }

    @Test
    fun withoutTheCameraTheSnapshotIsOpaque() {
        val frame = snapshotOf(RenderState(transparentBackground = false))

        val alphas = (3 until frame.bgra.size step 4).map { frame.bgra[it].toInt() and 0xFF }
        assertEquals(setOf(255), alphas.toSet())
    }
}
