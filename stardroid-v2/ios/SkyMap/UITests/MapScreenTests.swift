/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import XCTest

/// The shared map screen's taps, as the iOS host reports them (phase 5n): a tap on the sky
/// toggles the controls, a double tap in manual mode flips auto-level with an Undo, and the edge
/// swipe cancels a search.
final class MapScreenTests: XCTestCase {
    func testTapDoubleTapAndSearchBack() {
        let app = XCUIApplication()
        app.launch()
        passStartup(app)
        let more = app.buttons["More options"]
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        capture(self, "1-map")

        // A tap hides the controls and the next brings them back. Either may identify whatever
        // is there, so close any card it opens.
        let sky =
            app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.6, dy: 0.35))
        sky.tap()
        closeAnyCard(app)
        waitUntilGone(more)
        capture(self, "2-controls-hidden")
        sky.tap()
        closeAnyCard(app)
        XCTAssertTrue(more.waitForExistence(timeout: 5))

        // A simulator has no fix, so the location timeout's dialog comes up 30 s after launch and
        // hides the controls; this test runs longer than that. Wait for it here and keep waiting,
        // which leaves a clear 30 s for the rest.
        #if targetEnvironment(simulator)
        let keepWaiting = app.buttons["Keep Waiting"]
        if keepWaiting.waitForExistence(timeout: 25) {
            keepWaiting.tap()
        }
        #endif

        // Double tap in manual mode flips auto-level. Its first tap also toggles the controls (and
        // may open a card), so two double taps leave the controls as they were. A second double
        // tap that reports the same change shows Undo put the setting back. A simulator has no
        // sensors, so it is in manual mode already.
        let manual = app.buttons["Switch to manual mode"]
        let switchedToManual = manual.exists
        if switchedToManual {
            manual.tap()
        }
        let autoLevel = app.staticTexts.matching(
            NSPredicate(format: "label BEGINSWITH 'Horizon auto-leveling'")
        ).firstMatch
        sky.doubleTap()
        XCTAssertTrue(autoLevel.waitForExistence(timeout: 3))
        let first = autoLevel.label
        closeAnyCard(app)
        capture(self, "3-auto-level")
        app.buttons["Undo"].tap()
        waitUntilGone(autoLevel)
        sky.doubleTap()
        XCTAssertTrue(autoLevel.waitForExistence(timeout: 3))
        XCTAssertEqual(autoLevel.label, first, "Undo restored the setting")
        closeAnyCard(app)
        app.buttons["Undo"].tap()
        waitUntilGone(autoLevel)
        if !more.exists { sky.tap() }
        if switchedToManual {
            let auto = app.buttons["Switch to auto mode"]
            XCTAssertTrue(auto.waitForExistence(timeout: 5))
            auto.tap()
        }

        // A search, then the edge swipe: it cancels the search and stays on the map.
        app.buttons["Search"].tap()
        let field = app.textViews.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        typeSlowly(field, "Mars")
        app.buttons["Go"].tap()
        // The dialog has a Cancel of its own, so make sure it has gone first.
        waitUntilGone(field)
        let cancel = app.buttons["Cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        capture(self, "4-search")
        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5))
        edge.press(
            forDuration: 0.05,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)),
        )
        waitUntilGone(cancel)
        XCTAssertTrue(app.buttons["Search"].waitForExistence(timeout: 5))
        capture(self, "5-search-cancelled")
    }

    private func closeAnyCard(_ app: XCUIApplication) {
        let close = app.buttons["Close"]
        if close.waitForExistence(timeout: 1.5) {
            close.tap()
        }
    }

    private func waitUntilGone(_ element: XCUIElement) {
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: element)
        waitForExpectations(timeout: 5)
    }
}
