/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

import com.google.android.stardroid.math.Vector3
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RecordingSkyRendererTest {
    private val stars = LayerId("stars")
    private val grid = LayerId("grid")

    @Test
    fun submit_recordsLatestScenePerLayer() {
        val renderer = RecordingSkyRenderer()
        val first = LayerScene(depth = 1, points = listOf(point(1.0)))
        val second = LayerScene(depth = 1, points = listOf(point(1.0), point(2.0)))
        renderer.submit(stars, first)
        renderer.submit(grid, LayerScene(depth = 0))
        renderer.submit(stars, second) // latest wins for this layer

        assertThat(renderer.scenes.keys).containsExactly(stars, grid)
        assertThat(renderer.scenes[stars]!!.points).hasSize(2)
    }

    @Test
    fun submitNull_removesLayer() {
        val renderer = RecordingSkyRenderer()
        renderer.submit(stars, LayerScene(depth = 1))
        assertThat(renderer.scenes).containsKey(stars)
        renderer.submit(stars, null)
        assertThat(renderer.scenes).doesNotContainKey(stars)
    }

    @Test
    fun setCameraAndState_keepLatestValue() {
        val renderer = RecordingSkyRenderer()
        assertThat(renderer.lastCamera).isNull()
        assertThat(renderer.lastRenderState).isNull()

        renderer.setCamera(SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, fovDeg = 60.0))
        renderer.setCamera(SkyCamera(Vector3.UNIT_Y, Vector3.UNIT_Z, fovDeg = 30.0))
        renderer.setRenderState(RenderState(nightMode = true))

        assertThat(renderer.lastCamera!!.fovDeg).isEqualTo(30.0)
        assertThat(renderer.lastRenderState!!.nightMode).isTrue()
    }

    private fun point(magnitude: Double) =
        PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(magnitude))
}
