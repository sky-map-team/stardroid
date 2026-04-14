---
name: deploy-play-store
description: Deploy Sky Map to the Google Play Store using fastlane. Trigger on "deploy to Play Store", "release to internal", "promote to beta/production", "upload metadata", or similar Play Store release requests.
---

# Sky Map Play Store Deployment

Manages the full release pipeline: build → internal → alpha → beta → production.

## Prerequisites

- `fastlane/play-store-credentials.json` — service account key (never commit changes to this file)
- `fastlane` installed: `bundle exec fastlane --version` or `fastlane --version`
- `no-checkin.properties` in `app/` (required for release signing)
- Environment: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`

Run fastlane commands from the repo root with `bundle exec fastlane android <lane>` (or plain
`fastlane android <lane>` if not using Bundler).

## Release Pipeline

The standard path is: **internal → alpha → beta → production**. Always start at internal and
promote rather than uploading directly to later tracks.

```
Internal → Alpha (Closed Testing) → Beta (Open Testing, 10%) → Production (10% rollout)
```

### Step 0 — Pre-release checklist

Before deploying, confirm these are done:

1. **Splash screen** updated if this is a named planetary release (`/release-splash`)
2. **Sponsors** synced (`/skymap-sponsors`)
3. **Contributors** synced — ask the user: "Would you like to refresh the contributors list
   before deploying?" If yes, invoke the `/skymap-contributors` skill now before continuing.
4. **Changelog** written (`/whats-new <last-tag>`) — both `whatsnew_content.xml` and
   `fastlane/metadata/android/en-US/changelogs/default.txt`
5. **Version name** bumped if needed (see Version Management below)
6. All changes committed and on `master`

### Step 1 — Deploy to Internal Testing

This increments the version code, builds the release AAB, and uploads to the internal track.

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

After verifying the internal build is stable:

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
bundle exec fastlane android bump_version name:"1.5.0:Earth"

# Increment version code only (done automatically by the `internal` lane)
bundle exec fastlane android increment_version_code
```

Version name and code live in `app/build.gradle`. Commit the bump before deploying.

## Screenshots

Capture Play Store screenshots using a connected device or emulator:

```bash
bundle exec fastlane android screenshots
```

Add `--device_type=sevenInch` for tablet screenshots. Results land in
`fastlane/metadata/android/en-US/images/`.

## Direct-track Lanes (use sparingly)

These upload directly without going through internal first. Prefer the promote lanes above.

```bash
bundle exec fastlane android alpha              # Direct to alpha
bundle exec fastlane android beta_yes_im_sure  # Direct to beta
bundle exec fastlane android production_yes_im_sure  # Direct to production
```

## Troubleshooting

- **Authentication error**: check `fastlane/play-store-credentials.json` is present and the
  service account has the correct Play Console permissions.
- **Build fails**: ensure `no-checkin.properties` exists in `app/` and `JAVA_HOME` points to
  Java 17.
- **Version code conflict**: the `internal` lane auto-increments; if a manual upload already used
  the next code, increment again.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE` on device**: uninstall the Play Store version first with
  `adb uninstall com.google.android.stardroid`.

## Key Files

| File | Purpose |
|------|---------|
| `fastlane/Fastfile` | Lane definitions |
| `fastlane/Appfile` | Package name + credentials path |
| `fastlane/play-store-credentials.json` | Service account key (do not modify) |
| `fastlane/metadata/android/en-US/changelogs/default.txt` | Play Store changelog (≤350 chars) |
| `fastlane/metadata/android/` | Store listing text and images per locale |
| `app/build.gradle` | `versionCode` and `versionName` |
