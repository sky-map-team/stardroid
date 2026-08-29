/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.layers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.analytics.NoOpAnalytics
import com.google.android.stardroid.data.satellites.ElementFreshness
import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.LayerRegistry
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.render.api.LayerId
import com.google.android.stardroid.satellites.SatelliteUiStatus
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * How healthy a layer's underlying data is, shown as a corner badge on its glyph.
 *
 * A UI-agnostic enum rather than a colour, so the ViewModel stays free of Compose; the mapping to
 * the AGENTS.md status palette happens at the drawing site.
 *
 * Only satellites use this today. The glyph's *shape* stays fixed whatever the badge says, so
 * "is the layer on?" and "is its data any good?" stay two separately readable questions rather
 * than one overloaded icon (the iconography bench, panel C).
 */
enum class LayerDataStatus {
    /** 3–10 days old: usable, worth mentioning. `status_ok`. */
    AGEING,

    /** Past 10 days: degraded enough that pass times are suppressed. `status_warning`. */
    STALE,

    /** Nothing cached at all. `status_absent`. */
    ABSENT,
}

/** One row of the layer-toggle UI. */
data class LayerToggle(
    val id: LayerId,
    val enabled: Boolean,
    /**
     * Data-freshness badge, or null when the layer has no data health to report — which is every
     * layer but satellites, since a star catalog shipped in the APK cannot go stale.
     */
    val dataStatus: LayerDataStatus? = null,
)

/** A layer's declared parameter plus the option currently in force (D87). */
data class LayerParameterState(
    val id: LayerId,
    val parameter: LayerParameter,
    val selected: String,
)

/**
 * Toggle states ↔ DataStore (layers-and-app.md). The registry's toggleable set is static;
 * enablement comes from [Settings], so a change from any writer (this sheet, a future
 * settings screen) reaches both the checkboxes and the render binder.
 */
class LayersViewModel(
    private val settings: Settings,
    private val analytics: Analytics = NoOpAnalytics,
    // No default. A defaulted gate is one nobody has to wire, and an unwired one silently hides
    // the feature with nothing failing to say so — which is exactly what happened first time.
    satellitesEnabled: Boolean,
    /**
     * Freshness of the cached satellite element sets, or null where there is no satellite feature
     * to report on (the tour's canned chrome, and tests that do not care).
     */
    satelliteStatus: Flow<SatelliteUiStatus?> = flowOf(null),
    /**
     * The user-facing Refresh action.
     *
     * Wired to the repository's ordinary `refresh()`, which **respects the circuit breaker and
     * CelesTrak's minimum query interval**. Deliberately not the debug force path: this button is
     * reachable by every user, so it is the one that must never be able to hammer the source.
     * Returns how long the caller must wait, or [Duration.ZERO] when the request was made — the
     * card uses it to explain a refusal rather than appear inert.
     */
    private val onRefreshSatellites: suspend () -> Duration = { Duration.ZERO },
) : ViewModel() {
    private val refusedRefreshWait = MutableStateFlow(Duration.ZERO)

    private val toggleableIds = LayerRegistry.toggleableIds(satellitesEnabled)

    val toggles: StateFlow<List<LayerToggle>> =
        combine(
            combine(
                toggleableIds.map { id -> settings.layerEnabled(id).map { LayerToggle(id, it) } },
            ) { it.toList() },
            satelliteStatus,
        ) { rows, status ->
            rows.map { row ->
                if (row.id == SatelliteLayer.LAYER_ID) {
                    row.copy(dataStatus = status?.freshness?.asDataStatus())
                } else {
                    row
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            toggleableIds.map { LayerToggle(it, enabled = true) },
        )

    /**
     * True when satellites are on but there is nothing cached to draw — the state the empty-state
     * card explains. Distinct from "the layer is off", which needs no explanation.
     */
    val satelliteDataMissing: StateFlow<Boolean> =
        combine(
            settings.layerEnabled(SatelliteLayer.LAYER_ID),
            satelliteStatus,
        ) { enabled, status ->
            satellitesEnabled && enabled && status?.freshness == ElementFreshness.ABSENT
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * How long until a user-triggered refresh would actually reach CelesTrak, or [Duration.ZERO]
     * when it would go now.
     *
     * Drives whether the empty state offers a Refresh button at all. Backed by
     * [refusedRefreshWait] once a tap has been refused, so the message updates immediately rather
     * than waiting for the next poll.
     */
    val satelliteRefreshWait: StateFlow<Duration> =
        combine(satelliteStatus, refusedRefreshWait) { status, refused ->
            maxOf(refused, status?.refreshAllowedIn ?: Duration.ZERO)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, Duration.ZERO)

    /**
     * Asks for a satellite refresh, subject to policy. See [onRefreshSatellites].
     *
     * If policy refuses, the returned wait is published so the card can say when to try again
     * instead of appearing to have done nothing.
     */
    fun refreshSatelliteData() {
        viewModelScope.launch { refusedRefreshWait.value = onRefreshSatellites() }
    }

    /**
     * The sky gradient rides along with the layer toggles in the UI (as it did in v1's layer
     * list) even though it's a render-state, not a layer (layers-and-app.md).
     */
    val skyGradientEnabled: StateFlow<Boolean> =
        settings.showSkyGradient.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /**
     * The pointing HUD, alongside the sky gradient as display state rather than a layer. Off
     * removes it entirely; on leaves it showing and hiding with the rest of the chrome.
     */
    val hudEnabled: StateFlow<Boolean> =
        settings.showHud.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setHudEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setShowHud(enabled) }
    }

    /**
     * The parameters every layer declares, with their current selections — one entry per
     * (layer, parameter). Empty for every layer but the solar system so far.
     */
    val parameters: StateFlow<List<LayerParameterState>> =
        if (LayerRegistry.PARAMETERS.isEmpty()) {
            MutableStateFlow(emptyList<LayerParameterState>()).asStateFlow()
        } else {
            combine(
                LayerRegistry.PARAMETERS.map { (id, parameter) ->
                    settings
                        .layerParameter(id, parameter.key, parameter.defaultValue)
                        .map { LayerParameterState(id, parameter, it) }
                },
            ) { it.toList() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    LayerRegistry.PARAMETERS.map { (id, p) ->
                        LayerParameterState(id, p, p.defaultValue)
                    },
                )
        }

    fun setParameter(
        id: LayerId,
        key: String,
        option: String,
    ) {
        analytics.trackEvent(
            AnalyticsEvents.LAYER_PARAMETER_EVENT,
            mapOf(
                AnalyticsEvents.LAYER_PARAMETER_LAYER to id.id,
                AnalyticsEvents.LAYER_PARAMETER_KEY to key,
                AnalyticsEvents.LAYER_PARAMETER_VALUE to option,
            ),
        )
        viewModelScope.launch { settings.setLayerParameter(id, key, option) }
    }

    fun setEnabled(
        id: LayerId,
        enabled: Boolean,
    ) {
        trackToggle(id.id, enabled)
        viewModelScope.launch { settings.setLayerEnabled(id, enabled) }
    }

    fun setSkyGradientEnabled(enabled: Boolean) {
        // Not a layer internally, but it toggles alongside them in the sheet.
        trackToggle("sky_gradient", enabled)
        viewModelScope.launch { settings.setShowSkyGradient(enabled) }
    }

    /** v1's layer toggle event; the stable `LayerId` replaces the `source_provider.N` map. */
    private fun trackToggle(
        name: String,
        enabled: Boolean,
    ) {
        analytics.trackEvent(
            AnalyticsEvents.LAYER_TOGGLED_EVENT,
            mapOf(
                AnalyticsEvents.LAYER_TOGGLED_NAME to name,
                AnalyticsEvents.LAYER_TOGGLED_ENABLED to enabled.toString(),
            ),
        )
    }
}

/** The AGENTS.md status band a given freshness falls in. Fresh data earns no badge at all. */
private fun ElementFreshness.asDataStatus(): LayerDataStatus? =
    when (this) {
        ElementFreshness.FRESH -> null
        ElementFreshness.AGEING -> LayerDataStatus.AGEING
        ElementFreshness.STALE -> LayerDataStatus.STALE
        ElementFreshness.ABSENT -> LayerDataStatus.ABSENT
    }
