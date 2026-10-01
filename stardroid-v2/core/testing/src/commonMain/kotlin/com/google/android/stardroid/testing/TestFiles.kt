/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testing

/*
 * How a common test reads a fixture that a Gradle task produced for it: Gradle passes the file's
 * path in an environment variable, and the test reads the file directly. On the iOS simulator
 * the process reads host files as-is, but only sees variables its test task sets with a
 * `SIMCTL_CHILD_` prefix, which `xcrun simctl` strips as it forwards them.
 */

/** The environment variable [name] of the test process, or null if it is not set. */
expect fun environmentVariable(name: String): String?

/** The whole UTF-8 text file at the absolute [path]. */
expect fun readTextFile(path: String): String
