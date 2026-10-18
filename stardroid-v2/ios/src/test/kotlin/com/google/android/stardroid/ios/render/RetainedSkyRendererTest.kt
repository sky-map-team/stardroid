/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.render

import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.Viewport
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class RetainedSkyRendererTest {
    private val viewport = Viewport(100, 100, density = 1f)
    private val scene =
        LayerScene(
            0,
            points = listOf(PointPrimitive(Vector3.UNIT_X, PointAppearance.Fixed(Rgba.WHITE, 2.0))),
        )

    @Test
    fun drawsNothingUntilACameraIsSet_andInvalidatesOnEveryUpdate() {
        var invalidations = 0
        val renderer = RetainedSkyRenderer(onInvalidate = { invalidations++ })
        renderer.submit(LayerId("a"), scene)
        assertThat(renderer.plan(viewport)).isEmpty()

        renderer.setCamera(SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, 60.0))
        assertThat(renderer.plan(viewport)).hasSize(1)
        assertThat(invalidations).isEqualTo(2)
    }

    @Test
    fun submittingNull_removesTheLayer() {
        val renderer = RetainedSkyRenderer(onInvalidate = {})
        renderer.setCamera(SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, 60.0))
        renderer.submit(LayerId("a"), scene)
        renderer.submit(LayerId("b"), scene)
        renderer.submit(LayerId("a"), null)
        assertThat(renderer.plan(viewport)).hasSize(1)
    }
}
