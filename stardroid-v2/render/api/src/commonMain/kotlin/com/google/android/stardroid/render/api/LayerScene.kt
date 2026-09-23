/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.render.api

/**
 * The complete content of one layer at one instant — plain immutable data, so producers are
 * unit-testable by asserting on its contents with no GL anywhere.
 *
 * There are **no partial updates** (D13): a layer that changes resubmits its whole [LayerScene] via
 * [SkyRenderer.submit]. [depth] orders layers back-to-front (v1's depth table carries over).
 *
 * **Within-scene draw order is part of the contract:** the primitive types draw glows → lines →
 * images → points → labels, and within each type in list order (painter's algorithm, depth buffer
 * off). This is what makes solar-system occlusion correct — the solar-system layer sorts its
 * [images] by descending Earth-distance at every submission, so the Moon (nearest, last) occludes
 * the Sun during an eclipse — and what keeps the horizon line crisp on top of its glow mesh.
 */
data class LayerScene(
    val depth: Int,
    val points: List<PointPrimitive> = emptyList(),
    val lines: List<LinePrimitive> = emptyList(),
    val images: List<ImagePrimitive> = emptyList(),
    val labels: List<LabelPrimitive> = emptyList(),
    val glows: List<GlowPrimitive> = emptyList(),
)
