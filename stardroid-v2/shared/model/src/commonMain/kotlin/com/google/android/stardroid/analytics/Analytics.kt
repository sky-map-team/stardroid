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
 * The usage-analytics edge, v1's `AnalyticsInterface`: the gms flavor adapts Firebase
 * Analytics, the fdroid flavor is [NoOpAnalytics]. Event and property names live in
 * [AnalyticsEvents] and are carried over from v1 verbatim so the GA4 property keeps one
 * continuous event stream across the rewrite.
 *
 * Params are a plain map (not an Android `Bundle`) so callers stay JVM-testable; values
 * must be types Firebase accepts (String, Int, Long, Double).
 */
interface Analytics {
    /** Master collection switch, driven by the `enable_analytics` preference. */
    fun setEnabled(enabled: Boolean)

    fun trackEvent(
        event: String,
        params: Map<String, Any> = emptyMap(),
    )

    fun setUserProperty(
        name: String,
        value: String,
    )
}

/** The fdroid analytics implementation, as in v1: nothing is ever collected or sent. */
object NoOpAnalytics : Analytics {
    override fun setEnabled(enabled: Boolean) {}

    override fun trackEvent(
        event: String,
        params: Map<String, Any>,
    ) {}

    override fun setUserProperty(
        name: String,
        value: String,
    ) {}
}
