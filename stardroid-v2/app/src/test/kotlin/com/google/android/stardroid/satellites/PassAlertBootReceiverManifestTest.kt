/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.satellites

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PassAlertBootReceiverManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private val receiverTag =
        Regex("""<receiver[^>]*PassAlertBootReceiver[^>]*>""").find(manifest)?.value.orEmpty()

    @Test
    fun `the boot receiver is declared`() {
        assertThat(receiverTag).isNotEmpty()
    }

    @Test
    fun `the boot receiver is disabled by default`() {
        assertThat(receiverTag).contains("android:enabled=\"false\"")
    }

    @Test
    fun `the advance alert leaves the boot receiver on until the rise alert fires`() {
        assertThat(PassAlerts.bootReceiverNeededAfterFire(atRise = false)).isTrue()
        assertThat(PassAlerts.bootReceiverNeededAfterFire(atRise = true)).isFalse()
    }
}
