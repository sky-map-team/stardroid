---
name: skymap.build
description: Build, test, deploy, and manage data generation for Sky Map. Trigger on "build the app", "run tests", "deploy to device", "generate data", "run lint", or similar build/dev workflow requests.
---

# Sky Map Build & Dev Workflow

## Environment Setup

Always set before building:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME=~/Library/Android/sdk
```

`local.properties` must exist in the repo root with `sdk.dir=<path to Android SDK>`.
You will also need a `no-checkin.properties` in the `app` folder. If using a new
git worktree you should attempt to copy these files out of the current main folder.

## Build Flavors

- **gms** — Google Play Services (Analytics, Location). Requires `no-checkin.properties` for release builds.
- **fdroid** — Pure open source, no Google dependencies.

Always specify the flavor. Never use bare `assembleDebug`.

## Build Commands
Prefer to use the shell scripts:
```bash
# Full rebuild including data generation
./build.sh             # Skip data regeneration
./build.sh -d          # Build the debug version
./build.sh --full      # GMS including data generation
./build.sh --fdroid    # F-Droid
```
but you can also use gradlew directly:
```bash
# Debug APK
./gradlew :app:assembleGmsDebug
./gradlew :app:assembleFdroidDebug

# Release bundle
./gradlew :app:bundleGmsRelease
```
Prefer not using the -fullk option unless you are adding/removing strings.

## Testing

```bash
# All unit tests
./gradlew test

# App module unit tests only
./gradlew app:test

# Instrumented tests (requires connected device/emulator)
./gradlew app:connectedAndroidTest
```

## Linting

```bash
./gradlew lint
```

## Data Generation

Run when modifying star catalogs or astronomical data:

```bash
cd tools
./generate.sh   # Creates ASCII protocol buffers
./binary.sh     # Converts to binary in app/src/main/assets/
```

## Deployment to a phone

```bash
./deploy.sh       # Deploy to connected device or emulator
./deploy.sh -p    # Deploy to a physical device
./deploy.sh -d    # Deploy a debug build
./deploy.sh --frdroid # Deploy the fdroid build
./undeploy.sh     # Uninstall the app
```

### Common Deployment Issues

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: uninstall the Play Store version first — `adb uninstall com.google.android.stardroid`
- Device shows offline: `adb kill-server && adb start-server`
