/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


import XCTest

/// Through-camera mode and sharing on the simulator, over its stand-in camera and compass
/// (camera-ar-launch.md §4.6). `SKYMAP_EXPERIMENTS` only exists in simulator builds.
final class ArModeTests: XCTestCase {
    private let toggle = "Through-camera mode"
    private let shutter = "Share this view"

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.orientation = .portrait
    }

    func testShipsDark() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)
        capture(self, "dark-map")
        XCTAssertTrue(app.buttons["More options"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)[toggle].exists)
        XCTAssertFalse(app.buttons[shutter].exists)
    }

    func testCameraWithoutShareHasNoShutter() {
        let app = launch(flags: "camera_ar_enabled")
        turnOn(app)
        sleep(2)
        capture(self, "camera-only")
        XCTAssertFalse(app.buttons[shutter].exists)
    }

    func testCameraUnderTheMap() {
        let app = launch(flags: "camera_ar_enabled,share_sky_enabled")
        capture(self, "1-before")
        turnOn(app)
        sleep(3)
        capture(self, "2-camera-under-map")

        let window = app.windows.firstMatch
        let from = window.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45))
        let to = window.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.45))
        from.press(forDuration: 0.05, thenDragTo: to)
        XCTAssertTrue(app.buttons["Undo"].waitForExistence(timeout: 4))
        capture(self, "3-aligned")
        app.buttons["Undo"].tap()

        window.pinch(withScale: 2, velocity: 2)
        sleep(2)
        capture(self, "4-zoomed")

        // The zoom holds through a turn.
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(3)
        capture(self, "5-landscape")
        XCUIDevice.shared.orientation = .portrait
        sleep(3)
        capture(self, "5a-portrait-again")
    }

    func testShareToTheSystemSheet() {
        let app = launch(flags: "camera_ar_enabled,share_sky_enabled")
        turnOn(app)
        // Dimmed, so the saved picture can be held against the screen's.
        element(app, "Camera dimmer").coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
            .tap()
        sleep(1)
        capture(self, "6a-dimmed")
        offerLayouts(app)
        capture(self, "6-layouts")
        element(app, "Overlay").tap()
        let save = app.cells["Save Image"]
        XCTAssertTrue(save.waitForExistence(timeout: 20), "no share sheet")
        capture(self, "7-share-sheet")
        save.tap()

        // The system asks to add to Photos the first time, and kills an app that can't say why.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let prompt = springboard.alerts.firstMatch
        if prompt.waitForExistence(timeout: 8) {
            capture(self, "8-photos-prompt")
            for title in ["Allow", "OK"] where prompt.buttons[title].exists {
                prompt.buttons[title].tap()
                break
            }
        }
        sleep(3)
        capture(self, "9-after-save")
        XCTAssertEqual(app.state, .runningForeground)
    }

    func testLandscapeLayoutsCanBePicked() {
        let app = launch(flags: "camera_ar_enabled,share_sky_enabled")
        turnOn(app)
        XCUIDevice.shared.orientation = .landscapeLeft
        sleep(3)
        offerLayouts(app)
        sleep(1)
        capture(self, "10-landscape-layouts")
        let choice = element(app, "Side by side")
        XCTAssertTrue(choice.isHittable, "the layouts don't fit in landscape")
        choice.tap()
        XCTAssertTrue(app.cells["Save Image"].waitForExistence(timeout: 20), "no share sheet")
        capture(self, "11-landscape-share-sheet")
    }

    func testComesBackFromSettingsAndTheBackground() {
        let app = launch(flags: "camera_ar_enabled,share_sky_enabled")
        turnOn(app)
        app.buttons["More options"].tap()
        let settings = element(app, "Settings")
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        sleep(1)
        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        sleep(2)
        let back = app.buttons["Back"]
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        back.tap()
        sleep(2)
        capture(self, "12-back-on-the-map")
    }

    private func launch(flags: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment["SKYMAP_EXPERIMENTS"] = flags
        app.launch()
        passStartup(app)
        return app
    }

    private func element(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: label).firstMatch
    }

    private func turnOn(_ app: XCUIApplication) {
        let camera = element(app, toggle)
        XCTAssertTrue(camera.waitForExistence(timeout: 10), "no camera toggle")
        camera.tap()
    }

    private func offerLayouts(_ app: XCUIApplication) {
        let button = app.buttons[shutter]
        XCTAssertTrue(button.waitForExistence(timeout: 10), "no shutter")
        button.tap()
        XCTAssertTrue(element(app, "Overlay").waitForExistence(timeout: 20), "no layouts")
    }
}
