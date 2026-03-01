---
name: release-splash
description: Update the Sky Map splash screen for a new planetary release. Composites a portrait image onto the base splash with a branded strip. Use when asked to "update splash", "add splash for X release", or "create release branding". ARGUMENTS: "<ReleaseName> <path/to/source.png> [crop x1,y1,x2,y2]"
disable-model-invocation: true
---

# Sky Map Release Splash Screen

Update the splash screen assets for a new Sky Map release.

## Arguments

`$ARGUMENTS` should be: `<ReleaseName> <path/to/source.png> [x1,y1,x2,y2]`

Examples:
- `/release-splash Earth ~/Downloads/earth.png`
- `/release-splash Mars ~/Downloads/mars.png 200,0,1800,1600`
- `/release-splash "T CrB" ~/Downloads/tcrb.png 400,100,1600,1300`

If no arguments are provided, ask the user for:
1. The release label (e.g. "Earth", "Mars", "T CrB")
2. The path to the source portrait image
3. (Optional) A crop region `x1,y1,x2,y2` — suggest skipping if the image is already square and well-centered

## Workflow

### Step 1 — Inspect the source image

Read the image file to see it visually. Check:
- Dimensions (via `python3 -c "from PIL import Image; img = Image.open('PATH'); print(img.size)"`)
- Whether it is already square and subject is centered
- If not square or subject is off-center, ask the user to confirm a crop region

### Step 2 — Copy source image to assets

```bash
cp <source_path> assets/splashscreens/<release_name_lowercase>.png
```

Use the release label lowercased with spaces replaced by underscores as the filename
(e.g. "T CrB" → `t_crb.png`, "Earth" → `earth.png`).

### Step 3 — Generate the main splash (portrait phones)

```bash
python3 tools/make_release_splash.py \
    --input assets/splashscreens/<name>.png \
    --label "<ReleaseName>" \
    --output /tmp/splash_main.webp \
    [--crop x1,y1,x2,y2]
```

Read `/tmp/splash_main.webp` to visually verify it looks correct before proceeding.

### Step 4 — Generate the large splash (tablets/landscape)

```bash
python3 tools/make_release_splash.py \
    --input assets/splashscreens/<name>.png \
    --label "<ReleaseName>" \
    --output /tmp/splash_large.jpg \
    --splash assets/splashscreens/stardroid_big_image_large.jpg \
    [--crop x1,y1,x2,y2]
```

Read `/tmp/splash_large.jpg` to visually verify it looks correct before proceeding.

### Step 5 — Deploy to res/

```bash
cp /tmp/splash_main.webp  app/src/main/res/drawable/stardroid_big_image.webp
cp /tmp/splash_large.jpg  app/src/main/res/drawable-large/stardroid_big_image.jpg
```

### Step 6 — Report

Confirm to the user:
- Source saved as: `assets/splashscreens/<name>.png`
- Main splash updated: `app/src/main/res/drawable/stardroid_big_image.webp`
- Large splash updated: `app/src/main/res/drawable-large/stardroid_big_image.jpg`
- Suggest committing: `git add assets/splashscreens/<name>.png app/src/main/res/drawable/ app/src/main/res/drawable-large/ && git commit -m "Add <ReleaseName> release splash screen branding"`

## Reference: Base Splash Assets

The script reads from backup originals, not the live res files:
- Main base: `assets/splashscreens/stardroid_big_image.webp`
- Large base: `assets/splashscreens/stardroid_big_image_large.jpg`

This means it is safe to re-run without double-overlaying branding.

## Reference: Past Releases

| Release | Source file | Crop |
|---------|------------|------|
| Venus | `assets/splashscreens/venus.png` | `808,0,2016,1200` |
| Earth | `assets/splashscreens/earth.png` | none (2048×2048, centered) |

## Troubleshooting

- **Pillow not installed**: `pip install Pillow`
- **Text clipped**: try a narrower label or reduce `--strip-height`
- **Portrait looks squashed**: provide a square `--crop` centred on the subject
- **Portrait too dark/bright**: adjust with `--opacity` (default 1.0)
