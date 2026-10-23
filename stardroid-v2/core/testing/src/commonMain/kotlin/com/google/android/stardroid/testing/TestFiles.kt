/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.testing

// Lets a common test read a fixture a Gradle task produced: the path arrives in an environment
// variable (on iOS, SIMCTL_CHILD_-prefixed; see :core:catalog's build script).
expect fun environmentVariable(name: String): String?

expect fun readTextFile(path: String): String
