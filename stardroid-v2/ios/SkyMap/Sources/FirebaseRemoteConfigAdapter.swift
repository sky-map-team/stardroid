/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import FirebaseCore
import FirebaseRemoteConfig
import SkyMapKit

/// The shared `FlagSource` over Firebase Remote Config, as Android's gms
/// `RemoteConfigExperimentConfig` is over the same project and keys. The defaults and the list of
/// flags live in Kotlin. Without a Firebase configuration (no bundled GoogleService-Info.plist)
/// it reads nothing and every flag keeps its shipped default.
final class FirebaseRemoteConfigAdapter: NSObject, SkyMapKit.FlagSource {
    private static let fetchInterval: TimeInterval = 12 * 60 * 60

    private let remoteConfig: RemoteConfig?
    private var preparing = false
    private var ready = false
    private var waiting: [() -> Void] = []

    var canFetch: Bool { remoteConfig != nil }

    override init() {
        remoteConfig = FirebaseApp.app() != nil ? RemoteConfig.remoteConfig() : nil
        super.init()
    }

    /// Calls `done` once the stored values are readable, or after a short wait if they are not,
    /// because the flags are read once at launch and must not see a half-loaded config. Safe to
    /// call again (the background refresh and the first scene both do): later callers wait for,
    /// or get, the same answer. Main thread only, which is why the state needs no lock.
    func prepare(done: @escaping () -> Void) {
        if ready {
            done()
            return
        }
        waiting.append(done)
        guard !preparing else { return }
        preparing = true
        let finish = { [self] in
            guard !ready else { return }
            ready = true
            let callbacks = waiting
            waiting = []
            callbacks.forEach { $0() }
        }
        guard let remoteConfig else {
            finish()
            return
        }
        remoteConfig.ensureInitialized { _ in DispatchQueue.main.async(execute: finish) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5, execute: finish)
    }

    func serverValue(key: String) -> KotlinBoolean? {
        guard let remoteConfig else { return nil }
        let value = remoteConfig.configValue(forKey: key)
        // Only a value the server set: a missing key falls back to the Kotlin default.
        guard value.source == .remote else { return nil }
        return KotlinBoolean(bool: value.boolValue)
    }

    var lastFetchTimeMillis: KotlinLong? {
        guard let remoteConfig,
            remoteConfig.lastFetchStatus != .noFetchYet,
            let time = remoteConfig.lastFetchTime
        else { return nil }
        return KotlinLong(longLong: Int64(time.timeIntervalSince1970 * 1000))
    }

    func fetch(ignoreThrottle: Bool, onDone: @escaping (KotlinBoolean, String?) -> Void) {
        guard let remoteConfig else {
            onDone(KotlinBoolean(bool: false), "Firebase is not configured")
            return
        }
        remoteConfig.fetch(withExpirationDuration: ignoreThrottle ? 0 : Self.fetchInterval) {
            status, error in
            guard status == .success else {
                onDone(KotlinBoolean(bool: false), error?.localizedDescription ?? "Fetch failed")
                return
            }
            // Activated now, but the app read its flags at launch, so they apply from the next.
            remoteConfig.activate { changed, error in
                onDone(KotlinBoolean(bool: changed), error?.localizedDescription)
            }
        }
    }
}
