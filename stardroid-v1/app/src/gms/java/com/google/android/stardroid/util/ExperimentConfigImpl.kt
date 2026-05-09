package com.google.android.stardroid.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : ExperimentConfig {

    init {
        remoteConfig.setDefaultsAsync(com.google.android.stardroid.R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d(TAG, "Remote Config fetch successful. Updated: $updated")
            } else {
                Log.e(TAG, "Remote Config fetch failed", task.exception)
            }
        }
    }

    override fun isEnabled(experiment: Experiment): Boolean {
        return remoteConfig.getBoolean(experiment.remoteConfigKey)
    }

    companion object {
        private const val TAG = "ExperimentConfig"
    }
}
