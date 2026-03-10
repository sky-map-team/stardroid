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

## Step 1 — Gather and validate inputs

If `$ARGUMENTS` is empty, ask the user:

1. **Source image** — local path or URL
2. **Output path** — `<category>/<filename>` without extension.
   Valid categories: `constellations`, `stars`, `messier`, `planets`.
   Suggest a name based on the subject (e.g. `messier/hubble_m42`).
3. **Crop** (optional) — `x1,y1,x2,y2` to extract a sub-region before resizing.
   Skip if the subject is already well-centred and the image is in portrait or square orientation.

**Validate before proceeding — reject and ask again if any check fails:**

- **Category**: must be exactly one of `constellations`, `stars`, `messier`, `planets`.
- **Name** (filename part): must match `[a-z0-9_]+` only.
- **Crop**: if provided, must be exactly four tokens that are each non-negative integers.
- **URLs**: must begin with `https://`. Reject `http://`, `file://`, and all other schemes.
  Also reject any URL whose hostname resolves to a private/loopback range — this is enforced
  again at download time (Step 2), but reject obviously bad hostnames early (e.g. `localhost`,
  `127.*`, `10.*`, `192.168.*`, `169.254.*`, `[::1]`).
- **Local paths**: must match `[a-zA-Z0-9/_.\- ]+` — reject any path containing shell
  metacharacters (`"`, `'`, `` ` ``, `$`, `!`, `&`, `|`, `;`, `(`, `)`, `<`, `>`, `\n`, etc.).
  If the user provides such a path, ask them to move or rename the file.

## Step 2 — Write inputs to a config file (IMPORTANT — security boundary)

**Never interpolate user-provided values into shell command strings.**
Instead, use the **Write tool** to write a JSON config file. Python will read from it — the
values never pass through the shell.

Use the Write tool to create `/tmp/celestial_cfg.json` with the following structure
(substituting the actual validated values you collected):

```json
{
  "source": "/validated/local/path/or/https://validated.url/image.jpg",
  "category": "messier",
  "name": "hubble_m42",
  "crop": [x1, y1, x2, y2]
}
```

Set `"crop"` to `null` if no crop was requested.

## Step 3 — Acquire the image

If the source is a URL, download it using the script below. All inputs are read from the config
file — no user data ever appears in a shell command line.

```bash
python3 - <<'EOF'
import json, os, socket, tempfile, urllib.parse, urllib.request
import ipaddress

with open('/tmp/celestial_cfg.json') as f:
    cfg = json.load(f)

url = cfg['source']

# Validate scheme (defence-in-depth; Step 1 already checked this)
parsed = urllib.parse.urlparse(url)
if parsed.scheme != 'https':
    raise SystemExit(f'rejected scheme: {parsed.scheme!r}')

# Resolve hostname and block private/loopback ranges (SSRF guard)
host = parsed.hostname
try:
    addr = ipaddress.ip_address(socket.getaddrinfo(host, None)[0][4][0])
except Exception as e:
    raise SystemExit(f'cannot resolve host {host!r}: {e}')
if addr.is_private or addr.is_loopback or addr.is_link_local or addr.is_reserved:
    raise SystemExit(f'rejected private/reserved address: {addr}')

# Derive a safe extension from the URL path (ignore query strings)
url_path = parsed.path
suffix = os.path.splitext(url_path)[1][:5].lower() or '.jpg'
allowed_exts = {'.jpg', '.jpeg', '.png', '.webp', '.tif', '.tiff'}
if suffix not in allowed_exts:
    suffix = '.jpg'

# Use a unique temp file to avoid race conditions
fd, src_path = tempfile.mkstemp(suffix=suffix, prefix='celestial_src_')
os.close(fd)

urllib.request.urlretrieve(url, src_path)
print('downloaded to', src_path, os.path.getsize(src_path), 'bytes')

# Store the resolved local path back into config for subsequent steps
cfg['resolved_src'] = src_path
with open('/tmp/celestial_cfg.json', 'w') as f:
    json.dump(cfg, f)
EOF
```

If the source is already a local path, add `"resolved_src"` to the config manually using the
Write tool (copy the existing config and add `"resolved_src": "<the local path>"`).

**Read the source image file** with the Read tool to view it visually.

## Step 4 — Inspect dimensions

All inputs come from the config file — nothing is passed via shell arguments.

```bash
python3 - <<'EOF'
import json, os
from PIL import Image

with open('/tmp/celestial_cfg.json') as f:
    cfg = json.load(f)

src = cfg['resolved_src']
img = Image.open(src)
print('size:', img.size, '  mode:', img.mode)
print('file size:', os.path.getsize(src), 'bytes')
EOF
```

Report dimensions to the user. If the aspect ratio is very different from 480:800 (3:5) and no
crop was specified, suggest a crop that captures the most interesting region and ask the user to
confirm before proceeding. If the user provides a new crop, update `/tmp/celestial_cfg.json`
using the Write tool.

## Step 5 — Convert to 480×800 WebP

All inputs come from the config file. Crop values are parsed as integers with bounds checks.

```bash
python3 - <<'EOF'
import json, os, tempfile
from PIL import Image

with open('/tmp/celestial_cfg.json') as f:
    cfg = json.load(f)

src  = cfg['resolved_src']
crop = cfg.get('crop')  # null or [x1, y1, x2, y2]

if crop is not None:
    if len(crop) != 4 or not all(isinstance(v, int) and v >= 0 for v in crop):
        raise SystemExit('crop must be a list of 4 non-negative integers')
    crop = tuple(crop)

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

fd, out_path = tempfile.mkstemp(suffix='.webp', prefix='celestial_out_')
os.close(fd)
img.save(out_path, 'WEBP', quality=80, method=6)
print('saved', out_path, os.path.getsize(out_path), 'bytes')

cfg['out_path'] = out_path
with open('/tmp/celestial_cfg.json', 'w') as f:
    json.dump(cfg, f)
EOF
```

**Read the output `.webp` file** (path printed above) to visually verify quality and framing. If
it looks poor (badly cropped, blurry, or key detail lost), update the `crop` field in
`/tmp/celestial_cfg.json` using the Write tool and repeat this step.

### Format selection rationale

WebP lossy quality=80 is almost always the smallest option for photographic images and is
universally supported on Android 4.0+. Only fall back to JPEG if WebP produces a larger file
(rare). Never use PNG for photographic images.

## Step 6 — Check file size

Typical range for existing assets is **10–90 KB**. If the output exceeds 150 KB, try quality=70:

```bash
python3 - <<'EOF'
import json, os
from PIL import Image

with open('/tmp/celestial_cfg.json') as f:
    cfg = json.load(f)

out = cfg['out_path']
img = Image.open(out)
img.save(out, 'WEBP', quality=70, method=6)
print(os.path.getsize(out), 'bytes')
EOF
```

## Step 7 — Deploy

All values come from the config file. Category and name are re-validated here, and the resolved
destination path is checked to be inside the assets directory before any copy occurs.

```bash
python3 - <<'EOF'
import json, os, re, shutil

with open('/tmp/celestial_cfg.json') as f:
    cfg = json.load(f)

category = cfg['category']
name     = cfg['name']
out      = cfg['out_path']

VALID_CATEGORIES = {'constellations', 'stars', 'messier', 'planets'}
if category not in VALID_CATEGORIES:
    raise SystemExit(f'invalid category: {category!r}')
if not re.fullmatch(r'[a-z0-9_]+', name):
    raise SystemExit(f'invalid name: {name!r}')

assets_root = os.path.abspath('app/src/main/assets/celestial_images')
dest = os.path.abspath(os.path.join(assets_root, category, f'{name}.webp'))

if not dest.startswith(assets_root + os.sep):
    raise SystemExit('path traversal detected — aborting')

os.makedirs(os.path.dirname(dest), exist_ok=True)
shutil.copy2(out, dest)
print('deployed to', dest)
EOF
```

Then update `object_info.json` to refer to the new image.

## Step 8 — Report

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
