/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.android.stardroid.eclipses.EclipseAlertScheduler
import com.google.android.stardroid.layers.LayerParameter
import com.google.android.stardroid.layers.SatelliteLayer
import com.google.android.stardroid.layers.SolarSystemLayer
import com.google.android.stardroid.notifications.NotificationScheduler
import com.google.android.stardroid.notifications.SkyNotifier
import com.google.android.stardroid.satellites.PassAlerts
import com.google.android.stardroid.satellites.SatellitePassNotifier
import com.google.android.stardroid.satellites.SatelliteScheduler
import com.google.android.stardroid.satellites.tonightSatellitePasses
import com.google.android.stardroid.settings.Settings
import com.google.android.stardroid.startup.Experiment
import com.google.android.stardroid.startup.ExperimentConfig
import com.google.android.stardroid.widget.CountdownWidgetReceiver
import com.google.android.stardroid.widget.MoonWidgetReceiver
import com.google.android.stardroid.widget.TonightWidgetReceiver
import com.google.android.stardroid.widget.WidgetGate
import com.google.android.stardroid.widget.WidgetScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hilt's root: the singleton graph lives in `di/AppModule` and [CatalogAccess] (D59). */
@HiltAndroidApp
class SkyMapApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var experimentConfig: ExperimentConfig

    @Inject lateinit var settings: Settings

    /**
     * The singleton Coil loader — the location map is the only network image. Geoapify
     * serves `Cache-Control: max-age=600`, so the default header-respecting disk cache
     * would refetch the same map every ten minutes; ignoring cache headers keeps an entry
     * until disk-cache eviction, which is right for a static map of a fixed position
     * (a new position is a new URL, and therefore a new entry, anyway).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .build()

    override fun onCreate() {
        super.onCreate()
        // The widget kill switches (D75): evaluated per app start from the current
        // (defaults-or-fetched) config; a remote flip applies after the next fetch cycle.
        WidgetGate.apply(
            this,
            experimentConfig.isEnabled(Experiment.MOON_WIDGET),
            MoonWidgetReceiver::class.java,
        )
        WidgetGate.apply(
            this,
            experimentConfig.isEnabled(Experiment.TONIGHT_WIDGET),
            TonightWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
        )
        WidgetScheduler.syncSchedule(this)
        // Every app start refreshes placed widgets: recent Android doesn't deliver
        // TIME_SET/DATE_CHANGED to dead apps at all, so a clock change made while the
        // process was down lands here (and the periodic jobs bound staleness meanwhile).
        WidgetScheduler.refreshIfPlaced(this)
        // Satellite fetching follows both gates for the process lifetime (D92): the experiment
        // flag, and the user's consent to the network request. Either off cancels the job, so
        // withdrawing consent stops traffic immediately rather than at the next flag read.
        //
        // Note this only ever *schedules*; it never fetches. App opens are strongly diurnally
        // correlated across a timezone, so fetching here would convert user behaviour directly
        // into request volume against CelesTrak.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.satelliteDataEnabled.distinctUntilChanged().collect { consented ->
                SatelliteScheduler.syncSchedule(
                    this@SkyMapApplication,
                    featureEnabled = experimentConfig.isEnabled(Experiment.SATELLITES),
                    dataConsented = consented,
                )
            }
        }
        // Pass alerts (D92 phase 4c). Re-armed on every app start and whenever the opt-in
        // changes: the alarm targets one specific pass, so it has to be recomputed as the night
        // moves on rather than set once and forgotten. Cancelling when the opt-in goes off is
        // what stops an already-armed alarm interrupting someone who just said no.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings
                .layerParameter(
                    SatelliteLayer.LAYER_ID,
                    LayerParameter.PASS_ALERTS,
                    false.toString(),
                ).map { it.toBoolean() }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (!wanted) {
                        PassAlerts.cancel(this@SkyMapApplication)
                        return@collect
                    }
                    SatellitePassNotifier.ensureChannel(this@SkyMapApplication)
                    PassAlerts.armBestPass(
                        this@SkyMapApplication,
                        tonightSatellitePasses(
                            this@SkyMapApplication,
                            settings.savedLocation.first(),
                        ),
                    )
                }
        }
        // Eclipse alerts (D106): a daily WorkManager planner rather than D103's alarm, since
        // eclipse timing needs no Doze-defeating precision — see EclipseAlertScheduler's doc.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings
                .layerParameter(
                    SolarSystemLayer.LAYER_ID,
                    LayerParameter.ECLIPSE_ALERTS,
                    LayerParameter.ECLIPSE_ALERTS_PARAMETER.defaultValue,
                ).map { it.toBoolean() }
                .distinctUntilChanged()
                .collect { wanted ->
                    if (wanted && experimentConfig.isEnabled(Experiment.NOTIFICATIONS)) {
                        EclipseAlertScheduler.ensureScheduled(this@SkyMapApplication)
                    } else {
                        EclipseAlertScheduler.cancel(this@SkyMapApplication)
                    }
                }
        }
        // Notification scheduling follows the preferences for the process lifetime (D77):
        // either opt-in on → planner scheduled; both off → everything cancelled.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(
                settings.showerAlertsEnabled,
                settings.tonightDigestEnabled,
            ) { showers, digest -> showers || digest }
                .distinctUntilChanged()
                .collect { anyEnabled ->
                    if (anyEnabled && experimentConfig.isEnabled(Experiment.NOTIFICATIONS)) {
                        SkyNotifier.ensureChannels(this@SkyMapApplication)
                        NotificationScheduler.ensureScheduled(this@SkyMapApplication)
                    } else {
                        NotificationScheduler.cancel(this@SkyMapApplication)
                    }
                }
        }
    }
}
