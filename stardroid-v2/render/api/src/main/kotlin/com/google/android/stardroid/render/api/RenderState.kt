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
 *
 *   Deliberately has no default, unlike [turbidity]: "a clear day" is a fact about the air with
 *   one sane fallback everywhere, but "which way is up" has none — a default here would be some
 *   arbitrary direction wearing the shape of a real one, silently wrong for every caller that
 *   forgot to set it rather than refusing to compile. `MapViewModel` is the only producer today
 *   and always has an observer frame in hand ([SkyModel.localFrame]); a second producer without
 *   one (a preview scene, a test) should decide its own placeholder rather than inherit ours.
 * @property turbidity atmospheric haze, the Preetham model's T: 2 is an exceptionally clear
 *   mountain sky, ~3 a clear day, 6+ hazy or urban. Higher values whiten the sky, widen the
 *   circumsolar aureole and lift the horizon glow. Backends that cannot evaluate a scattering
 *   model ignore it, so this is additive and changes nothing on GLES1.
 * @property ground the translucent ground below the horizon. It lives *inside* the gradient
 *   rather than beside it in [RenderState] for two reasons. It needs [sunDirection] and
 *   [zenithDirection], which are already here. And the product decision is that one preference
 *   governs both — "draw the atmosphere" — so nesting makes "no sky means no ground" a fact
 *   about the type rather than a convention every producer has to remember: a null
 *   [RenderState.skyGradient] cannot carry a ground.
 */
data class SkyGradient(
    val sunDirection: Vector3,
    val zenithDirection: Vector3,
    val turbidity: Double = DEFAULT_TURBIDITY,
    val ground: Ground = Ground(),
) {
    companion object {
        /** A clear but not pristine sky — the sensible default when nothing measures the air. */
        const val DEFAULT_TURBIDITY = 2.5
    }
}

/**
 * The ground: a translucent shell over the lower hemisphere, standing in for the Earth you are
 * looking through.
 *
 * v2 deliberately lets you see the sky below the horizon — "where is the Sun right now?" is a
 * real question and an opaque ground destroys it — so this is a wash, never a wall. It exists
 * because the alternative reads worse: with nothing drawn below the horizon the lower hemisphere
 * is a black hole, identical at noon and midnight, and the boundary is carried entirely by the
 * horizon line drawn on top of it.
 *
 * **This is shading, not geometry, and that is what makes it render state rather than a layer.**
 * The distinction is not celestial-versus-observer-relative — the horizon and alt-az grid layers
 * are both observer-relative — it is that layers submit vertices while this is a continuous
 * function of view direction. The two places this codebase currently fakes a smooth gradient
 * with geometry are v1's eight-band sky dome and the eight-ring horizon glow, and both band
 * visibly; a ground built the same way would be a third instance.
 *
 * Backends realise it however they can, exactly as they do [SkyGradient] itself: `:render:gles3`
 * evaluates [GroundRamp] per pixel, while `:render:gles1` has no fragment shader and draws only
 * the horizon-hugging term as the ring mesh it already had — see its `GroundDrawer`. So the two
 * backends differ here by more than tuning, which is deliberate.
 *
 * @property opacity how opaque the ground is where it meets the horizon, at full daylight, before
 *   [GroundRamp]'s depth and solar terms scale it down. This is a limit rather than an attained
 *   value — the antialiasing ramp means the realised peak is a fraction of a percent under it —
 *   and it is an upper bound everywhere else. Zero draws no ground at all, which is the off
 *   switch until this becomes a user preference.
 * @property color the ground's hue. Supplied by the producer because the palette lives in the app
 *   (`SkyColors`), as it does for every primitive's colour; the alpha channel is ignored in favour
 *   of [opacity].
 */
data class Ground(
    val opacity: Double = DEFAULT_OPACITY,
    val color: Rgba = DEFAULT_COLOR,
) {
    init {
        require(opacity in 0.0..1.0) { "opacity must be in 0..1, was $opacity" }
    }

    companion object {
        /**
         * Enough to read as a surface without hiding what is behind it. Chosen on device against
         * the analytic sky rather than derived: the test is whether the ground and the daylight
         * sky look like two comparable things meeting at a line, with neither reading as a hole
         * in the other.
         */
        const val DEFAULT_OPACITY = 0.55

        /** A desaturated green, matching `SkyColors.HORIZON_LINE`'s hue. */
        val DEFAULT_COLOR = Rgba(0x59 / 255f, 0x7c / 255f, 0x4a / 255f, 1f)
    }
}
