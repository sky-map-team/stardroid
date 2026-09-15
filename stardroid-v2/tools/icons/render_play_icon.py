#!/usr/bin/env python3
# Copyright (c) 2026 Penterakt LLC.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NOTE: this *script* is GPLv3 like the rest of the repository's code; the icon
# *artwork* it composites (the geometry below, mirrored from the adaptive icon's
# vector drawables) is All Rights Reserved, Penterakt LLC — see
# the [arr] section of stardroid-v2/ASSET-LICENSES.txt.
"""Render the 512x512 Play Store listing icon from the adaptive icon layers.

The store listing needs a flat square PNG, so the background and foreground
vector drawables are composited here at 4x supersampling and flattened to RGB.
The monochrome layer is deliberately excluded: it exists only for the OS themed
icon. Output is the full 108dp canvas, unmasked — Play applies its own corner
rounding.

    python3 tools/icons/render_play_icon.py

Writes fastlane/metadata/android/en-US/images/icon.png. Deterministic; re-run
and commit.

The geometry below MIRRORS app/src/main/res/drawable/ic_launcher_{background,
foreground}.xml. There is no vector-drawable parser in the toolchain, so the
two must be kept in sync by hand — change a coordinate there, change it here.

Requires Pillow and numpy (`python3 -m pip install Pillow numpy`).
"""

from __future__ import annotations

import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SIZE = 512
SUPERSAMPLE = 4
R = SIZE * SUPERSAMPLE

# Adaptive-icon art space: the drawables author at 800 units inside a group
# translated by 4 and scaled by 0.125, so the 108dp canvas spans art -32..832.
ART_MIN = -32.0
ART_SPAN = 864.0
K = R / ART_SPAN


def to_art(v_108: float) -> float:
    """108dp viewport coordinate -> 800-unit art coordinate."""
    return (v_108 - 4.0) * 8.0


def px(v: float) -> float:
    return (v - ART_MIN) * K


# --- background layer ------------------------------------------------------
NEBULA = ((to_art(41), to_art(35), 92 * 8),
          ((0.00, "#3D2F74"), (0.42, "#1A1F4E"), (1.00, "#060819")))
WASH = ((to_art(76), to_art(78), 46 * 8), "#7EC8E3", 0x61 / 255)
BG_STARS = [(32, 30, 0.8), (76, 24, 0.6), (86, 49, 0.7), (41, 84, 0.6),
            (24, 65, 0.7), (70, 81, 0.6), (61, 18, 0.5)]
BG_STAR_COLOR, BG_STAR_ALPHA = "#EAF0FF", 0xCC / 255

# --- foreground layer ------------------------------------------------------
RING = (355.0, 375.0, 175.0, 52.0)          # cx, cy, r, stroke
HANDLE = (479.0, 499.0, 640.0, 660.0, 88.0)
LENS_INNER = RING[2] - RING[3] / 2          # 149
LENS_GRID = [
    "M213.6,337.5C215.2,370.6 215.1,403.8 211.8,436.8",
    "M265.1,270.3C272.8,346.9 272.3,424.4 262.7,500.8",
    "M323,240.6C327.5,336.2 327.3,432 322.1,527.5",
    "M387,240.6C382.5,336.2 382.7,432 387.9,527.5",
    "M444.9,270.3C437.2,346.9 437.7,424.4 447.3,500.8",
    "M496.4,337.5C494.8,370.6 494.9,403.8 498.2,436.8",
    "M317.5,233.6C350.6,235.2 383.8,235.1 416.8,231.8",
    "M250.3,285.1C326.9,292.8 404.4,292.3 480.8,282.7",
    "M220.6,343C316.2,347.5 412,347.3 507.5,342.1",
    "M220.6,407C316.2,402.5 412,402.7 507.5,407.9",
    "M250.3,464.9C326.9,457.2 404.4,457.7 480.8,467.3",
    "M317.5,516.4C350.6,514.8 383.8,514.9 416.8,518.2",
]
GRID_COLOR, GRID_ALPHA, GRID_WIDTH = "#9FD8EE", 0x6B / 255, 6.0
GLOW = (355.0, 375.0, 189.0, "#FFE58A", 0x8C / 255)
PLANETS = [(570.0, 244.0, 45.0, "#E05C34"), (200.0, 590.0, 50.0, "#4DB848")]
HERO = (355.0, 375.0, 1.28)
CORNER_STAR = (205.0, 150.0, 0.5, 12.0)
STAR_COLOR, LENS_COLOR = "#FFC107", "#7EC8E3"
STAR_PTS = [(0, -100), (23.5, -32.4), (95.1, -30.9), (38, 12.4), (58.8, 80.9),
            (0, 40), (-58.8, 80.9), (-38, 12.4), (-95.1, -30.9), (-23.5, -32.4)]


def rgb(hex_str: str) -> np.ndarray:
    h = hex_str.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)


class Canvas:
    """Float RGB canvas; shapes arrive as antialiased coverage masks."""

    def __init__(self) -> None:
        self.rgb = np.zeros((R, R, 3), dtype=np.float32)
        ys, xs = np.mgrid[0:R, 0:R].astype(np.float32)
        self.x_art = xs / K + ART_MIN
        self.y_art = ys / K + ART_MIN

    def blend(self, alpha: np.ndarray, color: np.ndarray) -> None:
        a = alpha[..., None]
        self.rgb = self.rgb * (1 - a) + color * a

    def radial(self, cx, cy, radius, stops) -> None:
        """Multi-stop radial gradient, opaque (the base nebula)."""
        t = np.clip(np.hypot(self.x_art - cx, self.y_art - cy) / radius, 0, 1)
        out = np.zeros((R, R, 3), dtype=np.float32)
        for (o0, c0), (o1, c1) in zip(stops, stops[1:]):
            m = (t >= o0) & (t <= o1)
            f = ((t - o0) / (o1 - o0))[..., None]
            out = np.where(m[..., None], rgb(c0) * (1 - f) + rgb(c1) * f, out)
        self.rgb = out

    def radial_wash(self, cx, cy, radius, color, peak) -> None:
        """Colour fading to fully transparent at the radius (glow / wash)."""
        t = np.clip(np.hypot(self.x_art - cx, self.y_art - cy) / radius, 0, 1)
        self.blend((1 - t) * peak, rgb(color))

    def stamp(self, mask: Image.Image, color: str, alpha: float = 1.0) -> None:
        self.blend(np.asarray(mask, dtype=np.float32) / 255 * alpha, rgb(color))


def new_mask() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    m = Image.new("L", (R, R), 0)
    return m, ImageDraw.Draw(m)


def disc(draw, cx, cy, r, fill=255) -> None:
    draw.ellipse([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], fill=fill)


def star_points(cx, cy, scale, rotation=0.0):
    a = math.radians(rotation)
    out = []
    for vx, vy in STAR_PTS:
        sx, sy = vx * scale, vy * scale
        out.append((px(cx + sx * math.cos(a) - sy * math.sin(a)),
                    px(cy + sx * math.sin(a) + sy * math.cos(a))))
    return out


def cubic_points(d: str, steps: int = 96):
    head, tail = d[1:].split("C")
    p0 = tuple(float(v) for v in head.split(","))
    p1, p2, p3 = (tuple(float(v) for v in part.split(",")) for part in tail.split())
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        pts.append((px(u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0]),
                    px(u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1])))
    return pts


def render() -> Image.Image:
    c = Canvas()

    # --- background ---
    (nx, ny, nr), stops = NEBULA
    c.radial(nx, ny, nr, stops)
    (wx, wy, wr), wc, wa = WASH
    c.radial_wash(wx, wy, wr, wc, wa)
    m, d = new_mask()
    for sx, sy, sr in BG_STARS:
        disc(d, to_art(sx), to_art(sy), sr * 8)
    c.stamp(m, BG_STAR_COLOR, BG_STAR_ALPHA)

    # --- lens grid, clipped to the glass ---
    m, d = new_mask()
    for path in LENS_GRID:
        d.line(cubic_points(path), fill=255, width=max(1, round(GRID_WIDTH * K)), joint="curve")
    clip, cd = new_mask()
    disc(cd, RING[0], RING[1], LENS_INNER)
    m = Image.fromarray((np.asarray(m, dtype=np.uint16) * np.asarray(clip, dtype=np.uint16) // 255)
                        .astype(np.uint8))
    c.stamp(m, GRID_COLOR, GRID_ALPHA)

    # --- glow, planets, stars ---
    gx, gy, gr, gc, ga = GLOW
    c.radial_wash(gx, gy, gr, gc, ga)
    for cx, cy, r, color in PLANETS:
        m, d = new_mask()
        disc(d, cx, cy, r)
        c.stamp(m, color)
    m, d = new_mask()
    d.polygon(star_points(*CORNER_STAR[:3], CORNER_STAR[3]), fill=255)
    c.stamp(m, STAR_COLOR)
    m, d = new_mask()
    d.polygon(star_points(*HERO), fill=255)
    c.stamp(m, STAR_COLOR)

    # --- magnifier, over the star ---
    m, d = new_mask()
    rcx, rcy, rr, rw = RING
    disc(d, rcx, rcy, rr + rw / 2)
    disc(d, rcx, rcy, rr - rw / 2, fill=0)
    hx1, hy1, hx2, hy2, hw = HANDLE
    d.line([px(hx1), px(hy1), px(hx2), px(hy2)], fill=255, width=round(hw * K))
    disc(d, hx1, hy1, hw / 2)          # round caps
    disc(d, hx2, hy2, hw / 2)
    c.stamp(m, LENS_COLOR)

    flat = (np.clip(c.rgb, 0, 1) * 255 + 0.5).astype(np.uint8)
    return Image.fromarray(flat, "RGB").resize((SIZE, SIZE), Image.LANCZOS)


def main() -> None:
    out = (Path(__file__).resolve().parents[2]
           / "fastlane/metadata/android/en-US/images/icon.png")
    render().save(out, "PNG", optimize=True)
    print(f"wrote {out.relative_to(Path(__file__).resolve().parents[2])} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
