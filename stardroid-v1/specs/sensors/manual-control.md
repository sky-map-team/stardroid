# Manual Control Mode

Manual control allows users to navigate the sky map without relying on device sensors.

## Overview

Manual mode is useful when:
- Device sensors are unavailable or unreliable
- User prefers touch-based navigation
- Viewing from a fixed position (e.g., desk)
- Demonstrating the app to others

## User Interface

### Activation

Manual mode can be activated via:
- Settings toggle
- Double-tap gesture (toggles modes)
- Automatic fallback when sensors unavailable

### Navigation Gestures

| Gesture | Action |
|---------|--------|
| Drag | Pan view in drag direction |
| Fling | Momentum-based pan |
| Pinch | Zoom in/out |

### Visual Feedback

When in manual mode:
- Mode indicator in UI
- Different gesture sensitivity
- No orientation-based updates

## Implementation

### ManualOrientationController

```java
public class ManualOrientationController implements OrientationController {
    private final AstronomerModel model;
    private float totalRotationX = 0;  // Accumulated horizontal rotation
    private float totalRotationY = 0;  // Accumulated vertical rotation

    @Override
    public void start() {
        // No sensors to register
    }

    @Override
    public void stop() {
        // No cleanup needed
    }

    /**
     * Handle drag gesture.
     *
     * @param dx Horizontal drag in pixels
     * @param dy Vertical drag in pixels
     */
    public void onDrag(float dx, float dy) {
        // Convert pixels to degrees
        float sensitivity = getSensitivity();
        float rotationX = -dx * sensitivity;  // Horizontal = azimuth
        float rotationY = dy * sensitivity;   // Vertical = altitude

        // Accumulate rotation
        totalRotationX += rotationX;
        totalRotationY += rotationY;

        // Clamp vertical rotation to avoid flipping
        totalRotationY = clamp(totalRotationY, -89, 89);

        // Update model
        updateModelPointing();
    }

    private void updateModelPointing() {
        // Convert accumulated rotation to pointing direction
        float azimuth = (float) Math.toRadians(totalRotationX);
        float altitude = (float) Math.toRadians(totalRotationY);

        // Calculate pointing vector
        GeocentricCoordinates pointing = calculatePointing(azimuth, altitude);
        model.setPointing(pointing);
    }

    private GeocentricCoordinates calculatePointing(float azimuth, float altitude) {
        // Start from reference direction (north, horizon)
        // Apply horizontal rotation (azimuth)
        // Apply vertical rotation (altitude)
        float x = (float) (Math.cos(altitude) * Math.cos(azimuth));
        float y = (float) (Math.cos(altitude) * Math.sin(azimuth));
        float z = (float) Math.sin(altitude);

        return new GeocentricCoordinates(x, y, z);
    }
}
```

### Gesture Detection

```java
public class DragRotateZoomGestureDetector {
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleDetector;
    private final GestureInterpreter interpreter;

    public boolean onTouchEvent(MotionEvent event) {
        // Handle both single-touch and multi-touch
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        return true;
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                               float distanceX, float distanceY) {
            interpreter.onDrag(distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                              float velocityX, float velocityY) {
            interpreter.onFling(velocityX, velocityY);
            return true;
        }
    }
}
```

### Momentum/Fling Animation

```java
public class Flinger {
    private final AstronomerModel model;
    private ValueAnimator animator;

    public void fling(float velocityX, float velocityY) {
        // Calculate initial velocity magnitude
        float velocity = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);

        // Normalize direction
        float dirX = velocityX / velocity;
        float dirY = velocityY / velocity;

        // Animate with deceleration
        animator = ValueAnimator.ofFloat(velocity, 0);
        animator.setDuration(calculateDuration(velocity));
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float currentVelocity = (float) animation.getAnimatedValue();
            float dx = dirX * currentVelocity * FRAME_TIME;
            float dy = dirY * currentVelocity * FRAME_TIME;
            manualController.onDrag(dx, dy);
        });

        animator.start();
    }

    public void stopFling() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }
}
```

## Mode Switching

### ControllerGroup

Manages switching between sensor and manual modes:

```java
public class ControllerGroup {
    private final SensorOrientationController sensorController;
    private final ManualOrientationController manualController;
    private OrientationController activeController;
    private boolean isManualMode = false;

    public void setManualMode(boolean manual) {
        // Stop current controller
        if (activeController != null) {
            activeController.stop();
        }

        isManualMode = manual;

        if (manual) {
            activeController = manualController;
            // Preserve current pointing when switching to manual
            syncManualToCurrentPointing();
        } else {
            activeController = sensorController;
        }

        activeController.start();
    }

    private void syncManualToCurrentPointing() {
        // Get current pointing from model
        GeocentricCoordinates pointing = model.getPointing();

        // Convert to azimuth/altitude
        float[] azAlt = pointingToAzAlt(pointing);

        // Initialize manual controller
        manualController.setInitialRotation(azAlt[0], azAlt[1]);
    }
}
```

### Auto-Switch on Sensor Unavailability

```java
public void onSensorUnavailable() {
    if (!isManualMode) {
        // Automatic fallback to manual mode
        setManualMode(true);

        // Notify user
        showDialog(new NoSensorsDialogFragment());
    }
}
```

## Sensitivity Settings

### Drag Sensitivity

```java
private float getSensitivity() {
    // Degrees per pixel
    // Adjusted based on screen density and field of view
    float baseSensitivity = 0.1f;
    float fovFactor = model.getFieldOfView() / 60f;
    float densityFactor = resources.getDisplayMetrics().density;

    return baseSensitivity * fovFactor / densityFactor;
}
```

### Zoom Sensitivity

```java
public void onScale(float scaleFactor) {
    float currentFov = model.getFieldOfView();
    float newFov = currentFov / scaleFactor;

    // Clamp to valid range
    newFov = clamp(newFov, MIN_FOV, MAX_FOV);

    model.setFieldOfView(newFov);
}
```

## Edge Cases

### Pole Crossing

When dragging past celestial poles:

```java
// Clamp altitude to prevent flipping
totalRotationY = clamp(totalRotationY, -89, 89);
```

### Wrap-Around

Horizontal rotation wraps at 360°:

```java
// Normalize azimuth
totalRotationX = ((totalRotationX % 360) + 360) % 360;
```

### Zoom Limits

```java
private static final float MIN_FOV = 10f;   // Maximum zoom in
private static final float MAX_FOV = 120f;  // Maximum zoom out
```

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `ManualOrientationController` | Touch-to-rotation conversion |
| `DragRotateZoomGestureDetector` | Gesture recognition |
| `GestureInterpreter` | Gesture-to-action mapping |
| `Flinger` | Momentum animation |
| `ControllerGroup` | Mode management |
