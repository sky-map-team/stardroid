#!/usr/bin/env python3
# Copyright (c) 2026 Penterakt LLC.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# NOTE: this *script* is GPLv3 like the rest of the repository's code. The
# imagery it consumes and emits is NASA/ESA/JHUAPL public-domain scientific
# data, not branding -- see app/src/main/assets/planets/SOURCES.md.
"""Generate the solar-system disc textures in app/src/main/assets/planets/.

Implements section 1 of docs/design/solar-system-imagery.md (D85): one
power-of-two, square, fully-lit disc per body, centred, north pole up, with a
feathered straight-alpha limb. Phase is procedural (D88), so nothing here bakes
a terminator.

The limb feather is applied *inward* from the texture edge, so the disc's
diameter is exactly the texture width. Draw-time sizing (D86) can therefore
treat angularSizeDeg as the body's true apparent diameter with no fudge factor.
Saturn is the one exception: its texture spans the ring system, and
RING_SPAN_RATIO below records how much wider that is than the disc.

Sources are either bundled (the celestial_images/ info-card photos, already
vetted and in-repo) or fetched once into .cache/ from permanent, dated URLs.
Nothing here reads a "latest" endpoint, so re-running is deterministic.

Outputs (deterministic; re-run and commit):
    ../../app/src/main/assets/planets/<body>.webp

Requires Pillow and numpy (`python3 -m pip install Pillow numpy`).
"""

from __future__ import annotations

import argparse
import math
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
BUNDLED = REPO / "app/src/main/assets/celestial_images/planets"
OUT = REPO / "app/src/main/assets/planets"
CACHE = HERE / ".cache"

# Width of the inward alpha feather, in output pixels. Two to three pixels is
# enough to kill the stair-stepping the old alpha-tested art had (D85) without
# visibly shrinking the disc.
FEATHER_PX = 2.5

# WebP quality for the RGB channels. The alpha channel is stored losslessly
# regardless (see the save call), since the feather is what the renderer blends.
QUALITY = 88

# Saturn's ring system spans this many disc diameters along the ring major
# axis, measured from the source image. The texture is sized to the rings, so
# SolarSystemLayer must scale Saturn's angular size by this factor (D85 s1).
RING_SPAN_RATIO = 2.27


@dataclass
class Body:
    name: str
    # Texture edge, a power of two (the renderer mipmaps these, which needs
    # one). Two things bound it, and the smaller wins:
    #
    #   - the source. Above the lit span measured out of the source image
    #     there is nothing left to resolve, so a bigger texture is a bigger
    #     upscale, not more planet. The bundled Hubble frames top out around
    #     450px, which is why nothing here goes past 512.
    #   - how large the body is ever drawn. A texture is only magnified past
    #     its texels once the disc exceeds `size` pixels on screen; below that
    #     the extra detail is mipmapped straight back out. In glyph mode
    #     (D87's default) a body drawn D degrees across passes `size` pixels
    #     at a field of view of shortSide * D / size -- so on a 1280px screen
    #     Jupiter's 2.05deg glyph outgrows 256 texels at a 10deg FOV, well
    #     inside the range people zoom through, while Mercury's 0.74deg glyph
    #     does not until 3.7deg.
    #
    # Jupiter and Saturn clear both bars: they are the largest planets drawn
    # and the only ones whose sources hold real structure (bands, the Great
    # Red Spot, the ring divisions -- Saturn's globe is only 226 of its 512).
    # Uranus and Neptune are drawn nearly as large but their sources are soft,
    # featureless discs, so 512 would buy nothing. Mercury and Venus have the
    # detail but are never drawn big enough to show it, and Pluto -- five times
    # below its New Horizons source -- is the smallest glyph in the ladder.
    size: int
    source: str
    # Degrees to rotate the source counter-clockwise to bring the body's IAU
    # north pole up. Measured from the image, not guessed -- see README.md.
    rotate: float = 0.0
    # Fit the texture to the whole lit object (Saturn's rings) rather than to
    # a circular disc, and matte the alpha from luminance so the ring gaps and
    # the gap between disc and rings stay transparent.
    ringed: bool = False
    # The source is an equirectangular global map to be projected onto a disc,
    # not a photograph of one. Guarantees a fully-lit disc, since a map carries
    # no illumination of its own.
    equirect: bool = False
    # Fill data gaps in the map before projecting (see fill_gaps).
    gapfill: bool = False
    url: str | None = None
    notes: str = ""


BODIES: list[Body] = [
    Body(
        "moon",
        1024,
        "lroc_color_poles_4k.tif",
        equirect=True,
        url="https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/lroc_color_poles_4k.tif",
        notes="near side, sub-Earth point 0N 0E",
    ),
    Body(
        "sun",
        512,
        "20260814_000000_1024_HMIIF.jpg",
        url="https://sdo.gsfc.nasa.gov/assets/img/browse/2026/08/14/"
        "20260814_000000_1024_HMIIF.jpg",
    ),
    Body(
        "mercury",
        256,
        "PIA12397.jpg",
        equirect=True,
        gapfill=True,
        url="https://images-assets.nasa.gov/image/PIA12397/PIA12397~orig.jpg",
        notes="the bundled nasa_mercury.webp photo has a baked terminator",
    ),
    Body("venus", 256, "nasa_venus.webp"),
    Body("mars", 256, "hubble_mars.webp"),
    Body("jupiter", 512, "hubble_jupiter.webp", rotate=-45.0),
    Body("saturn", 512, "hubble_saturn.webp", rotate=90.0, ringed=True),
    Body("uranus", 256, "hubble_uranus.webp", rotate=93.0),
    Body("neptune", 256, "hubble_neptune.webp"),
    Body("pluto", 128, "nh_pluto_in_false_color.webp"),
]


def source_image(body: Body) -> Image.Image:
    if body.url is None:
        return Image.open(BUNDLED / body.source).convert("RGB")
    CACHE.mkdir(exist_ok=True)
    cached = CACHE / body.source
    if not cached.exists():
        print(f"    fetching {body.url}")
        urllib.request.urlretrieve(body.url, cached)
    return Image.open(cached).convert("RGB")


def luminance(rgb: np.ndarray) -> np.ndarray:
    return rgb @ np.array([0.2126, 0.7152, 0.0722], dtype=np.float32)


# Luminance above which a pixel counts as part of the body rather than sky.
# Measured across all ten sources: the bounding box of the mask is stable from
# 0.06 to 0.20 for every one of them, while lower values pick up the New
# Horizons Pluto frame's non-black background, and an area-based radius drifts
# badly on bodies with a dark limb (Mercury loses 13% of its radius by 0.12).
# So: a mid threshold, and measure the extent by bounding box.
THRESHOLD = 0.10

# Outer edge of Saturn's A ring, in equatorial radii (136,775 / 60,268 km).
# The texture is fitted to the ring system, so this converts between the two.
A_RING_RADII = 2.269


def object_extent(lum: np.ndarray) -> tuple[float, float, float]:
    """Centre and half-extent of the lit body, ignoring stray marks.

    A first pass takes the centroid and a percentile radius, which the image
    credit burnt into the SDO frame cannot meaningfully shift; the bounding box
    is then measured only over pixels near that estimate, so the credit and any
    compression ringing are excluded from the extent that actually matters.
    """
    ys, xs = np.nonzero(lum > THRESHOLD)
    cx0, cy0 = xs.mean(), ys.mean()
    r0 = float(np.percentile(np.hypot(xs - cx0, ys - cy0), 99.5))
    keep = np.hypot(xs - cx0, ys - cy0) < r0 * 1.15
    xs, ys = xs[keep], ys[keep]
    cx = (xs.min() + xs.max()) / 2.0
    cy = (ys.min() + ys.max()) / 2.0
    half = max(xs.max() - cx, cx - xs.min(), ys.max() - cy, cy - ys.min())
    return cx, cy, float(half)


def refine_limb(
    lum: np.ndarray,
    cx: float,
    cy: float,
    half: float,
) -> tuple[float, float, float]:
    """Tighten [half] to the limb's half-maximum, where the source has a hard edge.

    A luminance threshold finds the outermost pixel above the background, which
    on a source with a soft halo -- scattered light around the Sun, a telescope's
    PSF wings -- sits well outside the body. The margin becomes a ring of opaque
    near-black pixels inside the alpha circle: invisible against the night sky,
    but a black rim around the Sun against the daytime gradient, and a disc drawn
    a few percent too large for D86's true-scale sizing.

    Where the profile is flat before it falls (a genuinely sharp edge), the limb
    is taken at half of the plateau, the usual convention. Where it declines
    steadily instead, that is real limb darkening -- Jupiter fades to a twentieth
    of its central brightness at the edge -- and cutting at half-maximum would
    slice off real planet, so the threshold extent stands.
    """
    h, w = lum.shape
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot(xx - cx, yy - cy) / half
    plateau_bins = [float(lum[(r >= f - 0.01) & (r < f + 0.01)].mean()) for f in
                    np.arange(0.80, 0.93, 0.02)]
    plateau = float(np.median(plateau_bins))
    if plateau <= 0 or (max(plateau_bins) - min(plateau_bins)) / plateau > 0.08:
        return cx, cy, half
    background = float(lum[r > 1.08].mean()) if (r > 1.08).any() else 0.0
    # The half-maximum contour is the disc itself, so it fixes the centre as
    # well as the radius. Getting the centre from the threshold mask instead
    # lets a lopsided halo pull it off true, and an off-centre disc shows the
    # margin as a dark arc down one side rather than an even ring.
    mask = (lum > 0.5 * (plateau + background)) & (r < 1.15)
    ys, xs = np.nonzero(mask)
    # Centroid and area, not a bounding box: the contour is noisy at the pixel
    # level, and a box takes its single furthest pixel, which lands outside the
    # limb again. Both of these average that noise away.
    ncx, ncy = float(xs.mean()), float(ys.mean())
    nhalf = math.sqrt(mask.sum() / math.pi)
    # Only ever tightens: the threshold extent is an upper bound on the limb.
    return ncx, ncy, min(half, float(nhalf))


def crop_square(im: Image.Image, cx: float, cy: float, half: float) -> Image.Image:
    """Crop a square of side 2*half about (cx, cy), padding with black if needed."""
    box = (round(cx - half), round(cy - half), round(cx + half), round(cy + half))
    return im.crop(box)


def feathered_circle(size: int) -> np.ndarray:
    """Alpha for a disc that exactly fills the texture, feathered inward."""
    axis = (np.arange(size) + 0.5) / size * 2.0 - 1.0
    r = np.hypot(*np.meshgrid(axis, axis, indexing="xy"))
    edge = 1.0 - FEATHER_PX / (size / 2.0)
    a = (1.0 - r) / (1.0 - edge)
    return np.clip(a, 0.0, 1.0).astype(np.float32)


def fill_gaps(im: Image.Image) -> tuple[Image.Image, float]:
    """Interpolate across unimaged patches in a global mosaic, along latitude.

    MESSENGER's Mercury mosaic has roughly a tenth of its area unimaged, and
    projected raw those holes read as black bites out of the disc -- exactly
    the baked-shadow look this whole design is removing. Interpolating along
    each row (longitude wraps, hence the period) fills them with neighbouring
    terrain: invented detail, but plausible terrain rather than a hole, at a
    size where the difference is invisible and the hole would not be.
    """
    a = np.asarray(im).astype(np.float32)
    gap = luminance(a / 255.0) < 0.05
    h, w, _ = a.shape
    out = a.copy()
    for y in range(h):
        idx = np.nonzero(~gap[y])[0]
        if idx.size < 8:
            continue
        for c in range(3):
            out[y, :, c] = np.interp(np.arange(w), idx, a[y, idx, c], period=w)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB"), float(gap.mean())


def orthographic(equirect: Image.Image, size: int) -> Image.Image:
    """Project an equirectangular map onto a disc, viewed from 0N 0E.

    The map's centre column is 0 deg longitude, which for the LROC mosaic is the
    centre of the near side -- so this is the Moon as it is actually seen.
    """
    src = np.asarray(equirect).astype(np.float32)
    h, w, _ = src.shape
    axis = (np.arange(size) + 0.5) / size * 2.0 - 1.0
    x, y = np.meshgrid(axis, -axis, indexing="xy")
    r2 = x * x + y * y
    inside = r2 <= 1.0
    z = np.sqrt(np.clip(1.0 - r2, 0.0, None))
    lat = np.arcsin(np.clip(y, -1.0, 1.0))
    lon = np.arctan2(x, z)
    u = (lon / (2 * math.pi) + 0.5) * w - 0.5
    v = (0.5 - lat / math.pi) * h - 0.5
    u = np.clip(u, 0, w - 1.001)
    v = np.clip(v, 0, h - 1.001)
    u0, v0 = np.floor(u).astype(np.int32), np.floor(v).astype(np.int32)
    fu, fv = (u - u0)[..., None], (v - v0)[..., None]
    u1, v1 = np.minimum(u0 + 1, w - 1), np.minimum(v0 + 1, h - 1)
    top = src[v0, u0] * (1 - fu) + src[v0, u1] * fu
    bot = src[v1, u0] * (1 - fu) + src[v1, u1] * fu
    out = (top * (1 - fv) + bot * fv) * inside[..., None]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")


def build(body: Body, report: dict) -> Image.Image:
    im = source_image(body)
    if body.equirect:
        report["source_px"] = f"{im.size[0]}x{im.size[1]} equirectangular"
        if body.gapfill:
            im, gap = fill_gaps(im)
            report["gaps_filled"] = f"{gap * 100:.1f}%"
        rgb = orthographic(im, body.size)
        alpha = feathered_circle(body.size)
    else:
        if body.rotate:
            im = im.rotate(body.rotate, resample=Image.BICUBIC, expand=True)
        lum = luminance(np.asarray(im).astype(np.float32) / 255.0)
        cx, cy, half = object_extent(lum)
        if not body.ringed:
            ncx, ncy, refined = refine_limb(lum, cx, cy, half)
            if refined < half * 0.995 or abs(ncx - cx) > 1 or abs(ncy - cy) > 1:
                report["limb"] = (
                    f"r {half:.0f}→{refined:.0f}px "
                    f"centre {cx - ncx:+.1f},{cy - ncy:+.1f}px"
                )
            cx, cy, half = ncx, ncy, refined
        report["source_px"] = f"span {2 * half:.0f}px in {im.size[0]}x{im.size[1]}"
        square = crop_square(im, cx, cy, half)
        rgb = square.resize((body.size, body.size), Image.LANCZOS)
        if body.ringed:
            # A circle would clip the rings, so matte the alpha from luminance:
            # the Cassini division and the sky inside the ring ellipse stay
            # transparent, which is what they are. Union that with the planet's
            # own disc, or the rings' shadow on the planet and the darkened limb
            # would punch holes straight through Saturn.
            lum_out = luminance(np.asarray(rgb).astype(np.float32) / 255.0)
            matte = np.clip(lum_out / THRESHOLD, 0.0, 1.0)
            disc_norm = 1.0 / A_RING_RADII
            feather = FEATHER_PX / (body.size / 2.0)
            disc = np.clip(
                (disc_norm - _radius_grid(body.size)) / feather, 0.0, 1.0
            )
            alpha = np.maximum(matte, disc).astype(np.float32)
            report["disc_px"] = round(body.size / A_RING_RADII)
        else:
            alpha = feathered_circle(body.size)
    out = np.dstack(
        [np.asarray(rgb).astype(np.float32), np.clip(alpha, 0, 1) * 255.0]
    )
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def _radius_grid(size: int) -> np.ndarray:
    axis = (np.arange(size) + 0.5) / size * 2.0 - 1.0
    return np.hypot(*np.meshgrid(axis, axis, indexing="xy"))


def validate(body: Body, img: Image.Image) -> list[str]:
    """Check the two invariants the rest of the design leans on.

    The disc must fill the texture, or D86's true-scale sizing draws every body
    the wrong size; and it must be lit all the way to the limb, or a baked
    shadow will fight the procedural terminator D88 puts on top of it.
    """
    a = np.asarray(img).astype(np.float32) / 255.0
    alpha, lum = a[..., 3], luminance(a[..., :3])
    lit = alpha > 0.5
    problems = []
    cols = np.nonzero(lit.any(axis=0))[0]
    if cols.min() > 1 or cols.max() < body.size - 2:
        problems.append(f"disc does not fill the texture (x {cols.min()}..{cols.max()})")
    if not body.ringed:
        # 10% splits the two cases cleanly: genuine limb darkening tops out at
        # 5% (Jupiter, the strongest here), while the terminator-baked Mercury
        # photo this set replaced reads 21%. Testing instead for a one-sided
        # shadow does not work -- the baked crescent scores lower on quadrant
        # asymmetry (1.6) than Pluto's real albedo does (2.2).
        dark = float((lum[lit] < 0.06).mean())
        if dark > 0.10:
            problems.append(f"{dark * 100:.0f}% of the disc is unlit — baked terminator?")
    return problems


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=OUT)
    ap.add_argument("--only", nargs="*", default=None)
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    total = 0
    for body in BODIES:
        if args.only and body.name not in args.only:
            continue
        report: dict = {}
        img = build(body, report)
        path = args.out / f"{body.name}.webp"
        # Lossy for the photograph, lossless for the matte: alpha_quality=100
        # keeps the limb feather exact, which is the part the renderer's
        # blending depends on, while the RGB compresses like the photo it is.
        # Lossless throughout costs 3.7x the bytes for no visible gain.
        img.save(
            path,
            "WEBP",
            quality=QUALITY,
            alpha_quality=100,
            method=6,
        )
        size = path.stat().st_size
        total += size
        extra = " ".join(f"{k}={v}" for k, v in report.items())
        print(f"  {body.name:<8} {body.size:>4}^2  {size / 1024:6.1f} KB  {extra}")
        for problem in validate(body, img):
            print(f"           WARNING: {problem}")
    print(f"  {'total':<8} {total / 1024:>11.1f} KB")


if __name__ == "__main__":
    main()
