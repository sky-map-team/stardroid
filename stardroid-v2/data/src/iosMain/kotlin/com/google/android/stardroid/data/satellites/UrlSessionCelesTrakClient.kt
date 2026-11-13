/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.data.satellites

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_sync
import platform.darwin.dispatch_time

/**
 * [CelesTrakClient] over `NSURLSession`, as [HttpUrlConnectionCelesTrakClient] is over the JDK's
 * connection: one GET, no retry, no redirect followed.
 *
 * Blocks until the request ends (the interface is synchronous), which the timeouts bound, so call
 * it off the main thread.
 */
@OptIn(ExperimentalForeignApi::class)
class UrlSessionCelesTrakClient(
    private val baseUrl: String = CelesTrakEndpoint.GP_URL,
    private val userAgent: String = CelesTrakEndpoint.USER_AGENT,
) : CelesTrakClient {
    override fun fetch(
        group: SatelliteGroup,
        ifModifiedSince: String?,
    ): FetchOutcome {
        val path = PathWatch()
        return try {
            fetchWatching(group, ifModifiedSince, path)
        } finally {
            path.stop()
        }
    }

    private fun fetchWatching(
        group: SatelliteGroup,
        ifModifiedSince: String?,
        path: PathWatch,
    ): FetchOutcome {
        val url =
            NSURL.URLWithString("$baseUrl?GROUP=${group.queryValue}&FORMAT=tle")
                ?: return FetchOutcome.TransportFailure(IllegalArgumentException("bad URL"))
        val request =
            NSMutableURLRequest.requestWithURL(url).apply {
                setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
                setValue(userAgent, forHTTPHeaderField = "User-Agent")
                ifModifiedSince?.let { setValue(it, forHTTPHeaderField = "If-Modified-Since") }
            }
        // Ephemeral, so no cache or cookie jar adds validators or state of its own, and no
        // waiting for connectivity: offline is a transport failure now, not a request that
        // quietly goes out later.
        val configuration =
            NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
                waitsForConnectivity = false
                timeoutIntervalForRequest = REQUEST_TIMEOUT_SECONDS
                timeoutIntervalForResource = RESOURCE_TIMEOUT_SECONDS
                setHTTPCookieStorage(null)
            }
        val session = NSURLSession.sessionWithConfiguration(configuration, NoRedirects(), null)

        var data: NSData? = null
        var response: NSHTTPURLResponse? = null
        var error: NSError? = null
        val done = dispatch_semaphore_create(0)
        session
            .dataTaskWithRequest(request) { body, urlResponse, failure ->
                data = body
                response = urlResponse as? NSHTTPURLResponse
                error = failure
                dispatch_semaphore_signal(done)
            }.resume()
        // The resource timeout already ends the task; this is a backstop so a lost completion
        // can never pin a thread forever.
        val deadline = dispatch_time(DISPATCH_TIME_NOW, WAIT_BACKSTOP_NANOS)
        val timedOut = dispatch_semaphore_wait(done, deadline) != 0L
        session.finishTasksAndInvalidate()
        if (timedOut) return FetchOutcome.TransportFailure(Exception("timed out"))

        error?.let {
            return failureOutcomeFor(it.domain, it.code, it.localizedDescription, path::online)
        }
        val http =
            response ?: return FetchOutcome.TransportFailure(Exception("not an HTTP response"))
        return fetchOutcomeFor(
            status = http.statusCode.toInt(),
            body = { data?.toBoundedString() },
            lastModified = { http.valueForHTTPHeaderField("Last-Modified") },
        )
    }

    /** Answers every redirect with "don't follow", so a 301 reaches the circuit breaker. */
    private class NoRedirects : NSObject(), NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest,
            completionHandler: (NSURLRequest?) -> Unit,
        ) = completionHandler(null)
    }

    /**
     * The default path, watched for the length of one request so a timeout can be told apart
     * from being offline. A satisfied `NWPathMonitor` path is iOS's word for a usable route out.
     */
    private class PathWatch {
        private val queue = dispatch_queue_create("SatelliteFetchPath", null)
        private val monitor = nw_path_monitor_create()
        private var updates = 0
        private var satisfied = false

        init {
            nw_path_monitor_set_queue(monitor, queue)
            nw_path_monitor_set_update_handler(monitor) { current ->
                updates++
                satisfied = nw_path_get_status(current) == nw_path_status_satisfied
            }
            nw_path_monitor_start(monitor)
        }

        /**
         * Satisfied, with no change since the request began. Any change counts against it: a
         * switch mid-request is not evidence about CelesTrak. Read on the monitor's own serial
         * queue, so it sees the handler's writes.
         */
        fun online(): Boolean {
            var result = false
            dispatch_sync(queue) { result = updates == 1 && satisfied }
            return result
        }

        fun stop() = nw_path_monitor_cancel(monitor)
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 20.0
        const val RESOURCE_TIMEOUT_SECONDS = 30.0
        const val WAIT_BACKSTOP_NANOS = 35_000_000_000L
    }
}

/**
 * A response as a [FetchOutcome]: `200` with a usable body is a success, `304` is "not modified",
 * anything else is an HTTP error (a policy event for the circuit breaker, never a retry). Pure,
 * so the mapping is tested without a socket; [body] and [lastModified] are read only for a `200`.
 */
internal fun fetchOutcomeFor(
    status: Int,
    body: () -> String?,
    lastModified: () -> String?,
): FetchOutcome =
    when (status) {
        STATUS_OK ->
            body()?.let { FetchOutcome.Success(it, lastModified()) }
                ?: FetchOutcome.UnusableBody("empty, oversized or undecodable body")

        STATUS_NOT_MODIFIED -> FetchOutcome.NotModified
        else -> FetchOutcome.HttpError(status)
    }

/**
 * A failed task as a [FetchOutcome]. Only a timeout while the path stayed [online] is
 * [FetchOutcome.ServerUnreachable], which is how CelesTrak's firewall dropping us looks; offline
 * fails fast with a different code, since the session does not wait for connectivity.
 */
internal fun failureOutcomeFor(
    domain: String?,
    code: Long,
    description: String,
    online: () -> Boolean,
): FetchOutcome {
    val cause = Exception(description)
    val timedOut = domain == NSURLErrorDomain && code == NSURLErrorTimedOut
    return if (timedOut && online()) {
        FetchOutcome.ServerUnreachable(cause)
    } else {
        FetchOutcome.TransportFailure(cause)
    }
}

private const val STATUS_OK = 200
private const val STATUS_NOT_MODIFIED = 304

/** ~2000x the expected ~1 KB payload: generous headroom, still far from unbounded. */
private const val MAX_BODY_BYTES = 2 * 1024 * 1024

/** UTF-8 text, or null if empty, oversized or undecodable. */
@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBoundedString(): String? {
    val size = length.toInt()
    if (size == 0 || size > MAX_BODY_BYTES) return null
    return NSString.create(this, NSUTF8StringEncoding)?.toString()
}
