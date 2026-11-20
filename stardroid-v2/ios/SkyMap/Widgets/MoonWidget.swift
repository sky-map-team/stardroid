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

/// The Moon widget (Android's MoonWidget, D75): the phase disc, phase name, illumination, day of
/// the cycle and the next rise and set, on the Home Screen and the Lock Screen.
struct MoonWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "MoonWidget", provider: MoonProvider()) { entry in
            MoonWidgetView(entry: entry)
        }
        .configurationDisplayName(widgetText("moon_widget_label"))
        .description(widgetText("moon_widget_description"))
        .supportedFamilies(Self.families)
    }

    private static var families: [WidgetFamily] {
        if #available(iOSApplicationExtension 16.0, *) {
            return [.systemSmall, .accessoryCircular, .accessoryInline]
        }
        return [.systemSmall]
    }
}

struct MoonEntry: TimelineEntry {
    let date: Date
    let snapshot: MoonWidgetSnapshot
}

private extension MoonEntry {
    init(_ snapshot: MoonWidgetSnapshot) {
        self.init(date: Date(timeIntervalSince1970: snapshot.epochSeconds), snapshot: snapshot)
    }
}

/// A day of hourly entries computed here, in the extension, so they never go stale while the app
/// goes unopened (§6.2). The app reloads the timeline when it goes to the background, which picks
/// up a changed location.
struct MoonProvider: TimelineProvider {
    func placeholder(in context: Context) -> MoonEntry { Self.entries(hours: 0)[0] }

    func getSnapshot(in context: Context, completion: @escaping (MoonEntry) -> Void) {
        completion(Self.entries(hours: 0)[0])
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MoonEntry>) -> Void) {
        completion(Timeline(entries: Self.entries(hours: 24), policy: .atEnd))
    }

    private static func entries(hours: Int32) -> [MoonEntry] {
        MoonWidgets.shared
            .snapshots(startEpochSeconds: Date().timeIntervalSince1970, hours: hours)
            .map(MoonEntry.init)
    }
}

struct MoonWidgetView: View {
    let entry: MoonEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        content
            .widgetURL(URL(string: MoonWidgets.shared.link))
    }

    @ViewBuilder
    private var content: some View {
        let snapshot = entry.snapshot
        let phase = widgetText(snapshot.phaseNameKey)
        switch family {
        case .accessoryInline:
            Text(verbatim: "\(phase) \(percentShort)").widgetSurface(.clear)
        case .accessoryCircular:
            circular.widgetSurface(.clear)
        default:
            full(phase: phase).widgetSurface(WidgetInk.surface)
        }
    }

    private var percentShort: String {
        widgetText("moon_widget_illumination_short", entry.snapshot.illuminationPercent)
    }

    private var circular: some View {
        ZStack {
            if #available(iOSApplicationExtension 16.0, *) {
                AccessoryWidgetBackground()
            }
            VStack(spacing: 1) {
                MoonDiscView(snapshot: entry.snapshot).frame(width: 26, height: 26)
                Text(percentShort).font(.system(size: 11, weight: .bold))
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(widgetText(entry.snapshot.phaseNameKey))
    }

    private func full(phase: String) -> some View {
        let snapshot = entry.snapshot
        return VStack(spacing: 0) {
            MoonDiscView(snapshot: snapshot)
                .frame(width: 64, height: 64)
                .accessibilityLabel(phase)
            Text(phase)
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(WidgetInk.starlight)
                .padding(.top, 6)
            Text(widgetText("moon_widget_illumination", snapshot.illuminationPercent))
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(WidgetInk.starGold)
            Text(widgetText("moon_widget_age", snapshot.dayOfCycle))
                .font(.system(size: 12))
                .foregroundColor(WidgetInk.muted)
            ForEach(Array(snapshot.crossings.enumerated()), id: \.offset) { index, crossing in
                Text(crossingLine(crossing))
                    .font(.system(size: 12))
                    .foregroundColor(WidgetInk.muted)
                    .padding(.top, index == 0 ? 2 : 0)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }

    /// "rises 17:42", with " +1" when it falls on a later local day than the entry.
    private func crossingLine(_ crossing: MoonWidgetCrossing) -> String {
        let time = Date(timeIntervalSince1970: crossing.epochSeconds)
            .formatted(date: .omitted, time: .shortened)
        let line = widgetText(
            crossing.rise ? "moon_widget_rise_time" : "moon_widget_set_time", time)
        guard crossing.dayOffset > 0 else { return line }
        return line + widgetText("moon_widget_time_day_offset", crossing.dayOffset)
    }
}
