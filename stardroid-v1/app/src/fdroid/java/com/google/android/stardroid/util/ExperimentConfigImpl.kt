package com.google.android.stardroid.util

import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor(
    private val executor: ScheduledExecutorService
) : ExperimentConfig {
    override fun isEnabled(experiment: Experiment): Boolean {
        return when (experiment) {
            Experiment.WARM_WELCOME -> false
            else -> false
        }
    }

    // F-Droid has no remote config; always fall back to the call-site default.
    override fun getDouble(experiment: Experiment, default: Double): Double = default

    override fun waitForInitialFetch(timeoutMs: Long): Boolean {
        return true
    }
}
