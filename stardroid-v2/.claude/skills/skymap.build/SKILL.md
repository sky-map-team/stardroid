---
name: skymap.build
description: Build, test, deploy, and manage catalog-data generation for Sky Map v2 (stardroid-v2). Trigger on "build the v2 app", "run v2 tests", "deploy v2 to device", "regenerate catalog db", "run ktlint", or similar v2 build/dev workflow requests.
---

# Sky Map v2 Build & Dev Workflow

Covers the Kotlin/Gradle v2 rewrite in `stardroid-v2/`. This is a different
module tree from the legacy `stardroid-v1/` app — see the root `AGENTS.md` for how the two
relate.

## Environment Setup

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=~/Library/Android/sdk
```

`local.properties` must exist in `stardroid-v2/` with `sdk.dir=<path to Android SDK>`.
If working in a git worktree, copy `local.properties` from the main checkout — new worktrees
don't have it and Gradle will fail to find the SDK.

## Build Flavors

- **gms** — Google Play Services (Analytics, Location). Requires `app/no-checkin.properties`
  for release builds.
- **fdroid** — Pure open source, no Google dependencies.

Always specify the flavor explicitly — never use bare `assembleDebug`/`installDebug` (this
matches the flavor rule in `AGENTS.md`).

## Build Commands

Run from `stardroid-v2/`:

```bash
# Debug APK
./gradlew :app:assembleGmsDebug
./gradlew :app:assembleFdroidDebug

# Install directly on a connected device/emulator
./gradlew :app:installGmsDebug
./gradlew :app:installFdroidDebug

# Release bundle (gms only — no-checkin.properties required)
./gradlew :app:bundleGmsRelease
```

There is no `build.sh`/`deploy.sh` equivalent in v2 (yet) — use `gradlew` directly, or the
fastlane lanes described in the `skymap.deploy-play-store` skill.

## Testing & Linting

```bash
./gradlew check
```

Runs unit tests (JUnit 5 + Truth), `ktlintCheck`, and the Konsist architecture gate (D20,
enforces the pure/Android module boundary). **This must pass before any commit** — see AGENTS.md.

```bash
# App module unit tests only
./gradlew :app:test

# Instrumented tests (requires connected device/emulator) — includes the D19 renderer perf
# smoke gate; pure modules (core/math, core/astronomy) don't need Android at all.
./gradlew connectedDebugAndroidTest

# Auto-fix ktlint violations (100-char line limit, Google Kotlin style)
./gradlew ktlintFormat
```

Pure modules (`core/math`, `core/astronomy`) must stay testable without the Android SDK on the
classpath — don't add Android dependencies to them.

## Catalog Data Generation

Unlike v1's `tools/generate.sh` + `tools/binary.sh`, there is **no separate data-generation
step to run manually**. Editing `source-data/*.csv`/`*.json` is picked up automatically: the
`:data:generateCatalogDb` Gradle task (in `data/build.gradle.kts`) is wired into AGP as a
generated-asset source directory, so any build/test task that needs `skymap.db` regenerates it
from `source-data/` on the fly. `skymap.db` itself is a build artifact — never checked in.

If you want to sanity-check catalog generation in isolation:

```bash
./gradlew :data:generateCatalogDb
```

Data-generation source order: `tools/harvest-v1/harvest.py` did the one-time port from v1;
`source-data/` is now hand-edited directly (see `source-data/README.md` and
`docs/design/catalog-and-schema.md`). See the `skymap.add-object` skill for the day-to-day
workflow of adding a new catalog object.

## Deployment to a Device/Emulator

```bash
adb devices                              # confirm target
./gradlew :app:installGmsDebug           # build + install
adb shell am start -n com.google.android.stardroid/.ui.MainActivity
```

To uninstall: `adb uninstall com.google.android.stardroid`. Note v1 and v2 currently ship the
same `applicationId` (v2 is designed to update the existing Play Store listing),
so installing one over the other via `adb install -r` works but installing v2 debug alongside
a v1 release build from Play Store can hit a signature mismatch — uninstall first if so.

### Common Deployment Issues

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: uninstall the existing app first —
  `adb uninstall com.google.android.stardroid`.
- Device shows offline: `adb kill-server && adb start-server`.
- Gradle can't find the SDK in a worktree: copy `local.properties` from the main checkout.
- ktlint failures: run `./gradlew ktlintFormat`, then re-check. Inline trailing comments inside
  argument/parameter lists aren't allowed — put the comment on its own line above instead.
