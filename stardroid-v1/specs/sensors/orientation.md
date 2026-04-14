# Device Orientation Detection

Sky Map uses Android sensors to detect device orientation and determine where the user is pointing.

## Sensor Selection

### Rotation Vector Sensor (Preferred)

Android's `TYPE_ROTATION_VECTOR` sensor provides fused orientation:

```java
// Check for rotation sensor
SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

if (rotationSensor != null) {
    // Use modern rotation sensor
    useRotationSensor = true;
} else {
    // Fall back to legacy sensors
    useRotationSensor = false;
}
```

### Legacy Sensor Fusion (Fallback)

When rotation sensor unavailable, combine raw sensors:

```java
Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

// Both required for legacy mode
if (accelerometer != null && magnetometer != null) {
    useLegacySensors = true;
}
```

## SensorOrientationController

Handles sensor events and updates the model.

### Initialization

```java
public class SensorOrientationController implements SensorEventListener {
    private final SensorManager sensorManager;
    private final AstronomerModel model;
    private final boolean useRotationSensor;

    // Sensor data buffers
    private final float[] rotationMatrix = new float[16];
    private final float[] accelerometerData = new float[3];
    private final float[] magnetometerData = new float[3];

    public void start() {
        int sensorDelay = getSensorDelay();  // From preferences

        if (useRotationSensor) {
            sensorManager.registerListener(this, rotationSensor, sensorDelay);
        } else {
            sensorManager.registerListener(this, accelerometer, sensorDelay);
            sensorManager.registerListener(this, magnetometer, sensorDelay);
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }
}
```

### Sensor Event Handling

```java
@Override
public void onSensorChanged(SensorEvent event) {
    switch (event.sensor.getType()) {
        case Sensor.TYPE_ROTATION_VECTOR:
            handleRotationVector(event.values);
            break;
        case Sensor.TYPE_ACCELEROMETER:
            System.arraycopy(event.values, 0, accelerometerData, 0, 3);
            updateLegacyOrientation();
            break;
        case Sensor.TYPE_MAGNETIC_FIELD:
            System.arraycopy(event.values, 0, magnetometerData, 0, 3);
            updateLegacyOrientation();
            break;
    }
}
```

### Rotation Vector Processing

```java
private void handleRotationVector(float[] rotationVector) {
    // Convert quaternion to rotation matrix
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

    // Apply viewing direction adjustment
    applyViewingDirection(rotationMatrix);

    // Apply damping filter
    float[] dampedMatrix = applyDamping(rotationMatrix);

    // Update model
    model.setPhoneOrientation(dampedMatrix);
}
```

### Legacy Sensor Fusion

```java
private void updateLegacyOrientation() {
    if (accelerometerData == null || magnetometerData == null) {
        return;  // Need both sensors
    }

    // Compute rotation matrix from raw sensors
    float[] R = new float[16];
    float[] I = new float[16];

    boolean success = SensorManager.getRotationMatrix(
        R, I, accelerometerData, magnetometerData
    );

    if (success) {
        applyViewingDirection(R);
        float[] dampedMatrix = applyDamping(R);
        model.setPhoneOrientation(dampedMatrix);
    }
}
```

## Sensor Speed Settings

| Setting | Delay | Approximate Rate |
|---------|-------|------------------|
| SLOW | SENSOR_DELAY_UI | ~16 Hz |
| STANDARD | SENSOR_DELAY_GAME | ~50 Hz |
| FAST | SENSOR_DELAY_FASTEST | ~100+ Hz |

```java
private int getSensorDelay() {
    String speedPref = preferences.getString("sensor_speed", "STANDARD");
    switch (speedPref) {
        case "SLOW": return SensorManager.SENSOR_DELAY_UI;
        case "FAST": return SensorManager.SENSOR_DELAY_FASTEST;
        default: return SensorManager.SENSOR_DELAY_GAME;
    }
}
```

## Sensor Damping

Smoothing filter reduces jitter in sensor readings.

### Exponential Moving Average

```java
public class SensorDamping {
    private float dampingFactor;  // 0.0 = no damping, 0.99 = heavy damping
    private float[] previousMatrix;

    public float[] apply(float[] newMatrix) {
        if (previousMatrix == null) {
            previousMatrix = newMatrix.clone();
            return newMatrix;
        }

        float[] result = new float[16];
        for (int i = 0; i < 16; i++) {
            result[i] = previousMatrix[i] * dampingFactor
                      + newMatrix[i] * (1 - dampingFactor);
        }

        // Renormalize rotation matrix
        normalizeRotationMatrix(result);

        previousMatrix = result.clone();
        return result;
    }
}
```

### Damping Levels

| Setting | Factor | Effect |
|---------|--------|--------|
| LOW | 0.3 | Responsive, may be jittery |
| MEDIUM | 0.6 | Balanced |
| HIGH | 0.8 | Smooth, slight lag |
| EXTRA_HIGH | 0.9 | Very smooth, noticeable lag |

## Viewing Direction

Adjusts for how user holds the phone.

### Standard Mode

Screen faces the observer (portrait, looking through phone):

```java
// No adjustment needed - default Android orientation
```

### Telescope Mode

Phone top points at sky (landscape, along telescope tube):

```java
private void applyTelescopeMode(float[] matrix) {
    // Rotate 90 degrees around X axis
    float[] adjustment = new float[16];
    Matrix.setRotateM(adjustment, 0, 90, 1, 0, 0);
    Matrix.multiplyMM(matrix, 0, matrix, 0, adjustment, 0);
}
```

## Magnetic Field Considerations

### Magnetic Declination

Difference between magnetic and true north:

```java
public float getMagneticDeclination(float latitude, float longitude, float altitude) {
    GeomagneticField field = new GeomagneticField(
        latitude, longitude, altitude,
        System.currentTimeMillis()
    );
    return field.getDeclination();  // Degrees
}
```

### Calibration

When magnetometer accuracy is low:

```java
@Override
public void onAccuracyChanged(Sensor sensor, int accuracy) {
    if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            // Prompt user to calibrate
            showCalibrationPrompt();
        }
    }
}
```

## Device Compatibility

### Sensor Availability

| Sensor | Availability | Required |
|--------|--------------|----------|
| Accelerometer | ~100% | Yes |
| Magnetometer | ~95% | Yes |
| Gyroscope | ~70% | No (but improves quality) |
| Rotation Vector | ~70% | No (preferred when available) |

### Fallback Chain

```
Rotation Vector Sensor
         │
         └─ (unavailable) ──► Accelerometer + Magnetometer + Gyroscope
                                        │
                                        └─ (no gyro) ──► Accelerometer + Magnetometer
                                                                   │
                                                                   └─ (no mag) ──► Manual mode only
```

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `SensorOrientationController` | Sensor event handling |
| `SensorDamping` | Smoothing filter |
| `MagneticDeclinationCalculator` | True north correction |
| `CompassCalibrationActivity` | User calibration guide |
