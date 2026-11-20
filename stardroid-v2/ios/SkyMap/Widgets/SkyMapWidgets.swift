/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SwiftUI
import WidgetKit

/// Sky Map's widgets (ios-platform-surfaces.md §6). The astronomy is the shared Kotlin, through
/// SkyMapWidgetKit; Swift only lays out what it computes.
@main
struct SkyMapWidgets: WidgetBundle {
    var body: some Widget {
        MoonWidget()
        TonightWidget()
        CountdownWidget()
    }
}
