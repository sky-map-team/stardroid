/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.objectinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.android.stardroid.astronomy.LunarEclipseCircumstances
import com.google.android.stardroid.astronomy.LunarEclipseType
import com.google.android.stardroid.astronomy.SatellitePass
import com.google.android.stardroid.catalog.ObjectInfo
import com.google.android.stardroid.catalog.SearchHit
import com.google.android.stardroid.ui.common.formatForLocale
import com.google.android.stardroid.ui.common.formattedStringResource
import com.google.android.stardroid.ui.common.rememberCelestialImage
import com.google.android.stardroid.ui.common.rememberDateFormatter
import com.google.android.stardroid.ui.common.rememberTimeFormatter
import com.google.android.stardroid.ui.map.HudFormats
import com.google.android.stardroid.ui.resources.Res
import com.google.android.stardroid.ui.resources.hud_cardinal_directions
import com.google.android.stardroid.ui.resources.object_info_also_known_as
import com.google.android.stardroid.ui.resources.object_info_always_above_horizon
import com.google.android.stardroid.ui.resources.object_info_always_below_horizon
import com.google.android.stardroid.ui.resources.object_info_close
import com.google.android.stardroid.ui.resources.object_info_distance
import com.google.android.stardroid.ui.resources.object_info_eclipse_greatest
import com.google.android.stardroid.ui.resources.object_info_eclipse_title
import com.google.android.stardroid.ui.resources.object_info_eclipse_totality
import com.google.android.stardroid.ui.resources.object_info_find_in_sky
import com.google.android.stardroid.ui.resources.object_info_fun_fact
import com.google.android.stardroid.ui.resources.object_info_image_credit
import com.google.android.stardroid.ui.resources.object_info_magnitude
import com.google.android.stardroid.ui.resources.object_info_mass
import com.google.android.stardroid.ui.resources.object_info_no_eclipse
import com.google.android.stardroid.ui.resources.object_info_rises
import com.google.android.stardroid.ui.resources.object_info_see_also
import com.google.android.stardroid.ui.resources.object_info_sets
import com.google.android.stardroid.ui.resources.object_info_size
import com.google.android.stardroid.ui.resources.object_info_spectral_class
import com.google.android.stardroid.ui.resources.satellite_next_pass
import com.google.android.stardroid.ui.resources.satellite_no_pass
import com.google.android.stardroid.ui.resources.satellite_pass_magnitude
import com.google.android.stardroid.ui.resources.satellite_pass_shadow
import com.google.android.stardroid.ui.resources.satellite_pass_unknown
import com.google.android.stardroid.ui.resources.satellite_pass_when
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_partial
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_penumbral
import com.google.android.stardroid.ui.resources.tonight_lunar_eclipse_total
import com.google.android.stardroid.ui.theme.NightPhotoTint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * v1's `ObjectInfoDialogFragment` as a Compose dialog: photo with credit, description, fun
 * fact, the scientific-data rows (each hidden when absent), and the see-also chips. [onFind]
 * is non-null only when the object's direction is resolvable — it re-aims the search overlay
 * exactly like a search (D33/D45); v1's map dialog had no Find, so this is the v2 upgrade that
 * makes see-also browsing navigable. Tapping the photo expands it full-screen
 * ([ImageExpandOverlay], v1's `onImageClicked`). [riseSet] adds the next rise/set at the
 * observer's location (D51) — null when the object has no position to rise or set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObjectInfoCard(
    info: ObjectInfo,
    riseSet: RiseSetState?,
    nightMode: Boolean,
    onSeeAlso: (SearchHit) -> Unit,
    onFind: ((ObjectInfo) -> Unit)?,
    onImageTap: (ObjectInfo) -> Unit,
    onDismiss: () -> Unit,
    promoRow: (@Composable () -> Unit)? = null,
    satellitePassRow: (@Composable () -> Unit)? = null,
    eclipseRow: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.name) },
        text = {
            ObjectInfoBody(
                info = info,
                riseSet = riseSet,
                nightMode = nightMode,
                onSeeAlso = onSeeAlso,
                onImageTap = onImageTap,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                promoRow = promoRow,
                satellitePassRow = satellitePassRow,
                eclipseRow = eclipseRow,
            )
        },
        confirmButton = {
            val label =
                if (onFind != null) {
                    Res.string.object_info_find_in_sky
                } else {
                    Res.string.object_info_close
                }
            TextButton(onClick = { if (onFind != null) onFind(info) else onDismiss() }) {
                Text(stringResource(label))
            }
        },
        dismissButton =
            if (onFind != null) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.object_info_close))
                    }
                }
            } else {
                null
            },
    )
}

/**
 * The card's content below the title: photo, description, fun fact, data rows, see-also
 * chips. Extracted from [ObjectInfoCard] so the warm welcome can show the same card with a
 * canned object (D61).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObjectInfoBody(
    info: ObjectInfo,
    riseSet: RiseSetState?,
    nightMode: Boolean,
    onSeeAlso: (SearchHit) -> Unit,
    onImageTap: (ObjectInfo) -> Unit,
    modifier: Modifier = Modifier,
    promoRow: (@Composable () -> Unit)? = null,
    /**
     * The next-visible-pass block, for satellite cards only.
     *
     * A slot rather than a parameter bundle, so this composable stays ignorant of satellites —
     * the same shape [promoRow] already uses for the moon-widget offer.
     */
    satellitePassRow: (@Composable () -> Unit)? = null,
    /**
     * The next-lunar-eclipse block, for the Moon's card only (D106).
     *
     * A slot, exactly like [satellitePassRow] and [promoRow], so this composable stays ignorant
     * of eclipses too.
     */
    eclipseRow: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        info.imageRef?.let {
            CelestialImage(
                imageRef = it,
                name = info.name,
                credit = info.imageCredit,
                nightMode = nightMode,
                onTap = { onImageTap(info) },
            )
        }
        info.description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        info.funFact?.let {
            // Set apart by lower emphasis, not italics: iOS's text renderer slants italics more
            // steeply than Android's, which read as too much on the card. The dialog's body text
            // is already the secondary colour, so the fun fact takes it at reduced opacity.
            Text(
                formattedStringResource(Res.string.object_info_fun_fact, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FUN_FACT_ALPHA),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OtherNames(info)
        DataRows(info, riseSet)
        satellitePassRow?.invoke()
        eclipseRow?.invoke()
        promoRow?.invoke()
        if (info.links.isNotEmpty()) {
            Text(
                stringResource(Res.string.object_info_see_also),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow {
                for (link in info.links) {
                    SuggestionChip(
                        onClick = { onSeeAlso(link) },
                        label = { Text(link.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * The "also known as" section: the object's alternative searchable names (aliases and catalog
 * designations), joined into one line. Now that search matches every name but map labels show
 * only the primary, this tells a user who searched, say, "M31" why the card is titled "Andromeda
 * Galaxy". Hidden when the object has no other names.
 */
@Composable
private fun OtherNames(info: ObjectInfo) {
    if (info.otherNames.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(Res.string.object_info_also_known_as),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            info.otherNames.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The v1 scientific-data section: label/value rows, absent fields skipped. */
@Composable
private fun DataRows(
    info: ObjectInfo,
    riseSet: RiseSetState?,
) {
    Column(Modifier.padding(top = 8.dp)) {
        DataRow(Res.string.object_info_distance, info.distance)
        DataRow(Res.string.object_info_size, info.size)
        DataRow(Res.string.object_info_mass, info.mass)
        DataRow(Res.string.object_info_spectral_class, info.spectralClass)
        DataRow(
            Res.string.object_info_magnitude,
            info.magnitude?.let { formatForLocale("%.1f", arrayOf(it)) },
        )
        RiseSetRows(riseSet)
    }
}

/**
 * The rise/set line (D51): the next crossings as locale short times — always within a day, so
 * the hour alone reads naturally — or the single circumpolar/never-rises line when the object
 * doesn't cross the horizon at the observer's location.
 */
@Composable
private fun RiseSetRows(riseSet: RiseSetState?) {
    when (riseSet) {
        null -> {}
        is RiseSetState.Times -> {
            val formatter = rememberTimeFormatter()
            DataRow(
                Res.string.object_info_rises,
                riseSet.rise?.let { formatter(it) },
            )
            DataRow(
                Res.string.object_info_sets,
                riseSet.set?.let { formatter(it) },
            )
        }
        RiseSetState.AlwaysAbove ->
            Text(
                stringResource(Res.string.object_info_always_above_horizon),
                style = MaterialTheme.typography.bodySmall,
            )
        RiseSetState.AlwaysBelow ->
            Text(
                stringResource(Res.string.object_info_always_below_horizon),
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
private fun DataRow(
    label: StringResource,
    value: String?,
) {
    if (value == null) return
    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The card photo, decoded off the main thread from the bundled `celestial_images/` asset tree
 * (v1's Coil `file:///android_asset/` load, without the dependency). A missing or corrupt
 * asset simply shows no image — the card's text is the content of record. Red-tinted in night
 * mode, like every photo (v1's `PorterDuff.MULTIPLY` filter).
 */
@Composable
private fun CelestialImage(
    imageRef: String,
    name: String,
    credit: String?,
    nightMode: Boolean,
    onTap: () -> Unit,
) {
    val bitmap = rememberCelestialImage(imageRef)
    bitmap?.let { image ->
        // v1's layout: 180 dp tall, centerCrop, rounded corners.
        Image(
            bitmap = image,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            colorFilter =
                if (nightMode) {
                    ColorFilter.tint(NightPhotoTint, BlendMode.Modulate)
                } else {
                    null
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTap),
        )
        credit?.let {
            Text(
                formattedStringResource(Res.string.object_info_image_credit, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The next visible pass, on a satellite's info card (D92 phase 4b).
 *
 * This is the answer the design argues is the whole point of the feature: for a satellite,
 * "what is that?" is mostly *"and here is when to look again"*. Tapping it is where someone
 * already is when they want to know.
 *
 * All three empty cases render explicitly rather than collapsing the section, because they mean
 * different things: [pass] null with fresh data is "nothing is coming, which is normal" — visible
 * passes cluster into multi-day seasons separated by multi-week gaps — while stale data means "we
 * could tell you, but the answer would be wrong".
 */
@Composable
fun SatellitePassRow(
    pass: SatellitePass?,
    passTimesReliable: Boolean,
) {
    val formatter = rememberTimeFormatter()
    Text(
        stringResource(Res.string.satellite_next_pass),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    when {
        !passTimesReliable ->
            Text(
                stringResource(Res.string.satellite_pass_unknown),
                style = MaterialTheme.typography.bodySmall,
            )
        pass == null ->
            Text(
                stringResource(Res.string.satellite_no_pass),
                style = MaterialTheme.typography.bodySmall,
            )
        else -> {
            val cardinals = stringArrayResource(Res.array.hud_cardinal_directions)
            Text(
                formattedStringResource(
                    Res.string.satellite_pass_when,
                    formatter(pass.start),
                    cardinals[HudFormats.cardinalIndex(pass.startAzimuthDeg)],
                    pass.maxAltitudeDeg.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                // One decimal and no more: the model is only good to about half a magnitude.
                formattedStringResource(Res.string.satellite_pass_magnitude, pass.peakMagnitude),
                style = MaterialTheme.typography.bodySmall,
            )
            pass.shadowEntry?.let { shadow ->
                // The most memorable thing the app can say: a satellite that fades out mid-sky is
                // the single most-asked question after someone watches it happen.
                Text(
                    formattedStringResource(
                        Res.string.satellite_pass_shadow,
                        formatter(shadow),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * The next lunar eclipse, on the Moon's info card (D106). [circumstances] null renders the
 * explicit "none coming up" line rather than hiding the section — same reasoning as
 * [SatellitePassRow]'s empty states: silence would read as "this card doesn't know", not "there
 * genuinely isn't one".
 */
@Composable
fun EclipseRow(circumstances: LunarEclipseCircumstances?) {
    Text(
        stringResource(Res.string.object_info_eclipse_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    if (circumstances == null) {
        Text(
            stringResource(Res.string.object_info_no_eclipse),
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }
    val typeRes =
        when (circumstances.type) {
            LunarEclipseType.TOTAL -> Res.string.tonight_lunar_eclipse_total
            LunarEclipseType.PARTIAL -> Res.string.tonight_lunar_eclipse_partial
            LunarEclipseType.PENUMBRAL -> Res.string.tonight_lunar_eclipse_penumbral
            // nextLunarEclipse never returns a NONE-type result; nothing to render for one.
            LunarEclipseType.NONE -> return
        }
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    val greatest = circumstances.greatestEclipse
    Text(
        formattedStringResource(
            Res.string.object_info_eclipse_greatest,
            stringResource(typeRes),
            dateFormatter(greatest),
            timeFormatter(greatest),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    val totalityBegin = circumstances.totalityBegin
    val totalityEnd = circumstances.totalityEnd
    if (totalityBegin != null && totalityEnd != null) {
        Text(
            formattedStringResource(
                Res.string.object_info_eclipse_totality,
                timeFormatter(totalityBegin),
                timeFormatter(totalityEnd),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The fun fact's emphasis against the description above it (Material's medium emphasis). */
private const val FUN_FACT_ALPHA = 0.74f
