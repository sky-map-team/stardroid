/*
 * Copyright (c) 2026 Penterakt LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.google.android.stardroid.activities.dialogs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SearchResultItem(val name: String, val x: Float, val y: Float, val z: Float) : Parcelable {
  override fun toString() = name
}
