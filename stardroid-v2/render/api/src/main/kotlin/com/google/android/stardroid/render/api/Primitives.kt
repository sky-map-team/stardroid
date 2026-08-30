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

/*
 * The drawable primitives a producer puts in a [LayerScene].
 *
 * Conventions shared by all primitives:
 * - positions are **unit geocentric** direction vectors ([Vector3] from `:core:math`);
 * - sizes/widths/offsets are **screen-space dp** (the backend resolves dp→px from the surface
 *   density), except [ImagePrimitive.angularSizeDeg], which is world-anchored and scales with zoom;
 * - colors are abstract [Rgba]; the night-mode transform is the backend's, not the producer's.
 */

/** A star or other point source. */
data class PointPrimitive(val pos: Vector3, val appearance: PointAppearance)

/** How a [PointPrimitive] is styled. */
sealed interface PointAppearance {
    /**
     * A catalog star: the backend's `StellarStyler` (D12) maps [magnitude] to size/intensity and
     * optionally tints by [colorIndex] (B−V). Carrying the domain magnitude — rather than a
     * pre-baked size — is what lets [RenderState.magnitudeLimit] re-filter without resubmission.
     */
    data class Stellar(val magnitude: Double, val colorIndex: Double? = null) : PointAppearance

    /** A fixed-appearance dot (planets-as-points, markers): explicit [color] and [sizeDp]. */
    data class Fixed(val color: Rgba, val sizeDp: Double) : PointAppearance

    /**
     * A screen-space icon (deep-sky type markers, meteor-shower radiants): a textured quad of
     * [sizeDp] square, anchored at the point's sky position. Like [Fixed] it does not scale with
     * zoom — unlike [ImagePrimitive], whose angular size is world-anchored. [image] is an opaque
     * [ImageRef] the backend resolves (a `null` resolution skips the icon, D24).
     *
     * [tint] modulates the texture at draw time (upstream #925's tintable-glyph path): glyph
     * assets are authored white so their color is layer policy like every other primitive's,
     * not baked into the bitmap. The default leaves full-color images unchanged.
     */
    data class Icon(
        val image: ImageRef,
        val sizeDp: Double,
        val tint: Rgba = Rgba.WHITE,
    ) : PointAppearance
}

/**
 * A polyline on the celestial sphere (constellation lines, grid, horizon). The backend subdivides
 * long arcs into great-circle segments — v1 did this at construction; it moves to the backend,
 * where the projection lives.
 */
data class LinePrimitive(val vertices: List<Vector3>, val color: Rgba, val widthDp: Double)

/**
 * A filled, additively-blended gradient mesh described by concentric vertex rings (the horizon
 * glow; ports v1's `HorizonGlowPrimitive`). Ring 0 is the outermost loop; each subsequent ring
 * carries its own color, and the backend fills the bands between consecutive rings, interpolating
 * color — including alpha — across each band, so the result is one smooth gradient rather than a
 * stack of discrete translucent strips.
 *
 * Drawn **additively** (the glow adds light to whatever is behind it, black sky or twilight
 * gradient alike, instead of blending toward it) and **before** the scene's lines, so a crisp
 * line can be drawn along the mesh's leading edge.
 *
 * The backend indexes every ring by the first ring's vertex count, so all rings must be the same
 * length; a mesh needs at least two rings of at least two vertices each to form a band, and the
 * backend skips anything smaller. Ring vertices are used as given — no great-circle subdivision.
 */
data class GlowPrimitive(val rings: List<GlowRing>) {
    init {
        val ringLength = rings.firstOrNull()?.vertices?.size ?: 0
        require(rings.all { it.vertices.size == ringLength }) {
            "All rings must have the same vertex count"
        }
    }
}

/** One loop of a [GlowPrimitive]: its vertices and the color the band interpolates from. */
data class GlowRing(val vertices: List<Vector3>, val color: Rgba)

/**
 * A world-anchored image (planet disc, nebula photo). [angularSizeDeg] is its **true** diameter on
 * the sky, so it scales with zoom; [image] is an opaque [ImageRef] the backend resolves.
 *
 * True size alone would make every planet an invisible speck, so [minScreenFraction] (of the short
 * viewport side) and [minSizeDp] floor the drawn size — see [SizeFloor], and note the floor is
 * applied at *draw* time, since a producer's ~1 Hz resubmission cannot track a pinch. A body is
 * drawn at all only when the field of view is at or below [visibleBelowFovDeg], if set.
 *
 * [rotationDeg] is the position angle of the image's up axis, degrees east of north — for a body
 * whose texture is authored north-pole-up, that is the position angle of its **north pole**
 * (D88). It orients the surface only: phase belongs to [terminator], which is applied on top and
 * rotates independently, so the surface stays fixed against the sky as the phase sweeps.
 * A `null` [terminator] draws the image fully lit, which is right for everything that is not a
 * body catching sunlight.
 */
data class ImagePrimitive(
    val center: Vector3,
    val angularSizeDeg: Double,
    val rotationDeg: Double,
    val image: ImageRef,
    val terminator: Terminator? = null,
    val minScreenFraction: Double = 0.0,
    val minSizeDp: Double = 0.0,
    val visibleBelowFovDeg: Double? = null,
)

/**
 * How much of a body is lit, and which way the lit side faces (D88).
 *
 * Backend-agnostic geometry, deliberately: the GLES1 backend composites it into the texture on
 * the CPU because fixed-function GL has no shaders, while a GLES3 backend evaluates the same
 * two numbers per pixel in a fragment shader and drops the compositor entirely. Encoding phase
 * into the [ImageRef] string instead would have made that swap a cross-cutting change.
 *
 * [brightLimbAngleDeg] is the position angle of the lit limb, degrees east of north — Meeus
 * chapter 48's χ, from `brightLimbAngleDeg` in `:core:astronomy`. It is independent of
 * [ImagePrimitive.rotationDeg], which orients the *surface*; that independence is the whole
 * point, since baking both into one bitmap is what used to rotate the Man in the Moon through
 * the month.
 */
data class Terminator(
    val illuminatedFraction: Double,
    val brightLimbAngleDeg: Double,
    val eclipse: EclipseShadow? = null,
)

/**
 * The Moon's position relative to Earth's shadow, for [PhaseCompositor] to tint on top of the
 * ordinary phase shading (D106) — additive to [Terminator], per the seam `render-gles3.md` §7.4
 * already sketched ("`Terminator` becomes a small sealed hierarchy of shadow sources").
 *
 * Every field is a ratio to the Moon's own angular radius, not an absolute angle, so the
 * compositor — which only ever sees a disc normalized to `[-1, 1]` — needs no astronomy of its
 * own: [umbraRadius]/[penumbraRadius] are the two shadow radii in Moon radii, [offset] is the
 * distance from the Moon's centre to the shadow axis in Moon radii, and [directionDeg] is that
 * offset's position angle (degrees east of north), the same convention
 * [Terminator.brightLimbAngleDeg] already uses so the compositor rotates both with one formula.
 *
 * The producer (`SolarSystemLayer`) only constructs one of these when the Moon's disc actually
 * overlaps the penumbra — true on a handful of nights a year — so this stays null the rest of the
 * time.
 */
data class EclipseShadow(
    val umbraRadius: Double,
    val penumbraRadius: Double,
    val offset: Double,
    val directionDeg: Double,
)

/**
 * A text label. [text] is already localized by the producer (the data layer); the renderer only
 * draws it. [priority] is the decluttering rank (higher wins screen-space conflicts).
 *
 * [magnitudeForThresholding], when present, lets the backend apply an FOV-dependent label-depth
 * threshold (zoom in → fainter objects' labels appear); `null` opts the label out of that test
 * entirely. It is deliberately *not* the object's true magnitude: producers bias it (deep-sky
 * objects subtract a bonus so famous-but-faint targets still label) or drop it altogether (the
 * planets, so Neptune and Pluto stay named), while [priority] keeps the honest brightness order.
 */
data class LabelPrimitive(
    val pos: Vector3,
    val text: String,
    val style: LabelStyle,
    val priority: Int,
    val magnitudeForThresholding: Double? = null,
)

/**
 * Visual style of a [LabelPrimitive]. The backend scales [size] by [RenderState.labelScaleFactor].
 *
 * The label hangs below its anchor in *screen space*: [offsetDp] is the gap in dp from the
 * anchor to the label's top edge, constant at every zoom level (D68 — v1's angular offset made
 * the gap grow as you zoomed in). [clearanceDeg] is a minimum *angular* clearance from the
 * anchor to the label's centre, for labels naming angularly-sized image objects (Sun, Moon,
 * planets): when the projected clearance exceeds the fixed pixel gap the label rides the
 * object's edge instead of sitting inside its disc. The backend applies
 * `max(offsetDp-derived, clearanceDeg-projected)`.
 */
data class LabelStyle(
    val size: LabelSize,
    val color: Rgba,
    val offsetDp: Double = DEFAULT_OFFSET_DP,
    val clearanceDeg: Double = 0.0,
    val clearanceMinScreenFraction: Double = 0.0,
    val clearanceMinSizeDp: Double = 0.0,
) {
    companion object {
        /** Default anchor-to-label-top gap: clears a star point with a little air. */
        const val DEFAULT_OFFSET_DP = 4.0
    }
}

/** Coarse label size buckets, scaled per-frame by the accessibility font-size preference. */
enum class LabelSize { TITLE, STANDARD, MINOR }
