/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.analytics

/** Records everything for assertion; the JVM tests' [Analytics]. */
class FakeAnalytics : Analytics {
    data class Event(
        val name: String,
        val params: Map<String, Any>,
    )

    val events = mutableListOf<Event>()
    val userProperties = mutableMapOf<String, String>()

    /** Null until [setEnabled] is first called. */
    var enabled: Boolean? = null

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun trackEvent(
        event: String,
        params: Map<String, Any>,
    ) {
        events += Event(event, params)
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        userProperties[name] = value
    }

    fun eventNames(): List<String> = events.map { it.name }
}
