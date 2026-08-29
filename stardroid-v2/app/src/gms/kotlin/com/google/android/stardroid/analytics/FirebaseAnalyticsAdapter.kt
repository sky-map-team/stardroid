/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.analytics

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/** [Analytics] over Firebase Analytics — v1's gms `Analytics` class. */
class FirebaseAnalyticsAdapter(
    application: Application,
) : Analytics {
    private val firebase = FirebaseAnalytics.getInstance(application)

    override fun setEnabled(enabled: Boolean) {
        Log.d(TAG, if (enabled) "Enabling stats collection" else "Disabling stats collection")
        firebase.setAnalyticsCollectionEnabled(enabled)
    }

    override fun trackEvent(
        event: String,
        params: Map<String, Any>,
    ) {
        Log.d(TAG, "Logging event $event, $params")
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        firebase.logEvent(event, bundle)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        Log.d(TAG, "Logging user property $name, $value")
        firebase.setUserProperty(name, value)
    }

    private companion object {
        const val TAG = "FirebaseAnalytics"
    }
}
