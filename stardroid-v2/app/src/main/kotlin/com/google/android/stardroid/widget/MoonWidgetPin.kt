/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Launches the system one-tap pin dialog for the moon widget (D75 discovery). Returns false
 * when the launcher doesn't support pinning — the caller shows the manual-instructions
 * fallback instead.
 */
fun requestPinMoonWidget(context: Context): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return false
    return manager.requestPinAppWidget(
        ComponentName(context, MoonWidgetReceiver::class.java),
        null,
        null,
    )
}
