package com.google.android.stardroid.util

import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor() : ExperimentConfig {
    override fun isEnabled(experiment: Experiment): Boolean {
        return when (experiment) {
            Experiment.WARM_WELCOME -> false
        }
    }
}
