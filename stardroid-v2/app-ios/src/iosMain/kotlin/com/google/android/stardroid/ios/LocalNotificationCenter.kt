/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.ios

/** One local notification for the system to deliver at [fireAtMillis], with no code running. */
class LocalNotification(
    /** The kind plus the night, so scheduling again replaces rather than duplicates. */
    val id: String,
    val threadId: String,
    val fireAtMillis: Long,
    val title: String,
    val body: String,
    /** The object a tap opens, or null to just open the map. */
    val objectId: String?,
)

enum class NotificationAuthorization { NOT_DETERMINED, DENIED, AUTHORIZED }

/**
 * `UNUserNotificationCenter`, which Kotlin cannot use for scheduling as comfortably as Swift
 * can, so Swift implements this. Callbacks may arrive on any thread.
 */
interface LocalNotificationCenter {
    /** Replaces every pending notification with [notifications]. */
    fun replaceAll(
        notifications: List<LocalNotification>,
        onDone: () -> Unit,
    )

    fun authorization(onResult: (NotificationAuthorization) -> Unit)

    /** Shows the system prompt if it has not been shown; [onResult] is whether alerts are allowed. */
    fun requestAuthorization(onResult: (Boolean) -> Unit)
}
