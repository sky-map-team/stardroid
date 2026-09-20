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
 * Global, per-frame render settings, applied by the backend without any producer resubmitting.
 *
 * @property nightMode when true the backend applies its red transform to **all** colors.
 * @property magnitudeLimit dynamic fine filter on stellar points (coarse filtering is the catalog
 *   query's job); `null` means no extra limit.
 * @property labelScaleFactor global accessibility/font-size multiplier applied to every label size.
 * @property skyGradient the sun-position-dependent sky dome behind all layers (layers-and-app.md:
 *   a render-state, not a scene — the backend owns the dome geometry and colors); `null` draws no
 *   dome (the user's preference is off, or night mode makes it moot).
 * @property transparentBackground when true the backend clears to alpha 0 instead of opaque
 *   black and never draws the sky-gradient dome, so whatever sits under the GL surface (the
 *   through-camera preview, camera-ar-mode.md/D64) shows through the empty sky.
 * @property cameraScrim opacity (0–1) of a full-screen black quad drawn before all layers while
 *   [transparentBackground] is set — the camera dimmer: it darkens the video plane below
 *   without touching the map drawn on top. Ignored when [transparentBackground] is false.
 */
data class RenderState(
    val nightMode: Boolean = false,
    val magnitudeLimit: Double? = null,
    val labelScaleFactor: Double = 1.0,
    val skyGradient: SkyGradient? = null,
    val transparentBackground: Boolean = false,
    val cameraScrim: Double = 0.0,
)

/**
 * Input to the backend's sky-gradient dome: where the sun is, and how hazy the air is.
 *
 * @property sunDirection the sun's geocentric direction as a **unit vector in celestial
 *   (equatorial) coordinates** — the same world frame every primitive and [SkyCamera] use, so the
 *   backend can orient the dome without knowing the observer's local frame.
 * @property zenithDirection the observer's local up, in the same celestial frame. A sun-centred
 *   ramp does not need this — `:render:gles1`'s dome is rotationally symmetric about the sun and
 *   ignores it — but every real sky phenomenon is defined relative to the horizon: the brightening
 *   toward it, the twilight bands, the Belt of Venus and the Earth's shadow rising opposite the
 *   sun. A backend evaluating a scattering model needs the horizon, so the producer supplies it.
 * @property turbidity atmospheric haze, the Preetham model's T: 2 is an exceptionally clear
 *   mountain sky, ~3 a clear day, 6+ hazy or urban. Higher values whiten the sky, widen the
 *   circumsolar aureole and lift the horizon glow. Backends that cannot evaluate a scattering
 *   model ignore it, so this is additive and changes nothing on GLES1.
 */
data class SkyGradient(
    val sunDirection: Vector3,
    val zenithDirection: Vector3,
    val turbidity: Double = DEFAULT_TURBIDITY,
) {
    companion object {
        /** A clear but not pristine sky — the sensible default when nothing measures the air. */
        const val DEFAULT_TURBIDITY = 2.5
    }
}
