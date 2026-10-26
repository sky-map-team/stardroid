/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The body-size cap (audit-2026-08 M6).
 *
 * The expected payload is ~1 KB, but nothing about an HTTP response guarantees that: a captive
 * portal, a hijacked DNS answer or a CelesTrak misconfiguration can return an arbitrarily large
 * body, and this fetch runs in a background WorkManager job where an OOM is a silent feature
 * failure. So the size bound is behaviour worth pinning, not an implementation detail.
 */
class HttpUrlConnectionCelesTrakClientTest {
    @Test
    fun `a normal body is returned intact with its Last-Modified`() {
        val body = "ISS (ZARYA)\n1 25544U ...\n2 25544 ...\n"
        val client =
            clientReturning(
                HttpURLConnection.HTTP_OK,
                body,
                lastModified = "Sat, 15 Aug 2026 12:00:00 GMT",
            )

        val outcome = client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null)

        assertThat(outcome).isInstanceOf(FetchOutcome.Success::class.java)
        val success = outcome as FetchOutcome.Success
        assertThat(success.body).isEqualTo(body)
        assertThat(success.lastModified).isEqualTo("Sat, 15 Aug 2026 12:00:00 GMT")
    }

    @Test
    fun `a body larger than the cap is unusable rather than materialized`() {
        // Comfortably over the 2 MB cap. The point is that this is refused, not truncated:
        // a half-read TLE catalog would parse into plausible-but-wrong element sets.
        val client = clientReturning(HttpURLConnection.HTTP_OK, "x".repeat(3 * 1024 * 1024))

        val outcome = client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null)

        assertThat(outcome).isInstanceOf(FetchOutcome.UnusableBody::class.java)
    }

    @Test
    fun `a body just under the cap is still accepted`() {
        // The boundary matters in the safe direction too — the cap must not reject a large but
        // legitimate group fetch (VISUAL is ~50 KB, and could grow).
        val body = "y".repeat(2 * 1024 * 1024 - 1)
        val client = clientReturning(HttpURLConnection.HTTP_OK, body)

        val outcome = client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null)

        assertThat((outcome as FetchOutcome.Success).body).hasLength(body.length)
    }

    @Test
    fun `a 304 is reported as NotModified without reading a body`() {
        val client = clientReturning(HttpURLConnection.HTTP_NOT_MODIFIED, body = "")

        assertThat(client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = "whenever"))
            .isEqualTo(FetchOutcome.NotModified)
    }

    private fun clientReturning(
        status: Int,
        body: String,
        lastModified: String? = null,
    ) = HttpUrlConnectionCelesTrakClient(
        openConnection = { url -> FakeConnection(url, status, body, lastModified) },
    )

    /**
     * The smallest [HttpURLConnection] that serves this client's needs: a status, a body and one
     * header. Hand-written rather than mocked because the client deliberately depends on the JDK
     * type (no HTTP library, per the class KDoc), and a fake keeps that honest.
     */
    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val body: String,
        private val lastModified: String?,
    ) : HttpURLConnection(url) {
        override fun getResponseCode(): Int = status

        override fun getInputStream(): InputStream = ByteArrayInputStream(body.toByteArray())

        override fun getHeaderField(name: String): String? =
            lastModified.takeIf { name == "Last-Modified" }

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false
    }
}
