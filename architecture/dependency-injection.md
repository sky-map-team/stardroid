# Dependency Injection

Sky Map uses Dagger 2 for dependency injection with a two-level component hierarchy.

## Component Hierarchy

```
┌─────────────────────────────────────────────┐
│           ApplicationComponent              │
│              (Singleton)                    │
│                                             │
│  Provides:                                  │
│  • SharedPreferences                        │
│  • SensorManager                            │
│  • LocationManager                          │
│  • LayerManager                             │
│  • Analytics                                │
│  • ConnectivityManager                      │
└─────────────────┬───────────────────────────┘
                  │ depends on
                  ▼
┌─────────────────────────────────────────────┐
│          Activity Components                │
│            (@PerActivity)                   │
│                                             │
│  • DynamicStarMapComponent                  │
│  • EditSettingsActivityComponent            │
│  • ImageGalleryActivityComponent            │
│  • DiagnosticActivityComponent              │
│  • CompassCalibrationComponent              │
└─────────────────────────────────────────────┘
```

## ApplicationComponent

**Scope**: Singleton (app lifetime)
**Created**: `StardroidApplication.onCreate()`

```java
@Singleton
@Component(modules = {ApplicationModule.class})
public interface ApplicationComponent {
    // Provision methods for activity components
    SharedPreferences provideSharedPreferences();
    SensorManager provideSensorManager();
    LayerManager provideLayerManager();
    Analytics provideAnalytics();
    // ...
}
```

### ApplicationModule

Provides app-wide dependencies:

```java
@Module
public class ApplicationModule {
    @Provides @Singleton
    SharedPreferences provideSharedPreferences(Application app) {
        return PreferenceManager.getDefaultSharedPreferences(app);
    }

    @Provides @Singleton
    SensorManager provideSensorManager(Application app) {
        return (SensorManager) app.getSystemService(SENSOR_SERVICE);
    }
    // ...
}
```

## Activity Components

Each activity defines its own Dagger component that depends on `ApplicationComponent`.

### DynamicStarMapComponent

The main star map activity's component:

```java
@PerActivity
@Component(
    dependencies = {ApplicationComponent.class},
    modules = {AbstractDynamicStarMapModule.class}
)
public interface DynamicStarMapComponent {
    void inject(DynamicStarMapActivity activity);
}
```

### @PerActivity Scope

Custom scope annotation for activity-lifetime instances:

```java
@Scope
@Retention(RUNTIME)
public @interface PerActivity {}
```

## Component Access Pattern

Activities implement `HasComponent<T>` interface:

```java
public class DynamicStarMapActivity extends Activity
    implements HasComponent<DynamicStarMapComponent> {

    private DynamicStarMapComponent component;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        component = DaggerDynamicStarMapComponent.builder()
            .applicationComponent(getApplicationComponent())
            .abstractDynamicStarMapModule(new DynamicStarMapModule(this))
            .build();
        component.inject(this);
    }

    @Override
    public DynamicStarMapComponent getComponent() {
        return component;
    }
}
```

## Accessing Application Component

```java
// From any Activity
ApplicationComponent getApplicationComponent() {
    return ((StardroidApplication) getApplication()).getApplicationComponent();
}
```

## Key Injected Dependencies

### DynamicStarMapActivity

| Dependency | Purpose |
|------------|---------|
| `AstronomerModel` | Coordinate transformation |
| `ControllerGroup` | Input controllers |
| `LayerManager` | Layer visibility |
| `RendererController` | Rendering updates |
| `SkyRenderer` | OpenGL renderer |
| `SharedPreferences` | User settings |

### StardroidApplication

| Dependency | Purpose |
|------------|---------|
| `SensorManager` | Device sensors |
| `ConnectivityManager` | Network state |
| `Analytics` | Usage tracking |

## Module Structure

```
com.google.android.stardroid.inject/
├── HasComponent.java           # Component access interface
├── PerActivity.java            # Activity scope annotation
└── modules/
    ├── ApplicationModule.java   # App-wide providers
    └── AbstractDynamicStarMapModule.java  # Star map providers
```
