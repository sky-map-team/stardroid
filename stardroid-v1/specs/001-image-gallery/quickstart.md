# Quickstart: Gallery Rewrite

## Build & run

```bash
# Build debug (requires JAVA_HOME set to JDK 17)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleGmsDebug

# Install on connected device / emulator
./gradlew installGmsDebug

# Build fdroid flavor (verify flavor purity — no Google deps)
./gradlew assembleFdroidDebug
```

## Navigate to the gallery

1. Launch Sky Map
2. Tap the **⋮** overflow menu → **Image Gallery**
3. Verify a 3-column grid of thumbnail images with titles appears
4. Scroll to confirm smooth loading (no jank, no placeholder flicker)

## Test a thumbnail tap

1. Tap any thumbnail
2. Confirm the info card dialog appears (image, name, description, data fields, fun fact)
3. Tap outside the dialog → dialog dismisses
4. Tap a thumbnail again → confirm "Find" button is visible (not "OK")
5. Tap "Find" → star map opens and navigates to the object

## Run unit tests

```bash
./gradlew :app:testGmsDebugUnitTest --tests "*.GalleryItemsTest"
```

Expected: all tests pass, verifying that gallery items are populated from `ObjectInfoRegistry`
and that only objects with images are included.

## Run all tests

```bash
./gradlew :app:testGmsDebugUnitTest
./gradlew :app:connectedGmsDebugAndroidTest   # requires connected device/emulator
```

## Verify old code is gone

```bash
# These should return no results:
grep -r "ImageDisplayActivity" app/src/main/
grep -r "HardcodedGallery" app/src/main/
grep -r "GalleryFactory" app/src/main/
grep -r "android.widget.Gallery" app/src/main/
```

## Verify flavor purity

```bash
# Must succeed with no errors:
./gradlew assembleFdroidDebug
# Confirm no com.google.* imports in main/ source sets:
grep -r "import com.google" app/src/main/java/ | grep -v "stardroid"
```

## Check image count

From `adb logcat` or by adding a temporary log in `ImageGalleryActivity.onCreate`:
- Expect 214 items by default (or ~61 if curated to DSO + planets + moon/dwarf_planet)
- Verify no crash with all items loaded

## Night mode

1. Enable night mode in Sky Map settings
2. Open gallery → images should have a red-tinted overlay (existing NightModeHelper behavior)
3. Info card text should be in night-mode red colors
