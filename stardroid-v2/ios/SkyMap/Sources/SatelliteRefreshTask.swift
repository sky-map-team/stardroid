/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import BackgroundTasks
import SkyMapKit

/// Best-effort background refresh of the satellite element sets. The system picks when (often
/// rarely, for a little-used app), so the foreground fetch on app open stays the dependable path;
/// this one just keeps data fresh for people who open the app only briefly.
enum SatelliteRefreshTask {
    static let identifier = "io.penterakt.skymap.satellite-refresh"

    private static let interval: TimeInterval = 12 * 60 * 60
    private static let jitter: TimeInterval = 60 * 60

    /// Must run before the app finishes launching.
    static func register(analytics: SkyMapKit.Analytics, flags: FirebaseRemoteConfigAdapter) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { task in
            schedule()
            // The stored flags must be readable before the shared graph freezes them.
            flags.prepare {
                let work = SatelliteBackgroundRefreshKt.refreshSatellitesInBackground(
                    analytics: analytics, flags: flags
                ) { completed in
                    task.setTaskCompleted(success: completed.boolValue)
                }
                task.expirationHandler = { work.cancel() }
            }
        }
    }

    /// Spread over an hour so installs don't all fetch together.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval + .random(in: 0...jitter))
        try? BGTaskScheduler.shared.submit(request)
    }
}
