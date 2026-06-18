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
| Gradle | 8.13 | Build automation |
| Android Gradle Plugin | 8.13.2 | Android build |
| Kotlin | 2.0.20 | Kotlin compilation |
| Protocol Buffers | 3.13.0 | Data serialization |

## Project Structure

```
stardroid/
├── app/                    # Main Android application
│   ├── build.gradle       # App build config (Groovy DSL)
│   └── src/
│       ├── main/          # Common source
│       ├── gms/           # Google Play Services flavor
│       ├── fdroid/        # F-Droid flavor
│       ├── test/          # Unit tests
│       └── androidTest/   # Instrumented tests
├── datamodel/              # Protocol Buffer definitions
│   └── build.gradle       # Protobuf plugin config
├── tools/                  # Data generation (Java)
│   └── build.gradle       # Tools build config
├── build.gradle           # Root build config (Groovy DSL)
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

### Root build.gradle (Groovy DSL)

```groovy
buildscript {
    ext.kotlin_version = '2.0.20'
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
| Target SDK | 36 |
| Compile SDK | 35 |
| Java | 17 |

## Dependencies

### Core

| Dependency | Purpose |
|------------|---------|
| AndroidX AppCompat | Compatibility |
| Material Components | UI components |
| Hilt | Dependency injection |
| Protocol Buffers (protobuf-javalite) | Data serialization |

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
