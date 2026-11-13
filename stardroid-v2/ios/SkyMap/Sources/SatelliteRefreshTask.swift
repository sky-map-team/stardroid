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
///
/// It is first asked for on leaving the foreground, and each run asks for the next only while
/// the download gates hold (#1272), so an install nobody opens stops downloading.
enum SatelliteRefreshTask {
    static let identifier = "io.penterakt.skymap.satellite-refresh"

    private static let interval: TimeInterval = 12 * 60 * 60
    private static let jitter: TimeInterval = 60 * 60

    /// Must run before the app finishes launching.
    static func register(analytics: SkyMapKit.Analytics, flags: FirebaseRemoteConfigAdapter) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { task in
            // The stored flags must be readable before the shared graph freezes them.
            flags.prepare {
                let work = SatelliteBackgroundRefreshKt.refreshSatellitesInBackground(
                    analytics: analytics, flags: flags
                ) { completed, scheduleNext in
                    if scheduleNext.boolValue { submit() }
                    task.setTaskCompleted(success: completed.boolValue)
                }
                task.expirationHandler = { work.cancel() }
            }
        }
    }

    /// On leaving the foreground: asks for a refresh if one would download, else drops any
    /// pending request.
    static func schedule() {
        SatelliteBackgroundRefreshKt.satelliteRefreshWanted { wanted in
            if wanted.boolValue {
                submit()
            } else {
                BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
            }
        }
    }

    /// Spread over an hour so installs don't all fetch together.
    private static func submit() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval + .random(in: 0...jitter))
        try? BGTaskScheduler.shared.submit(request)
    }
}
