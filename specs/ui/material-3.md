# Theme & Night Mode System

## Purpose

Describes the **actual theme system** in Stardroid and the **night mode mechanism** that preserves dark adaptation while using the app outdoors.

---

## Actual Theme Structure

### Base Theme

The app uses **`Theme.Holo`** (Android built-in), not Material 3.

```xml
<!-- res/values/styles.xml -->
<style name="AppTheme" parent="@android:style/Theme.Holo">
    <!-- No overrides — plain Holo dark -->
</style>

<!-- Used by DynamicStarMapActivity only -->
<style name="FullscreenTheme" parent="AppTheme">
    <item name="android:actionBarStyle">@style/FullscreenActionBarStyle</item>
    <item name="android:windowActionBarOverlay">true</item>
    <item name="android:windowBackground">@null</item>
</style>

<style name="FullscreenActionBarStyle" parent="android:Widget.Holo.ActionBar">
    <item name="android:background">@color/black_overlay</item>  <!-- #66000000 -->
</style>
```

All secondary activities (`EditSettingsActivity`, `ImageGalleryActivity`, `DiagnosticActivity`, `CompassCalibrationActivity`) use the default `AppTheme`.

---

## Night Mode Mechanism

### Overview

Night mode dims the screen and applies a red tint to UI elements to preserve the user's dark-adapted vision during outdoor stargazing.

### Key Classes

| Class | Role |
|-------|------|
| `ActivityLightLevelManager` | Reads `lightmode` preference; calls `ActivityLightLevelChanger.setNightMode()` on resume and on preference change |
| `ActivityLightLevelChanger` | Adjusts window brightness; calls `NightModeable.setNightMode()` if the activity implements it |
| `ActivityLightLevelChanger.NightModeable` | Interface for activities with custom night mode UI changes |

### Lifecycle

```java
// In each activity:
@Inject ActivityLightLevelManager activityLightLevelManager;

@Override public void onResume() {
    super.onResume();
    activityLightLevelManager.onResume();  // reads preference, calls setNightMode()
}

@Override public void onPause() {
    super.onPause();
    activityLightLevelManager.onPause();
}
```

### Preference Key

```java
ActivityLightLevelManager.LIGHT_MODE_KEY = "lightmode"
// Values: "DAY" | "NIGHT"
```

The user toggles night mode via Menu → dim icon in `DynamicStarMapActivity`, which writes to this preference.

### Dim Options

Three dimness options (set in Settings under "dim mode"):
- **DIM** — `BRIGHTNESS_OVERRIDE_OFF` (minimum)
- **SYSTEM** — `BRIGHTNESS_OVERRIDE_NONE` (let system control)
- **CLASSIC** — `20/255` (original behaviour)

---

## Night Colour Palette

```java
// Shared across activities — defined as private constants in each activity
static final int NIGHT_TEXT_COLOR  = 0xFFCC4444;  // text, headings
static final int NIGHT_LINK_COLOR  = 0xFFCC6666;  // hyperlinks in WebViews
static final int DAY_LINK_COLOR    = 0xFF33B5E5;  // Holo default link blue

// Action bar overlay backgrounds (used in setBackgroundDrawable)
static final int NIGHT_RED_OVERLAY = 0x66220000;
static final int DAY_OVERLAY       = 0x66000000;  // matches @color/black_overlay

// Time-travel bar backgrounds
static final int NIGHT_BAR_BG = 0x20990000;  // red-tinted
static final int DAY_BAR_BG   = 0x20990099;  // purple (original)
```

### WebView night mode

`HelpDialogFragment`, `CreditsDialogFragment`, `EulaDialogFragment`, and `CompassCalibrationActivity` use a WebView. Night mode is applied by toggling the `night-mode` CSS class on `<body>`:

```java
webView.evaluateJavascript("document.body.classList.add('night-mode')", null);
```

The `help.css` asset provides red-tinted styles for `h1`, `h2`, `h3`, links, and callout boxes under `.night-mode`.

---

## Per-Activity Night Mode Support

| Activity | `NightModeable`? | Night UI changes |
|----------|-----------------|------------------|
| `DynamicStarMapActivity` | Yes | Action bar, layer sidebar tint, icon color filters, time-player bar & text |
| `CompassCalibrationActivity` | Yes | Text colours, WebView body class, sensor accuracy colours |
| `DiagnosticActivity` | Yes | Action bar, sensor accuracy colours |
| `EditSettingsActivity` | Yes | Action bar |
| `ImageGalleryActivity` | Yes | Action bar |
| `TimeTravelDialog` | N/A (Dialog) | Text colours read from preference in `onStart()` |
| WebView dialogs (Help/Credits/ToS) | N/A (DialogFragment) | CSS class applied at creation time |

---

## Future / Aspirations

The following are **not yet implemented**; they describe a possible future direction:

- **Material 3 / Material You**: Replace `Theme.Holo` with `Theme.Material3.DayNight`. Use dynamic colours from the user's wallpaper (Android 12+).
- **Jetpack Compose**: Replace XML layouts and Activities/Fragments with Compose UI.
- **ViewModel / StateFlow**: Replace imperative UI updates with reactive state.
- **Bottom sheets**: Replace `AlertDialog`-based `DialogFragment`s with `BottomSheetDialogFragment`.

---

## Related Specifications

- [activities.md](activities.md) — How activities use the night mode mechanism
- [dialogs.md](dialogs.md) — Night mode in dialogs
