/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

    override fun getDouble(experiment: Experiment, default: Double): Double {
        val value = remoteConfig.getValue(experiment.remoteConfigKey)
        // STATIC means neither a remote value nor an in-app default (XML) was found.
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) return default
        // Remote values are operator-controlled; a typo (e.g. a non-numeric value) must not crash
        // clients, so fall back to the default if it can't be parsed as a double.
        return try {
            value.asDouble()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Remote config ${experiment.remoteConfigKey} is not a valid double", e)
            default
        }
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
        private val TAG = MiscUtil.getTag(ExperimentConfigImpl::class.java)
    }
}
