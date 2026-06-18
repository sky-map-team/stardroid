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
