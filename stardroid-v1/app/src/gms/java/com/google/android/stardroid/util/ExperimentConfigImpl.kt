package com.google.android.stardroid.util

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor() : ExperimentConfig {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        remoteConfig.setDefaultsAsync(com.google.android.stardroid.R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate()
    }

    override fun isEnabled(experiment: Experiment): Boolean {
        return remoteConfig.getBoolean(experiment.remoteConfigKey)
    }
}
