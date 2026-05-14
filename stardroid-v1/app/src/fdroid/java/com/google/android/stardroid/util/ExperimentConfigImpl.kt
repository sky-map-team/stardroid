package com.google.android.stardroid.util

import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor(
    private val executor: ScheduledExecutorService
) : ExperimentConfig {
    override fun isEnabled(experiment: Experiment): Boolean {
        return when (experiment) {
            Experiment.WARM_WELCOME -> false
        }
    }

    override fun waitForInitialFetch(timeoutMs: Long): Boolean {
        return true
    }
}
