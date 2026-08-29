/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render

import com.google.android.stardroid.layers.SkyLayer
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.SkyRenderer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RenderBinderTest {
    private class RecordingRenderer : SkyRenderer {
        val submissions = mutableListOf<Pair<LayerId, LayerScene?>>()
        val cameras = mutableListOf<SkyCamera>()
        val states = mutableListOf<RenderState>()

        override fun submit(
            layerId: LayerId,
            scene: LayerScene?,
        ) {
            submissions += layerId to scene
        }

        override fun setCamera(camera: SkyCamera) {
            cameras += camera
        }

        override fun setRenderState(state: RenderState) {
            states += state
        }
    }

    private class FakeLayer : SkyLayer {
        override val id = LayerId("test/fake")
        override val depth = 42
        val emissions = MutableSharedFlow<LayerScene>()
        var subscriptions = 0

        override fun scenes(): Flow<LayerScene> {
            subscriptions++
            return emissions
        }
    }

    private val renderer = RecordingRenderer()
    private val binder = RenderBinder(renderer)
    private val layer = FakeLayer()
    private val scene = LayerScene(depth = 42)

    @Test
    fun `enabled layer's scenes reach the renderer`() =
        runTest {
            val enabled = MutableStateFlow(true)
            binder.bindLayer(backgroundScope, layer) { enabled }
            testScheduler.runCurrent()
            layer.emissions.emit(scene)
            testScheduler.runCurrent()
            assertThat(renderer.submissions).containsExactly(layer.id to scene)
        }

    @Test
    fun `disabling submits null and stops collection, re-enabling restarts the flow`() =
        runTest {
            val enabled = MutableStateFlow(true)
            binder.bindLayer(backgroundScope, layer) { enabled }
            testScheduler.runCurrent()
            layer.emissions.emit(scene)
            testScheduler.runCurrent()

            enabled.value = false
            testScheduler.runCurrent()
            assertThat(renderer.submissions.last()).isEqualTo(layer.id to null)

            enabled.value = true
            testScheduler.runCurrent()
            layer.emissions.emit(scene)
            testScheduler.runCurrent()
            assertThat(layer.subscriptions).isEqualTo(2)
            assertThat(renderer.submissions.last()).isEqualTo(layer.id to scene)
            coroutineContext.cancelChildren()
        }

    @Test
    fun `layer disabled from the start submits null, never subscribes`() =
        runTest {
            val enabled = MutableStateFlow(false)
            binder.bindLayer(backgroundScope, layer) { enabled }
            testScheduler.runCurrent()
            assertThat(renderer.submissions).containsExactly(layer.id to null)
            assertThat(layer.subscriptions).isEqualTo(0)
        }

    @Test
    fun `camera and render state flows pass through`() =
        runTest {
            val camera = SkyCamera(Vector3(1.0, 0.0, 0.0), Vector3.UNIT_Z, 45.0)
            val state = RenderState(nightMode = true)
            binder.bindCamera(backgroundScope, MutableStateFlow(camera))
            binder.bindRenderState(backgroundScope, MutableStateFlow(state))
            testScheduler.runCurrent()
            assertThat(renderer.cameras).containsExactly(camera)
            assertThat(renderer.states).containsExactly(state)
        }
}
