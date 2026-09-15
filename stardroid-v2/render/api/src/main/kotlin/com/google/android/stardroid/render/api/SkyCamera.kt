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

/**
 * The per-frame view onto the celestial sphere. The eye sits at the sphere's centre, so there is no
 * position — only orientation and field of view.
 *
 * [fovDeg] is the full field of view across the **shorter** viewport side (the vertical FOV in
 * landscape), so zoom reads the same in portrait and landscape; the longer side's FOV derives
 * from the aspect ([Viewport.aspect]). A [SkyCamera] alone does not determine where a point lands
 * on screen — it lacks the screen; the backend supplies the missing half via its [Viewport] (D21).
 * See [SkyProjection].
 */
data class SkyCamera(
    /** Unit vector, celestial coordinates: the direction the camera looks. */
    val lineOfSight: Vector3,
    /** Unit vector, perpendicular to [lineOfSight]: the screen-up direction. */
    val up: Vector3,
    val fovDeg: Double,
) {
    init {
        require(fovDeg > 0.0 && fovDeg < 180.0) { "fovDeg must be in (0, 180), got $fovDeg" }
        require((lineOfSight cross up).length2 >= 1e-12) {
            "lineOfSight and up must not be collinear"
        }
    }
}
