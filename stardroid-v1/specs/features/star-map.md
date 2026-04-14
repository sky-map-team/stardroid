# Star Map Feature

The main interactive star map is the core feature of Sky Map, implemented in `DynamicStarMapActivity`.

## Overview

The star map displays a real-time view of the night sky, synchronized with device orientation. Users point their device at the sky to see celestial objects in that direction.

## User Interface

### Main View

```
┌────────────────────────────────────────┐
│  [≡]              21:45    [🔍]  [⋮]  │  ← Toolbar
├────────────────────────────────────────┤
│                                        │
│         ★ Polaris                      │
│              ·                         │
│    ·  ·                    ★ Vega      │
│          ·    ·                        │
│    ·                                   │
│              ·      ·                  │
│                   ★ Deneb              │
│         ·                              │
│              ·        ·                │
│                                        │
├────────────────────────────────────────┤
│  N                                     │  ← Status bar
└────────────────────────────────────────┘
```

### UI Elements

| Element | Purpose |
|---------|---------|
| Menu button | Access settings, layers, help |
| Time display | Current time (or time travel time) |
| Search icon | Open search interface |
| Overflow menu | Additional options |
| Status bar | Compass direction, coordinates |

## Interaction Modes

### Sensor Mode (Default)

The display automatically tracks device orientation:
- **Pitch**: Tilt device up/down to look higher/lower
- **Yaw**: Rotate device left/right to look around
- **Roll**: Tilt sideways (usually ignored)

```java
// SensorOrientationController
public void onSensorChanged(SensorEvent event) {
    // Update model with new orientation
    model.setPhoneOrientation(rotationMatrix);
}
```

### Manual Mode

Touch-based navigation when sensors are unavailable or disabled:
- **Drag**: Pan the view
- **Fling**: Momentum-based panning
- **Pinch**: Zoom in/out

```java
// ManualOrientationController
public void onDrag(float dx, float dy) {
    model.rotateView(dx, dy);
}
```

### Switching Modes

Users can switch modes via:
- Settings toggle
- Auto-switch when sensors unavailable
- Gesture detection (long press)

## Gestures

| Gesture | Action |
|---------|--------|
| Single tap | Show object info (if enabled) |
| Double tap | Toggle manual/sensor mode |
| Long press | Context menu |
| Drag | Pan view (manual mode) |
| Fling | Momentum pan |
| Pinch | Zoom in/out |
| Two-finger drag | (Reserved) |

### Gesture Implementation

```java
// DragRotateZoomGestureDetector
@Override
public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
    gestureInterpreter.onDrag(dx, dy);
    return true;
}

@Override
public boolean onScale(ScaleGestureDetector detector) {
    gestureInterpreter.onScale(detector.getScaleFactor());
    return true;
}
```

## Object Selection

### Tap to Identify

When `show_object_info_on_tap` preference is enabled:

1. User taps screen location
2. `CelestialHitTester` determines closest object
3. `ObjectInfoDialogFragment` displays object details

```java
// CelestialHitTester
public SearchResult hitTest(float x, float y, float radius) {
    GeocentricCoordinates tapCoords = screenToSky(x, y);
    return findClosestObject(tapCoords, radius);
}
```

### Selection Feedback

- Visual highlight on selected object
- Name label emphasis
- Dialog with detailed information

## Field of View

### Zoom Levels

| Zoom | Field of View | Use Case |
|------|---------------|----------|
| Min | ~120° | Wide view, multiple constellations |
| Default | ~60° | Normal viewing |
| Max | ~10° | Close-up, individual stars |

### Zoom Controls

```java
// ZoomController
public void onScale(float scaleFactor) {
    float newFov = currentFov / scaleFactor;
    newFov = clamp(newFov, MIN_FOV, MAX_FOV);
    model.setFieldOfView(newFov);
}
```

## Night Mode

Preserves dark adaptation with red-tinted display:

| Mode | Description |
|------|-------------|
| OFF | Normal colors |
| AUTO | Adjusts based on ambient light |
| SYSTEM | Follows Android night mode |

```java
// ActivityLightLevelManager
public void setNightMode(NightMode mode) {
    switch (mode) {
        case AUTO:
            applyRedFilter(ambientLightLevel);
            break;
        case SYSTEM:
            followSystemNightMode();
            break;
    }
}
```

## Performance

### Frame Rate Target

- **Goal**: 60 FPS smooth rendering
- **Sensor Updates**: ~60Hz
- **Position Updates**: As needed (solar system, ISS)

### Optimization Strategies

1. **Frustum Culling**: Only render visible objects
2. **LOD (Level of Detail)**: Reduce detail at low zoom
3. **Update Batching**: Group similar updates
4. **Texture Caching**: Reuse loaded textures

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `DynamicStarMapActivity` | Main activity, UI coordination |
| `SkyRenderer` | OpenGL rendering |
| `AstronomerModel` | Coordinate transformation |
| `ControllerGroup` | Input controller management |
| `GestureInterpreter` | Gesture to action mapping |
| `CelestialHitTester` | Object selection |
