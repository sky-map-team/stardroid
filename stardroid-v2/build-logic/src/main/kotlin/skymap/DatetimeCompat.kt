/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package skymap

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

/**
 * Compose Material 3 1.9 depends on kotlinx-datetime 0.7, where Instant and Clock moved to
 * kotlin.time and the kotlinx.datetime classes the shared modules are compiled against (0.6.1)
 * no longer exist, so an iOS link fails ("IrClassSymbolImpl is unbound ... Instant", or "already
 * bound" when both meet). 0.7.1-0.6.x-compat is the 0.7 API with those classes kept, published
 * for exactly this; this resolves it in the [configurations] given — the Kotlin/Native ones.
 *
 * Android stays on 0.6.1: the shared UI's Android side is androidx Compose, which does not
 * depend on kotlinx-datetime. Moving the whole build to 0.7 (kotlin.time.Instant) is its own
 * change, and removes this.
 */
fun Project.useDatetimeCompat(configurations: (Configuration) -> Boolean) {
    this.configurations.configureEach {
        if (!configurations(this)) return@configureEach
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" &&
                requested.name.startsWith("kotlinx-datetime")
            ) {
                useVersion("0.7.1-0.6.x-compat")
                because("kotlinx.datetime.Instant for the 0.6-compiled shared modules")
            }
        }
    }
}
