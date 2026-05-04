# Sky Map Project Memory

## Build Gotchas
- Always set `JAVA_HOME=$(/usr/libexec/java_home -v 17)` before building — Gradle will silently use the wrong JDK otherwise
- Use `assembleGmsDebug` not `assembleDebug` (no plain flavor exists)
- Full build/test/deploy commands are in the `/build` skill (`stardroid-v1/.claude/skills/build/SKILL.md`)

## Adding Catalog Objects (Messier/Special)

Use the `/skymap.add_object` skill — it handles all steps automatically.

### Files to touch (in order)
1. `stardroid-v1/tools/data/deep_sky_objects.csv` — source of truth; RA in decimal hours, Dec in decimal degrees
2. `stardroid-v1/app/src/main/res/values/celestial_objects.xml` — one `<string>` per name/alias
3. `stardroid-v1/app/src/main/res/values/celestial_info_cards.xml` — keys: `object_info_<key>_{description,funfact,distance,size}`
4. `stardroid-v1/app/src/main/assets/object_info.json` — JSON entry keyed by primary name key; add `imageKey`/`imageCredit` if image available
5. *(Optional)* `stardroid-v1/app/src/main/assets/celestial_images/messier/<name>.webp` — 480×800 WebP info card image; use `/celestial-image` skill to process a source image. If no free image exists, a 480×800 solid-black WebP is acceptable as placeholder (`python3 -c "from PIL import Image; Image.new('RGB',(480,800),(0,0,0)).save('willman_1.webp','WEBP')"`).

### CSV name format
- Use natural names with spaces, pipe-separated: `T CrB|Blaze Star|T Coronae Borealis`
- `AbstractAsciiProtoWriter.rKeysFromName()` converts spaces→underscores and lowercases to make resource IDs
- Primary label and object_info.json key = first name converted (e.g. `t_crb`)

### Coordinate precision
- For extended nebulae use the **nebula centre**, not the embedded star
- Eta Carinae Nebula (NGC 3372): RA 10h 45m 08.5s / Dec −59° 52' 04" (centre)
  vs. Eta Carinae star: Dec −59° 41' — these differ by ~11 arcmin

## Release Splash Screens

- Tool: `stardroid-v1/tools/make_release_splash.py` — composites a portrait + gold label onto the base splash
- Source images stored in: `stardroid-v1/assets/splashscreens/<name>.png` (e.g. `venus.png`, `earth.png`)
- Base splashes (unbranded originals): `stardroid-v1/assets/splashscreens/stardroid_big_image.webp` / `stardroid_big_image_large.jpg`
- Deployed to: `stardroid-v1/app/src/main/res/drawable/stardroid_big_image.webp` (phones) and `drawable-large/stardroid_big_image.jpg` (tablets)
- Skill: `/release-splash <Label> <path/to/source.png> [crop x1,y1,x2,y2]`
- New skills must be force-added: `git add --force stardroid-v1/.claude/skills/<name>/SKILL.md` (parent `.claude/` is gitignored but skills are tracked)

| Release | Source | Crop |
|---------|--------|------|
| Venus   | `venus.png` | `808,0,2016,1200` |
| Earth   | `earth.png` | none (2048×2048 square) |
| Mars    | `mars splash 5.png` | `640,0,2176,1536` |

## Release Process
- [feedback_play_store_build.md](feedback_play_store_build.md) — Always run a full build before pushing to Play Store
- [feedback_version_name.md](feedback_version_name.md) — Always preserve the full version name including the planet suffix (e.g. `:Mars`) when bumping. Suffix changes with minor/major bumps, not point/bugfix bumps.

## Branching
- [feedback_feature_branches.md](feedback_feature_branches.md) — Always work on a feature branch, never commit directly to master

## Implementation Discipline
- [feedback_no_incidental_cleanups.md](feedback_no_incidental_cleanups.md) — Never make incidental cleanups (lambda conversions, `final`, cast removals, locale changes, style fixes) in a feature PR. Feature-scoped changes only.

## Architecture Constraints
- **No Material Snackbar** — use `Toast` instead. The app's `FullscreenTheme` is AppCompat-only and missing `?attr/colorOnSurface`; inflating a Material `Snackbar` crashes at runtime.
- [Don't confuse "pull and merge" with merging a PR](feedback_pull_and_merge.md) — "pull and merge" means git pull; only merge PRs to master on explicit instruction

