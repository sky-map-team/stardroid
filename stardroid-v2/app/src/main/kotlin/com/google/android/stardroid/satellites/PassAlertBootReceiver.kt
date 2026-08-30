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
import android.content.Context
import android.content.Intent

/**
 * Brings the process up after a reboot so the pass alert (D92 phase 4c) gets re-armed.
 *
 * `AlarmManager` alarms do not survive a reboot, and a user who armed tonight's ISS alert before
 * one gets nothing, with no signal anything was dropped. This receiver carries no logic of its
 * own: `SkyMapApplication.onCreate` already re-derives and re-arms the best pass from current
 * settings on **every** process start (see the comment there), which is exactly what needs to
 * happen here too. `onReceive` only needs to exist for `BOOT_COMPLETED` to launch the process.
 */
class PassAlertBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}
