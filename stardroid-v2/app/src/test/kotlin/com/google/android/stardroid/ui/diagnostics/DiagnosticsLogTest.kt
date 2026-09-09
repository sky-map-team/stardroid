/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DiagnosticsLogTest {
    @Test
    fun `never throws even when logcat is unavailable, as on this JVM test host`() {
        // No Android `logcat` binary exists on the JVM unit test host, so this exercises the
        // same IOException fallback that a sandboxed OEM ROM would hit on-device.
        assertThat(DiagnosticsLog.recentLines()).isNotNull()
    }
}
