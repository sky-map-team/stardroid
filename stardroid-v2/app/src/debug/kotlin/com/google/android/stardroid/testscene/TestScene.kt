/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testscene

import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.render.api.ImagePrimitive
import com.google.android.stardroid.render.api.ImageRef
import com.google.android.stardroid.render.api.LabelPrimitive
import com.google.android.stardroid.render.api.LabelSize
import com.google.android.stardroid.render.api.LabelStyle
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.render.api.LayerScene
import com.google.android.stardroid.render.api.LinePrimitive
import com.google.android.stardroid.render.api.PointAppearance
import com.google.android.stardroid.render.api.PointPrimitive
import com.google.android.stardroid.render.api.Rgba
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The porting-order step-2 hardcoded test scene (D28/D29): drives [GLSkyRenderer] with a
 * synthetic sky — stars, a grid, a planet image, and labels — without the data layer being
 * in place.
 *
 * Star count is set high enough (~100k) to exercise the D13/D19 "no CPU point culling"
 * assumption (points GPU-resident, vertex stage discards offscreen geometry). If the
 * [RendererTestActivity] perf gate (D19) fails at this count, the escape hatch is a backend
 * region index — API-compatible and invisible to producers.
 *
 * Fixed random seed so the generated scene is deterministic (reproducible screenshot comparisons).
 */
internal object TestScene {
    val STARS_LAYER = LayerId("test/stars")
    val GRID_LAYER = LayerId("test/grid")
    val IMAGES_LAYER = LayerId("test/images")
    val LABELS_LAYER = LayerId("test/labels")

    const val STAR_COUNT = 100_000

    /**
     * The test camera: looking toward RA ≈ 6 h (roughly Orion/Gemini border), Dec = 0°, which
     * keeps Betelgeuse, Rigel, and the Orion belt region in the 45° FOV for label verification.
     */
    val CAMERA_LOS = Vector3(0.0, 1.0, 0.0)
    val CAMERA_UP = Vector3(0.0, 0.0, 1.0)
    const val CAMERA_FOV_DEG = 45.0

    fun buildStarsScene(): LayerScene {
        val rng = Random(42L)
        val points =
            buildList(STAR_COUNT) {
                repeat(STAR_COUNT) {
                    // Uniform random point on the unit sphere.
                    val theta = acos(1.0 - 2.0 * rng.nextDouble())
                    val phi = 2.0 * PI * rng.nextDouble()
                    val x = sin(theta) * cos(phi)
                    val y = sin(theta) * sin(phi)
                    val z = cos(theta)
                    // Magnitude distribution weighted toward faint stars.
                    val mag = -1.5 + 9.5 * sqrt(rng.nextDouble())
                    add(PointPrimitive(Vector3(x, y, z), PointAppearance.Stellar(mag)))
                }
            }
        return LayerScene(depth = 0, points = points)
    }

    fun buildGridScene(): LayerScene {
        val gridColor = Rgba(0.4f, 0.5f, 0.9f, 1f)
        val lines = mutableListOf<LinePrimitive>()

        // Meridians (RA lines) every 30° — 12 lines, each sampling 19 points from pole to pole.
        for (i in 0..11) {
            val ra = i * 30.0 * DEGREES_TO_RADIANS
            val vertices =
                (0..18).map { j ->
                    val dec = (j * 10.0 - 90.0) * DEGREES_TO_RADIANS
                    Vector3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
                }
            lines.add(LinePrimitive(vertices, gridColor, 1.5))
        }

        // Parallels (Dec lines) at ±30° and ±60°, plus the equator (0°).
        for (decDeg in intArrayOf(-60, -30, 0, 30, 60)) {
            val dec = decDeg * DEGREES_TO_RADIANS
            val vertices =
                (0..36).map { j ->
                    val ra = j * 10.0 * DEGREES_TO_RADIANS
                    Vector3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
                }
            lines.add(LinePrimitive(vertices, gridColor, 1.5))
        }

        return LayerScene(depth = 1, lines = lines)
    }

    /**
     * Returns a scene with a single synthetic planet placed at RA ≈ 6 h, Dec +20°
     * (within the test camera FOV). The key [TestImageRef.PLANET] is resolved by
     * [RendererTestActivity]'s image loader.
     */
    fun buildImagesScene(): LayerScene {
        val ra = PI / 2.0
        val dec = 20.0 * DEGREES_TO_RADIANS
        val center = Vector3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
        return LayerScene(
            depth = 2,
            images =
                listOf(
                    ImagePrimitive(
                        center,
                        angularSizeDeg = 5.0,
                        rotationDeg = 0.0,
                        image = TestImageRef.PLANET,
                    ),
                ),
        )
    }

    fun buildLabelsScene(): LayerScene {
        return LayerScene(depth = 3, labels = testLabels())
    }

    private fun testLabels(): List<LabelPrimitive> {
        val white = Rgba(1f, 1f, 1f, 1f)
        val orange = Rgba(1f, 0.7f, 0.45f, 1f)
        val blue = Rgba(0.75f, 0.85f, 1f, 1f)
        return listOf(
            // Betelgeuse: RA = 5 h 55 m (88.8°), Dec = +7.4°
            label(
                "Betelgeuse",
                88.8,
                7.4,
                LabelSize.STANDARD,
                orange,
                priority = 90,
                magnitude = 0.42,
            ),
            // Rigel: RA = 5 h 14 m (78.5°), Dec = −8.2°
            label("Rigel", 78.5, -8.2, LabelSize.STANDARD, blue, priority = 85, magnitude = 0.13),
            // Bellatrix: RA = 5 h 25 m (81.3°), Dec = +6.3°
            label("Bellatrix", 81.3, 6.3, LabelSize.MINOR, white, priority = 70, magnitude = 1.64),
            // Mintaka (belt): RA = 5 h 32 m (83.0°), Dec = −0.3°
            label("Mintaka", 83.0, -0.3, LabelSize.MINOR, white, priority = 65, magnitude = 2.21),
            // Alnilam (belt): RA = 5 h 36 m (84.1°), Dec = −1.2°
            label("Alnilam", 84.1, -1.2, LabelSize.MINOR, white, priority = 65, magnitude = 1.65),
            // Alnitak (belt): RA = 5 h 41 m (85.2°), Dec = −1.9°
            label("Alnitak", 85.2, -1.9, LabelSize.MINOR, white, priority = 65, magnitude = 1.74),
            // Sirius: RA = 6 h 45 m (101.3°), Dec = −16.7°  (just inside 45° FOV)
            label(
                "Sirius",
                101.3,
                -16.7,
                LabelSize.TITLE,
                white,
                priority = 100,
                magnitude = -1.46,
            ),
        )
    }

    private fun label(
        text: String,
        raDeg: Double,
        decDeg: Double,
        size: LabelSize,
        color: Rgba,
        priority: Int,
        magnitude: Double,
    ): LabelPrimitive {
        val ra = raDeg * DEGREES_TO_RADIANS
        val dec = decDeg * DEGREES_TO_RADIANS
        val pos = Vector3(cos(dec) * cos(ra), cos(dec) * sin(ra), sin(dec))
        return LabelPrimitive(pos, text, LabelStyle(size, color), priority, magnitude)
    }
}

/** [ImageRef] keys used by the test scene; resolved by [RendererTestActivity]'s image loader. */
internal object TestImageRef {
    val PLANET = ImageRef("test/planet")
}
