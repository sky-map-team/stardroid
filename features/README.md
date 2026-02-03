# Features Overview

This section documents the user-facing features of Sky Map.

## Contents

| Specification | Description |
|--------------|-------------|
| [Star Map](star-map.md) | Main interactive sky visualization |
| [Search](search.md) | Finding celestial objects |
| [Time Travel](time-travel.md) | Viewing sky at different times |
| [Layers](layers.md) | Toggleable celestial object categories |
| [Settings](settings.md) | User preferences and configuration |
| [Image Gallery](image-gallery.md) | Astronomical image browser |

## Feature Summary

### Core Experience

| Feature | Description |
|---------|-------------|
| **Real-time Sky** | Point device at sky to see what's there |
| **Auto-rotation** | Display follows device orientation |
| **Tap to Identify** | Touch objects to see names and info |
| **Pinch to Zoom** | Zoom in/out with standard gestures |

### Navigation

| Feature | Description |
|---------|-------------|
| **Sensor Mode** | Automatic orientation tracking |
| **Manual Mode** | Drag to pan, no sensors needed |
| **Search & Center** | Jump directly to any object |
| **Time Travel** | View past or future sky |

### Customization

| Feature | Description |
|---------|-------------|
| **12 Layers** | Toggle stars, planets, grids, etc. |
| **Night Mode** | Red tint for dark adaptation |
| **Font Sizes** | Small, medium, large labels |
| **Sensor Tuning** | Speed and damping adjustments |

### Information

| Feature | Description |
|---------|-------------|
| **Object Info** | Detailed information dialogs |
| **Image Gallery** | Browse astronomical photographs |
| **Compass Calibration** | Improve accuracy guide |
| **Diagnostics** | Sensor and system info |

## Activities

| Activity | Purpose |
|----------|---------|
| `DynamicStarMapActivity` | Main star map interface |
| `SplashScreenActivity` | App launch and EULA |
| `EditSettingsActivity` | Preferences panel |
| `ImageGalleryActivity` | Image browsing grid |
| `ImageDisplayActivity` | Full-screen image view |
| `DiagnosticActivity` | Debug information |
| `CompassCalibrationActivity` | Magnetometer calibration |

## Dialog Fragments

| Dialog | Purpose |
|--------|---------|
| `EulaDialogFragment` | Terms of service |
| `HelpDialogFragment` | In-app help |
| `TimeTravelDialogFragment` | Date/time picker |
| `ObjectInfoDialogFragment` | Object details |
| `MultipleSearchResultsDialogFragment` | Ambiguous search results |
| `NoSearchResultsDialogFragment` | Empty search results |
| `NoSensorsDialogFragment` | Sensor unavailable alert |

## Feature Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│                    Core Features                             │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │ Star Map    │◄───│   Layers    │◄───│  Rendering  │     │
│  └──────┬──────┘    └─────────────┘    └─────────────┘     │
│         │                                                    │
│         ▼                                                    │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │   Search    │    │ Time Travel │    │  Settings   │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Supporting Features                         │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │   Gallery   │    │ Diagnostics │    │ Calibration │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```
