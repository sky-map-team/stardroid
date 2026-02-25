# Build Flavors

Sky Map supports two build flavors to accommodate different distribution channels.

## Flavor Overview

| Flavor | Target | Google Services | Analytics |
|--------|--------|-----------------|-----------|
| **gms** | Google Play Store | Yes | Firebase Analytics |
| **fdroid** | F-Droid | No | None |

## Build Commands

```bash
# GMS flavor
./gradlew assembleGmsDebug
./gradlew assembleGmsRelease

# F-Droid flavor
./gradlew assembleFdroidDebug
./gradlew assembleFdroidRelease
```

## Flavor Configuration

### app/build.gradle

```groovy
android {
    flavorDimensions += "store"

    productFlavors {
        gms {
            dimension "store"
            applicationIdSuffix ""
        }

        fdroid {
            dimension "store"
            applicationIdSuffix ".fdroid"
        }
    }
}

dependencies {
    // GMS-only dependencies
    gmsImplementation 'com.google.firebase:firebase-analytics:21.x'
    gmsImplementation 'com.google.android.gms:play-services-location:21.x'
}
```

## Source Sets

### Directory Structure

```
app/src/
├── main/           # Shared code
│   ├── java/
│   ├── kotlin/
│   ├── res/
│   └── AndroidManifest.xml
├── gms/            # GMS-specific code
│   ├── java/
│   └── AndroidManifest.xml
└── fdroid/         # F-Droid-specific code
    ├── java/
    └── AndroidManifest.xml
```

### Shared Code (main/)

Common functionality used by both flavors:
- Core astronomy calculations
- Rendering pipeline
- UI components
- Sensor handling

### GMS-Specific (gms/)

```java
// gms/java/.../analytics/AnalyticsModule.java
@Module
public class AnalyticsModule {
    @Provides
    @Singleton
    Analytics provideAnalytics(Context context) {
        return new FirebaseAnalyticsImpl(context);
    }
}
```

### F-Droid-Specific (fdroid/)

```java
// fdroid/java/.../analytics/AnalyticsModule.java
@Module
public class AnalyticsModule {
    @Provides
    @Singleton
    Analytics provideAnalytics(Context context) {
        return new NoOpAnalytics();  // Stub implementation
    }
}
```

## Feature Differences

### Analytics

| Feature | GMS | F-Droid |
|---------|-----|---------|
| Usage tracking | Firebase Analytics | Disabled |
| Crash reporting | Firebase Crashlytics | Disabled |
| User preferences | Opt-in toggle | No toggle (always off) |

### Location Services

| Feature | GMS | F-Droid |
|---------|-----|---------|
| Location API | Play Services Location | Android Location API |
| Fused provider | Yes | No |
| Battery optimization | Better | Standard |

### Ads

Neither flavor includes advertisements.

## Interface Abstraction

### Analytics Interface

```kotlin
// main/java/.../analytics/Analytics.kt
interface Analytics {
    fun trackEvent(event: String, params: Map<String, Any> = emptyMap())
    fun setUserProperty(name: String, value: String)
    fun setEnabled(enabled: Boolean)
}
```

### GMS Implementation

```kotlin
// gms/java/.../analytics/FirebaseAnalyticsImpl.kt
class FirebaseAnalyticsImpl(context: Context) : Analytics {
    private val firebase = FirebaseAnalytics.getInstance(context)

    override fun trackEvent(event: String, params: Map<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                // ...
            }
        }
        firebase.logEvent(event, bundle)
    }

    override fun setEnabled(enabled: Boolean) {
        firebase.setAnalyticsCollectionEnabled(enabled)
    }
}
```

### F-Droid Implementation

```kotlin
// fdroid/java/.../analytics/NoOpAnalytics.kt
class NoOpAnalytics : Analytics {
    override fun trackEvent(event: String, params: Map<String, Any>) {
        // No-op
    }

    override fun setUserProperty(name: String, value: String) {
        // No-op
    }

    override fun setEnabled(enabled: Boolean) {
        // No-op
    }
}
```

## Manifest Differences

### GMS Manifest

```xml
<!-- gms/AndroidManifest.xml -->
<manifest>
    <application>
        <!-- Firebase initialization -->
        <meta-data
            android:name="com.google.firebase.analytics.APPLICATION_ID"
            android:value="@string/google_app_id" />

        <!-- Google Play Services -->
        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />
    </application>
</manifest>
```

### F-Droid Manifest

```xml
<!-- fdroid/AndroidManifest.xml -->
<manifest>
    <!-- No Google-specific declarations -->
</manifest>
```

## Signing Configuration

### Debug Builds

Both flavors use the default debug keystore.

### Release Builds

#### GMS Release

Requires `no-checkin.properties`:

```properties
# no-checkin.properties (not in version control)
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=xxxxx
RELEASE_KEY_ALIAS=skymap
RELEASE_KEY_PASSWORD=xxxxx
```

```groovy
// app/build.gradle
android {
    signingConfigs {
        release {
            def props = new Properties()
            props.load(new FileInputStream("no-checkin.properties"))

            storeFile file(props['RELEASE_STORE_FILE'])
            storePassword props['RELEASE_STORE_PASSWORD']
            keyAlias props['RELEASE_KEY_ALIAS']
            keyPassword props['RELEASE_KEY_PASSWORD']
        }
    }

    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                         'proguard-rules.pro'
        }
    }
}
```

#### F-Droid Release

F-Droid builds and signs APKs on their build servers.

## Preferences Differences

### GMS Settings

```xml
<!-- gms/res/xml/preference_screen.xml -->
<PreferenceScreen>
    <!-- Analytics toggle -->
    <SwitchPreference
        android:key="enable_analytics"
        android:title="@string/analytics_title"
        android:summary="@string/analytics_summary"
        android:defaultValue="true" />
</PreferenceScreen>
```

### F-Droid Settings

```xml
<!-- fdroid/res/xml/preference_screen.xml -->
<PreferenceScreen>
    <!-- No analytics toggle -->
</PreferenceScreen>
```

## Build Verification

```bash
# Verify GMS build
./gradlew assembleGmsDebug
adb install app/build/outputs/apk/gms/debug/app-gms-debug.apk

# Verify F-Droid build
./gradlew assembleFdroidDebug
adb install app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

## Application IDs

| Flavor | Application ID |
|--------|----------------|
| GMS | `com.google.android.stardroid` |
| F-Droid | `com.google.android.stardroid.fdroid` |

Different IDs allow both variants to be installed simultaneously.
