/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.startup

import android.util.Log
import com.google.android.stardroid.R
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * [ExperimentConfig] over Firebase Remote Config — v1's gms `ExperimentConfigImpl`.
 *
 * Construction seeds the shipped defaults (`remote_config_defaults.xml`) and starts an
 * async fetch-and-activate. As in v1, nothing blocks on the fetch: a first
 * run answers from the defaults and a fetched flag applies from the next lookup on — for
 * the warm-welcome gate that in practice means the fetch from the previous process. The
 * client's default 12-hour fetch throttle applies.
 */
class RemoteConfigExperimentConfig : ExperimentConfig {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Remote Config fetch successful. Updated: ${task.result}")
            } else {
                Log.e(TAG, "Remote Config fetch failed", task.exception)
            }
        }
    }

    override fun isEnabled(experiment: Experiment): Boolean {
        val value = remoteConfig.getValue(experiment.key)
        // Until setDefaultsAsync lands (it can lose the race against reads in
        // Application.onCreate, like the moon-widget gate), getValue answers from the static
        // empty config — false for every boolean. Answer from the shipped defaults instead;
        // without this the gate disabled the widget component on a fast cold start (D75).
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            ExperimentConfig.Static.isEnabled(experiment)
        } else {
            value.asBoolean()
        }
    }

    private companion object {
        const val TAG = "RemoteConfigExperiment"
    }
}
