# Quickstart: Modern Location Handling

## Build & run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# GMS flavor (includes FusedLocation + Google Maps)
./gradlew assembleGmsDebug

# fdroid flavor (platform LocationManager, text-only location display)
./gradlew assembleFdroidDebug

# Install on connected device / emulator
./gradlew installGmsDebug
```

## Test the new-user permission flow

1. Fresh install (or clear app data in device Settings)
2. Launch Sky Map
3. **Expected**: A rationale dialog appears explaining why location is needed
4. Tap **Grant** → system permission dialog appears
5. Grant permission → toast appears confirming the detected location; star map shows your sky
6. Verify the sky is NOT centred on the North Pole (which would indicate location not received)

## Test the permission-denied path

1. Fresh install / clear app data
2. Launch Sky Map → tap **Deny** (or **Enter manually**) on the rationale dialog
3. **Expected**: A manual location entry dialog appears immediately
4. Enter a known city name (online) → confirm → toast shows resolved coordinates; star map updates
5. Reset; enter lat/lon directly (e.g., 51.5 / -0.1 for London) → verify offline-capable

## Test the permanently-denied path

1. Fresh install / clear app data
2. Deny location permission twice (or use "Don't ask again" on Android 11+)
3. Navigate to **Location Management** → tap **Use automatic location**
4. **Expected**: A dialog appears explaining permission is permanently denied, with an
   **Open Settings** button
5. Tap **Open Settings** → device App Settings page opens; grant permission manually
6. Return to Sky Map → verify automatic location is now active

## Test the acquiring timeout

1. Enable airplane mode (disables network location)
2. Go indoors away from GPS signal
3. Launch Sky Map with auto-location enabled
4. **Expected**: Sky is shown at the North Pole (if no prior location) with an "acquiring…" banner
5. After 30 seconds: a dialog asks "Keep waiting" or "Enter manually"
6. Tap **Enter manually** → manual entry dialog appears

## Test live location updates

1. With auto-location active, use developer options → **Mock location app** to simulate a
   position change > 2 km from current location
2. **Expected**: Star map re-renders for the new sky; a toast shows the updated location
3. Simulate a change < 2 km — **Expected**: no toast, no map recalculation

## Test the Location Management screen

1. With a valid location set (auto or manual), tap overflow menu → **Location**
2. **GMS**: Verify a map is shown with a pin at the current coordinates and a "Auto" or "Manual"
   source label
3. **fdroid**: Verify text coordinates (lat/lon) and source label are shown; no map tiles
4. Tap **Switch to manual** (if in auto mode) → manual entry dialog appears; coordinates
   pre-filled from last known location

## Run unit tests

```bash
# All new location unit tests
./gradlew :app:testGmsDebugUnitTest \
  --tests "*.LocationControllerTest" \
  --tests "*.LocationStateTest" \
  --tests "*.ManualLocationValidationTest"

# Full unit test suite
./gradlew :app:testGmsDebugUnitTest
./gradlew :app:testFdroidDebugUnitTest
```

## Run instrumented tests

```bash
# Requires connected device / emulator
./gradlew :app:connectedGmsDebugAndroidTest
```

## Verify flavor purity

```bash
# Neither build should have com.google.android.gms imports in main/ source
./gradlew assembleFdroidDebug  # must succeed cleanly
grep -r "com.google.android.gms" app/src/main/java/ && echo "FAIL: GMS import in main/" || echo "OK"
```
