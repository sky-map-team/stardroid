# Gemini Code Assistant Information

This document provides context for the Gemini code assistant to understand the Sky Map project.

## Project Overview

Sky Map is an open-source astronomy app for Android. It was originally created by Google as Google Sky Map and was open-sourced in 2011. The project is now maintained by a community of developers. The app's original internal name was "Stardroid", which is still present in the codebase and package names.

The app's main function is to provide a dynamic star chart that identifies celestial objects. It uses the device's sensors to determine location and orientation to display the sky in the correct direction.

## Tech Stack

*   **Language:** The project is primarily written in Kotlin, with some older parts in Java.
*   **Build System:** Gradle is used for building the project.
*   **Platform:** Android.
*   **Key Libraries:** 
    *   Dagger/Hilt for dependency injection.
    *   [Please fill in other key libraries, e.g., for astronomy calculations, UI, etc.]

## Project Structure

The project is a standard Android application structure. Here are some of the key directories:

*   `app/`: The main application module.
    *   `app/src/main/`: Contains the source code for the application.
    *   `app/src/main/java/com/google/android/stardroid/`: The root package for the application code.
*   `datamodel/`: A separate module for data models.
*   `tools/`: Contains tools for generating data used by the app.
*   `assets/`: Contains images, and other assets.
*   `docs/`: Contains project documentation, including architecture information.

## How to Build the Project

### Prerequisites

1.  Android SDK: You need the Android SDK installed.
2.  `local.properties`: Create a `local.properties` file in the root directory with the following content:
    ```
    sdk.dir=<path to your Android SDK>
    ```

### Building

*   **Full build (including data generation):**
    ```bash
    ./build_skymap.sh
    ```
*   **Build a debug APK:**
    ```bash
    ./gradlew :app:assembleGmsDebug
    ```
    The output APK will be in `app/build/outputs/apk/`.
*   **Build a release bundle:**
    ```bash
    ./gradlew :app:bundleGmsRelease
    ```
    The output bundle will be in `app/build/outputs/bundle/`.

## How to Run Tests

*   **Unit tests:**
    ```bash
    ./gradlew :app:test
    ```
*   **Instrumented (connected) tests:** These require a connected Android device or emulator.
    ```bash
    ./gradlew :app:connectedAndroidTest
    ```

## Linting and Formatting

The project follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

*   Line length is 100 characters.
*   Member variables are **not** prefixed with 'm'.

To run the linter:

```bash
./gradlew lint
```

[Please specify any auto-formatting tools or configurations if they exist, e.g., ktlint.]

## Important Commands

*   `./deploy.sh`: Deploys the app to a connected phone or emulator. Use the `-d` flag for a debug build.
*   `./undeploy.sh`: Uninstalls the app.

## Architectural Patterns

The codebase is old and is undergoing modernization. The architecture is a mix of patterns. See `docs/ARCHITECTURE.md` for more details.

[If you can provide a brief summary of the main architectural pattern (e.g., MVP, MVVM), please add it here.]
