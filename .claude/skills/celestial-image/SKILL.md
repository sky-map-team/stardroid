---
name: celestial-image
description: Process and size an image for a Sky Map celestial info card. Prompts for an image (URL or local path), optional crop, and output filename, then converts to 480×800 WebP and saves to the correct assets directory. Trigger on "process celestial image", "add image for info card", "convert image for Sky Map", etc. ARGUMENTS: "[source_url_or_path] [category/output_name] [crop x1,y1,x2,y2]"
---

# Sky Map Celestial Info Card Image

Convert and size an image for use in a Sky Map celestial info card.

## Target Spec

- **Size**: 480 × 800 px (portrait)
- **Format**: WebP (lossy, quality 80) — smallest file for photographic content
- **Location**: `app/src/main/assets/celestial_images/<category>/<name>.webp`
- **Referenced in**: `object_info.json` as `"imageKey": "<category>/<name>.webp"`

## Arguments

`$ARGUMENTS` may contain (all optional — prompt for missing ones):

1. Source image — a local file path or a URL
2. Output path relative to `celestial_images/` — e.g. `messier/hubble_m1` or `stars/eso_sirius`
   (`.webp` extension added automatically)
3. Crop region `x1,y1,x2,y2` in source-image pixels

## Step 1 — Gather inputs

If `$ARGUMENTS` is empty, ask the user:

1. **Source image** — local path or URL
   (For URLs, tell the user you'll download it to `/tmp/`)
2. **Output path** — `<category>/<filename>` without extension.
   Valid categories: `constellations`, `stars`, `messier`, `planets`.
   Suggest a name based on the subject (e.g. `messier/hubble_m42`).
3. **Crop** (optional) — `x1,y1,x2,y2` to extract a sub-region before resizing.
   Skip if the subject is already well-centred and the image is in portrait or square orientation.

Parse any arguments provided in `$ARGUMENTS` before asking.

**Validate inputs before proceeding:**
- Category must be one of: `constellations`, `stars`, `messier`, `planets` — reject anything else.
- Name (filename part) must match `[a-z0-9_]+` — reject if it contains any other characters.
- If a crop is provided, all four values must be non-negative integers — reject otherwise.
- If a URL is provided, it must begin with `https://` — reject `http://` and any other scheme.

## Step 2 — Acquire the image

**If the source is a URL**, download using Python (never pass the URL to a shell command):

```python
import urllib.request, os, sys
url = sys.argv[1]
ext = url.rsplit('.', 1)[-1].split('?')[0][:4].lower() or 'jpg'
dest = f'/tmp/celestial_src.{ext}'
urllib.request.urlretrieve(url, dest)
print('downloaded to', dest, os.path.getsize(dest), 'bytes')
```

Run this by writing it to `/tmp/dl.py` and executing:
```bash
python3 /tmp/dl.py "https://example.com/image.jpg"
```

**Read the downloaded or local file** with the Read tool to view it visually.

## Step 3 — Inspect dimensions

Pass the source path as an argument — never interpolate it into the script body:

```bash
python3 - "$SOURCE_PATH" <<'EOF'
import sys, os
from PIL import Image
src = sys.argv[1]
img = Image.open(src)
print('size:', img.size, '  mode:', img.mode)
print('file size:', os.path.getsize(src), 'bytes')
EOF
```

Report dimensions to the user. If the aspect ratio is very different from 480:800 (3:5) and no
crop was specified, suggest a crop that captures the most interesting region and ask the user to
confirm before proceeding.

## Step 4 — Convert to 480×800 WebP

Pass source path and optional crop as arguments — never embed them in the script body.
Crop values must be validated as integers before this step (Step 1).

```bash
python3 - "$SOURCE_PATH" "${CROP_OR_EMPTY}" <<'EOF'
import sys
from PIL import Image

src = sys.argv[1]
crop_arg = sys.argv[2] if len(sys.argv) > 2 else ''

crop = None
if crop_arg:
    # Parse and validate: must be exactly 4 non-negative integers
    parts = crop_arg.split(',')
    if len(parts) != 4:
        sys.exit('crop must be x1,y1,x2,y2')
    try:
        crop = tuple(int(p) for p in parts)
    except ValueError:
        sys.exit('crop values must be integers')
    if any(v < 0 for v in crop):
        sys.exit('crop values must be non-negative')

dst = '/tmp/celestial_out.webp'

img = Image.open(src).convert('RGB')

if crop:
    img = img.crop(crop)

target_w, target_h = 480, 800
src_w, src_h = img.size
scale = max(target_w / src_w, target_h / src_h)
new_w = round(src_w * scale)
new_h = round(src_h * scale)
img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)

left = (new_w - target_w) // 2
top  = (new_h - target_h) // 2
img = img.crop((left, top, left + target_w, top + target_h))

img.save(dst, 'WEBP', quality=80, method=6)
import os; print('saved', dst, os.path.getsize(dst), 'bytes')
EOF
```

**Read `/tmp/celestial_out.webp`** to visually verify quality and framing. If it looks poor (badly
cropped, blurry, or key detail lost), suggest an adjusted crop to the user and repeat.

### Format selection rationale

WebP lossy quality=80 is almost always the smallest option for photographic images and is
universally supported on Android 4.0+. Only fall back to JPEG if the source is JPEG with no
alpha and WebP produces a larger file (rare). Never use PNG for photographic images.

## Step 5 — Check file size

Typical range for existing assets is **10–90 KB**. If the output exceeds 150 KB, try quality=70:

```bash
python3 - <<'EOF'
import os
from PIL import Image
img = Image.open('/tmp/celestial_out.webp')
img.save('/tmp/celestial_out.webp', 'WEBP', quality=70, method=6)
print(os.path.getsize('/tmp/celestial_out.webp'), 'bytes')
EOF
```

## Step 6 — Deploy

Use Python to copy the file — never interpolate category or name into a shell command.
Validate that the resolved destination path is inside the assets directory before copying.

```bash
python3 - "$CATEGORY" "$NAME" <<'EOF'
import sys, os, shutil

category = sys.argv[1]
name     = sys.argv[2]

VALID_CATEGORIES = {'constellations', 'stars', 'messier', 'planets'}
import re
if category not in VALID_CATEGORIES:
    sys.exit(f'invalid category: {category!r}')
if not re.fullmatch(r'[a-z0-9_]+', name):
    sys.exit(f'invalid name: {name!r}')

assets_root = os.path.abspath('app/src/main/assets/celestial_images')
dest = os.path.abspath(os.path.join(assets_root, category, f'{name}.webp'))

# Guard against path traversal
if not dest.startswith(assets_root + os.sep):
    sys.exit('path traversal detected — aborting')

os.makedirs(os.path.dirname(dest), exist_ok=True)
shutil.copy2('/tmp/celestial_out.webp', dest)
print('deployed to', dest)
EOF
```

Then update `object_info.json` to refer to the new image.

## Step 7 — Report

Tell the user:

- **Saved to**: `app/src/main/assets/celestial_images/<category>/<name>.webp`
- **File size**: X KB
- **Dimensions**: 480 × 800
- **imageKey** to use in `object_info.json`: `"<category>/<name>.webp"`
- **imageCredit**: remind the user to add an appropriate credit string (e.g. `"NASA/ESA/Hubble"`)
- Suggest git staging: `git add app/src/main/assets/celestial_images/<category>/<name>.webp`

## Reference — existing naming conventions

| Category | Prefix examples |
|---|---|
| `messier/` | `hubble_m1`, `hubble_m42`, `eso_m104` |
| `stars/` | `eso_sirius`, `nasa_sun`, `hubble_eta_carinae` |
| `constellations/` | `iau_orion`, `iau_ursa_major` |
| `planets/` | `hubble_saturn`, `nasa_mercury` |

Use `<source_agency>_<subject>` in lowercase with underscores.

## Troubleshooting

- **Pillow not installed**: `pip install Pillow`
- **WebP larger than JPEG**: use JPEG quality=85 instead (uncommon for space photos)
- **URL download fails**: ask the user to download the image manually and provide a local path
- **Image too dark/washed out after resize**: the source may need colour correction — note it in
  the report but do not auto-correct
