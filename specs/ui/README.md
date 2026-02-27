# UI Layer Specification

This directory contains specifications for the user interface layer of Stardroid.

## Purpose

The UI layer is **responsible for user interaction and visual presentation** of the core domain. It consumes the astronomical calculations and presents them in a night-vision-friendly interface.

## Navigation

| Spec | Purpose |
|------|---------|
| [material-3.md](material-3.md) | Theme system, night mode mechanism, colour palette |
| [activities.md](activities.md) | Activity classes and their responsibilities |
| [dialogs.md](dialogs.md) | Dialog fragments, injection pattern, night mode |

## UI Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Activities                              │
│  DynamicStarMapActivity  — main star map (OpenGL + overlays)│
│  SplashScreenActivity    — onboarding                        │
│  EditSettingsActivity    — preferences (PreferenceActivity)  │
│  ImageGalleryActivity    — astronomical image browser        │
│  CompassCalibrationActivity — magnetometer calibration       │
│  DiagnosticActivity      — sensor/system debug info          │
└─────────────────────────────────────────────────────────────┘
                              │
                    Dagger 2 injection
                              │
┌─────────────────────────────────────────────────────────────┐
│                    DialogFragments                           │
│  EulaDialogFragment      — Terms of Service (WebView)       │
│  WhatsNewDialogFragment  — Release notes                     │
│  HelpDialogFragment      — Help content (WebView)           │
│  CreditsDialogFragment   — Credits (WebView)                 │
│  TimeTravelDialogFragment — wraps TimeTravelDialog           │
│  NoSearchResultsDialogFragment                               │
│  MultipleSearchResultsDialogFragment                         │
│  ObjectInfoDialogFragment                                    │
│  NoSensorsDialogFragment                                     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                Night Mode Infrastructure                     │
│  ActivityLightLevelManager  — reads preference; triggers     │
│  ActivityLightLevelChanger  — dims window; calls NightModeable│
│  NightModeable interface    — activities implement for custom│
│                               UI colour changes              │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Core Domain (Consumer)                   │
│  AstronomerModel   — celestial coordinates                   │
│  LayerManager      — celestial object layers                 │
│  ControllerGroup   — user input handling                     │
└─────────────────────────────────────────────────────────────┘
```

## Design Principles

### Dark-First

The entire app is designed for night-time outdoor use:
- **Dark backgrounds** are essential — no light mode
- **Night vision mode** applies a red tint to all UI elements, not just the OpenGL surface
- **High contrast** text for outdoor readability

### Current Technology

- **Theme:** `Theme.Holo` (Android built-in dark theme)
- **DI:** Dagger 2 with per-activity components
- **Dialogs:** `DialogFragment` + `AlertDialog.Builder`, NOT BottomSheets
- **Settings:** `PreferenceActivity` + `PreferenceFragment`

### Future Direction (not yet implemented)

- Material 3 / Material You with dynamic colour
- Jetpack Compose UI
- ViewModel / StateFlow reactive updates
- BottomSheetDialogFragment for modal interactions

## Relationship to Other Specs

- **[../core/](../core/README.md)** — UI layer consumes core domain abstractions
- **[../features/](../features/README.md)** — UI layer implements feature specifications
- **[../architecture/](../architecture/README.md)** — UI layer follows architecture patterns
