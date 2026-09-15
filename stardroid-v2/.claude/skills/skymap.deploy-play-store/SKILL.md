---
name: skymap.deploy-play-store
description: Deploy Sky Map v2 (stardroid-v2) to the Google Play Store using fastlane. Trigger on "deploy v2 to Play Store", "release v2 to internal", "promote v2 to beta/production", "upload v2 metadata", or similar v2 Play Store release requests.
---

# Sky Map v2 Play Store Deployment

Manages the release pipeline for the v2 rewrite: build → internal → alpha → beta → production.
v2 ships as an **update to the existing Play Store listing** (same `applicationId`,
`com.google.android.stardroid`, as v1), so this reuses the same Play
Console app entry as v1's `skymap.deploy-play-store` skill, but the Fastfile, Gradle file, and
build outputs are v2's own.

## Prerequisites

- `fastlane/play-store-credentials.json` — service account key (never commit changes to this file)
- `fastlane` installed: `bundle exec fastlane --version` or `fastlane --version`
- `app/no-checkin.properties` — required for release signing
- Environment: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

Run fastlane commands from `stardroid-v2/` with `bundle exec fastlane android
<lane>` (or plain `fastlane android <lane>` if not using Bundler).

**Note**: v2's Fastfile (`fastlane/Fastfile`) targets the Kotlin DSL build file
(`app/build.gradle.kts`, referenced internally as `GRADLE_FILE`) via
`fastlane-plugin-versioning_android` — not the legacy Groovy `build.gradle` v1 uses. Don't try
to point fastlane at a Groovy file path in this module.

## Release Pipeline

Same standard path as v1: **internal → alpha → beta → production**. Always start at internal
and promote rather than uploading directly to later tracks.

```
Internal → Alpha (Closed Testing) → Beta (Open Testing, 10%) → Production (10% rollout)
```

### Step 0 — Pre-release checklist

Before deploying, confirm these are done:

1. **Changelog** written (`skymap.whatsnew` skill) — v2 keeps What's New content in
   `app/src/main/res/values/whatsnew.xml` (`whats_new_content` string), not a separate
   `whatsnew_content.xml` file like v1. Also update
   `fastlane/metadata/android/en-US/changelogs/default.txt`.
2. **Version name** bumped if needed (see Version Management below)
3. `./gradlew check` passes (unit tests, ktlint, Konsist gate)
4. All changes committed and on `master`

v1's splash-screen sync step has **no v2 equivalent yet** — v2 uses the AndroidX SplashScreen API
(no per-release branded image). Don't invent that step for v2 releases.

v2 *does* now mirror v1's Buy Me a Coffee supporters / GitHub contributors sync, though: the name
lists live in `app/src/main/res/values/contributors.xml` (`sponsors_text`, `contributors_text`,
both `translatable="false"`) and are spliced into `credits_text`
(`app/src/main/res/values/credits.xml`) via `stringResource` format args in `HelpScreen.kt`. If
v1's sync step for those lists runs as part of this release, update `contributors.xml` too — check
whether `beta_user_help_text` (`app/src/main/res/values/whatsnew.xml`) also needs updating or
clearing for this release, same as v1's `beta_user_help_text`.

### Step 1 — Deploy to Internal Testing

This syncs the local version code against the Play Console (see Version Management below),
increments it, builds the release AAB, and uploads to the internal track.

```bash
bundle exec fastlane android internal
```

The build output is `app/build/outputs/bundle/gmsRelease/app-gms-release.aab`.
Metadata and screenshots are **not** uploaded by this lane — use `upload_metadata` separately.

### Step 2 — Upload metadata (optional but recommended)

Upload store listing text, changelogs, and/or images independently of a binary upload.

```bash
# Store text only (descriptions, title)
bundle exec fastlane android upload_metadata

# Include changelogs (whatsnew)
bundle exec fastlane android upload_metadata whatsnew:true

# Include screenshots/images
bundle exec fastlane android upload_metadata imgs:true

# Everything
bundle exec fastlane android upload_metadata all:true

# Skip store text, changelogs only
bundle exec fastlane android upload_metadata store:false whatsnew:true
```

To attach changelogs to a specific already-uploaded version code:
```bash
bundle exec fastlane android upload_metadata whatsnew:true version_code:1234
```

### Step 3 — Promote to Alpha (Closed Testing)

```bash
bundle exec fastlane android promote_to_alpha
```

### Step 4 — Promote to Beta (Open Testing, 10% rollout)

```bash
bundle exec fastlane android promote_to_beta
```

### Step 5 — Promote to Production (10% rollout)

```bash
bundle exec fastlane android promote_to_production
```

Production starts at 10%. Increase the rollout percentage manually in the Play Console as
confidence grows.

## Version Management

```bash
# Bump version name (e.g., for a named release). No spaces in name.
bundle exec fastlane android bump_version name:"2.0.0:Earth"

# Increment version code only (done automatically by the `internal` lane)
bundle exec fastlane android increment_version_code

# Sync the local version code to the Play Console's highest used value across all
# tracks (done automatically by `internal`/`alpha`/`beta_yes_im_sure`/`production_yes_im_sure`
# before they increment — see below)
bundle exec fastlane android sync_version_code_from_play_store
```

Version name and code live in `app/build.gradle.kts` (`versionCode`/`versionName` inside
`defaultConfig`). Commit the bump before deploying.

**Version code can drift from the Play Console**: the local `versionCode` in
`app/build.gradle.kts` only reflects what *this checkout* has done — another checkout, a manual
upload, or an interrupted prior release can leave the Play Console ahead of it. Uploading with a
code the Console has already seen fails with "Version code N has already been used." The
`sync_version_code_from_play_store` lane guards against this: it queries
`google_play_track_version_codes` across internal/alpha/beta/production and bumps the local file
up to match the highest one found *before* `increment_version_code` runs, so the upload always
lands one past the true high-water mark. It's a no-op (and safe to run standalone) when the local
file is already ahead of or equal to the Play Console.

**v1-retained-version-codes caveat**: v1's Fastfile hardcoded a `version_codes_to_retain` array
for old-device APK retention (`[1115, 1202]`). Those codes are v1-specific and must **not** be
copied into v2's `internal` lane — see the `TODO(v2-versioning)` comment in v2's Fastfile. If
this comes up, ask the user for the correct v2 retention set rather than assuming.

## Screenshots

```bash
bundle exec fastlane android screenshots
```

Add `--device_type=sevenInch` for tablet screenshots. Results land in
`fastlane/metadata/android/en-US/images/`.

## Direct-track Lanes (use sparingly)

These upload directly without going through internal first. Prefer the promote lanes above.

```bash
bundle exec fastlane android alpha                    # Direct to alpha
bundle exec fastlane android beta_yes_im_sure         # Direct to beta
bundle exec fastlane android production_yes_im_sure   # Direct to production
```

## Troubleshooting

- **Authentication error**: check `fastlane/play-store-credentials.json` is present and the
  service account has the correct Play Console permissions.
- **Build fails**: ensure `app/no-checkin.properties` exists and `JAVA_HOME` points to Java 17.
- **Version code conflict**: the `internal`/`alpha`/`beta_yes_im_sure`/`production_yes_im_sure`
  lanes call `sync_version_code_from_play_store` before incrementing, so this should self-heal on
  the next run. If it still happens (e.g. a code was used on a track outside the four checked, or
  two releases raced), run `bundle exec fastlane android sync_version_code_from_play_store`
  manually, confirm the local `versionCode` in `app/build.gradle.kts` is now at or above the
  Console's highest, then retry.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` on device**: uninstall the existing app first with
  `adb uninstall com.google.android.stardroid`.

## Key Files

| File | Purpose |
|------|---------|
| `fastlane/Fastfile` | Lane definitions |
| `fastlane/Appfile` | Package name + credentials path |
| `fastlane/play-store-credentials.json` | Service account key (do not modify) |
| `fastlane/metadata/android/en-US/changelogs/default.txt` | Play Store changelog (≤350 chars) |
| `fastlane/metadata/android/` | Store listing text and images per locale |
| `app/build.gradle.kts` | `versionCode` and `versionName` |
