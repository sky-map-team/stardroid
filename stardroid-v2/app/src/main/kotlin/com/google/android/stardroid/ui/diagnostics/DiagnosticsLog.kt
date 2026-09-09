/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import android.os.Process
import java.io.IOException

/**
 * The app's own recent log lines, for inclusion in the emailed diagnostics report.
 *
 * `READ_LOGS` has restricted a process to reading only its own log output since Android 4.1 —
 * no other app's or system-wide log is reachable this way, and no permission declaration or
 * runtime request is needed for a process to read its own. `--pid` scopes `logcat` to exactly
 * this process for that reason. A handful of heavily customized OEM ROMs sandbox `exec()`
 * further via SELinux, so this degrades to an empty list rather than crashing the diagnostics
 * screen when `logcat` isn't reachable.
 */
object DiagnosticsLog {
    fun recentLines(maxLines: Int = 200): List<String> =
        try {
            ProcessBuilder(
                "logcat",
                "-d",
                "-t",
                maxLines.toString(),
                "--pid",
                Process.myPid().toString(),
            )
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readLines()
        } catch (e: IOException) {
            emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
}
