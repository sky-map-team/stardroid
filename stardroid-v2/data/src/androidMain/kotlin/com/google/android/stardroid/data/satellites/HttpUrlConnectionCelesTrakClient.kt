/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.time.Duration.Companion.seconds

/**
 * [CelesTrakClient] over the JDK's own [HttpURLConnection].
 *
 * **No HTTP dependency is added**, extending the stance D79 took for data packs. The entire
 * requirement is one GET, a timeout and a status code; connection pooling, HTTP/2 and
 * interceptors
 * buy nothing against one small request every twelve hours, and the one thing a client library
 * would genuinely add — automatic retry with backoff — is exactly the behaviour this feature must
 * not have.
 *
 * [validatedNetwork] returns an id for the default network if Android has validated it, else null
 * ([validatedNetworkOf] in production). It is how a timeout on a working network is told apart
 * from being offline.
 */
class HttpUrlConnectionCelesTrakClient(
    private val validatedNetwork: () -> Long?,
    private val baseUrl: String = CelesTrakEndpoint.GP_URL,
    private val userAgent: String = CelesTrakEndpoint.USER_AGENT,
    private val openConnection: (
        URL,
    ) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : CelesTrakClient {
    override fun fetch(
        group: SatelliteGroup,
        ifModifiedSince: String?,
    ): FetchOutcome {
        val url = URL("$baseUrl?GROUP=${group.queryValue}&FORMAT=tle")
        val networkAtStart = runCatching(validatedNetwork).getOrNull()
        var connection: HttpURLConnection? = null
        return try {
            connection =
                openConnection(url).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT.inWholeMilliseconds.toInt()
                    readTimeout = READ_TIMEOUT.inWholeMilliseconds.toInt()
                    // Named, with a contact URL: CelesTrak's operator has historically preferred
                    // to make contact before firewalling anyone, and an anonymous User-Agent
                    // forecloses that conversation.
                    setRequestProperty("User-Agent", userAgent)
                    // Never follow a redirect silently. A 301 means our URL is outdated, which is
                    // information the circuit breaker must see rather than paper over.
                    instanceFollowRedirects = false
                    ifModifiedSince?.let { setRequestProperty("If-Modified-Since", it) }
                }

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> readBoundedBody(connection)

                HttpURLConnection.HTTP_NOT_MODIFIED -> FetchOutcome.NotModified
                else -> FetchOutcome.HttpError(status)
            }
        } catch (e: IOException) {
            // Usually never reached the server, which is not evidence about CelesTrak and must not
            // be reported as an HTTP error or the breaker would punish being offline. The
            // exception is a timeout with the same validated network as the default at both ends
            // (a switch mid-request is not evidence): that is how CelesTrak's firewall looks.
            val onlineThroughout =
                networkAtStart != null &&
                    runCatching(validatedNetwork).getOrNull() == networkAtStart
            if (onlineThroughout && e.isTimeout()) {
                FetchOutcome.ServerUnreachable(e)
            } else {
                FetchOutcome.TransportFailure(e)
            }
        } catch (e: RuntimeException) {
            // Broader than it looks like it should be, and deliberate. This runs in a background
            // worker, and the platform HTTP stack can raise unchecked exceptions that are not
            // IOException — SecurityException with no INTERNET permission, and assorted
            // IllegalState/IllegalArgument from deep in the stack on malformed input. Any of them
            // means "no element sets today", which is a fetch failure, not a reason to take the
            // process down.
            //
            // Reporting it as a transport failure is also the right breaker treatment: our own bug
            // is not CelesTrak refusing us, so the circuit must not open — and the two-hour
            // minimum interval still bounds how often a persistent one can retry.
            FetchOutcome.TransportFailure(e)
        } finally {
            // Errors (OOM, StackOverflow) are deliberately not caught above: they are not fetch
            // failures and swallowing them would hide a real problem. This still runs for them.
            connection?.disconnect()
        }
    }

    /**
     * Reads the response body with a hard cap, rather than trusting a ~1 KB payload to stay
     * ~1 KB. A captive portal, a hijacked DNS answer, or a CelesTrak misconfiguration can hand
     * back an arbitrarily large body; an over-long one is treated as unusable rather than
     * materialized into memory.
     */
    private fun readBoundedBody(connection: HttpURLConnection): FetchOutcome =
        connection.inputStream.bufferedReader().use { reader ->
            // Grown a chunk at a time rather than pre-allocating [MAX_BODY_CHARS]: the cap is
            // ~2000x the expected payload, so allocating it up front would make the normal ~1 KB
            // fetch cost several megabytes — the opposite of the point.
            val chunk = CharArray(CHUNK_CHARS)
            val body = StringBuilder()
            while (true) {
                val read = reader.read(chunk, 0, chunk.size)
                if (read < 0) break
                if (body.length + read > MAX_BODY_CHARS) {
                    return FetchOutcome.UnusableBody("body exceeds $MAX_BODY_CHARS characters")
                }
                body.appendRange(chunk, 0, read)
            }
            FetchOutcome.Success(
                body = body.toString(),
                lastModified = connection.getHeaderField("Last-Modified"),
            )
        }

    /**
     * Searches suppressed exceptions too: the platform stack can try each of the host's addresses
     * and throw the last failure, so an IPv4 timeout may sit behind an unreachable IPv6 route.
     */
    private fun Throwable.isTimeout(): Boolean =
        generateSequence(this) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .flatMap { sequenceOf(it) + it.suppressed }
            .any { it is SocketTimeoutException }

    companion object {
        private const val MAX_CAUSE_DEPTH = 8

        private val CONNECT_TIMEOUT = 15.seconds
        private val READ_TIMEOUT = 20.seconds

        /** ~2000x the expected ~1 KB payload — generous headroom, still far from unbounded. */
        private const val MAX_BODY_CHARS = 2 * 1024 * 1024

        /** Read granularity. Comfortably larger than the whole expected payload. */
        private const val CHUNK_CHARS = 8 * 1024
    }
}

/**
 * [HttpUrlConnectionCelesTrakClient]'s `validatedNetwork` on a device: the default network's
 * handle if Android's own probe has confirmed it reaches the internet. Null when offline, and also
 * when this app's network access is blocked (Data Saver, background restrictions), because
 * `activeNetwork` is null for a blocked app.
 */
fun validatedNetworkOf(context: Context): () -> Long? {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    return {
        connectivity?.activeNetwork?.let { network ->
            network.networkHandle.takeIf {
                connectivity.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            }
        }
    }
}
