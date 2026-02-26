# Activities Specification

## Purpose

Defines all **Activity classes** in Stardroid, their responsibilities, lifecycle, and UI patterns.

## Activity Overview

| Activity | Purpose | Launch Mode | Theme |
|----------|---------|-------------|-------|
| `DynamicStarMapActivity` | Main star map | SingleTask | `FullscreenTheme` (overlay action bar) |
| `SplashScreenActivity` | Onboarding | SingleTop | `AppTheme` |
| `EditSettingsActivity` | Preferences | Standard | `AppTheme` |
| `ImageGalleryActivity` | Image browsing | Standard | `AppTheme` |
| `CompassCalibrationActivity` | Sensor calibration | Standard | `AppTheme` |
| `DiagnosticActivity` | Debug info | Standard | `AppTheme` |

## DynamicStarMapActivity

### Purpose
Main interactive star map — shows the night sky in real-time based on device orientation.

### Key Responsibilities
- **OpenGL Rendering:** Manages `GLSurfaceView` with `SkyRenderer`
- **Sensor Input:** Uses `ControllerGroup` to handle rotation sensor / manual drag
- **User Input:** Touch gestures (drag, pinch, tap) via `GestureInterpreter`
- **Menu System:** Options, search, time travel, layers, credits, help, ToS, calibration, diagnostics
- **Overlays:** Layer icon sidebar, manual/auto toggle, time travel player bar, search bar

### Injection

Uses Dagger 2 via `DaggerDynamicStarMapComponent`:

```java
daggerComponent = DaggerDynamicStarMapComponent.builder()
    .applicationComponent(getApplicationComponent())
    .dynamicStarMapModule(new DynamicStarMapModule(this)).build();
daggerComponent.inject(this);
```

### Night Mode

Implements `ActivityLightLevelChanger.NightModeable`. `setNightMode(boolean)` is called by `ActivityLightLevelManager` when the preference changes or on resume.

```java
@Override
public void setNightMode(boolean mode) {
    nightMode = mode;
    rendererController.queueNightVisionMode(mode);  // OpenGL renderer
    applyNightModeToUi();                            // Action bar, sidebar, icons, time bar
}
```

`applyNightModeToUi()` updates:
1. **Action bar** — `getActionBar().setBackgroundDrawable(new ColorDrawable(...))`
2. **Layer sidebar** — `getBackground().mutate().setColorFilter(0xFF660000 or 0xFFFFFFFF, MULTIPLY)`
3. **Layer icon buttons** — `setColorFilter(NIGHT_TEXT_COLOR, SRC_ATOP)` / `clearColorFilter()`
4. **Manual/auto toggle button** — same color filter approach
5. **Time-player bar** — background colour + text colour on labels

The `nightMode` field is persisted via `onSaveInstanceState` / `onRestoreInstanceState`.

### Dialogs hosted

All dialogs are `DialogFragment`s injected via Dagger and shown with `fragment.show(fragmentManager, tag)`:
- `EulaDialogFragment` (ToS, no buttons variant)
- `CreditsDialogFragment`
- `HelpDialogFragment`
- `TimeTravelDialogFragment` (wraps `TimeTravelDialog extends Dialog`)
- `NoSearchResultsDialogFragment`
- `MultipleSearchResultsDialogFragment`
- `NoSensorsDialogFragment`
- `ObjectInfoDialogFragment`

---

## SplashScreenActivity

### Purpose
Initial app experience — shows splash, checks EULA, shows What's New, then launches `DynamicStarMapActivity`.

### Key Responsibilities
- **EULA Check:** Has user accepted current version?
- **Splash delay** before launching star map
- **Dialog Management:** `EulaDialogFragment`, `WhatsNewDialogFragment`

### UI Layout
- `R.layout.splash` — static image layout, no animated starfield in current code
- No `StarfieldView` class exists in the codebase

---

## EditSettingsActivity

### Purpose
User preferences — extends `PreferenceActivity` (deprecated but still in use).

### Key Responsibilities
- **Location:** Manual lat/long entry; geocoding from place name
- **Layer Toggles:** Enable/disable celestial object layers (via preferences)
- **Sensor options:** Gyro enable/disable, sensor speed/damping

### Night Mode

Implements `NightModeable`. Only the action bar background is changed (preference items cannot be re-coloured without major refactoring):

```java
@Override
public void setNightMode(boolean nightMode) {
    this.nightMode = nightMode;
    applyNightMode();  // sets action bar background
}
```

---

## ImageGalleryActivity

### Purpose
Browse astronomical images; tap to view full-screen.

### Key Responsibilities
- Loads images from assets asynchronously via `AssetImageLoader`
- Displays thumbnails in a `Gallery` widget with `ImageAdapter`
- Starts `ImageDisplayActivity` with the selected position

### Night Mode

Same as `EditSettingsActivity` — action bar background only.

---

## CompassCalibrationActivity

### Purpose
Help users calibrate their device's magnetometer.

### UI Pattern
- **WebView** (`R.id.compass_calib_activity_webview`) — shows animated calibration GIF
- **CheckBox** (`R.id.compass_calib_activity_donotshow`) — "Don't show again"
- **TextViews** — heading, explanation text (with HTML link), sensor accuracy reading

### Night Mode

Implements `NightModeable`:
- Toggles `night-mode` CSS class on WebView `<body>` via `evaluateJavascript`
- Sets `textColor` on all `TextView` IDs to `NIGHT_TEXT_COLOR` / `Color.WHITE`
- Sets link text colour on the explanation TextView
- Uses `sensorAccuracyDecoder.getNightColorForAccuracy()` for the accuracy reading

Pattern:
```java
@Override
public void setNightMode(boolean nightMode) {
    this.nightMode = nightMode;
    applyNightMode();
}
```

---

## DiagnosticActivity

### Purpose
Display debug information for troubleshooting — sensor readings, GPS, network, model state.

### Key Responsibilities
- Registers for all available sensors and updates TextViews at 500ms
- Shows location, model pointing, UTC/local time, network status
- Colors sensor value TextViews by accuracy using `SensorAccuracyDecoder`

### Night Mode

Implements `NightModeable`:
- Action bar background tinted red
- Sensor accuracy colors use `sensorAccuracyDecoder.getNightColorForAccuracy()` when night mode is on

---

## Common Patterns

### Dagger 2 Injection

All activities (except `SplashScreenActivity`) use Dagger 2 components:

```java
// In onCreate():
DaggerXyzActivityComponent.builder()
    .applicationComponent(getApplicationComponent())
    .xyzActivityModule(new XyzActivityModule(this))
    .build().inject(this);
```

Each module provides:
- `Activity`, `Context`, `Handler`, `Window`
- `ActivityLightLevelChanger.NightModeable` — return `null` to disable custom night mode, or `activity` if the activity implements `NightModeable`

### Night Mode Management

All activities that support night mode follow this pattern:

```java
// Activity implements NightModeable:
public class MyActivity extends InjectableActivity
    implements ActivityLightLevelChanger.NightModeable {

    private boolean nightMode = false;

    @Inject ActivityLightLevelManager activityLightLevelManager;

    @Override public void onResume() {
        super.onResume();
        activityLightLevelManager.onResume();  // triggers setNightMode() call
    }

    @Override public void onPause() {
        super.onPause();
        activityLightLevelManager.onPause();
    }

    @Override
    public void setNightMode(boolean nightMode) {
        this.nightMode = nightMode;
        applyNightMode();
    }

    private void applyNightMode() {
        // e.g. action bar background, text colours, etc.
    }
}

// Module:
@Provides @PerActivity
ActivityLightLevelChanger.NightModeable provideNightModeable() {
    return activity;  // NOT null
}
```

## Related Specifications

- [dialogs.md](dialogs.md) — Dialog fragments used by activities
- [material-3.md](material-3.md) — Theme system and night mode colours
