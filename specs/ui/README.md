# UI Layer Specification

This directory contains specifications for the user interface layer of Stardroid.

## Purpose

The UI layer is **responsible for user interaction and visual presentation** of the core domain. It consumes the astronomical calculations and presents them in an intuitive, accessible Material 3 interface.

## Navigation

| Spec | Purpose | Status |
|------|---------|--------|
| [material-3.md](material-3.md) | Material Design 3 theme system, dynamic color, components | TODO |
| [activities.md](activities.md) | Activity classes and their responsibilities | TODO |
| [dialogs.md](dialogs.md) | Bottom sheets, fragments, user interactions | TODO |

## UI Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Activities (Fragments)                    │
│  - DynamicStarMapActivity: Main star map                    │
│  - SplashScreenActivity: Onboarding and splash             │
│  - EditSettingsActivity: Preferences                        │
│  - ImageGalleryActivity: Astronomical images                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Dialogs (Bottom Sheets)                  │
│  - EulaBottomSheet: Terms of Service                       │
│  - WhatsNewBottomSheet: Release notes                       │
│  - TimeTravelBottomSheet: Time travel controls               │
│  - SearchResultsBottomSheet: Search results                  │
│  - ObjectInfoBottomSheet: Celestial object details           │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Material 3 Components                      │
│  - MaterialButton, MaterialTextView                         │
│  - MaterialBottomSheetDialog                               │
│  - MaterialCard, MaterialDivider                            │
│  - Dynamic Colors (Material You)                             │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    ViewModels (Future)                       │
│  - StarMapViewModel: Star map state and interactions        │
│  - SearchViewModel: Search state and results                │
│  - SettingsViewModel: Preferences and theme                 │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     Core Domain (Consumer)                    │
│  - AstronomerModel: Celestial coordinates                  │
│  - LayerManager: Celestial object layers                    │
│  - ControllerGroup: User input handling                    │
└─────────────────────────────────────────────────────────────┘
```

## Design Principles

### Material 3 Guidelines

**Color System:**
- **Dark mode required** for astronomy (night vision)
- **Dynamic accents** from wallpaper (Material You)
- **Red night mode** for preserving night vision
- **High contrast** for outdoor visibility

**Typography:**
- Roboto font family (Material 3 standard)
- Large headers for readability outdoors
- High legibility body text for information

**Components:**
- Bottom sheets for modal interactions
- Cards for grouped information
- FABs for primary actions
- Navigation rail for feature navigation (future)

### Accessibility

**Requirements:**
- Screen reader support (TalkBack)
- High contrast mode support
- Scalable text (100%-200%)
- Touch target size (48dp minimum)
- Keyboard navigation support

**Testing:**
- Accessibility scanner checks
- TalkBack manual testing
- Screen zoom testing

### Performance

**Target:**
- 60 FPS animations
- < 100ms touch response
- Smooth page transitions
- Efficient view recycling

## Current UI → Target UI Migration

### Phase 1: Material 3 Foundation
- **Current:** AppCompat theme, AlertDialogs, basic layouts
- **Target:** Material 3 theme, Bottom Sheets, modern components
- **Impact:** Visual modernization, better ergonomics

### Phase 2: Jetpack Compose (Future)
- **Current:** XML layouts, Activities/Fragments
- **Target:** Compose UI, declarative, state-driven
- **Impact:** Modern UI toolkit, simpler code

### Phase 3: Advanced UX (Future)
- **Current:** Basic gestures (drag, pinch)
- **Target:** Advanced interactions, animations
- **Impact:** More intuitive, delightful experience

## Activities Overview

| Activity | Purpose | Key UI Elements |
|----------|---------|-----------------|
| `DynamicStarMapActivity` | Main star map | OpenGL surface, control buttons, menus |
| `SplashScreenActivity` | Splash screen | Animated starfield, dialogs |
| `EditSettingsActivity` | Preferences | PreferenceScreen, categories |
| `ImageGalleryActivity` | Image browsing | RecyclerView, Grid layout |
| `CompassCalibrationActivity` | Sensor calibration | Visual feedback, instructions |
| `DiagnosticActivity` | Debug info | Lists, metrics, charts |

## Dialogs Overview

| Dialog | Purpose | Type | Parent |
|--------|---------|------|--------|
| `EulaBottomSheetFragment` | Terms of Service | Bottom Sheet | SplashScreenActivity |
| `WhatsNewBottomSheetFragment` | Release notes | Bottom Sheet | SplashScreenActivity |
| `TimeTravelDialogFragment` | Time travel | Modal | DynamicStarMapActivity |
| `SearchResultsBottomSheetFragment` | Search results | Bottom Sheet | DynamicStarMapActivity |
| `ObjectInfoBottomSheetFragment` | Object details | Bottom Sheet | DynamicStarMapActivity |
| `HelpDialogFragment` | Help content | Modal | DynamicStarMapActivity |

## Relationship to Other Specs

- **[../core/](../core/README.md)** - UI layer consumes core domain abstractions
- **[../features/](../features/README.md)** - UI layer implements feature specifications
- **[../architecture/](../architecture/README.md)** - UI layer follows architecture patterns
- **[../blueprint/](../blueprint/README.md)** - UI layer modernization roadmap
