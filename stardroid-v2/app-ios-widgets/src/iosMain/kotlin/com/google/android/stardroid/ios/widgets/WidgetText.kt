/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ios.widgets

import com.google.android.stardroid.astronomy.LunarPhase
import com.google.android.stardroid.events.CountdownTarget
import com.google.android.stardroid.surfaces.NotificationText
import com.google.android.stardroid.text.TextSource
import com.google.android.stardroid.text.TextTemplate
import kotlinx.datetime.Instant
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * The extension's strings, from the `Localizable.strings` that `exportWidgetStrings` writes from
 * the shared XML. The shared [TextSource] keys are [com.google.android.stardroid.text.TextId]'s
 * resource names, so the rows read exactly as Android's widget and the digest do.
 */
internal object WidgetText {
    fun text(
        key: String,
        vararg args: Any,
    ): String = TextTemplate.fill(NSBundle.mainBundle.localizedStringForKey(key, null, null), args)

    val words: NotificationText by lazy {
        NotificationText(TextSource { id, args -> text(id.resourceName, *args) }, ::time)
    }

    private val timeFormatter by lazy {
        NSDateFormatter().apply {
            timeStyle = NSDateFormatterShortStyle
            dateStyle = NSDateFormatterNoStyle
        }
    }

    // "Wed 30 Jul", in the order and words of the device's locale.
    private val shortDateFormatter by lazy {
        NSDateFormatter().apply { setLocalizedDateFormatFromTemplate("EEEdMMM") }
    }

    fun time(time: Instant): String = timeFormatter.stringFromDate(time.toNSDate())

    fun shortDate(time: Instant): String = shortDateFormatter.stringFromDate(time.toNSDate())

    /** The countdown target's name: "Perseids peak", "Full Moon". */
    fun countdownName(target: CountdownTarget): String =
        when (target) {
            is CountdownTarget.ToShowerPeak -> text("countdown_shower_peak", target.shower.name)
            is CountdownTarget.ToMoonPhase ->
                text(
                    if (target.phase == LunarPhase.NEW) {
                        "countdown_new_moon"
                    } else {
                        "countdown_full_moon"
                    },
                )
        }

    private fun Instant.toNSDate(): NSDate =
        NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)
}
