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
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.RenderState
import com.google.android.stardroid.render.api.Rgba
import com.google.android.stardroid.render.api.SkyCamera
import com.google.android.stardroid.render.api.Viewport
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FramePlannerTest {
    // Looking down +X, up +Z: screen-right is -Y (see SkyProjectionTest).
    private val camera = SkyCamera(Vector3.UNIT_X, Vector3.UNIT_Z, fovDeg = 90.0)
    private val viewport = Viewport(600, 600, density = 1f)
    private val red = Rgba(1f, 0f, 0f)

    @Test
    fun pointAtTheLineOfSight_landsAtTheCentre() {
        val ops = plan(LayerScene(0, points = listOf(fixedPoint(Vector3.UNIT_X))))
        val dot = ops.single() as DrawOp.Dot
        assertThat(dot.x).isWithin(1e-3f).of(300f)
        assertThat(dot.y).isWithin(1e-3f).of(300f)
        assertThat(dot.radius).isEqualTo(4f) // sizeDp is a diameter
    }

    @Test
    fun pointsBehindTheViewerOrOffScreen_areDropped() {
        val ops =
            plan(
                LayerScene(
                    0,
                    points =
                        listOf(
                            fixedPoint(-Vector3.UNIT_X),
                            fixedPoint(Vector3(1.0, -3.0, 0.0).normalized()),
                        ),
                ),
            )
        assertThat(ops).isEmpty()
    }

    @Test
    fun layersDrawByDepth_andLinesBeforePointsWithinALayer() {
        val back =
            LayerScene(
                depth = 10,
                points = listOf(fixedPoint(Vector3.UNIT_X)),
                lines =
                    listOf(
                        LinePrimitive(listOf(Vector3.UNIT_X, Vector3(1.0, 0.1, 0.0)), red, 1.0),
                    ),
            )
        val front = LayerScene(depth = 20, points = listOf(fixedPoint(Vector3(1.0, 0.2, 0.0))))
        val ops = plan(front, back)
        assertThat(
            ops.map {
                it::class
            },
        ).containsExactly(
            DrawOp.Polyline::class,
            DrawOp.Dot::class,
            DrawOp.Dot::class,
        ).inOrder()
        assertThat((ops[2] as DrawOp.Dot).x).isLessThan(300f) // +Y is screen-left
    }

    @Test
    fun linesSplitWhereTheyPassBehindTheViewer() {
        val vertices =
            listOf(
                Vector3(1.0, 0.1, 0.0),
                Vector3(1.0, -0.1, 0.0),
                // Behind the viewer.
                Vector3(-1.0, 0.0, 0.0),
                Vector3(1.0, 0.0, 0.1),
                Vector3(1.0, 0.0, -0.1),
            ).map { it.normalized() }
        val ops = plan(LayerScene(0, lines = listOf(LinePrimitive(vertices, red, 2.0))))
        assertThat(ops).hasSize(2)
        assertThat(ops.all { (it as DrawOp.Polyline).xs.size == 2 }).isTrue()
    }

    @Test
    fun magnitudeLimit_filtersStellarPoints() {
        val scene =
            LayerScene(
                0,
                points =
                    listOf(
                        PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(1.0)),
                        PointPrimitive(Vector3.UNIT_X, PointAppearance.Stellar(5.0)),
                    ),
            )
        assertThat(plan(scene)).hasSize(2)
        assertThat(plan(scene, state = RenderState(magnitudeLimit = 3.0))).hasSize(1)
    }

    @Test
    fun brighterStars_drawLarger() {
        assertThat(FramePlanner.stellarRadius(-1.0)).isGreaterThan(FramePlanner.stellarRadius(3.0))
        assertThat(FramePlanner.stellarRadius(20.0)).isGreaterThan(0f)
    }

    @Test
    fun nightMode_mapsEveryColourToRed() {
        val green = Rgba(0.2f, 0.9f, 0.1f, 0.5f)
        val ops =
            plan(
                LayerScene(
                    0,
                    points =
                        listOf(
                            PointPrimitive(Vector3.UNIT_X, PointAppearance.Fixed(green, 4.0)),
                        ),
                ),
                state = RenderState(nightMode = true),
            )
        assertThat(ops.single().color).isEqualTo(Rgba(0.9f, 0f, 0f, 0.5f))
    }

    @Test
    fun overlappingLabels_keepTheHigherPriority_andComeAfterEverythingElse() {
        val style = LabelStyle(LabelSize.STANDARD, red)
        val scene =
            LayerScene(
                0,
                points = listOf(fixedPoint(Vector3.UNIT_X)),
                labels =
                    listOf(
                        LabelPrimitive(Vector3.UNIT_X, "Low", style, priority = 1),
                        LabelPrimitive(
                            Vector3(1.0, 0.001, 0.0).normalized(),
                            "High",
                            style,
                            priority = 9,
                        ),
                        LabelPrimitive(
                            Vector3(1.0, 0.0, -0.5).normalized(),
                            "Apart",
                            style,
                            priority = 0,
                        ),
                    ),
            )
        val ops = plan(scene)
        assertThat(ops.first()).isInstanceOf(DrawOp.Dot::class.java)
        assertThat(
            ops.filterIsInstance<DrawOp.Text>().map {
                it.text
            },
        ).containsExactly("High", "Apart").inOrder()
    }

    @Test
    fun labelMagnitudeThreshold_relaxesWhenZoomedIn() {
        val wide = FramePlanner.labelMagnitudeLimit(fovDeg = 70.0, RenderState())
        val narrow = FramePlanner.labelMagnitudeLimit(fovDeg = 17.5, RenderState())
        assertThat(narrow - wide).isWithin(1e-9).of(5.0) // two halvings, 2.5 mag each
        assertThat(
            FramePlanner.labelMagnitudeLimit(17.5, RenderState(magnitudeLimit = 3.0)),
        ).isEqualTo(3.0)
    }

    private fun fixedPoint(pos: Vector3) = PointPrimitive(pos, PointAppearance.Fixed(red, 8.0))

    private fun plan(
        vararg scenes: LayerScene,
        state: RenderState = RenderState(),
    ) = FramePlanner.plan(scenes.toList(), camera, state, viewport)
}
