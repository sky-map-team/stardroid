---
name: skymap.release-splashscreen
description: Generate the small circular release-branding icon for a Sky Map v2 release's CHANGELOG entry and GitHub release, and update the app's in-app launch splash portrait. Use when asked to "update splash for v2", "add release icon for X release", or "create v2 release branding". ARGUMENTS — "<ReleaseName> <path/to/source.png> [crop x1,y1,x2,y2]"
---

# Sky Map v2 Release Icon

Generates the small circular portrait icon embedded in `CHANGELOG.md` and the GitHub release for
a v2 release (e.g. an "Eclipse" portrait for a release named Eclipse), mirroring v1's
`skymap.release-splashscreen` skill — and also refreshes the app's in-app launch splash portrait
from the same source image.

The app's launch banner (`VersionBanner.kt`) always loads a single fixed asset,
`app/src/main/assets/splash/splash.png` — there is no per-release lookup or codename resolution
in code. This skill is what keeps that file current: it overwrites `splash.png` with a processed
copy of the release's portrait each time it runs. If this skill is skipped for a release, the
previous release's portrait keeps showing — that's a silent no-op, not a build failure, so don't
skip it without telling the user.

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
inside the app). It's just the historical archive of source portraits (one per release, plus
the changelog/GitHub-release icons); Step 4 below is what actually ships.

Examples: `2.0.0-beta06_louise.png`, `2.0.0_jupiter.png`.

### Step 3 — Generate the circular changelog/GitHub-release icon

```bash
python3 tools/make_release_icon.py \
    --input assets/release-icons/<version>_<name>.png \
    --output /tmp/<version>_<name>_icon.png \
    --size 256 \
    [--crop x1,y1,x2,y2]
```

Read `/tmp/<version>_<name>_icon.png` to confirm it looks good (a small round portrait with a
transparent background).

```bash
cp /tmp/<version>_<name>_icon.png assets/release-icons/<version>_<name>_icon.png
```

**Important:** the icon filename must include the version, so distinct sub-releases of the same
name (e.g. two beta builds both named "Louise") get distinct icons.

### Step 4 — Deploy the in-app launch splash portrait

Overwrite the single fixed asset the app loads, applying the same crop as Step 3 but without the
circular mask (`VersionBanner.kt`'s `LogoDisc` clips it to a circle itself at render time, and
crops centered on load — see D-splash comments there):

```bash
python3 -c "
from PIL import Image
img = Image.open('assets/release-icons/<version>_<name>.png').convert('RGBA')
$( [ -n '<crop>' ] && echo \"img = img.crop((<x1>, <y1>, <x2>, <y2>))\" )
w, h = img.size
s = min(w, h)
img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))
img = img.resize((300, 300), Image.LANCZOS)
img.save('app/src/main/assets/splash/splash.png')
"
```

This overwrites whatever the previous release shipped — `splash.png` always holds exactly one
image. It lives inside `app/src/main/assets/`, so it's already classified once in
`ASSET-LICENSES.txt` under `[third-party]`; no per-release edit needed there unless the source
image's licensing terms differ from the note already on record (check it if the source isn't
public-domain/CC-BY NASA/ESA/JPL/ESO-style imagery).

### Step 5 — Report

Confirm to the user:
- Source saved as: `assets/release-icons/<version>_<name>.png`
- Circular changelog icon saved as: `assets/release-icons/<version>_<name>_icon.png`
- In-app launch splash updated: `app/src/main/assets/splash/splash.png`
- The changelog icon is embedded into `CHANGELOG.md` and the GitHub release by `skymap.release`
  Step 6; `splash.png` ships in the next build as-is. Neither is committed separately here —
  `skymap.release`'s Step 6 commit covers all of it.

## Troubleshooting

- **Pillow not installed**: `pip install Pillow`
- **Portrait looks squashed**: provide a square `--crop` centred on the subject
