# Dependency Injection

Sky Map uses Hilt for dependency injection.

## Component Hierarchy

Hilt provides a standard set of components with predefined scopes and lifetimes:

- **SingletonComponent**: For application-wide dependencies (lifetime: app process)
- **ActivityComponent**: For dependencies scoped to an activity (lifetime: activity instance)
- **FragmentComponent**: For dependencies scoped to a fragment
- **ViewModelComponent**: For dependencies scoped to a ViewModel

## Application Setup

The `StardroidApplication` is annotated with `@HiltAndroidApp`, which triggers Hilt's code generation, including the base class for the application that serves as the application-level dependency container.

## Activity Setup

Activities like `DynamicStarMapActivity` are annotated with `@AndroidEntryPoint`. This enables Hilt to inject dependencies into the activity.

```java
@AndroidEntryPoint
public class DynamicStarMapActivity extends FragmentActivity {
    @Inject SharedPreferences sharedPreferences;
    @Inject LayerManager layerManager;
    // ...
}
```

## Modules

### ApplicationModule

Provides application-wide dependencies. It is installed in the `SingletonComponent`.

```java
@Module
@InstallIn(SingletonComponent.class)
public class ApplicationModule {
    @Provides
    @Singleton
    public SharedPreferences provideSharedPreferences(@ApplicationContext Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
    // ...
}
```

### Activity Modules

Dependencies scoped to activities are provided by modules installed in `ActivityComponent`.

- **ActivityBindingsModule**: Provides bindings common to all activities (e.g., `Window`, `FragmentManager`, `Handler`).
- **DynamicStarMapActivityModule**: Provides dependencies specific to `DynamicStarMapActivity`.
- **DynamicStarMapGmsModule**: Provides GMS-specific dependencies (e.g., `GoogleApiAvailability`), only included in the `gms` flavor.
- **ImageGalleryActivityModule**: Provides dependencies specific to `ImageGalleryActivity`.

Example of an activity-specific module:

```java
@Module
@InstallIn(ActivityComponent.class)
public class DynamicStarMapActivityModule {
  @Provides
  @Named("timetravel")
  @Nullable
  MediaPlayer provideTimeTravelNoise(Activity activity) {
    return prepareMediaPlayerAsync(activity, R.raw.timetravel);
  }
  // ...
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

## Adding Dialog Fragments

Dialog fragments are typically instantiated manually in the host activity using the `newInstance()` pattern. They are NOT generally provided via Hilt `@Provides` methods.

```java
// In DynamicStarMapActivity
helpDialogFragment = new HelpDialogFragment();
helpDialogFragment.show(fragmentManager, "Help Dialog");
```

If a `DialogFragment` needs dependencies, it can be annotated with `@AndroidEntryPoint` to receive them via field injection.
