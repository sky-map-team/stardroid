/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Brings the process up after a reboot so the pass alert (D92 phase 4c) gets re-armed.
 *
 * `AlarmManager` alarms do not survive a reboot, and a user who armed tonight's ISS alert before
 * one gets nothing, with no signal anything was dropped. This receiver carries no logic of its
 * own: `SkyMapApplication.onCreate` already re-derives and re-arms the best pass from current
 * settings on **every** process start, which is exactly what needs to happen here too.
 *
 * It is disabled in the manifest and enabled only while an alert is armed ([setEnabled], driven
 * by [PassAlerts]). Otherwise every install, used or not, would start the process on every boot
 * and re-schedule its background jobs (background-network-audit.md, finding 2).
 */
class PassAlertBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit

    companion object {
        fun setEnabled(
            context: Context,
            enabled: Boolean,
        ) {
            val state =
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, PassAlertBootReceiver::class.java),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }

        /** Only an explicit enable counts: the manifest default is disabled. */
        fun isEnabled(context: Context): Boolean =
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, PassAlertBootReceiver::class.java),
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}
