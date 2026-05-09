package com.google.android.stardroid.util

interface ExperimentConfig {
    fun isEnabled(experiment: Experiment): Boolean
}
