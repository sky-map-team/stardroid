/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.ui.common

import org.jetbrains.compose.resources.StringResource

/** This platform's What's New text: iOS has its own, since it never had the earlier releases. */
internal expect val whatsNewContentResource: StringResource

/** Whether the beta testers' note leads What's New. iOS has no beta track, so it never does. */
internal expect val betaHelpTextShown: Boolean
