/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal actual fun RemoteImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    loading: @Composable () -> Unit,
    error: @Composable () -> Unit,
    modifier: Modifier,
) {
    val fetched by produceState<Fetched>(Fetched.Loading, url) {
        value = Fetched.Loading
        value =
            try {
                Fetched.Loaded(fetchImage(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Fetched.Failed
            }
    }
    Box(modifier) {
        when (val image = fetched) {
            Fetched.Loading -> loading()
            Fetched.Failed -> error()
            is Fetched.Loaded ->
                Image(
                    image.bitmap,
                    contentDescription,
                    Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                )
        }
    }
}

private sealed interface Fetched {
    data object Loading : Fetched

    data object Failed : Fetched

    class Loaded(
        val bitmap: ImageBitmap,
    ) : Fetched
}

private suspend fun fetchImage(url: String): ImageBitmap {
    val data = fetch(url)
    return withContext(Dispatchers.Default) {
        Image.makeFromEncoded(data.toByteArray()).toComposeImageBitmap()
    }
}

/** [url]'s body, through the shared session and so the shared URL cache; non-2xx fails. */
private suspend fun fetch(url: String): NSData {
    val nsUrl = NSURL.URLWithString(url) ?: throw IllegalArgumentException("bad URL")
    return suspendCancellableCoroutine { continuation ->
        val task =
            NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, response, error ->
                val status = (response as? NSHTTPURLResponse)?.statusCode ?: 0L
                if (data != null && error == null && status in 200L..299L) {
                    continuation.resume(data)
                } else {
                    // A cancelled continuation ignores this, as it does the cancelled task's.
                    continuation.resumeWithException(
                        IllegalStateException("HTTP $status: ${error?.localizedDescription}"),
                    )
                }
            }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
