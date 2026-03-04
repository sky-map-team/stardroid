---
name: Sky Map Build Assistant
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

## Build Flavors

- **gms** — Google Play Services (Analytics, Location). Requires `no-checkin.properties` for release builds.
- **fdroid** — Pure open source, no Google dependencies.

Always specify the flavor. Never use bare `assembleDebug`.

## Build Commands

```bash
# Debug APK
./gradlew :app:assembleGmsDebug
./gradlew :app:assembleFdroidDebug

# Full rebuild including data generation
./build_skymap.sh             # GMS
./build_skymap.sh --fdroid    # F-Droid
./build_skymap.sh --quick     # Skip data regeneration

# Release bundle
./gradlew :app:bundleGmsRelease
```

Note: `build_skymap.sh` patches Gradle's generated classpath — prefer it over raw `gradlew` for full builds.

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

## Deployment

```bash
./deploy.sh       # Deploy to connected device or emulator
./deploy.sh -p    # Deploy to a physical device
./deploy.sh -d    # Deploy a debug build
./undeploy.sh     # Uninstall the app
```

### Common Deployment Issues

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: uninstall the Play Store version first — `adb uninstall com.google.android.stardroid`
- Device shows offline: `adb kill-server && adb start-server`
