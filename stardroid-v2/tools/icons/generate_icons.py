#!/usr/bin/env python3
# Copyright (c) 2026 Penterakt LLC.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NOTE: this *script* is GPLv3 like the rest of the repository's code; the icon
# *artwork* it defines and emits (the geometry below, the SVG masters in dso/,
# and the webp files in app/src/main/assets/catalog/icons/) is All Rights
# Reserved, Penterakt LLC — see dso/LICENSE.md and the assets directory's
# LICENSE.md.
"""Generate the sky-marker icon set: SVG masters (dso/) and shipped webps.

Single source of truth for the D62 icon redesign. Glyphs live on a 24-unit
grid in the D57 stroke language (round caps/joins, white strokes tinted at
runtime) with the parameters signed off on the design mockup:

    stroke 1.4 · drawn at 20 dp (radiants 32 dp) · baked halo 35 %

The halo is a black underlay stroke (glyph stroke + 2.2 units wide) baked into
the bitmaps: the renderer multiply-tints the texture, so white pixels take the
layer tint and the halo stays dark whatever the tint or night mode does.

Outputs (deterministic; re-run and commit):
    dso/<name>.svg                                   design masters
    ../../app/src/main/assets/catalog/icons/<name>.webp   4x-density bitmaps
        (80x80 for the 20 dp DSO markers, 128x128 for the 32 dp radiants)

Requires Pillow with webp support (`python3 -m pip install Pillow`).
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

STROKE = 1.4
HALO_EXTRA = 2.2
HALO_OPACITY = 0.35
GRID = 24.0
SUPERSAMPLE = 8

# ---------------------------------------------------------------------------
# Geometry primitives: ("dot", cx, cy, r) filled circle · ("ring", cx, cy, r)
# stroked circle · ("line", pts, closed, width_mul) stroked polyline.
# ---------------------------------------------------------------------------


def _spiral_arm(phase: float):
    """Archimedean arm, ~215° sweep, r ≈ 2.7 → 6.7."""
    pts = []
    for i in range(19):
        t = 1.1 + (i / 18) * 3.75
        r = 1.55 + 1.06 * t
        pts.append((12 + r * math.cos(t + phase), 12 - r * math.sin(t + phase)))
    return ("line", pts, False, 1.0)


def _jagged_burst():
    """Uneven eight-spike explosion outline (the v1 glyph's spirit)."""
    spikes = [0, 42, 88, 135, 178, 224, 266, 312]
    outer = [7.7, 6.5, 8.0, 6.8, 7.4, 6.4, 7.9, 6.9]
    inner = [3.1, 3.6, 2.8, 3.4, 3.0, 3.7, 3.1, 3.5]
    pts = []
    for i, spike in enumerate(spikes):
        nxt = spikes[i + 1] if i + 1 < len(spikes) else spikes[0] + 360
        for angle, radius in ((spike, outer[i]), ((spike + nxt) / 2, inner[i])):
            a = math.radians(angle)
            pts.append((12 + radius * math.cos(a), 12 - radius * math.sin(a)))
    return ("line", pts, True, 1.0)


def _rays(table):
    """Deliberately jittered ray segments (v1's radiants were irregular too)."""
    parts = []
    for angle, r1, r2 in table:
        a = math.radians(angle)
        c, s = math.cos(a), math.sin(a)
        parts.append(("line", [(12 + r1 * c, 12 - r1 * s), (12 + r2 * c, 12 - r2 * s)], False, 1.0))
    return parts


def _cubic_path(d_start, segments, samples=24):
    """Flatten a chain of relative cubic-bezier segments into a polyline."""
    pts = [d_start]
    x, y = d_start
    for c1x, c1y, c2x, c2y, ex, ey in segments:
        p0 = (x, y)
        p1 = (x + c1x, y + c1y)
        p2 = (x + c2x, y + c2y)
        p3 = (x + ex, y + ey)
        for i in range(1, samples + 1):
            t = i / samples
            u = 1 - t
            pts.append(
                (
                    u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0],
                    u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1],
                )
            )
        x, y = p3
    return pts


# The wispy asymmetric nebula contour from the mockup (M 6.3 13.9 c …z).
_NEBULA_OUTLINE = _cubic_path(
    (6.3, 13.9),
    [
        (-0.9, -1.6, -0.3, -3.6, 1.3, -4.5),
        (0.2, -1.8, 1.8, -3.1, 3.6, -2.8),
        (0.8, -1.1, 2.3, -1.5, 3.5, -0.9),
        (1.8, -0.2, 3.4, 1.1, 3.6, 2.9),
        (1.0, 0.8, 1.3, 2.2, 0.6, 3.3),
        (0.5, 1.6, -0.4, 3.3, -2.0, 3.8),
        (-0.6, 1.4, -2.2, 2.1, -3.6, 1.6),
        (-1.0, 0.8, -2.5, 0.7, -3.4, -0.2),
        (-1.5, 0.2, -2.9, -0.7, -3.3, -2.2),
    ],
)

ICONS = {
    "galaxy": {
        "size_dp": 20,
        "parts": [("dot", 12, 12, 1.35), _spiral_arm(0.0), _spiral_arm(math.pi)],
    },
    "open_cluster": {
        "size_dp": 20,
        "parts": [
            ("dot", 8.2, 8.8, 1.15), ("dot", 13.4, 6.6, 0.95), ("dot", 17.2, 10.2, 1.15),
            ("dot", 6.6, 13.8, 0.95), ("dot", 11.6, 12.4, 0.8), ("dot", 15.8, 15.4, 1.15),
            ("dot", 9.6, 17.4, 0.95),
        ],
    },
    "globular_cluster": {
        "size_dp": 20,
        "parts": [("dot", 12, 12, 1.5)]
        + [
            ("dot", 12 + 3.4 * math.cos(math.radians(90 - i * 60)),
             12 - 3.4 * math.sin(math.radians(90 - i * 60)), 0.85)
            for i in range(6)
        ]
        + [
            ("dot", 12 + 6.8 * math.cos(math.radians(90 - i * 45)),
             12 - 6.8 * math.sin(math.radians(90 - i * 45)), 0.6)
            for i in range(8)
        ],
    },
    "diffuse_nebula": {
        "size_dp": 20,
        "parts": [
            ("line", _NEBULA_OUTLINE, True, 1.0),
            ("dot", 10.4, 11.2, 0.75),
            ("dot", 13.8, 13.1, 0.75),
        ],
    },
    "planetary_nebula": {
        "size_dp": 20,
        "parts": [("ring", 12, 12, 5.2), ("dot", 12, 12, 1.35)],
    },
    "supernova_remnant": {"size_dp": 20, "parts": [_jagged_burst()]},
    "asterism": {
        "size_dp": 20,
        "parts": [
            ("line", [(6.4, 16.6), (10.2, 9.4), (15.2, 13.0), (18.6, 6.6)], False, 0.62),
            ("dot", 6.4, 16.6, 1.2), ("dot", 10.2, 9.4, 1.2),
            ("dot", 15.2, 13.0, 1.2), ("dot", 18.6, 6.6, 1.2),
        ],
    },
    "other": {
        "size_dp": 20,
        "parts": [("line", [(12, 6.4), (17, 12), (12, 17.6), (7, 12)], True, 1.0)],
    },
    "meteor_radiant": {
        "size_dp": 32,
        "parts": _rays([
            (14, 2.6, 7.3), (64, 3.3, 6.0), (108, 2.2, 8.1), (155, 3.1, 5.7),
            (198, 2.5, 7.7), (246, 3.5, 6.2), (288, 2.3, 8.0), (336, 3.2, 5.5),
        ]),
    },
    "meteor_radiant_peak": {
        "size_dp": 32,
        "parts": _rays([
            (8, 2.4, 9.3), (44, 3.2, 6.3), (78, 2.1, 8.6), (116, 3.4, 6.0),
            (150, 2.3, 9.0), (187, 3.1, 6.6), (222, 2.2, 8.3), (255, 3.5, 5.9),
            (290, 2.4, 9.1), (327, 3.0, 6.4),
        ])
        + [("dot", 12, 12, 1.1), ("dot", 19.6, 16.9, 0.6), ("dot", 4.9, 6.8, 0.6)],
    },
}


# ---------------------------------------------------------------------------
# Raster (webp) emission
# ---------------------------------------------------------------------------


def _draw_pass(draw, parts, scale, color, extra_width):
    for part in parts:
        if part[0] == "dot":
            _, cx, cy, r = part
            rr = (r + extra_width / 2) * scale
            draw.ellipse(
                [cx * scale - rr, cy * scale - rr, cx * scale + rr, cy * scale + rr],
                fill=color,
            )
        elif part[0] == "ring":
            _, cx, cy, r = part
            w = (STROKE + extra_width) * scale
            rr = r * scale + w / 2
            draw.ellipse(
                [cx * scale - rr, cy * scale - rr, cx * scale + rr, cy * scale + rr],
                outline=color,
                width=round(w),
            )
        else:
            _, pts, closed, width_mul = part
            w = (STROKE * width_mul + extra_width) * scale
            xy = [(x * scale, y * scale) for x, y in pts]
            if closed:
                xy.append(xy[0])
            draw.line(xy, fill=color, width=round(w), joint="curve")
            # Round caps (joint= only rounds interior joints).
            caps = [xy[0]] if closed else [xy[0], xy[-1]]
            for px, py in caps:
                draw.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=color)


def render_webp(name, spec, out_path):
    out_px = spec["size_dp"] * 4  # 4x density, matching high-dpi screens
    big = out_px * SUPERSAMPLE
    scale = big / GRID

    halo = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    _draw_pass(ImageDraw.Draw(halo), spec["parts"], scale, (0, 0, 0, 255), HALO_EXTRA)
    alpha = halo.getchannel("A").point(lambda a: int(a * HALO_OPACITY))
    halo.putalpha(alpha)

    ink = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    _draw_pass(ImageDraw.Draw(ink), spec["parts"], scale, (255, 255, 255, 255), 0.0)

    img = Image.alpha_composite(halo, ink).resize((out_px, out_px), Image.LANCZOS)
    img.save(out_path, "WEBP", lossless=True, quality=100, method=6, exact=True)


# ---------------------------------------------------------------------------
# SVG master emission
# ---------------------------------------------------------------------------


def _svg_elements(parts, color_attr, extra_width):
    out = []
    for part in parts:
        if part[0] == "dot":
            _, cx, cy, r = part
            out.append(
                f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r + extra_width / 2:.2f}" '
                f'fill="{color_attr}"/>'
            )
        elif part[0] == "ring":
            _, cx, cy, r = part
            out.append(
                f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}" fill="none" '
                f'stroke="{color_attr}" stroke-width="{STROKE + extra_width:.2f}"/>'
            )
        else:
            _, pts, closed, width_mul = part
            d = "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts) + ("Z" if closed else "")
            out.append(
                f'<path d="{d}" fill="none" stroke="{color_attr}" '
                f'stroke-width="{STROKE * width_mul + extra_width:.2f}" '
                'stroke-linecap="round" stroke-linejoin="round"/>'
            )
    return out


def render_svg(name, spec, out_path):
    halo = "\n    ".join(_svg_elements(spec["parts"], "#000", HALO_EXTRA))
    ink = "\n    ".join(_svg_elements(spec["parts"], "#fff", 0.0))
    out_path.write_text(
        "<!-- Sky Map sky-marker icon. Copyright (c) 2026 Penterakt LLC.\n"
        "     All Rights Reserved - see LICENSE.md in this directory. -->\n"
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID:g} {GRID:g}">\n'
        f'  <g opacity="{HALO_OPACITY}">\n    {halo}\n  </g>\n'
        f"  <g>\n    {ink}\n  </g>\n"
        "</svg>\n"
    )


def main():
    here = Path(__file__).resolve().parent
    svg_dir = here / "dso"
    asset_dir = here.parent.parent / "app/src/main/assets/catalog/icons"
    svg_dir.mkdir(exist_ok=True)
    for name, spec in ICONS.items():
        render_svg(name, spec, svg_dir / f"{name}.svg")
        render_webp(name, spec, asset_dir / f"{name}.webp")
        print(f"{name}: {spec['size_dp']}dp -> {spec['size_dp'] * 4}px webp + svg master")


if __name__ == "__main__":
    main()
