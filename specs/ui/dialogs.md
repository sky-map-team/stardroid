# Dialogs Specification

## Purpose

Defines all **dialog fragments and dialogs** in Stardroid, their purpose, UI patterns, and user interactions.

## Dialog Overview

| Dialog | Type | Parent Activity | Trigger |
|--------|------|-----------------|---------|
| `EulaDialogFragment` | `DialogFragment` + `AlertDialog` | `SplashScreenActivity`, `DynamicStarMapActivity` | First launch / ToS menu item |
| `WhatsNewDialogFragment` | `DialogFragment` + `AlertDialog` | `SplashScreenActivity` | App update |
| `HelpDialogFragment` | `DialogFragment` + `AlertDialog` (WebView) | `DynamicStarMapActivity` | Menu |
| `CreditsDialogFragment` | `DialogFragment` + `AlertDialog` (WebView) | `DynamicStarMapActivity` | Menu |
| `TimeTravelDialogFragment` | `DialogFragment` wrapping `TimeTravelDialog` | `DynamicStarMapActivity` | Menu |
| `NoSearchResultsDialogFragment` | `DialogFragment` + `AlertDialog` | `DynamicStarMapActivity` | Search failed |
| `MultipleSearchResultsDialogFragment` | `DialogFragment` + `AlertDialog` | `DynamicStarMapActivity` | Search ambiguity |
| `ObjectInfoDialogFragment` | `DialogFragment` + `AlertDialog` | `DynamicStarMapActivity` | Tap on object |
| `NoSensorsDialogFragment` | `DialogFragment` + `AlertDialog` | `DynamicStarMapActivity` | Sensor missing |

---

## Fragment Architecture

### All dialogs use `DialogFragment`, NOT BottomSheet

All dialogs extend `androidx.fragment.app.DialogFragment` and create their dialog in `onCreateDialog()` using `AlertDialog.Builder.create()` (or wrap a custom `Dialog` subclass).

There are **no** `BottomSheetDialogFragment` or `MaterialBottomSheetDialogFragment` classes in the current codebase.

### Dagger 2 Injection Pattern

Each dialog has an inner `ActivityComponent` interface. The parent activity implements `HasComponent<ActivityComponent>`:

```java
// In DynamicStarMapActivity:
public class DynamicStarMapActivity extends InjectableActivity
    implements HasComponent<DynamicStarMapComponent> { ... }

// DynamicStarMapComponent extends each dialog's ActivityComponent:
@Component(...)
public interface DynamicStarMapComponent extends
    HelpDialogFragment.ActivityComponent,
    CreditsDialogFragment.ActivityComponent,
    TimeTravelDialogFragment.ActivityComponent,
    ... { }

// In the dialog:
public class HelpDialogFragment extends DialogFragment {
    public interface ActivityComponent {
        void inject(HelpDialogFragment fragment);
    }
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ((HasComponent<ActivityComponent>) getActivity())
            .getComponent().inject(this);
    }
}
```

The activity injects dialog instances directly:
```java
@Inject HelpDialogFragment helpDialogFragment;
```

---

## WebView Dialogs (Help, Credits, EULA)

`HelpDialogFragment`, `CreditsDialogFragment`, and `EulaDialogFragment` all inflate a layout containing a `WebView` that loads a local HTML asset.

### Night Mode

These dialogs read the `lightmode` preference at dialog-creation time and inject a CSS class:

```java
SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
boolean isNight = "NIGHT".equals(prefs.getString(ActivityLightLevelManager.LIGHT_MODE_KEY, "DAY"));
String nightClass = isNight ? " class=\"night-mode\"" : "";
webView.loadDataWithBaseURL(baseUrl,
    "<html><body" + nightClass + ">...</body></html>", ...);
```

The `help.css` asset defines `.night-mode` styles: red-tinted `h1`, `h2`, `h3`, links, and callout boxes.

---

## TimeTravelDialogFragment / TimeTravelDialog

`TimeTravelDialogFragment` is a `DialogFragment` that wraps `TimeTravelDialog`, which extends `android.app.Dialog` directly (not `AlertDialog`).

`TimeTravelDialog` inflates `R.layout.time_dialog` in its own `onCreate()`.

### Views in `time_dialog.xml`

| ID | Widget | Purpose |
|----|--------|---------|
| `R.id.dateDisplay` | `TextView` | Shows currently selected date/time |
| `R.id.popular_dates_spinner` | `Spinner` | Pre-set astronomical events |
| `R.id.pickDate` | `Button` | Opens date picker |
| `R.id.pickTime` | `Button` | Opens time picker |
| `R.id.timeTravelGo` | `Button` | Starts time travel |
| `R.id.timeTravelCancel` | `Button` | Cancels |

### Night Mode

`TimeTravelDialog.onStart()` reads the preference and applies `NIGHT_TEXT_COLOR` (`0xFFCC4444`) to the main readout and button text colours:

```java
private void applyNightMode() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    boolean isNight = "NIGHT".equals(prefs.getString(ActivityLightLevelManager.LIGHT_MODE_KEY, "DAY"));
    int textColor = isNight ? NIGHT_TEXT_COLOR : Color.WHITE;
    dateTimeReadout.setTextColor(textColor);
    // ... buttons
}
```

---

## Search Result Dialogs

**`NoSearchResultsDialogFragment`:**
- Message: localised "no objects found" string
- Single "OK" button

**`MultipleSearchResultsDialogFragment`:**
- `AlertDialog` with a list adapter showing matching object names
- Selecting an item calls `activateSearchTarget()` on the parent activity

---

## ObjectInfoDialogFragment

Shows information about a tapped celestial object.

- Inflates `R.layout.object_info_card`
- Loaded with `ObjectInfoDialogFragment.newInstance(ObjectInfo objectInfo)`
- Displays: name, image (if available), description, fun fact, distance, size
- Image loaded asynchronously from assets

---

## Common Dialog Behaviour

### Showing a dialog

```java
// In DynamicStarMapActivity (injected via Dagger):
@Inject HelpDialogFragment helpDialogFragment;

helpDialogFragment.show(fragmentManager, "Help Dialog");
```

### Cancellation

All `AlertDialog`-based dialogs are cancellable (tap outside or back button) unless the dialog logic explicitly disables it (e.g. EULA on first launch sets `setCancelable(false)`).

---

## Related Specifications

- [activities.md](activities.md) — Activities that host these dialogs
- [material-3.md](material-3.md) — Night mode colours and theme system
