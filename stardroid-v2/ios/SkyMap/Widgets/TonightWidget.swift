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

/// The tonight's-sky widget (Android's TonightWidget, D75): sunset and darkness, then up to three
/// ranked highlights from the shared events engine, each opening its object's card.
struct TonightWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TonightWidget", provider: TonightProvider()) { entry in
            TonightWidgetView(entry: entry)
        }
        .configurationDisplayName(widgetText("tonight_widget_label"))
        .description(widgetText("tonight_widget_description"))
        .supportedFamilies(Self.families)
    }

    private static var families: [WidgetFamily] {
        if #available(iOSApplicationExtension 16.0, *) {
            return [.systemMedium, .accessoryRectangular]
        }
        return [.systemMedium]
    }
}

struct TonightEntry: TimelineEntry {
    let date: Date
    let snapshot: TonightWidgetSnapshot
}

/// Hourly entries for the next 12 hours, computed in the extension from the location, showers and
/// passes the app leaves in the App Group (§6.2). A sunset that has passed gives way to
/// tomorrow's and a pass that has begun drops out, with no reload.
struct TonightProvider: TimelineProvider {
    func placeholder(in context: Context) -> TonightEntry { Self.entries(hours: 0)[0] }

    func getSnapshot(in context: Context, completion: @escaping (TonightEntry) -> Void) {
        completion(Self.entries(hours: 0)[0])
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TonightEntry>) -> Void) {
        completion(Timeline(entries: Self.entries(hours: 12), policy: .atEnd))
    }

    private static func entries(hours: Int32) -> [TonightEntry] {
        TonightWidgets.shared
            .snapshots(startEpochSeconds: Date().timeIntervalSince1970, hours: hours)
            .map {
                TonightEntry(date: Date(timeIntervalSince1970: $0.epochSeconds), snapshot: $0)
            }
    }
}

struct TonightWidgetView: View {
    let entry: TonightEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryRectangular:
            rectangular.widgetSurface(.clear)
        default:
            medium.widgetPadding().widgetSurface(WidgetInk.surface)
        }
    }

    private var snapshot: TonightWidgetSnapshot { entry.snapshot }

    private var medium: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(snapshot.header).font(.system(size: 11, weight: .bold))
                Spacer(minLength: 8)
                if let sunTimes = snapshot.sunTimes {
                    Text(sunTimes).font(.system(size: 11))
                }
            }
            .foregroundColor(WidgetInk.muted)
            if snapshot.rows.isEmpty {
                quiet
            } else {
                ForEach(Array(snapshot.rows.enumerated()), id: \.offset) { _, row in
                    rowLink(row)
                }
            }
        }
        .lineLimit(1)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func rowLink(_ row: TonightWidgetRow) -> some View {
        let line = HStack(spacing: 0) {
            Circle().fill(Color(argb: row.dotColor)).frame(width: 8, height: 8)
            Text(row.text)
                .font(.system(size: 13))
                .foregroundColor(WidgetInk.starlight)
                .padding(.leading, 9)
            Spacer(minLength: 8)
            Text(row.trailing)
                .font(.system(size: 11))
                .foregroundColor(WidgetInk.muted)
        }
        .padding(.top, 7)
        if let url = URL(string: row.link) {
            Link(destination: url) { line }
        } else {
            line
        }
    }

    private var quiet: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(TonightWidgets.shared.quiet)
                .font(.system(size: 13))
                .foregroundColor(WidgetInk.starlight)
            if let next = snapshot.quietNext {
                Text(next).font(.system(size: 12)).foregroundColor(WidgetInk.muted)
            }
        }
        .padding(.top, 8)
    }

    /// The Lock Screen's three lines: the top highlights, or the quiet-night message. Only the
    /// whole widget can be tapped there, so it opens the first highlight.
    private var rectangular: some View {
        VStack(alignment: .leading, spacing: 0) {
            if snapshot.rows.isEmpty {
                Text(TonightWidgets.shared.quiet).font(.headline)
                if let next = snapshot.quietNext { Text(next) }
            } else {
                ForEach(Array(snapshot.rows.enumerated()), id: \.offset) { index, row in
                    Text(row.text).font(index == 0 ? .headline : .body)
                }
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .widgetURL(snapshot.rows.first.flatMap { URL(string: $0.link) })
    }
}
