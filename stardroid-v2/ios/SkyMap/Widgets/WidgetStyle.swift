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

extension Color {
    /// From the shared palette's 0xAARRGGBB (WidgetColors).
    init(argb: Int64) {
        let value = UInt32(truncatingIfNeeded: argb)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: Double(value >> 24) / 255)
    }
}

enum WidgetInk {
    static let starlight = Color(argb: WidgetPalette.shared.starlight)
    static let starGold = Color(argb: WidgetPalette.shared.starGold)
    static let muted = Color(argb: WidgetPalette.shared.muted)
    static let surface = Color(argb: WidgetPalette.shared.surface)
    static let outline = Color(argb: WidgetPalette.shared.outline)
    static let planetGreen = Color(argb: WidgetPalette.shared.planetGreen)
}

/// A string from the generated Localizable.strings, keyed by the shared resource name.
/// Pass Kotlin's Int32 values for `%d`, never a Swift Int: `%d` reads 32 bits.
func widgetText(_ key: String, _ args: CVarArg...) -> String {
    let format = NSLocalizedString(key, comment: "")
    return args.isEmpty ? format : String(format: format, arguments: args)
}

extension View {
    /// The navy widget surface. iOS 17 asks for it as the container background, so the system
    /// can drop it where it draws its own (StandBy, the Lock Screen).
    @ViewBuilder
    func widgetSurface(_ color: Color) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}

extension View {
    /// iOS 17 insets a widget's content by the system's margins; earlier systems leave it to us.
    @ViewBuilder
    func widgetPadding() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            self
        } else {
            padding()
        }
    }
}
