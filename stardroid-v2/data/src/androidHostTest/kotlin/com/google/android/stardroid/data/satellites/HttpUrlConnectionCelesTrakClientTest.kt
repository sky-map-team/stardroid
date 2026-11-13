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
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * The body-size cap (audit-2026-08 M6), and telling a firewall from being offline (#1277).
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

    @Test
    fun `a timeout on a validated network is reported as CelesTrak being unreachable`() {
        val outcome = failingWith(SocketTimeoutException("connect timed out"), WIFI, WIFI)

        assertThat(outcome).isInstanceOf(FetchOutcome.ServerUnreachable::class.java)
    }

    @Test
    fun `a timeout without a validated network is just being offline`() {
        assertThat(failingWith(SocketTimeoutException(), null, null))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
        // Validation lost during the request: the network went, not CelesTrak.
        assertThat(failingWith(SocketTimeoutException(), WIFI, null))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
    }

    @Test
    fun `a timeout across a network switch is not evidence about CelesTrak`() {
        // Walked out of Wi-Fi range mid-request: the socket died with the old network, and the
        // new one being validated says nothing about the request that timed out.
        assertThat(failingWith(SocketTimeoutException(), WIFI, CELLULAR))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
    }

    @Test
    fun `only a timeout counts, not any failure on a validated network`() {
        // A refused connection or a DNS failure is not how CelesTrak's firewall behaves.
        assertThat(failingWith(ConnectException("Connection refused"), WIFI, WIFI))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
        assertThat(failingWith(UnknownHostException("celestrak.org"), WIFI, WIFI))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
    }

    @Test
    fun `a timeout behind another address's failure still counts`() {
        // The stack tries each address and throws the last failure, with earlier ones suppressed.
        val lastRoute =
            ConnectException("Network is unreachable").apply {
                addSuppressed(SocketTimeoutException("connect timed out"))
            }

        assertThat(failingWith(lastRoute, WIFI, WIFI))
            .isInstanceOf(FetchOutcome.ServerUnreachable::class.java)
    }

    @Test
    fun `a read timeout after the status line is not CelesTrak being unreachable`() {
        // The server answered, so it is not dropping us; a stalled body is a flaky link.
        val client =
            HttpUrlConnectionCelesTrakClient(
                validatedNetwork = { WIFI },
                openConnection = { url ->
                    object : FakeConnection(url, HttpURLConnection.HTTP_OK, "", null) {
                        override fun getInputStream(): InputStream =
                            throw SocketTimeoutException("Read timed out")
                    }
                },
            )

        assertThat(client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null))
            .isInstanceOf(FetchOutcome.TransportFailure::class.java)
    }

    /** Fails the request with [failure], with [before] and [after] as the validated network. */
    private fun failingWith(
        failure: IOException,
        before: Long?,
        after: Long?,
    ): FetchOutcome {
        val networks = ArrayDeque(listOf(before, after))
        val client =
            HttpUrlConnectionCelesTrakClient(
                validatedNetwork = { networks.removeFirst() },
                openConnection = { url ->
                    object : FakeConnection(url, HttpURLConnection.HTTP_OK, "", null) {
                        override fun getResponseCode(): Int = throw failure
                    }
                },
            )
        return client.fetch(SatelliteGroup.STATIONS, ifModifiedSince = null)
    }

    private fun clientReturning(
        status: Int,
        body: String,
        lastModified: String? = null,
    ) = HttpUrlConnectionCelesTrakClient(
        validatedNetwork = { WIFI },
        openConnection = { url -> FakeConnection(url, status, body, lastModified) },
    )

    /**
     * The smallest [HttpURLConnection] that serves this client's needs: a status, a body and one
     * header. Hand-written rather than mocked because the client deliberately depends on the JDK
     * type (no HTTP library, per the class KDoc), and a fake keeps that honest.
     */
    private open class FakeConnection(
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

    private companion object {
        const val WIFI = 100L
        const val CELLULAR = 101L
    }
}
