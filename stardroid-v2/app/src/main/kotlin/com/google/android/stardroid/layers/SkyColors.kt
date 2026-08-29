/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.render.api.Rgba

/**
 * The sky-map color palette (D40): one place for every layer's colors, mirroring upstream v1's
 * `colors.xml` "sky rendering layer colors" block and its brand palette
 * (`stardroid-v1/docs/design/visual_design.md` — Star Gold `#FF9F1C`, Lens Blue `#7EC8E3`,
 * Planet Red `#E05C34`).
 *
 * Values are upstream's **true-ARGB** palette (#925): v1's line/point renderer historically
 * decoded packed color ints as ABGR, so the ints in v1 sources were authored pre-swapped and
 * what v1 *rendered* had R and B exchanged relative to those ints. D37 ported the ints as ARGB,
 * silently changing several rendered colors; these constants restore the rendered scheme.
 *
 * **Labels are authored opaque:** v1's label renderer forced alpha to 1 whatever the color's
 * alpha byte said, so v1 sources could reuse a translucent line color for its labels. v2's
 * [com.google.android.stardroid.render.api.LabelPrimitive] honors alpha, so label constants here
 * carry the alpha v1 actually rendered — 1.0 — rather than the line's.
 *
 * The night-mode red transform stays the backend's (D12); these are day-mode colors.
 */
object SkyColors {
    /** Horizon circle and glow: a muted green (upstream `horizon_line` `#78597C4A`). */
    val HORIZON_LINE = Rgba(0x59 / 255f, 0x7c / 255f, 0x4a / 255f, 0x78 / 255f)

    /**
     * Cardinal/zenith/nadir labels share the horizon line's green so the horizon reads as one
     * element (upstream `horizon_label`).
     */
    val HORIZON_LABEL = Rgba(0x59 / 255f, 0x7c / 255f, 0x4a / 255f, 1f)

    /** RA/Dec graticule lines (upstream `grid_line` `#14BCEFF8`). */
    val GRID_LINE = Rgba(0xbc / 255f, 0xef / 255f, 0xf8 / 255f, 0x14 / 255f)

    /** Grid pole/hour/degree labels: the grid color at the opacity v1 rendered. */
    val GRID_LABEL = Rgba(0xbc / 255f, 0xef / 255f, 0xf8 / 255f, 1f)

    /**
     * The ecliptic line and its graduation ticks: opaque dimmed Star Gold (upstream
     * `ecliptic_line` `#FF735314`). Opaque so ticks merge into the line instead of compounding
     * into bright patches where translucent quads overlap; the layer sits just above the grid
     * (depth 5) so the opaque line stays behind constellations, DSOs, and stars.
     */
    val ECLIPTIC_LINE = Rgba(0x73 / 255f, 0x53 / 255f, 0x14 / 255f, 1f)

    /** Ecliptic name/degree labels: full brand Star Gold (upstream `ecliptic_label`). */
    val ECLIPTIC_LABEL = Rgba(0xff / 255f, 0x9f / 255f, 0x1c / 255f, 1f)

    /** Planet/comet/meteor-shower labels: brand Planet Red (upstream `sky_label` `#FFE05C34`). */
    val SKY_LABEL = Rgba(0xe0 / 255f, 0x5c / 255f, 0x34 / 255f, 1f)

    /** Planet dot fallback when no image renders (upstream `planet_body` `#14F67E81`). */
    val PLANET_BODY = Rgba(0xf6 / 255f, 0x7e / 255f, 0x81 / 255f, 0x14 / 255f)

    /** Star labels (v1 `StellarAsciiProtoWriter.STAR_COLOR` `0xCFCCCF` — R=B symmetric). */
    val STAR_LABEL = Rgba(0xcf / 255f, 0xcc / 255f, 0xcf / 255f, 1f)

    /**
     * Deep-sky marker icons: brand Lens Blue, applied as a tint over the white glyphs
     * (upstream `DeepSkyObjectAsciiProtoWriter.POINT_COLOR` `#FF7EC8E3`).
     */
    val DEEP_SKY_ICON = Rgba(0x7e / 255f, 0xc8 / 255f, 0xe3 / 255f, 1f)

    /**
     * Deep-sky labels: a dimmer ~75% Lens Blue so the marker leads and the text reads as
     * secondary (upstream `LABEL_COLOR` `#FF5E96AA`).
     */
    val DEEP_SKY_LABEL = Rgba(0x5e / 255f, 0x96 / 255f, 0xaa / 255f, 1f)

    /** Constellation figure lines (v1 `constellations.ascii` rendered `#80B27CC9`). */
    val CONSTELLATION_LINE = Rgba(0xb2 / 255f, 0x7c / 255f, 0xc9 / 255f, 0x80 / 255f)

    /** Constellation name labels: the line color at the opacity v1 rendered. */
    val CONSTELLATION_LABEL = Rgba(0xb2 / 255f, 0x7c / 255f, 0xc9 / 255f, 1f)
}
