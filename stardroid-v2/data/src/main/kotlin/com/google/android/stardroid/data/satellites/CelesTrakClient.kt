/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.seconds

/**
 * Which CelesTrak group to fetch. "Download only what you need" is part of the usage policy, so
 * this is a closed set rather than a free-form query.
 */
enum class SatelliteGroup(
    val queryValue: String,
) {
    /** ISS, Tiangong and station-adjacent objects — about 1 KB. What ships first. */
    STATIONS("stations"),

    /** The ~100 brightest objects, about 50 KB. Phase 5 only. */
    VISUAL("visual"),
}

/**
 * Fetches two-line element sets from CelesTrak.
 *
 * An interface with one method, so the circuit breaker and repository can be tested against a
 * fake without a socket. That testability is the whole reason a real HTTP client library was not
 * worth adding for a single ~1 KB GET every 12 hours.
 */
fun interface CelesTrakClient {
    /**
     * One GET. Implementations must never retry internally — retry policy belongs to
     * [SatelliteFetchPolicy], and a library that helpfully retries is precisely what CelesTrak's
     * usage policy forbids.
     */
    fun fetch(
        group: SatelliteGroup,
        ifModifiedSince: String?,
    ): FetchOutcome
}

/**
 * [CelesTrakClient] over the JDK's own [HttpURLConnection].
 *
 * **No HTTP dependency is added**, extending the stance D79 took for data packs. The entire
 * requirement is one GET, a timeout and a status code; connection pooling, HTTP/2 and
 * interceptors
 * buy nothing against one small request every twelve hours, and the one thing a client library
 * would genuinely add — automatic retry with backoff — is exactly the behaviour this feature must
 * not have.
 */
class HttpUrlConnectionCelesTrakClient(
    private val baseUrl: String = CELESTRAK_GP_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val openConnection: (
        URL,
    ) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : CelesTrakClient {
    override fun fetch(
        group: SatelliteGroup,
        ifModifiedSince: String?,
    ): FetchOutcome {
        val url = URL("$baseUrl?GROUP=${group.queryValue}&FORMAT=tle")
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
            // Never reached the server, or died mid-body: not evidence about CelesTrak, so it must
            // not be reported as an HTTP error or the breaker would punish being offline.
            FetchOutcome.TransportFailure(e)
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

    companion object {
        const val CELESTRAK_GP_URL = "https://celestrak.org/NORAD/elements/gp.php"

        /** Descriptive and contactable, as the policy prefers. */
        const val DEFAULT_USER_AGENT = "SkyMap/2.0 (+https://github.com/sky-map-team/stardroid)"

        private val CONNECT_TIMEOUT = 15.seconds
        private val READ_TIMEOUT = 20.seconds

        /** ~2000x the expected ~1 KB payload — generous headroom, still far from unbounded. */
        private const val MAX_BODY_CHARS = 2 * 1024 * 1024

        /** Read granularity. Comfortably larger than the whole expected payload. */
        private const val CHUNK_CHARS = 8 * 1024
    }
}
