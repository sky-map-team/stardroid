/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.onboarding

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * v1 `WarmWelcomeActivity.buzz()`: as the welcome's sensor check reveals each sensor, fire a
 * short confident tap when the sensor is [present] and a heavier double-buzz when it is missing.
 * Predefined effects on API 30+ (falling back to hand-rolled waveforms where unsupported), with
 * the notification usage hint so it isn't suppressed like a touch tick.
 *
 * No-ops silently when the device has no vibrator. Requires the `VIBRATE` permission.
 */
internal fun Context.sensorCheckBuzz(present: Boolean) {
    val vibrator = sensorCheckVibrator() ?: return
    if (!vibrator.hasVibrator()) return
    val effect = predefinedOrFallback(vibrator, present)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attrs =
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .build()
        vibrator.vibrate(effect, attrs)
    } else {
        val attrs =
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
        @Suppress("DEPRECATION")
        vibrator.vibrate(effect, attrs)
    }
}

private fun Context.sensorCheckVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

private fun predefinedOrFallback(
    vibrator: Vibrator,
    present: Boolean,
): VibrationEffect {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val effectId =
            if (present) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_DOUBLE_CLICK
        if (vibrator.areEffectsSupported(effectId).firstOrNull() ==
            Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        ) {
            return VibrationEffect.createPredefined(effectId)
        }
    }
    return if (present) {
        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
    } else {
        VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1)
    }
}
