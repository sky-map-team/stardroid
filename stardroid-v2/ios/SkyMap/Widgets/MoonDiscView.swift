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

/// The moon disc from the shared geometry (MoonDisc), drawn as Android's MoonDiscRenderer draws
/// it: shadowed disc, lit lune, faint maria clipped to the lune. No image assets.
struct MoonDiscView: View {
    let snapshot: MoonWidgetSnapshot

    var body: some View {
        ZStack {
            Circle().fill(Color(argb: WidgetPalette.shared.moonShadow))
            LitLune(snapshot: snapshot).fill(Color(argb: WidgetPalette.shared.moonLit))
            Maria(flipped: snapshot.discFlipped)
                .fill(Color(argb: WidgetPalette.shared.moonMaria))
                .clipShape(LitLune(snapshot: snapshot))
        }
        .aspectRatio(1, contentMode: .fit)
    }
}

private struct Disc {
    let center: CGPoint
    let radius: CGFloat
    let flipped: Bool

    init(_ rect: CGRect, flipped: Bool) {
        center = CGPoint(x: rect.midX, y: rect.midY)
        radius = min(rect.width, rect.height) / 2
        self.flipped = flipped
    }

    /// A point in disc units (x right, y down, radius 1), mirrored when the disc is flipped.
    func point(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(
            x: center.x + radius * CGFloat(flipped ? -x : x),
            y: center.y + radius * CGFloat(y))
    }
}

/// The right half-circle from top to bottom, closed back up by the terminator ellipse.
private struct LitLune: Shape {
    let snapshot: MoonWidgetSnapshot

    func path(in rect: CGRect) -> Path {
        let disc = Disc(rect, flipped: snapshot.discFlipped)
        let bulge = snapshot.terminatorBulgesLeft ? -1.0 : 1.0
        let steps = 48
        var path = Path()
        for i in 0...steps {
            let t = -Double.pi / 2 + Double.pi * Double(i) / Double(steps)
            let p = disc.point(cos(t), sin(t))
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        for i in 0...steps {
            let t = Double.pi / 2 - Double.pi * Double(i) / Double(steps)
            path.addLine(to: disc.point(bulge * snapshot.terminatorRx * cos(t), sin(t)))
        }
        path.closeSubpath()
        return path
    }
}

private struct Maria: Shape {
    let flipped: Bool

    func path(in rect: CGRect) -> Path {
        let disc = Disc(rect, flipped: flipped)
        var path = Path()
        for mare in MoonWidgets.shared.maria {
            let c = disc.point(mare.x, mare.y)
            let r = disc.radius * CGFloat(mare.r)
            path.addEllipse(in: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
        }
        return path
    }
}
