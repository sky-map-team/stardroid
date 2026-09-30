/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.content.Context
import com.google.android.stardroid.analytics.AnalyticsEvents

/**
 * Logs a widget lifecycle [event] for [widget] (an `AnalyticsEvents.WIDGET_TYPE_*` value). Widget
 * receivers run without a ViewModel, so this reaches the app's analytics through
 * [WidgetEntryPoint], like the rest of the widget code (D75).
 */
internal fun trackWidgetEvent(
    context: Context,
    event: String,
    widget: String,
    extras: Map<String, Any> = emptyMap(),
) {
    widgetEntryPoint(context)
        .analytics()
        .trackEvent(event, mapOf(AnalyticsEvents.WIDGET_TYPE to widget) + extras)
}

/** The `AnalyticsEvents.WIDGET_TYPE_*` value for a widget's [receiver] class. */
internal fun widgetTypeOf(receiver: Class<*>): String =
    when (receiver) {
        MoonWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_MOON
        TonightWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_TONIGHT
        CountdownWidgetReceiver::class.java -> AnalyticsEvents.WIDGET_TYPE_COUNTDOWN
        else -> receiver.simpleName
    }
