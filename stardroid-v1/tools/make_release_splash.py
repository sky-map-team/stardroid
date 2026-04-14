#!/usr/bin/env python3
"""
make_release_splash.py — Add a release branding strip to the Sky Map splash screen.

Adds a semi-transparent dark strip at the bottom of the splash screen containing
a circular portrait and the release label in gold. Output must then be saved in
the correct format for each splash asset (see "Updating the app" below).

Usage:
    python3 tools/make_release_splash.py \
        --input assets/splashscreens/venus.png \
        --label "Venus" \
        --output /tmp/splash_main.jpg \
        --crop 808,0,2016,1200

    # Then also generate the large variant:
    python3 tools/make_release_splash.py \
        --input assets/splashscreens/venus.png \
        --label "Venus" \
        --output /tmp/splash_large.jpg \
        --splash assets/splashscreens/stardroid_big_image_large.jpg \
        --crop 808,0,2016,1200

Options:
    --input PATH          Portrait/source image (required)
    --label TEXT          Release label, e.g. "Venus" (default: Venus)
    --output PATH         Output image path (.jpg or .webp); format is inferred from extension
    --crop x1,y1,x2,y2   Pixel crop of input before compositing.
                          Use a square region centred on the face to avoid distortion.
    --splash PATH         Override base splash (default: res/drawable/stardroid_big_image.webp)
    --strip-height FRAC   Strip height as fraction of splash height (default: 0.13)
    --opacity FLOAT       Portrait circle opacity 0.0–1.0 (default: 1.0)

Updating the app:
    Copy output over the live splash assets:
      app/src/main/res/drawable/stardroid_big_image.webp   ← main (portrait phones)
      app/src/main/res/drawable-large/stardroid_big_image.jpg ← large (tablets/landscape)

Asset locations:
    assets/splashscreens/stardroid_big_image.webp        — original main splash (backup)
    assets/splashscreens/stardroid_big_image_large.jpg   — original large splash (backup)
    assets/splashscreens/venus.png                       — Venus release portrait source
                                                           (Gemini_Generated_Image_q1smpjq1smpjq1sm.png,
                                                            crop 808,0,2016,1200 to isolate face)

Requires: pip install Pillow
"""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow is required: pip install Pillow")

# Point at the backup originals, not the live res files (which may already be branded)
DEFAULT_SPLASH = Path(__file__).parent.parent / "assets/splashscreens/stardroid_big_image.webp"

# Layout proportions (as fractions of strip height)
_CIRCLE_SIZE_FRAC = 0.80
_MARGIN_FRAC = 0.10
_FONT_SIZE_FRAC = 0.48

# Strip overlay colour and text colours
_STRIP_COLOR = (0, 0, 0, 120)       # semi-transparent black (~47% opacity)
_TEXT_COLOR = (255, 220, 120, 255)  # gold
_TEXT_SHADOW_COLOR = (0, 0, 0, 180) # dark drop shadow

# Output quality for lossy formats
_SAVE_QUALITY = 92


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


def get_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    return ImageFont.load_default()


def make_splash(
    input_path: Path,
    label: str,
    output_path: Path,
    crop: tuple[int, int, int, int] | None = None,
    splash_path: Path | None = None,
    strip_height_frac: float = 0.13,
    opacity: float = 1.0,
    bottom_margin_frac: float = 0.07,
    scale: int = 3,
) -> None:
    splash = Image.open(splash_path or DEFAULT_SPLASH).convert("RGBA")
    # Upscale the splash so the overlaid circle has enough pixels to look smooth
    # on high-density phone screens (avoids heavy pixelation from centerCrop upscaling).
    if scale > 1:
        splash = splash.resize((splash.width * scale, splash.height * scale), Image.LANCZOS)
    portrait = Image.open(input_path).convert("RGBA")

    if crop:
        portrait = portrait.crop(crop)

    sw, sh = splash.size
    strip_h = int(sh * strip_height_frac)
    circle_size = int(strip_h * _CIRCLE_SIZE_FRAC)
    margin = int(strip_h * _MARGIN_FRAC)
    # Offset from bottom to keep strip clear of the device navigation bar
    bottom_offset = int(sh * bottom_margin_frac)

    # Semi-transparent strip so the nebula shows through
    strip = Image.new("RGBA", (sw, strip_h), _STRIP_COLOR)
    splash.paste(strip, (0, sh - strip_h - bottom_offset), strip)

    # Circular portrait
    vc = circular_crop(portrait, circle_size)
    if opacity < 1.0:
        r, g, b, a = vc.split()
        a = a.point(lambda x: int(x * opacity))
        vc.putalpha(a)

    # Measure text so we can center the [circle + gap + text] group horizontally.
    # This ensures content stays visible regardless of how centerCrop clips the sides
    # on tall-screen devices.
    draw = ImageDraw.Draw(splash)
    font = get_font(int(strip_h * _FONT_SIZE_FRAC))
    bbox = draw.textbbox((0, 0), label, font=font)
    text_w = bbox[2] - bbox[0]
    text_h = bbox[3] - bbox[1]

    total_w = circle_size + margin + text_w
    x_start = (sw - total_w) // 2

    strip_top = sh - strip_h - bottom_offset
    cy = strip_top + (strip_h - circle_size) // 2
    splash.paste(vc, (x_start, cy), vc)

    tx = x_start + circle_size + margin
    ty = strip_top + (strip_h - text_h) // 2

    draw.text((tx + 2, ty + 2), label, font=font, fill=_TEXT_SHADOW_COLOR)
    draw.text((tx, ty), label, font=font, fill=_TEXT_COLOR)

    # Infer format from extension; quality param is ignored for lossless formats
    suffix = output_path.suffix.lower()
    fmt = {".jpg": "JPEG", ".jpeg": "JPEG", ".webp": "WEBP"}.get(suffix)
    if fmt is None:
        sys.exit(f"Unsupported output format '{suffix}'. Use .jpg or .webp.")
    splash.convert("RGB").save(output_path, fmt, quality=_SAVE_QUALITY)
    print(f"Saved: {output_path}")


def parse_crop(s: str) -> tuple[int, int, int, int]:
    try:
        parts = [int(v) for v in s.split(",")]
    except ValueError:
        raise argparse.ArgumentTypeError(f"--crop values must be integers, got: {s!r}")
    if len(parts) != 4:
        raise argparse.ArgumentTypeError(f"--crop requires exactly 4 values (x1,y1,x2,y2), got {len(parts)}")
    return tuple(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, required=True, help="Path to portrait/source image")
    parser.add_argument("--label", default="Venus", help="Release label text (default: Venus)")
    parser.add_argument("--output", type=Path, required=True,
                        help="Output image path (.jpg or .webp); format inferred from extension")
    parser.add_argument("--crop", type=parse_crop, metavar="x1,y1,x2,y2",
                        help="Pixel crop of input image before compositing")
    parser.add_argument("--splash", type=Path, help="Override splash screen path")
    parser.add_argument("--strip-height", type=float, default=0.13, metavar="FRAC",
                        dest="strip_height_frac",
                        help="Strip height as fraction of splash height (default 0.13)")
    parser.add_argument("--opacity", type=float, default=1.0,
                        help="Portrait circle opacity 0.0–1.0 (default 1.0)")
    parser.add_argument("--bottom-margin", type=float, default=0.07, metavar="FRAC",
                        dest="bottom_margin_frac",
                        help="Gap below strip as fraction of splash height, to clear nav bar (default 0.07)")
    parser.add_argument("--scale", type=int, default=3,
                        help="Upscale factor for output image to reduce pixelation on high-density screens (default 3)")
    args = parser.parse_args()

    make_splash(
        input_path=args.input,
        label=args.label,
        output_path=args.output,
        crop=args.crop,
        splash_path=args.splash,
        strip_height_frac=args.strip_height_frac,
        opacity=args.opacity,
        bottom_margin_frac=args.bottom_margin_frac,
        scale=args.scale,
    )


if __name__ == "__main__":
    main()
