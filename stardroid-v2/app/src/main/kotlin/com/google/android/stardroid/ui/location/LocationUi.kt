/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.google.android.stardroid.R
import com.google.android.stardroid.location.LocationSource
import com.google.android.stardroid.location.LocationState
import com.google.android.stardroid.math.LatLong
import com.google.android.stardroid.ui.theme.NightPhotoTint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The location management surface (v1 `LocationManagementActivity`, as a sheet until the
 * Navigation graph lands with the screens slice — D44): the static map centered on the
 * current fix (v1's Geoapify image, red-tinted in night mode), current source and
 * coordinates, the auto/manual mode toggle, and the entry point to manual entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSheet(
    viewModel: LocationViewModel,
    nightMode: Boolean,
    onRequestAutoLocation: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val manualMode by viewModel.manualMode.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.location_management_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(sourceLabel(state), style = MaterialTheme.typography.bodyMedium)
            (state as? LocationState.Confirmed)?.let { confirmed ->
                LocationMap(confirmed.location, nightMode)
                Text(
                    stringResource(
                        R.string.location_long_lat,
                        confirmed.location.longitudeDeg,
                        confirmed.location.latitudeDeg,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manualMode) {
                    FilledTonalButton(onClick = onRequestAutoLocation) {
                        Text(stringResource(R.string.location_switch_to_auto))
                    }
                    FilledTonalButton(onClick = onEnterManually) {
                        Text(stringResource(R.string.location_change))
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            viewModel.switchToManual()
                            onEnterManually()
                        },
                    ) {
                        Text(stringResource(R.string.location_switch_to_manual))
                    }
                }
            }
        }
    }
}

/**
 * v1 `LocationManagementActivity`'s map: a static Geoapify image centered on the fix
 * (`GeoapifyMapsAdapter` — plain HTTP, so it works in both flavors), night-mode red-tinted
 * like every other photograph. Key-less builds skip the map entirely and a failed load shows
 * v1's `map_unavailable_label` fallback.
 */
@Composable
private fun LocationMap(
    location: LatLong,
    nightMode: Boolean,
) {
    val apiKey = stringResource(R.string.geoapify_maps_api_key)
    if (apiKey == "unset" || apiKey.isEmpty()) return
    SubcomposeAsyncImage(
        model =
            staticMapUrl(
                latitudeDeg = location.latitudeDeg,
                longitudeDeg = location.longitudeDeg,
                apiKey = apiKey,
            ),
        contentDescription = stringResource(R.string.location_map_content_description),
        contentScale = ContentScale.Crop,
        colorFilter =
            if (nightMode) {
                ColorFilter.tint(NightPhotoTint, BlendMode.Multiply)
            } else {
                null
            },
        loading = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        error = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.location_map_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT)
                .clip(MaterialTheme.shapes.medium),
    )
}

/**
 * v1 `GeoapifyMapsAdapter`'s request, at a fixed cache-friendly size instead of the view's
 * pixel dimensions (Compose lays the image out independently of the fetched bitmap).
 * Coordinates are rounded to three decimals (~110 m, invisible at zoom 11) so GPS jitter
 * doesn't mint a fresh URL — the URL is Coil's disk-cache key, and every distinct URL is
 * another hit on the Geoapify server. Locale-pinned: some default locales format doubles
 * with a ',' decimal separator, which would corrupt the query.
 */
internal fun staticMapUrl(
    latitudeDeg: Double,
    longitudeDeg: Double,
    apiKey: String,
): String {
    val lonLat = "%.3f,%.3f".format(Locale.US, longitudeDeg, latitudeDeg)
    return "https://maps.geoapify.com/v1/staticmap?" +
        "style=osm-bright" +
        "&width=$MAP_IMAGE_WIDTH_PX" +
        "&height=$MAP_IMAGE_HEIGHT_PX" +
        "&center=lonlat:$lonLat" +
        "&zoom=11" +
        "&pitch=50" +
        "&marker=lonlat:$lonLat;color:red" +
        "&apiKey=$apiKey"
}

private val MAP_HEIGHT = 160.dp
private const val MAP_IMAGE_WIDTH_PX = 640
private const val MAP_IMAGE_HEIGHT_PX = 320

@Composable
private fun sourceLabel(state: LocationState): String =
    stringResource(
        when (state) {
            is LocationState.Confirmed ->
                when (state.source) {
                    LocationSource.AUTO -> R.string.location_source_auto
                    LocationSource.MANUAL -> R.string.location_source_manual
                }
            LocationState.Acquiring,
            LocationState.AcquiringTimeout,
            -> R.string.location_source_acquiring
            LocationState.HardwareUnavailable -> R.string.location_source_hardware_unavailable
            LocationState.Unset,
            LocationState.PermissionDenied,
            LocationState.PermissionPermanentlyDenied,
            -> R.string.location_source_unset
        },
    )

/**
 * v1 `ManualLocationEntryDialogFragment`: place-name geocoding into editable latitude and
 * longitude fields with explicit range validation. Field text lives here; validation and
 * geocoding live in [LocationViewModel].
 */
@Composable
fun ManualLocationEntryDialog(
    viewModel: LocationViewModel,
    onDismiss: () -> Unit,
) {
    val entry by viewModel.manualEntry.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val coordinateFormat = stringResource(R.string.location_coordinate_format)
    var placeText by rememberSaveable { mutableStateOf("") }
    // Prefill from the current location, as v1 did when coordinates were known.
    var latitudeText by rememberSaveable {
        mutableStateOf(
            (state as? LocationState.Confirmed)
                ?.let { coordinateFormat.format(it.location.latitudeDeg) } ?: "",
        )
    }
    var longitudeText by rememberSaveable {
        mutableStateOf(
            (state as? LocationState.Confirmed)
                ?.let { coordinateFormat.format(it.location.longitudeDeg) } ?: "",
        )
    }

    // A successful place resolution overwrites the coordinate fields (v1's Resolve).
    LaunchedEffect(entry.resolved) {
        entry.resolved?.let {
            latitudeText = coordinateFormat.format(it.latitudeDeg)
            longitudeText = coordinateFormat.format(it.longitudeDeg)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_manual_entry_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = placeText,
                        onValueChange = { placeText = it },
                        label = { Text(stringResource(R.string.location_place_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.resolving) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        TextButton(onClick = { viewModel.resolvePlace(placeText) }) {
                            Text(stringResource(R.string.location_resolve_button))
                        }
                    }
                }
                entry.placeError?.let { error ->
                    Text(
                        stringResource(
                            when (error) {
                                LocationViewModel.PlaceError.NOT_FOUND ->
                                    R.string.location_place_not_found
                                LocationViewModel.PlaceError.UNAVAILABLE ->
                                    R.string.location_geocoder_offline
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = { latitudeText = it },
                    label = { Text(stringResource(R.string.location_latitude_hint)) },
                    singleLine = true,
                    isError = entry.latitudeInvalid,
                    supportingText =
                        if (entry.latitudeInvalid) {
                            { Text(stringResource(R.string.location_invalid_latitude)) }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = { longitudeText = it },
                    label = { Text(stringResource(R.string.location_longitude_hint)) },
                    singleLine = true,
                    isError = entry.longitudeInvalid,
                    supportingText =
                        if (entry.longitudeInvalid) {
                            { Text(stringResource(R.string.location_invalid_longitude)) }
                        } else {
                            null
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val scope = rememberCoroutineScope()
            Button(
                // A blank while resolving would let a second tap start a duplicate lookup.
                enabled = !entry.resolving,
                onClick = {
                    // Auto-resolve a typed-but-unresolved place before applying (v1 required an
                    // explicit Resolve tap, which was easy to forget).
                    scope.launch {
                        if (viewModel.confirmManualLocation(
                                placeText,
                                latitudeText,
                                longitudeText,
                            )
                        ) {
                            onDismiss()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.location_set_button))
            }
        },
    )
}

/**
 * v1 `LocationPermissionRationaleDialogFragment`: why Sky Map wants the location, with
 * grant / enter-manually / later exits.
 */
@Composable
fun LocationRationaleDialog(
    onGrant: () -> Unit,
    onEnterManually: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.location_permission_dialog_title)) },
        text = { Text(stringResource(R.string.location_permission_dialog_message)) },
        confirmButton = {
            Button(onClick = onGrant) {
                Text(stringResource(R.string.location_permission_grant))
            }
        },
        dismissButton = {
            TextButton(onClick = onEnterManually) {
                Text(stringResource(R.string.location_permission_enter_manually))
            }
            TextButton(onClick = onLater) {
                Text(stringResource(R.string.location_permission_later))
            }
        },
    )
}

/** v1 `LocationPermissionPermanentlyDeniedDialogFragment`: system settings or manual entry. */
@Composable
fun LocationPermanentlyDeniedDialog(
    onOpenSettings: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_permanently_denied_title)) },
        text = { Text(stringResource(R.string.location_permanently_denied_message)) },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.location_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onEnterManually) {
                Text(stringResource(R.string.location_permission_enter_manually))
            }
        },
    )
}

/** v1 `AcquiringLocationTimeoutDialogFragment`: still searching — wait more, or type it. */
@Composable
fun AcquiringTimeoutDialog(
    onKeepWaiting: () -> Unit,
    onEnterManually: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_acquiring_title)) },
        text = { Text(stringResource(R.string.location_acquiring_message)) },
        confirmButton = {
            Button(onClick = onKeepWaiting) {
                Text(stringResource(R.string.location_keep_waiting))
            }
        },
        dismissButton = {
            TextButton(onClick = onEnterManually) {
                Text(stringResource(R.string.location_permission_enter_manually))
            }
        },
    )
}
