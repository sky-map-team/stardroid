# Build System Overview

This section documents Sky Map's build system and configuration.

## Contents

| Specification | Description |
|--------------|-------------|
| [Flavors](flavors.md) | GMS and F-Droid build variants |
| [Data Generation](data-generation.md) | Catalog to binary conversion |

## Build Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Gradle | 8.x | Build automation |
| Android Gradle Plugin | 8.x | Android build |
| Kotlin | 1.9.x | Kotlin compilation |
| FlatBuffers | 24.x | FlatBuffers code generation |

## Project Structure

```
stardroid/
├── app/                    # Main Android application
│   ├── build.gradle       # App build config
│   └── src/
│       ├── main/          # Common source
│       ├── gms/           # Google Play Services flavor
│       ├── fdroid/        # F-Droid flavor
│       ├── test/          # Unit tests
│       └── androidTest/   # Instrumented tests
├── datamodel/              # FlatBuffers data module
│   └── build.gradle       # FlatBuffers build config
├── tools/                  # Data generation
│   ├── build.gradle       # Tools build config
│   ├── generate.sh        # ASCII generation script
│   └── binary.sh          # Binary conversion script
├── build.gradle           # Root build config
├── settings.gradle        # Module settings
└── gradle.properties      # Build properties
```

## Quick Commands

### Debug Builds

```bash
# GMS debug (Google Play Services)
./gradlew assembleGmsDebug

# F-Droid debug (no Google services)
./gradlew assembleFdroidDebug
```

### Release Builds

```bash
# GMS release (requires signing config)
./gradlew assembleGmsRelease

# F-Droid release
./gradlew assembleFdroidRelease
```

### Testing

```bash
# Unit tests
./gradlew test

# Specific module
./gradlew app:test

# Instrumented tests (requires device)
./gradlew connectedAndroidTest
```

### Data Generation

```bash
# Full rebuild with data
./build_skymap.sh

# F-Droid variant
./build_skymap.sh --fdroid

# Quick build (skip data generation)
./build_skymap.sh --quick
```

### Clean Build

```bash
./gradlew clean
./gradlew assembleGmsDebug
```

## Build Configuration

### Root build.gradle

```groovy
plugins {
    id 'com.android.application' version '8.x' apply false
    id 'com.android.library' version '8.x' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.x' apply false
    }
```

### gradle.properties

```properties
# Gradle settings
org.gradle.jvmargs=-Xmx2048m
org.gradle.parallel=true
org.gradle.caching=true

# Android settings
android.useAndroidX=true
android.enableJetifier=true

# Kotlin settings
kotlin.code.style=official
```

## SDK Requirements

| Requirement | Version |
|-------------|---------|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |
| Compile SDK | 35 |
| Java | 17 |

## Dependencies

### Core

| Dependency | Purpose |
|------------|---------|
| AndroidX AppCompat | Compatibility |
| Material Components | UI components |
| Dagger | Dependency injection |
| FlatBuffers | Data serialization (zero-copy) |

### Testing

| Dependency | Purpose |
|------------|---------|
| JUnit 4 | Unit testing |
| Robolectric | Android unit tests |
| Mockito | Mocking |
| Truth | Assertions |
| Espresso | UI testing |

### GMS-Only

| Dependency | Purpose |
|------------|---------|
| Firebase Analytics | Usage tracking |
| Play Services Location | Location services |

## Output Locations

| Output | Path |
|--------|------|
| Debug APK | `app/build/outputs/apk/gms/debug/` |
| Release APK | `app/build/outputs/apk/gms/release/` |
| Unit test results | `app/build/reports/tests/` |
| Lint results | `app/build/reports/lint-results.html` |
