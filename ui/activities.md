# Activities Specification

## Purpose

Defines all **Activity classes** in Stardroid, their responsibilities, lifecycle, and UI patterns.

## Activity Overview

| Activity | Purpose | Launch Mode | UI Pattern |
|----------|---------|-------------|-----------|
| `DynamicStarMapActivity` | Main star map | SingleTask | Fullscreen OpenGL |
| `SplashScreenActivity` | Onboarding | SingleTop | Animated splash + dialogs |
| `EditSettingsActivity` | Preferences | SingleTask | PreferenceScreen |
| `ImageGalleryActivity` | Image browsing | Standard | RecyclerView grid |
| `CompassCalibrationActivity` | Sensor calibration | Standard | Visual feedback |
| `DiagnosticActivity` | Debug info | Standard | Lists and metrics |

## DynamicStarMapActivity

### Purpose
Main interactive star map - shows the night sky in real-time based on device orientation.

### Key Responsibilities
- **OpenGL Rendering:** Manages GLSurfaceView with SkyRenderer
- **Sensor Input:** Listens to rotation sensor, updates camera position
- **User Input:** Handles touch gestures (drag, pinch, tap)
- **Menu System:** Options, search, time travel, layers
- **Overlays:** Compass, time travel controls, search results

### Lifecycle
```kotlin
onCreate()
  → Initialize OpenGL surface
  → Inject dependencies (Hilt)
  → Setup sensor listeners
  → Check for permissions (location)

onResume()
  → Register sensor listeners
  → Start sensor fusion
  → Enable OpenGL rendering

onPause()
  → Unregister sensors
  → Disable OpenGL rendering
  → Save current state
```

### UI Layout
- **Main:** GLSurfaceView (full screen)
- **Overlays:**
  - Compass (top center) - Shows cardinal directions
  - Time travel controls (bottom) - Slider, play/pause
  - Menu button (top right)
  - Layer toggles (side drawer or menu)
  - Search results (bottom sheet)

### User Interactions
- **Drag:** Rotate sky view manually
- **Pinch:** Zoom in/out
- **Tap:** Identify celestial object
- **Long press:** Enable manual navigation mode
- **Menu button:** Open options menu

### Permissions
- **ACCESS_FINE_LOCATION:** For compass accuracy
- **CAMERA:** (Future) For AR mode

## SplashScreenActivity

### Purpose
Initial app experience - shows splash screen, EULA (if needed), What's New (if needed).

### Key Responsibilities
- **EULA Check:** Has user accepted current version?
- **Splash Animation:** Show starfield animation
- **Dialog Management:** EULA and What's New bottom sheets
- **Transition:** Launch DynamicStarMapActivity when done

### Lifecycle
```kotlin
onCreate()
  → Set splash theme
  → Check EULA status
  → Start splash animation

onResume()
  → If EULA not accepted → Show EulaBottomSheet
  → Else → Start animation → Show What's New if needed
  → After 3 seconds → Launch DynamicStarMapActivity
```

### UI Layout
- **Main:** StarfieldView (animated stars)
- **Center:** App logo
- **Bottom:** Tagline ("Explore the Universe")

### User Interactions
- **None** - Fully automated

## EditSettingsActivity

### Purpose
User preferences and settings management.

### Key Responsibilities
- **Preference Categories:** Display, Sensor, Location, Data
- **Theme Selection:** Light/Dark (not user-changeable for astronomy, but can select accent)
- **Layer Toggles:** Enable/disable celestial object layers
- **Night Mode:** Enable red tint for night vision

### UI Pattern
- **Parent:** PreferenceActivity (deprecated) → migrate to Jetpack Preference library
- **Structure:** Categorized preferences with headers
- **Components:**
  - SwitchPreference: Boolean toggles (layers, night mode)
  - ListPreference: Multi-choice (accent color, location provider)
  - PreferenceCategory: Section headers

## ImageGalleryActivity

### Purpose
Browse astronomical images (Messier objects, planets, etc.).

### Key Responsibilities
- **Image Loading:** Load images from assets/internet
- **Grid Display:** Show thumbnails in RecyclerView
- **Full Screen:** Tap to view full image
- **Zoom:** Pinch-to-zoom on full image

### UI Pattern
- **Main:** RecyclerView with GridLayoutManager
- **Item:** CardView with ImageView + TextView
- **Detail:** Full-screen image view with zoom

## CompassCalibrationActivity

### Purpose
Help users calibrate their device's magnetometer for accurate compass.

### Key Responsibilities
- **Visual Feedback:** Show compass reading in real-time
- **Instructions:** Guide user through calibration process
- **Progress:** Show calibration quality metrics

### UI Pattern
- **Main:** Large compass visualization
- **Instructions:** TextView with step-by-step guide
- **Progress:** ProgressBar or circular indicator

## DiagnosticActivity

### Purpose
Display debug information for troubleshooting.

### Key Responsibilities
- **System Info:** Android version, device model, sensors
- **Sensor Data:** Accelerometer, magnetometer readings
- **Performance:** Frame rate, memory usage
- **Location:** GPS coordinates, accuracy

### UI Pattern
- **Main:** ScrollView with multiple sections
- **Format:** Key-value pairs (Label: Value)
- **Copy:** Long-press to copy values to clipboard

## Activity Communication

### Intents

**Launching DynamicStarMapActivity:**
```kotlin
val intent = Intent(this, DynamicStarMapActivity::class.java)
// Optional: Set specific time
intent.putExtra(DynamicStarMapActivity.EXTRA_TIME_MILLIS, timeInMillis)
startActivity(intent)
```

**Returning from activities:**
```kotlin
// From EditSettingsActivity
setResult(RESULT_OK)
finish()
```

## Common Patterns

### Permission Handling

```kotlin
private fun checkLocationPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                      LOCATION_PERMISSION_REQUEST_CODE)
    } else {
        // Permission granted
    }
}
```

### Night Mode Management

```kotlin
// All activities support night mode (red tint)
@Inject lateinit var nightModeManager: ActivityLightLevelManager

override fun onResume() {
    super.onResume()
    nightModeManager.apply(this)
}
```

## Related Specifications

- [dialogs.md](dialogs.md) - Dialog fragments used by activities
- [material-3.md](material-3.md) - Theme system
- [../features/star-map.md](../features/star-map.md) - Star map feature details
