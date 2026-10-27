#!/usr/bin/env python3
# Copyright (c) 2026 Penterakt LLC.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NOTE: this *script* is GPLv3 like the rest of the repository's code; the icon
# *artwork* it renders is All Rights Reserved, Penterakt LLC — see the [arr]
# section of stardroid-v2/ASSET-LICENSES.txt.
"""Render the 1024x1024 iOS app icon from the adaptive icon's vector drawables.

Unlike render_play_icon.py, which mirrors the geometry by hand, this converts
ic_launcher_{background,foreground}.xml to SVG directly, so the icon follows the
drawables. The foreground is scaled up about the centre (Android's adaptive mask
crops the 108dp canvas to its middle 72dp; iOS shows the whole square, rounded by
the system). The result is flattened to an opaque RGB PNG, as the App Store requires.

    python3 tools/icons/render_ios_icon.py

Writes ios/SkyMap/Sources/Assets.xcassets/AppIcon.appiconset/AppIcon.png.
Deterministic; re-run and commit. macOS only: it rasterises with Quick Look (qlmanage)
and sips. Handles only the drawable features the launcher layers use (groups with
translate/scale/rotation, clip paths, paths with solid or radial-gradient fills, strokes).
"""

import struct
import subprocess
import sys
import tempfile
import zlib
import xml.etree.ElementTree as ET
from pathlib import Path

MODULE_ROOT = Path(__file__).resolve().parent.parent.parent
DRAWABLES = MODULE_ROOT / "app/src/main/res/drawable"
OUT = MODULE_ROOT / "ios/SkyMap/Sources/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
SIZE = 1024
# Foreground scale about the centre; 1.3 fills the square and keeps the handle inside
# the rounded corner.
FOREGROUND_SCALE = 1.3

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


class Svg:
    def __init__(self):
        self.defs = []

    def _id(self, prefix):
        return f"{prefix}{len(self.defs)}"

    @staticmethod
    def _color(argb):
        c = argb.lstrip("#")
        if len(c) == 8:
            return "#" + c[2:], round(int(c[:2], 16) / 255, 3)
        return "#" + c, 1

    def _gradient(self, g):
        gid = self._id("g")
        stops = "".join(
            '<stop offset="{}" stop-color="{}" stop-opacity="{}"/>'.format(
                it.get(A + "offset"), *self._color(it.get(A + "color"))
            )
            for it in g.findall("item")
        )
        self.defs.append(
            f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" '
            f'cx="{g.get(A + "centerX")}" cy="{g.get(A + "centerY")}" '
            f'r="{g.get(A + "gradientRadius")}">{stops}</radialGradient>'
        )
        return f"url(#{gid})"

    def node(self, e):
        if e.tag == "group":
            transform = "translate({} {}) scale({} {})".format(
                e.get(A + "translateX", "0"),
                e.get(A + "translateY", "0"),
                e.get(A + "scaleX", "1"),
                e.get(A + "scaleY", "1"),
            )
            if e.get(A + "rotation"):
                transform += f' rotate({e.get(A + "rotation")})'
            clip, inner = "", ""
            for c in e:
                if c.tag == "clip-path":
                    cid = self._id("c")
                    self.defs.append(
                        f'<clipPath id="{cid}"><path d="{c.get(A + "pathData")}"/></clipPath>'
                    )
                    clip = f' clip-path="url(#{cid})"'
                else:
                    inner += self.node(c)
            return f'<g transform="{transform}"{clip}>{inner}</g>'
        if e.tag == "path":
            attrs = f' d="{e.get(A + "pathData")}"'
            fill, gradient = e.get(A + "fillColor"), e.find(AAPT + "attr")
            if gradient is not None:
                attrs += f' fill="{self._gradient(gradient.find("gradient"))}"'
            elif fill:
                attrs += ' fill="{}" fill-opacity="{}"'.format(*self._color(fill))
            stroke = e.get(A + "strokeColor")
            if stroke:
                attrs += ' stroke="{}" stroke-opacity="{}"'.format(*self._color(stroke))
                attrs += ' stroke-width="{}" stroke-linecap="{}"'.format(
                    e.get(A + "strokeWidth"), e.get(A + "strokeLineCap", "butt")
                )
            return f"<path{attrs}/>"
        return ""

    def layer(self, name):
        return "".join(self.node(c) for c in ET.parse(DRAWABLES / name).getroot())


def build_svg():
    svg = Svg()
    background = svg.layer("ic_launcher_background.xml")
    foreground = svg.layer("ic_launcher_foreground.xml")
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" '
        f'viewBox="0 0 108 108"><defs>{"".join(svg.defs)}</defs>{background}'
        f'<g transform="translate(54 54) scale({FOREGROUND_SCALE}) translate(-54 -54)">'
        f"{foreground}</g></svg>"
    )


def flatten_to_rgb_png(bmp_path):
    """Rewrite a 32-bit BMP as an RGB PNG, dropping alpha (the icon must be opaque)."""
    b = bmp_path.read_bytes()
    offset = struct.unpack_from("<I", b, 10)[0]
    w, h = struct.unpack_from("<ii", b, 18)
    top_down, h = h < 0, abs(h)
    rows = []
    for y in range(h):
        row = b[offset + y * w * 4 : offset + (y + 1) * w * 4]
        rgb = bytearray()
        for i in range(0, len(row), 4):
            if row[i + 3] != 255:
                sys.exit("error: rendered icon has transparent pixels")
            rgb += bytes((row[i + 2], row[i + 1], row[i]))
        rows.append(b"\x00" + bytes(rgb))
    if not top_down:
        rows.reverse()

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(b"".join(rows), 9))
        + chunk(b"IEND", b"")
    )


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "icon.svg").write_text(build_svg())
        subprocess.run(
            ["qlmanage", "-t", "-s", str(SIZE), "-o", str(tmp), str(tmp / "icon.svg")],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            ["sips", "-s", "format", "bmp", str(tmp / "icon.svg.png"), "--out", str(tmp / "i.bmp")],
            check=True,
            capture_output=True,
        )
        OUT.write_bytes(flatten_to_rgb_png(tmp / "i.bmp"))
    print(f"wrote {OUT.relative_to(MODULE_ROOT)}")


if __name__ == "__main__":
    main()
