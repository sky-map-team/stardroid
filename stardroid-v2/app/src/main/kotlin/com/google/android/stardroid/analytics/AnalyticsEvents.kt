/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.analytics

/**
 * Event, parameter, and user-property names, v1's `AnalyticsInterface` constants verbatim —
 * renaming any of these would fork the GA4 event stream. v1 names not ported: the
 * `PREFERENCE_BUTTON_*` pair (v2 has no `PreferencesButton`; layer toggles are
 * [LAYER_TOGGLED_EVENT]) and the `MAP_LOAD_*` set (no Geoapify map preview in v2).
 */
object AnalyticsEvents {
    // User properties.
    const val NEW_USER = "new_user_prop"
    const val DEVICE_SENSORS = "device_sensors_prop"
    const val DEVICE_SENSORS_NONE = "none"
    const val DEVICE_SENSORS_ACCELEROMETER = "accel"
    const val DEVICE_SENSORS_GYRO = "gyro"
    const val DEVICE_SENSORS_MAGNETIC = "mag"
    const val DEVICE_SENSORS_ROTATION = "rot"
    const val HAS_GYRO = "has_gyro_prop"
    const val HAS_ROTATION_VECTOR = "has_rotation_vector_prop"
    const val FIRST_INSTALL_VERSION = "first_install_version_prop"
    const val USER_LOCALE = "user_locale_prop"
    const val COMPLETED_WARM_WELCOME = "completed_warm_welcome_prop"

    // Startup gates.
    const val TOS_ACCEPTED_EVENT = "TOS_accepted_ev"
    const val TOS_REJECTED_EVENT = "TOS_rejected_ev"

    // The start-of-session snapshot.
    const val START_EVENT = "start_up_event_ev"
    const val START_EVENT_HOUR = "local_hour"
    const val START_EVENT_DAY_OF_WEEK = "day_of_week"
    const val START_EVENT_NIGHT_MODE = "night_mode_on"
    const val START_EVENT_SENSOR_PATH = "sensor_path"
    const val SENSOR_PATH_ROTATION_VECTOR = "rotation_vector"
    const val SENSOR_PATH_ACCEL_MAG = "accel_mag"
    const val SENSOR_PATH_NONE = "none"

    // Session length, bucketed on the map screen's foreground time.
    const val SESSION_LENGTH_EVENT = "session_length_ev"
    const val SESSION_LENGTH_TIME_VALUE = "session_length"
    const val SESSION_BUCKET = "session_bucket"

    // Map chrome.
    const val MENU_ITEM_EVENT = "menu_item_pressed_ev"
    const val MENU_ITEM_EVENT_VALUE = "menu_item"
    const val TOGGLED_NIGHT_MODE_LABEL = "night_mode"
    const val SEARCH_REQUESTED_LABEL = "search_requested"
    const val SHARE_SKY_LABEL = "share_sky"
    const val SETTINGS_OPENED_LABEL = "settings_opened"
    const val HELP_OPENED_LABEL = "help_opened"
    const val WHATS_NEW_OPENED_LABEL = "whats_new_opened"
    const val TUTORIAL_OPENED_LABEL = "tutorial_opened"
    const val CALIBRATION_OPENED_LABEL = "calibration_opened"
    const val TIME_TRAVEL_OPENED_LABEL = "time_travel_opened"
    const val GALLERY_OPENED_LABEL = "gallery_opened"
    const val DIAGNOSTICS_OPENED_LABEL = "diagnostics_opened"
    const val MANUAL_MODE_TOGGLED_EVENT = "manual_mode_toggled_ev"
    const val MANUAL_MODE_ENABLED = "enabled"
    const val HORIZON_LEVEL_TOGGLED_EVENT = "horizon_level_toggled_ev"
    const val HORIZON_LEVEL_TOGGLED_VALUE = "enabled"

    // Satellite element fetches (D92). These two are not telemetry for its own sake: CelesTrak's
    // usage policy requires clients to report non-200 responses to a human, and we cannot see
    // individual devices' logs, so the aggregate is what stands in for that. Together they are
    // what would tell us a retry bug is loose in production *before* anyone gets firewalled.
    const val SATELLITE_FETCH_FAILED_EVENT = "satellite_fetch_failed"
    const val SATELLITE_FETCH_HTTP_CODE = "http_code"
    const val SATELLITE_FETCH_CIRCUIT_OPENED = "circuit_opened"
    const val SATELLITE_FETCH_CIRCUIT_OPEN_UNTIL = "circuit_open_until"

    const val SATELLITE_CIRCUIT_CLOSED_EVENT = "satellite_circuit_closed"
    const val SATELLITE_CIRCUIT_OPEN_HOURS = "open_hours"

    // Search (SEARCH_EVENT/SEARCH_TERM mirror Firebase's built-in names).
    const val SEARCH_EVENT = "search"
    const val SEARCH_TERM = "search_term"
    const val SEARCH_SUCCESS = "search_success"
    const val SEARCH_FAILED_EVENT = "search_failed_ev"
    const val OBJECT_LOCKED_EVENT = "object_locked_ev"
    const val OBJECT_LOCKED_NAME = "object_name"
    const val OBJECT_LOCKED_MODE = "mode"
    const val OBJECT_LOCKED_MODE_AUTO = "auto"
    const val OBJECT_LOCKED_MODE_MANUAL = "manual"

    // Time travel.
    const val TIME_TRAVEL_USED_EVENT = "time_travel_used_ev"
    const val TIME_TRAVEL_EVENT_KEY = "travel_event"

    // Preferences and layers.
    const val PREFERENCE_CHANGE_EVENT = "preference_change_ev"
    const val PREFERENCE_CHANGE_EVENT_VALUE = "value"
    const val LAYER_TOGGLED_EVENT = "layer_toggled_ev"
    const val LAYER_TOGGLED_NAME = "layer_name"
    const val LAYER_TOGGLED_ENABLED = "layer_enabled"
    const val LAYER_PARAMETER_EVENT = "layer_parameter_ev"
    const val LAYER_PARAMETER_LAYER = "layer_name"
    const val LAYER_PARAMETER_KEY = "parameter"
    const val LAYER_PARAMETER_VALUE = "value"

    // Object info and the gallery.
    const val OBJECT_INFO_VIEWED_EVENT = "object_info_viewed_ev"
    const val OBJECT_INFO_ID = "object_id"
    const val GALLERY_IMAGE_VIEWED_EVENT = "gallery_image_viewed_ev"
    const val GALLERY_IMAGE_NAME = "image_name"

    // Sensor warnings and the calibration funnel.
    const val NO_SENSORS_WARNING_EVENT = "no_sensors_warning_ev"
    const val CALIBRATION_AUTO_TRIGGERED_EVENT = "calibration_auto_triggered_ev"
    const val CALIBRATION_TOAST_SHOWN_EVENT = "calibration_toast_shown_ev"

    // The warm-welcome funnel.
    const val WARM_WELCOME_STARTED_EVENT = "warm_welcome_started_ev"
    const val WARM_WELCOME_SLIDE_VIEWED_EVENT = "warm_welcome_slide_viewed_ev"
    const val WARM_WELCOME_SLIDE_NUMBER = "slide_number"
    const val WARM_WELCOME_SKIPPED_EVENT = "warm_welcome_skipped_ev"
    const val WARM_WELCOME_COMPLETED_EVENT = "warm_welcome_completed_ev"

    // v2-only: fires once, the first time a device with v1 history launches v2 — no v1
    // equivalent, since v1 never needed to detect a rewrite of itself.
    const val UPGRADED_TO_V2_EVENT = "upgraded_to_v2_ev"
    const val UPGRADED_TO_V2_NEW_VERSION = "new_version"
}

/**
 * v1's session-length buckets (`DynamicStarMapActivity.SessionBucketLength`): the bucket
 * name is the [AnalyticsEvents.SESSION_BUCKET] param of the session-length event.
 */
enum class SessionBucket(private val upperBoundSeconds: Int) {
    LESS_THAN_TEN_SECS(10),
    TEN_SECS_TO_THIRTY_SECS(30),
    THIRTY_SECS_TO_ONE_MIN(60),
    ONE_MIN_TO_FIVE_MINS(300),
    MORE_THAN_FIVE_MINS(Int.MAX_VALUE),
    ;

    companion object {
        fun forSeconds(sessionLengthSeconds: Int): SessionBucket =
            entries.firstOrNull { sessionLengthSeconds < it.upperBoundSeconds } ?: entries.last()
    }
}
