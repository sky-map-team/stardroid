---
name: skymap.release-splashscreen
description: Generate the small circular release-branding icon for a Sky Map v2 release's CHANGELOG entry and GitHub release. Use when asked to "update splash for v2", "add release icon for X release", or "create v2 release branding". ARGUMENTS — "<ReleaseName> <path/to/source.png> [crop x1,y1,x2,y2]"
disable-model-invocation: true
---

# Sky Map v2 Release Icon

Generates the small circular portrait icon embedded in `CHANGELOG.md` and the GitHub release for
a v2 release (e.g. an "Eclipse" portrait for a release named Eclipse), mirroring v1's
`skymap.release-splashscreen` skill.

**This does not touch the app itself.** v2 has no per-release branded splash screen — it uses the
stock AndroidX SplashScreen API with a single static image
(`app/src/main/assets/splash/apollo.png`) that is not swapped per release (see
`stardroid-v2/AGENTS.md`). Only the changelog/GitHub-release icon is generated here.

## Arguments

`$ARGUMENTS` should be: `<ReleaseName> <path/to/source.png> [x1,y1,x2,y2]`

Examples:
- `/skymap.release-splashscreen Louise ~/Downloads/louise.png`
- `/skymap.release-splashscreen Jupiter ~/Downloads/jupiter.png 200,0,1800,1600`

If no arguments are provided, ask the user for:
1. The release label (e.g. "Louise", "Jupiter")
2. The path to the source portrait image
3. (Optional) A crop region `x1,y1,x2,y2` — suggest skipping if the image is already square and
   well-centered

## Workflow

### Step 1 — Inspect the source image

Read the image file to see it visually. Check:
- Dimensions (via `python3 -c "from PIL import Image; img = Image.open('PATH'); print(img.size)"`)
- Whether it is already square and the subject is centered
- If not square or off-center, ask the user to confirm a crop region

### Step 2 — Copy source image to assets with a version stamp

```bash
mkdir -p assets/release-icons
cp <source_path> assets/release-icons/<version>_<release_name_lowercase>.png
```

Use the version number and release label lowercased with spaces replaced by underscores.
`assets/release-icons/` lives at the `stardroid-v2/` module root — like v1's
`assets/splashscreens/`, it is outside `app/src/main/res` and `app/src/main/assets`, so
`tools/check_asset_licenses.py` does not need to classify it (it only enforces assets shipped
inside the app).

Examples: `2.0.0-beta06_louise.png`, `2.0.0_jupiter.png`.

### Step 3 — Generate the circular icon

```bash
python3 tools/make_release_icon.py \
    --input assets/release-icons/<version>_<name>.png \
    --output /tmp/<version>_<name>_icon.png \
    --size 256 \
    [--crop x1,y1,x2,y2]
```

Read `/tmp/<version>_<name>_icon.png` to confirm it looks good (a small round portrait with a
transparent background).

### Step 4 — Deploy to assets/

```bash
cp /tmp/<version>_<name>_icon.png assets/release-icons/<version>_<name>_icon.png
```

**Important:** the icon filename must include the version, so distinct sub-releases of the same
name (e.g. two beta builds both named "Louise") get distinct icons.

### Step 5 — Report

Confirm to the user:
- Source saved as: `assets/release-icons/<version>_<name>.png`
- Circular icon saved as: `assets/release-icons/<version>_<name>_icon.png`
- This icon is embedded into `CHANGELOG.md` and the GitHub release by `skymap.release` Step 6 —
  it is not committed separately here; `skymap.release`'s Step 6 commit covers it.

## Troubleshooting

- **Pillow not installed**: `pip install Pillow`
- **Portrait looks squashed**: provide a square `--crop` centred on the subject
