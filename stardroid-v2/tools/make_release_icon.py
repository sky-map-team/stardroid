#!/usr/bin/env python3
"""
make_release_icon.py — Make a circular release-branding icon for the changelog/GitHub release.

Unlike v1's make_release_splash.py, this does not touch any app splash-screen resource — v2 has
no per-release branded app splash (see stardroid-v2/AGENTS.md). This only produces a small
circular portrait PNG used to decorate a CHANGELOG.md entry / GitHub release, mirroring v1's
`<version>_<name>_icon.png` convention.

Usage:
    python3 tools/make_release_icon.py \
        --input assets/release-icons/2.0.0-beta06_louise.png \
        --output /tmp/2.0.0-beta06_louise_icon.png \
        [--crop x1,y1,x2,y2] \
        [--size 256]

Requires: pip install Pillow
"""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")


def circular_crop(img: Image.Image, size: int) -> Image.Image:
    # Center-crop to square first to avoid distortion
    w, h = img.size
    s = min(w, h)
    img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))
    img = img.resize((size, size), Image.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(img, (0, 0), mask)
    return result


def parse_crop(s: str) -> tuple[int, int, int, int]:
    try:
        parts = [int(v) for v in s.split(",")]
    except ValueError:
        raise argparse.ArgumentTypeError(f"--crop values must be integers, got: {s!r}")
    if len(parts) != 4:
        raise argparse.ArgumentTypeError(
            f"--crop requires exactly 4 values (x1,y1,x2,y2), got {len(parts)}"
        )
    return tuple(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True, help="Path to portrait/source image")
    parser.add_argument("--output", type=Path, required=True, help="Output icon path (.png)")
    parser.add_argument("--size", type=int, default=256, help="Icon size in pixels (default 256)")
    parser.add_argument("--crop", type=parse_crop, metavar="x1,y1,x2,y2",
                         help="Pixel crop of input image before cropping to a circle")
    args = parser.parse_args()

    portrait = Image.open(args.input).convert("RGBA")
    if args.crop:
        portrait = portrait.crop(args.crop)

    icon = circular_crop(portrait, args.size)
    icon.save(args.output, "PNG")
    print(f"Saved: {args.output}")


if __name__ == "__main__":
    main()
