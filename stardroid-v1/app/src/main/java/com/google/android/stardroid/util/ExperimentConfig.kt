/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.util

interface ExperimentConfig {
    fun isEnabled(experiment: Experiment): Boolean

    /**
     * Returns the numeric value configured for [experiment], or [default] if no remote or
     * built-in default value is available.
     */
    fun getDouble(experiment: Experiment, default: Double): Double

    fun waitForInitialFetch(timeoutMs: Long = 2000): Boolean
}
