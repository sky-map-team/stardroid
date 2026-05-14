package com.google.android.stardroid.util

import android.util.Log
import com.google.android.stardroid.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : ExperimentConfig {

    private val fetchTask: com.google.android.gms.tasks.Task<Boolean>

    init {
        remoteConfig.setDefaultsAsync(com.google.android.stardroid.R.xml.remote_config_defaults)
        if (BuildConfig.DEBUG) {
            remoteConfig.minimumFetchIntervalInSeconds = 0
        }
        fetchTask = remoteConfig.fetchAndActivate()
        fetchTask.addOnCompleteListener { task ->
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

    fun waitForInitialFetch(timeoutMs: Long = 2000): Boolean {
        return try {
            Tasks.await(fetchTask, timeoutMs, TimeUnit.MILLISECONDS)
            Log.d(TAG, "Initial fetch completed within timeout")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch timeout or failed, using defaults", e)
            false
        }
    }

    companion object {
        private const val TAG = "ExperimentConfig"
    }
}
