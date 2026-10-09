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

        // A simulator has no fix, so the location timeout's dialog comes up 30 s after launch and
        // hides the map's controls from accessibility, mid-test. Keep Waiting rearms the timeout,
        // leaving a clear 30 s for the controls.
        let keepWaiting = app.buttons["Keep Waiting"]
        if keepWaiting.waitForExistence(timeout: 35) {
            keepWaiting.tap()
        }

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

/// Accepts the EULA, skips the warm welcome and allows location, if this launch still asks, and
/// leaves the map with its controls showing.
func passStartup(_ app: XCUIApplication) {
    // The version banner covers the first few seconds of every launch; a tap dismisses it, and
    // would otherwise land on it rather than on Accept.
    let banner = app.staticTexts["Sky Map"]
    if banner.waitForExistence(timeout: 5) {
        banner.tap()
    }
    // The EULA, the welcome and the location prompt each show only until answered, so take
    // whichever come up, and count the map as reached once none has for a few seconds. The map's
    // controls can't say so: they exist underneath the welcome, and may have hidden themselves.
    // Quick matters: a simulator has no fix, so the location timeout's dialog comes up 30 s after
    // launch and hides the map's controls from accessibility; the tests must be done by then.
    let accept = app.buttons["Accept"]
    let skip = app.buttons["Skip"].firstMatch
    let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    let allow = springboard.alerts.buttons["Allow While Using App"]
    let deadline = Date().addingTimeInterval(20)
    var quietSince = Date()
    while Date() < deadline && Date().timeIntervalSince(quietSince) < 3 {
        for step in [accept, skip, allow] where step.exists {
            step.tap()
            quietSince = Date()
        }
        usleep(250_000)
    }
    // The map's controls flash for a few seconds, then hide, once anyone has ever toggled them
    // with a tap on the sky (an earlier test's tap counts). Wait the flash out; if they went, a
    // tap on the sky brings them back, and a toggle by hand stops the auto-hide for this run.
    // The tap also identifies whatever is there, so close any card it opens.
    sleep(1)
    let more = app.buttons["More options"]
    if !(more.exists && more.isHittable) {
        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.4)).tap()
        let close = app.buttons["Close"]
        if close.waitForExistence(timeout: 2) {
            close.tap()
        }
    }
}

/// Keeps a screenshot of the whole screen as a test attachment.
func capture(_ test: XCTestCase, _ name: String) {
    let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
    shot.name = name
    shot.lifetime = .keepAlways
    test.add(shot)
}
