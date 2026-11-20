/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SkyMapWidgetKit
import SwiftUI
import WidgetKit

/// The countdown widget (Android's CountdownWidget, D75): a ring counting down the days to the
/// next major meteor shower peak, else the next new or full moon.
struct CountdownWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "CountdownWidget", provider: CountdownProvider()) { entry in
            CountdownWidgetView(entry: entry)
        }
        .configurationDisplayName(widgetText("countdown_widget_label"))
        .description(widgetText("countdown_widget_description"))
        .supportedFamilies([.systemSmall])
    }
}

struct CountdownEntry: TimelineEntry {
    let date: Date
    let snapshot: CountdownWidgetSnapshot
}

/// An entry now and at each of the next seven local midnights, when the day count changes. The
/// target is worked out afresh at each, so it moves on by itself once an event has passed.
struct CountdownProvider: TimelineProvider {
    func placeholder(in context: Context) -> CountdownEntry { Self.entries(days: 0)[0] }

    func getSnapshot(in context: Context, completion: @escaping (CountdownEntry) -> Void) {
        completion(Self.entries(days: 0)[0])
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<CountdownEntry>) -> Void) {
        completion(Timeline(entries: Self.entries(days: 7), policy: .atEnd))
    }

    private static func entries(days: Int32) -> [CountdownEntry] {
        TonightWidgets.shared
            .countdownSnapshots(startEpochSeconds: Date().timeIntervalSince1970, days: days)
            .map {
                CountdownEntry(date: Date(timeIntervalSince1970: $0.epochSeconds), snapshot: $0)
            }
    }
}

struct CountdownWidgetView: View {
    let entry: CountdownEntry

    var body: some View {
        content
            .widgetPadding()
            .widgetURL(entry.snapshot.link.flatMap { URL(string: $0) })
            .widgetSurface(WidgetInk.surface)
    }

    @ViewBuilder
    private var content: some View {
        let snapshot = entry.snapshot
        if snapshot.hasTarget {
            VStack(spacing: 0) {
                ZStack {
                    CountdownRing(progress: snapshot.progress).frame(width: 74, height: 74)
                    VStack(spacing: 0) {
                        Text(snapshot.figure)
                            .font(.system(size: snapshot.isTonight ? 16 : 22, weight: .bold))
                            .foregroundColor(WidgetInk.starlight)
                        if !snapshot.isTonight {
                            Text(snapshot.unit)
                                .font(.system(size: 9))
                                .foregroundColor(WidgetInk.muted)
                        }
                    }
                }
                Text(snapshot.name)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(WidgetInk.starlight)
                    .padding(.top, 4)
                if let note = snapshot.note {
                    Text(note)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(WidgetInk.planetGreen)
                }
            }
            .lineLimit(1)
            .minimumScaleFactor(0.8)
        } else {
            Color.clear
        }
    }
}

/// A navy track with a Star Gold arc from the top, clockwise, as CountdownRingRenderer draws it.
private struct CountdownRing: View {
    let progress: Double

    var body: some View {
        GeometryReader { geometry in
            let stroke = min(geometry.size.width, geometry.size.height) * 0.07
            ZStack {
                Circle().stroke(WidgetInk.outline, lineWidth: stroke)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(WidgetInk.starGold, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                    .rotationEffect(.degrees(-90))
            }
            .padding(stroke / 2)
        }
    }
}
