# Settings Feature

Sky Map provides extensive user preferences for customizing the app's behavior.

## Overview

Settings are managed through Android's `SharedPreferences` and presented via `EditSettingsActivity`. Preferences are organized into categories.

## Settings Categories

### Location Settings

Control how the app determines user location.

| Setting | Key | Type | Default | Description |
|---------|-----|------|---------|-------------|
| Auto-locate | `no_auto_locate` | Boolean | false | Disable automatic location |
| Force GPS | `force_gps` | Boolean | false | Use GPS instead of network |
| Location Name | `location` | String | - | Manual location (geocoded) |
| Latitude | `latitude` | Float | 0 | Manual latitude (-90 to +90) |
| Longitude | `longitude` | Float | 0 | Manual longitude (-180 to +180) |
| Magnetic Correction | `use_magnetic_correction` | Boolean | true | Apply magnetic declination |

### Sensor Settings

Configure sensor behavior and sensitivity.

| Setting | Key | Type | Default | Description |
|---------|-----|------|---------|-------------|
| Disable Gyroscope | `disable_gyro` | Boolean | false | Use legacy sensor fusion |
| Sensor Speed | `sensor_speed` | Enum | STANDARD | SLOW/STANDARD/FAST |
| Sensor Damping | `sensor_damping` | Enum | MEDIUM | LOW/MEDIUM/HIGH/EXTRA_HIGH |
| Reverse Magnetic Z | `reverse_magnetic_z` | Boolean | false | Legacy device workaround |
| Viewing Direction | `viewing_direction` | Enum | STANDARD | STANDARD/LANDSCAPE |

### Appearance Settings

Customize visual display.

| Setting | Key | Type | Default | Description |
|---------|-----|------|---------|-------------|
| Night Mode | `auto_dimness` | Enum | SYSTEM | OFF/AUTO/SYSTEM |
| Font Size | `font_size` | Enum | MEDIUM | SMALL/MEDIUM/LARGE |
| Show Messier Images | `show_messier_images` | Boolean | true | Display deep-sky photos |

### General Settings

Miscellaneous preferences.

| Setting | Key | Type | Default | Description |
|---------|-----|------|---------|-------------|
| Tap for Info | `show_object_info_on_tap` | Boolean | true | Show info on object tap |
| Sound Effects | `sound_effects` | Boolean | false | Play audio feedback |
| Analytics | `enable_analytics` | Boolean | true | Google Analytics (GMS only) |

## Settings UI

### Preference Screen

Defined in `res/xml/preference_screen.xml`:

```xml
<PreferenceScreen>
    <PreferenceCategory android:title="@string/location_settings">
        <SwitchPreference
            android:key="no_auto_locate"
            android:title="@string/disable_auto_locate"
            android:defaultValue="false" />
        <EditTextPreference
            android:key="location"
            android:title="@string/location_name"
            android:dependency="no_auto_locate" />
        <!-- ... -->
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/sensor_settings">
        <ListPreference
            android:key="sensor_speed"
            android:title="@string/sensor_speed"
            android:entries="@array/sensor_speed_entries"
            android:entryValues="@array/sensor_speed_values"
            android:defaultValue="STANDARD" />
        <!-- ... -->
    </PreferenceCategory>
</PreferenceScreen>
```

## Settings Implementation

### Reading Preferences

```kotlin
// Inject SharedPreferences via Dagger
@Inject lateinit var sharedPreferences: SharedPreferences

// Read a preference
val nightMode = sharedPreferences.getString("auto_dimness", "SYSTEM")
val fontSize = FontSize.valueOf(
    sharedPreferences.getString("font_size", "MEDIUM")!!
)
```

### Preference Change Listener

```kotlin
class SettingsChangeListener : OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(
        prefs: SharedPreferences,
        key: String
    ) {
        when (key) {
            "auto_dimness" -> updateNightMode()
            "sensor_speed" -> updateSensorRate()
            "font_size" -> updateLabels()
        }
    }
}
```

## Setting Details

### Sensor Speed

Controls sensor sampling rate:

| Value | Rate | Use Case |
|-------|------|----------|
| SLOW | ~10 Hz | Battery saving |
| STANDARD | ~30 Hz | Normal use |
| FAST | ~60 Hz | Smooth tracking |

### Sensor Damping

Controls smoothing filter strength:

| Value | Effect |
|-------|--------|
| LOW | Responsive, may be jittery |
| MEDIUM | Balanced |
| HIGH | Smooth, slight lag |
| EXTRA_HIGH | Very smooth, noticeable lag |

### Night Mode

Preserves night vision:

| Value | Behavior |
|-------|----------|
| OFF | Normal colors |
| AUTO | Adjust based on ambient light sensor |
| SYSTEM | Follow Android system night mode |

### Font Size

Label text size:

| Value | Size |
|-------|------|
| SMALL | 12sp |
| MEDIUM | 16sp |
| LARGE | 20sp |

## Preference Keys Constants

```kotlin
object PreferenceKeys {
    // Location
    const val NO_AUTO_LOCATE = "no_auto_locate"
    const val FORCE_GPS = "force_gps"
    const val LOCATION = "location"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val USE_MAGNETIC_CORRECTION = "use_magnetic_correction"

    // Sensors
    const val DISABLE_GYRO = "disable_gyro"
    const val SENSOR_SPEED = "sensor_speed"
    const val SENSOR_DAMPING = "sensor_damping"
    const val REVERSE_MAGNETIC_Z = "reverse_magnetic_z"
    const val VIEWING_DIRECTION = "viewing_direction"

    // Appearance
    const val AUTO_DIMNESS = "auto_dimness"
    const val FONT_SIZE = "font_size"
    const val SHOW_MESSIER_IMAGES = "show_messier_images"

    // General
    const val SHOW_OBJECT_INFO_ON_TAP = "show_object_info_on_tap"
    const val SOUND_EFFECTS = "sound_effects"
    const val ENABLE_ANALYTICS = "enable_analytics"

    // Layer visibility (dynamic)
    const val LAYER_PREFIX = "source_provider."
}
```

## Preference Migration

When preference keys change between versions:

```kotlin
class PreferenceMigrator(private val prefs: SharedPreferences) {
    fun migrate(fromVersion: Int, toVersion: Int) {
        if (fromVersion < 2 && toVersion >= 2) {
            // Migrate old key to new key
            if (prefs.contains("old_key")) {
                val value = prefs.getString("old_key", "")
                prefs.edit()
                    .putString("new_key", value)
                    .remove("old_key")
                    .apply()
            }
        }
    }
}
```

## Build Flavor Differences

### GMS Flavor

All settings available, including:
- Google Analytics toggle
- Google Location Services

### F-Droid Flavor

Analytics settings hidden:
- No `enable_analytics` preference
- No Google Location Services
- Uses Android location APIs directly

## Key Classes

| Class | Responsibility |
|-------|----------------|
| `EditSettingsActivity` | Settings UI |
| `SharedPreferences` | Android preference storage |
| `PreferenceKeys` | Key constants |
| Various preference fragments | Category UI |
