/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

/** Feature-flag experiments, keyed as v1's Remote Config parameters. */
enum class Experiment(val key: String) {
    /** The moon-phase home-screen widget and its info-card promo row (D75). */
    MOON_WIDGET("moon_widget_enabled"),

    /** The tonight's-sky and countdown widgets over the events engine (D75 phase 2). */
    TONIGHT_WIDGET("tonight_widget_enabled"),

    /** Shower-peak and tonight-digest notifications and their settings section (D77). */
    NOTIFICATIONS("notifications_enabled"),

    /** Through-camera AR mode and its Layers-sheet toggle (camera-ar-mode.md/D64). */
    CAMERA_AR("camera_ar_enabled"),

    /** Sharing the sky: the overflow row and the in-AR share shutter. */
    SHARE_SKY("share_sky_enabled"),

    /**
     * Satellite tracking — the layer, the pass predictor and the CelesTrak fetch (D92).
     *
     * The primary gate: nothing satellite-related is reachable unless this is on. It matters more
     * than the other flags here because the feature depends on a third party whose usage policy
     * firewalls misbehaving clients, so this is the lever that turns the whole thing off remotely
     * if our traffic ever looks wrong.
     */
    SATELLITES("satellites_enabled"),

    /**
     * Remotely authored announcements on the notification, widget and interstitial surfaces
     * (docs/design/remote-announcements.md). The kill switch for the whole feature.
     */
    ANNOUNCEMENTS("announcements_enabled"),
}

/** The outcome of [ExperimentConfig.fetchNow]. */
sealed interface FetchResult {
    /** This config has no server to fetch from. */
    data object Unsupported : FetchResult

    /** Fetched and activated; [updated] is true when the activated values changed. */
    data class Success(val updated: Boolean) : FetchResult

    data class Failure(val message: String) : FetchResult
}

/**
 * Which experiments are on. The gms flavor backs this with Firebase Remote Config
 * (`RemoteConfigExperimentConfig`); the fdroid flavor answers [Static] — the shipped
 * defaults, as in v1 (D49).
 */
fun interface ExperimentConfig {
    fun isEnabled(experiment: Experiment): Boolean

    /**
     * Epoch millis of the last successful fetch from the server, or null if there has been none
     * (or this config never fetches, as on fdroid).
     */
    val lastFetchTimeMillis: Long? get() = null

    /** Whether [fetchNow] does anything — false for configs with no server. */
    val canFetch: Boolean get() = false

    /**
     * Fetches and activates the server config now, ignoring the usual 12-hour throttle — a
     * testing aid for Diagnostics. Only meaningful when [canFetch] is true.
     */
    suspend fun fetchNow(): FetchResult = FetchResult.Unsupported

    companion object {
        /** The shipped defaults, kept in sync with `remote_config_defaults.xml`. */
        val Static: ExperimentConfig =
            ExperimentConfig {
                when (it) {
                    Experiment.MOON_WIDGET -> true
                    Experiment.TONIGHT_WIDGET -> true
                    Experiment.NOTIFICATIONS -> false
                    Experiment.CAMERA_AR -> false
                    Experiment.SHARE_SKY -> false
                    Experiment.SATELLITES -> false
                    Experiment.ANNOUNCEMENTS -> false
                }
            }
    }
}
