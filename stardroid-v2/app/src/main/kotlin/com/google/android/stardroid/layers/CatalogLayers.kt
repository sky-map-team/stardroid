/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.layers

import com.google.android.stardroid.catalog.CatalogObject
import com.google.android.stardroid.catalog.CatalogRepository
import com.google.android.stardroid.catalog.Figure
import com.google.android.stardroid.catalog.LayerKind
import com.google.android.stardroid.catalog.LocaleSpec
import com.google.android.stardroid.catalog.TypeCode
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
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/**
 * The three catalog-backed layer instances and their [PrimitiveMapping] policies
 * (layers-and-app.md). Colors come from the [SkyColors] palette (D40) and are layer policy,
 * applied at mapping time, because the DB stores semantics only (D12) — v1 baked them into the
 * generated catalogs (`StellarAsciiProtoWriter`, `DeepSkyObjectAsciiProtoWriter`,
 * `constellations.ascii`).
 */
object CatalogLayers {
    val STARS_LAYER_ID = LayerId("catalog/stars")
    val DEEP_SKY_LAYER_ID = LayerId("catalog/deep_sky")
    val CONSTELLATIONS_LAYER_ID = LayerId("catalog/constellations")

    // v1 depth table: constellations 10, deep-sky 20, stars 30.
    private const val CONSTELLATIONS_DEPTH = 10
    private const val DEEP_SKY_DEPTH = 20
    private const val STARS_DEPTH = 30

    private const val CONSTELLATION_LINE_WIDTH_DP = 1.5

    /** The v1 icons are 40 px bitmaps authored for ~2x density: 20 dp on screen. */
    private const val DSO_ICON_SIZE_DP = 20.0

    /** All three catalog layers, wired to [catalog] and re-emitting on [locale] change. */
    fun create(
        catalog: CatalogRepository,
        locale: Flow<LocaleSpec>,
    ): List<CatalogLayer> =
        listOf(
            CatalogLayer(
                id = STARS_LAYER_ID,
                depth = STARS_DEPTH,
                kind = LayerKind.STARS,
                catalog = catalog,
                locale = locale,
                mapping = StarsMapping,
            ),
            CatalogLayer(
                id = DEEP_SKY_LAYER_ID,
                depth = DEEP_SKY_DEPTH,
                kind = LayerKind.DEEP_SKY,
                catalog = catalog,
                locale = locale,
                mapping = DeepSkyMapping,
            ),
            CatalogLayer(
                id = CONSTELLATIONS_LAYER_ID,
                depth = CONSTELLATIONS_DEPTH,
                kind = LayerKind.CONSTELLATIONS,
                catalog = catalog,
                locale = locale,
                mapping = ConstellationsMapping,
            ),
        )

    /** Stars: one stellar point per object (D12 styling in the backend), labels for named ones. */
    internal object StarsMapping : PrimitiveMapping {
        override fun toScene(
            depth: Int,
            objects: List<CatalogObject>,
            figures: List<Figure>,
        ): LayerScene {
            val points =
                objects.map {
                    PointPrimitive(
                        it.position.toGeocentricVector(),
                        PointAppearance.Stellar(
                            // The rare magnitude-less star renders at the faint floor, not
                            // maximally bright.
                            magnitude = it.magnitude ?: FAINT_MAGNITUDE_FALLBACK,
                            colorIndex = it.colorIndex,
                        ),
                    )
                }
            val labels =
                objects.mapNotNull { obj ->
                    label(obj, LabelSize.STANDARD, SkyColors.STAR_LABEL)
                }
            return LayerScene(depth = depth, points = points, labels = labels)
        }
    }

    /** Deep sky: one Lens-Blue-tinted type icon per object (render-API addendum, D40 colors). */
    internal object DeepSkyMapping : PrimitiveMapping {
        override fun toScene(
            depth: Int,
            objects: List<CatalogObject>,
            figures: List<Figure>,
        ): LayerScene {
            val points =
                objects.map {
                    PointPrimitive(
                        it.position.toGeocentricVector(),
                        PointAppearance.Icon(
                            iconFor(it.type),
                            DSO_ICON_SIZE_DP,
                            tint = SkyColors.DEEP_SKY_ICON,
                        ),
                    )
                }
            val labels =
                objects.mapNotNull { obj ->
                    label(
                        obj,
                        LabelSize.MINOR,
                        SkyColors.DEEP_SKY_LABEL,
                        magnitudeBonus = DSO_LABEL_MAGNITUDE_BONUS,
                        // Past the type icon's lower half.
                        offsetDp = DSO_ICON_SIZE_DP / 2 + 2.0,
                    )
                }
            return LayerScene(depth = depth, points = points, labels = labels)
        }
    }

    /** Constellations: figure polylines plus a name label at each constellation's position. */
    internal object ConstellationsMapping : PrimitiveMapping {
        override val usesFigures: Boolean = true

        override fun toScene(
            depth: Int,
            objects: List<CatalogObject>,
            figures: List<Figure>,
        ): LayerScene {
            val lines =
                figures.flatMap { figure ->
                    figure.strokes.map { stroke ->
                        LinePrimitive(
                            stroke.map { it.toGeocentricVector() },
                            SkyColors.CONSTELLATION_LINE,
                            CONSTELLATION_LINE_WIDTH_DP,
                        )
                    }
                }
            val labels =
                objects.mapNotNull { obj ->
                    label(obj, LabelSize.STANDARD, SkyColors.CONSTELLATION_LABEL)
                }
            return LayerScene(depth = depth, lines = lines, labels = labels)
        }
    }

    /**
     * Maps a hierarchical [TypeCode] to its marker icon by walking **up** the type hierarchy
     * (catalog-and-schema.md): unknown subtypes — including ones from future downloaded packs —
     * inherit their nearest known ancestor's icon; unknown roots fall back to [ICON_OTHER].
     * These refs are resolved to bitmaps by the app's asset-backed image loader.
     */
    internal fun iconFor(type: TypeCode): ImageRef {
        var path = type.path
        while (path.isNotEmpty()) {
            DSO_ICONS_BY_TYPE[path]?.let { return it }
            path = path.substringBeforeLast('.', missingDelimiterValue = "")
        }
        return ICON_OTHER
    }

    private val ICON_OTHER = ImageRef("icon/other")

    /** v1 `getDrawableForShape`, keyed by v2 type codes instead of proto shapes. */
    private val DSO_ICONS_BY_TYPE =
        mapOf(
            "galaxy" to ImageRef("icon/galaxy"),
            "cluster.open" to ImageRef("icon/open_cluster"),
            "cluster.globular" to ImageRef("icon/globular_cluster"),
            "cluster" to ImageRef("icon/open_cluster"),
            "nebula.planetary" to ImageRef("icon/planetary_nebula"),
            "nebula.supernova_remnant" to ImageRef("icon/supernova_remnant"),
            "nebula" to ImageRef("icon/diffuse_nebula"),
            // New in the D62 icon set: previously these three objects fell through to
            // ICON_OTHER for want of a glyph.
            "asterism" to ImageRef("icon/asterism"),
            "star_cloud" to ImageRef("icon/asterism"),
        )

    /**
     * Unnamed objects get no label, and so do objects whose best name is a secondary
     * designation ("Alpha Cygni" — searchable, but not label-worthy); the declutterer ranks
     * the rest by [labelPriority].
     *
     * [magnitudeBonus] brightens the magnitude the *declutterer* sees (not the decluttering
     * [labelPriority], which keeps the true magnitude). Deep-sky objects use it because the
     * shared FOV→magnitude threshold tops out near 8.4 at maximum zoom, so a faint-but-famous
     * DSO like the Ring Nebula (M57, mag 9.0) would otherwise never earn a label however far
     * you zoom in. Named DSOs are few and worth labeling; a mag-9 *star* is one of ~10^5 and
     * stays gated because stars pass no bonus.
     */
    private fun label(
        obj: CatalogObject,
        size: LabelSize,
        color: Rgba,
        magnitudeBonus: Double = 0.0,
        offsetDp: Double = LabelStyle.DEFAULT_OFFSET_DP,
    ): LabelPrimitive? {
        if (obj.name.isEmpty() || !obj.nameIsPrimary) return null
        return LabelPrimitive(
            pos = obj.position.toGeocentricVector(),
            text = obj.name,
            style = LabelStyle(size, color, offsetDp = offsetDp),
            priority = labelPriority(obj.magnitude),
            magnitudeForThresholding = obj.magnitude?.minus(magnitudeBonus),
        )
    }

    /**
     * Decluttering rank: brighter objects win screen-space conflicts. Magnitude −1.5 (Sirius)
     * maps to ~100, magnitude 10 to ~0; magnitude-less objects (constellations) rank mid-band,
     * below every bright star but above faint ones.
     */
    internal fun labelPriority(magnitude: Double?): Int {
        if (magnitude == null) return 50
        return (((10.0 - magnitude) / 11.5) * 100).roundToInt().coerceIn(0, 100)
    }

    private const val FAINT_MAGNITUDE_FALLBACK = 6.0

    /**
     * Magnitude the declutterer subtracts from a deep-sky object's real magnitude. 2.0 lets M57
     * (mag 9.0) and the rest of the Messier list clear the FOV→magnitude threshold once the user
     * has zoomed in to look, without flooding wide-field views with faint-DSO labels.
     */
    private const val DSO_LABEL_MAGNITUDE_BONUS = 2.0
}
