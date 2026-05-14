package com.google.android.stardroid.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ExperimentConfigImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
    private val executor: ScheduledExecutorService
) : ExperimentConfig {

    private val fetchTask: com.google.android.gms.tasks.Task<Boolean>

    init {
        remoteConfig.setDefaultsAsync(com.google.android.stardroid.R.xml.remote_config_defaults)
        fetchTask = remoteConfig.fetchAndActivate()
        fetchTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d(TAG, "Remote Config fetch successful. Updated: $updated")
                Log.d(TAG, "Remote Config values:")
                remoteConfig.all.toSortedMap().forEach { (key, value) ->
                    val source = when (value.source) {
                        FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "Remote"
                        FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "Default"
                        FirebaseRemoteConfig.VALUE_SOURCE_STATIC -> "Static"
                        else -> "Unknown"
                    }
                    Log.d(TAG, "  $key = ${value.asString()} [$source]")
                }
            } else {
                Log.e(TAG, "Remote Config fetch failed", task.exception)
            }
        }
    }

    override fun isEnabled(experiment: Experiment): Boolean {
        return remoteConfig.getBoolean(experiment.remoteConfigKey)
    }

    override fun waitForInitialFetch(timeoutMs: Long): Boolean {
        if (fetchTask.isComplete) {
            Log.d(TAG, "Initial fetch already completed")
            return true
        }

        val latch = CountDownLatch(1)
        executor.execute {
            try {
                Tasks.await(fetchTask, timeoutMs, TimeUnit.MILLISECONDS)
                Log.d(TAG, "Initial fetch completed within timeout")
            } catch (e: Exception) {
                Log.w(TAG, "Remote Config fetch timeout or failed, using defaults", e)
            } finally {
                latch.countDown()
            }
        }

        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (e: InterruptedException) {
            Log.w(TAG, "Wait for fetch interrupted", e)
            false
        }
    }

    companion object {
        private const val TAG = "ExperimentConfig"
    }
}
