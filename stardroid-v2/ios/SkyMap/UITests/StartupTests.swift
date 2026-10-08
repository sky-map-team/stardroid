/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// The first-launch path, captured as test attachments: the EULA, the location prompt it
/// holds back until accepted, then the map with the shared chrome, in day and night mode, and
/// the Layers sheet.
final class StartupTests: XCTestCase {
    func testEulaThenMap() {
        let app = XCUIApplication()
        app.launch()
        usleep(500_000)
        capture(self, "0-banner")
        sleep(4)
        capture(self, "1-launch")
        passStartup(app)
        sleep(4)
        capture(self, "2-map")

        // The shared map chrome's own controls, found by their accessibility labels.
        let night = app.buttons["Night mode"]
        if night.waitForExistence(timeout: 5) {
            night.tap()
            sleep(2)
            capture(self, "3-night")
            night.tap()
        }
        let layers = app.buttons["More layers and options"]
        if layers.waitForExistence(timeout: 5) {
            layers.tap()
            sleep(2)
            capture(self, "4-layers-sheet")
        }
    }
}

/// Accepts the EULA, skips the warm welcome and allows location, if this launch still asks.
func passStartup(_ app: XCUIApplication) {
    // The version banner covers the first few seconds of every launch; a tap dismisses it, and
    // would otherwise land on it rather than on Accept.
    let banner = app.staticTexts["Sky Map"]
    if banner.waitForExistence(timeout: 5) {
        banner.tap()
    }
    let accept = app.buttons["Accept"]
    if accept.waitForExistence(timeout: 5) {
        accept.tap()
    }
    // The welcome shows on a first run only, so wait for whichever comes up: its Skip, or the
    // map's controls. Those exist underneath the welcome, and look hittable for a moment while
    // the startup state loads, so the map only counts once it has stayed up with no welcome.
    let skip = app.buttons["Skip"].firstMatch
    let more = app.buttons["More options"]
    let deadline = Date().addingTimeInterval(20)
    var mapSince: Date?
    while Date() < deadline {
        if skip.exists {
            skip.tap()
            break
        }
        if more.exists && more.isHittable {
            let since = mapSince ?? Date()
            mapSince = since
            if Date().timeIntervalSince(since) > 3 {
                break
            }
        } else {
            mapSince = nil
        }
        usleep(250_000)
    }
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    let allow = springboard.alerts.buttons["Allow While Using App"]
    if allow.waitForExistence(timeout: 5) {
        allow.tap()
    }
}

/// Keeps a screenshot of the whole screen as a test attachment.
func capture(_ test: XCTestCase, _ name: String) {
    let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    shot.name = name
    shot.lifetime = .keepAlways
    test.add(shot)
}
