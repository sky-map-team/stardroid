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
     * Whether the notifications experiment is on. Parameters that exist only to opt in to a
     * notification are withheld from [parameters] when it is off: nothing would ever be sent, so
     * offering the switch — and asking for POST_NOTIFICATIONS to back it — is a silent failure.
     * No default, for the same reason as [satellitesEnabled].
     */
    notificationsEnabled: Boolean,
    /**
     * Freshness of the cached satellite element sets, or null where there is no satellite feature
     * to report on (the tour's canned chrome, and tests that do not care).
     */
    satelliteStatus: Flow<SatelliteUiStatus?> = flowOf(null),
) : ViewModel() {
    private val toggleableIds = LayerRegistry.toggleableIds(satellitesEnabled)

    val toggles: StateFlow<List<LayerToggle>> =
        combine(
            combine(
                toggleableIds.map { id ->
                    LayerRegistry.layerEnabled(settings, id).map { LayerToggle(id, it) }
                },
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
            toggleableIds.map { LayerToggle(it, enabled = LayerRegistry.defaultEnabled(it)) },
        )

    /**
     * True when satellites are on but there is nothing cached to draw — the state the empty-state
     * card explains. Distinct from "the layer is off", which needs no explanation.
     */
    val satelliteDataMissing: StateFlow<Boolean> =
        combine(
            LayerRegistry.layerEnabled(settings, SatelliteLayer.LAYER_ID),
            satelliteStatus,
        ) { enabled, status ->
            satellitesEnabled && enabled && status?.freshness == ElementFreshness.ABSENT
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    // Registry parameters minus notification opt-ins while the experiment is off.
    private val offeredParameters =
        LayerRegistry.PARAMETERS.filter { (_, parameter) ->
            notificationsEnabled ||
                !(parameter is LayerParameter.Toggle && parameter.requiresNotificationPermission)
        }

    /**
     * The parameters every layer declares, with their current selections — one entry per
     * (layer, parameter). Empty for every layer but the solar system so far.
     */
    val parameters: StateFlow<List<LayerParameterState>> =
        if (offeredParameters.isEmpty()) {
            MutableStateFlow(emptyList<LayerParameterState>()).asStateFlow()
        } else {
            combine(
                offeredParameters.map { (id, parameter) ->
                    settings
                        .layerParameter(id, parameter.key, parameter.defaultValue)
                        .map { LayerParameterState(id, parameter, it) }
                },
            ) { it.toList() }
                .stateIn(
                    viewModelScope,
                    SharingStarted.Eagerly,
                    offeredParameters.map { (id, p) ->
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
