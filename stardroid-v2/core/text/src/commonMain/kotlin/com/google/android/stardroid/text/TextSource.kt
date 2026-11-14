/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.google.android.stardroid.text

/**
 * Translated text for shared logic, which never reads resources or the system locale itself.
 *
 * [args] fill the string's placeholders in order (`%1$s`, `%2$d`).
 */
fun interface TextSource {
    fun text(
        id: TextId,
        vararg args: Any,
    ): String
}
