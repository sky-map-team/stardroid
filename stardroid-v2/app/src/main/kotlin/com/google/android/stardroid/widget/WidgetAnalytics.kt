/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.BroadcastReceiver
import android.content.Context
import com.google.android.stardroid.analytics.Analytics
import com.google.android.stardroid.analytics.AnalyticsEvents
import com.google.android.stardroid.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a receiver waits for the opt-out preference to load from DataStore. Well inside the
 * ~10s a `goAsync()` receiver gets; on a timeout the event is dropped rather than risk logging
 * for an opted-out user.
 */
private const val OPT_OUT_LOOKUP_TIMEOUT_MS = 5_000L

/** The event params: [extras] plus the `widget` type, which an extra can never overwrite. */
internal fun widgetEventParams(
    widget: String,
    extras: Map<String, Any> = emptyMap(),
): Map<String, Any> = extras + (AnalyticsEvents.WIDGET_TYPE to widget)

/**
 * Logs a widget [event] for [widget] (an `AnalyticsEvents.WIDGET_TYPE_*` value), but only once
 * the `enable_analytics` preference has loaded and says yes. `AppModule` applies that preference
 * to the analytics edge asynchronously, so in a cold process — a widget placed while the app
 * isn't running — logging straight away could beat the opt-out; awaiting the preference is the
 * same guard `MainActivity.logStartupSnapshot` uses.
 */
internal suspend fun logWidgetEvent(
    analytics: Analytics,
    settings: Settings,
    event: String,
    widget: String,
    extras: Map<String, Any> = emptyMap(),
) {
    if (!settings.enableAnalytics.first()) return
    analytics.trackEvent(event, widgetEventParams(widget, extras))
}

/** [logWidgetEvent] through the app singletons, for callers that have a [Context] (D75). */
internal suspend fun trackWidgetEvent(
    context: Context,
    event: String,
    widget: String,
    extras: Map<String, Any> = emptyMap(),
) {
    val entryPoint = widgetEntryPoint(context)
    logWidgetEvent(entryPoint.analytics(), entryPoint.settings(), event, widget, extras)
}

/**
 * Logs a widget lifecycle event from a receiver callback. The opt-out lookup is asynchronous,
 * so this holds the broadcast open with `goAsync()` and finishes it once the event is logged or
 * dropped.
 */
internal fun BroadcastReceiver.trackWidgetEventAsync(
    context: Context,
    event: String,
    widget: String,
) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(OPT_OUT_LOOKUP_TIMEOUT_MS) {
                trackWidgetEvent(context, event, widget)
            }
        } finally {
            pending.finish()
        }
    }
}

/** The `AnalyticsEvents.WIDGET_TYPE_*` value for a widget's [receiver] class. */
internal fun widgetTypeOf(receiver: Class<*>): String =
    when (receiver) {
        MoonWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_MOON
        TonightWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_TONIGHT
        CountdownWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_COUNTDOWN
        else -> AnalyticsEvents.WIDGET_TYPE_UNKNOWN
    }
