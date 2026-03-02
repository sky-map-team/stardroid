# Sky Map Project Memory

## Build Setup
- Always run `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` before building
- Always set `ANDROID_HOME=~/Library/Android/sdk` when running gradlew
- Use `assembleGmsDebug` not `assembleDebug` for the GMS flavor

## Architecture Patterns
- Dialog fragments follow the pattern in `AbstractDynamicStarMapModule` — add a `@Provides @PerActivity` method returning `new XyzDialogFragment()`
- Add `XyzDialogFragment.ActivityComponent` to `DynamicStarMapComponent` interface
- Inject the fragment in `DynamicStarMapActivity` and handle in `onOptionsItemSelected`

## Adding Catalog Objects (Messier/Special)

### Files to touch (in order)
1. `tools/data/messier.csv` — source of truth; RA in decimal hours, Dec in decimal degrees
2. `app/src/main/res/values/celestial_objects.xml` — one `<string>` per name/alias
3. `app/src/main/res/values/celestial_info_cards.xml` — keys: `object_info_<key>_{description,funfact,distance,size}`
4. `app/src/main/assets/object_info.json` — JSON entry keyed by primary name key

### Rebuild pipeline (from project root)
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew clean :tools:installDist
# build_skymap.sh fixes classpath automatically; if running manually:
sed -i -e 's#CLASSPATH=#CLASSPATH=$APP_HOME/lib/:#g' tools/build/install/datagen/bin/datagen
cd tools && ./generate.sh && ./binary.sh
```

### CSV name format
- Use natural names with spaces, pipe-separated: `T CrB|Blaze Star|T Coronae Borealis`
- `AbstractAsciiProtoWriter.rKeysFromName()` converts spaces→underscores and lowercases to make resource IDs
- Primary label and object_info.json key = first name converted (e.g. `t_crb`)

## Release Splash Screens

- Tool: `tools/make_release_splash.py` — composites a portrait + gold label onto the base splash
- Source images stored in: `assets/splashscreens/<name>.png` (e.g. `venus.png`, `earth.png`)
- Base splashes (unbranded originals): `assets/splashscreens/stardroid_big_image.webp` / `stardroid_big_image_large.jpg`
- Deployed to: `app/src/main/res/drawable/stardroid_big_image.webp` (phones) and `drawable-large/stardroid_big_image.jpg` (tablets)
- Skill: `/release-splash <Label> <path/to/source.png> [crop x1,y1,x2,y2]`
- New skills must be force-added: `git add --force .claude/skills/<name>/SKILL.md` (parent `.claude/` is gitignored but skills are tracked)

| Release | Source | Crop |
|---------|--------|------|
| Venus   | `venus.png` | `808,0,2016,1200` |
| Earth   | `earth.png` | none (2048×2048 square) |

### Coordinate precision
- For extended nebulae use the **nebula centre**, not the embedded star
- Eta Carinae Nebula (NGC 3372): RA 10h 45m 08.5s / Dec −59° 52' 04" (centre)
  vs. Eta Carinae star: Dec −59° 41' — these differ by ~11 arcmin

## Auto-Level Horizon (manual mode)

Branch: `feature/auto-level-horizon` (merged/pushed 2026-03-01)

### How it works
- `HorizonLeveler.kt` — runs at 20 fps via `ScheduledExecutorService`; each frame computes signed misalignment angle between `currentPerp` and the roll-corrected zenith projection, springs 20% of the way per frame (~1–2 s return). Stops at <0.1°.
- `DragRotateZoomGestureDetector` — `onGestureEnd()` added to listener interface (default no-op); `ACTION_UP` always fires it.
- `MapMover` — accepts `SharedPreferences`; starts leveler on `onGestureEnd()` if pref on; stops it on any drag/rotate/stretch.
- `GestureInterpreter.onDown` — calls `mapMover.stopLeveling()` alongside `flinger.stop()`.
- Pref key: `ApplicationConstants.AUTO_LEVEL_HORIZON_PREF_KEY = "auto_level_horizon"` (default true).
- Setting lives in Sensor Settings category of `preference_screen.xml`.

### Key math
1. Project zenith onto view plane: `zenithProj = zenith − (zenith·los)×los`; if `|zenithProj|² < 0.001` skip (looking straight at zenith).
2. Roll correction: `rollDeg = atan2(phoneUp.x, phoneUp.y) * R2D`; rotate zenithProj by rollDeg around los → `targetPerp`.
3. Signed angle: `cross = currentPerp × targetPerp`; `angle = atan2(cross·los, currentPerp·targetPerp) * R2D`.
4. Callback calls `controllerGroup.rotate(delta)` directly (not via `mapMover.onRotate` which has sign flip).
