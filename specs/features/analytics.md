# Sky Map Analytics Specification

> **Status**: Current as of March 2026 (Firebase Analytics, GMS flavor only)
> **Related**: [analytics.md](analytics.md) (original design doc)

# To be implemented
This section describes a revamp of the analytics infrastructure. The section titled Overview
describes the current situation. As work progresses on the features that section should be
updated as we go.

## 1. Ensure that events are logged with a consistent naming pattern
In the GA4 UI we want to be able to easily identify bespoke Sky Map GA events from ones that are provided
"for free" by Google Analytics. Ensure a clear distinction between 'user level properties' and session events.
In general we would like to be able to slice by user level properties in the GA4 UI.

Acceptance criteria: all Sky Map-specific events have a common naming pattern (e.g. the _ev suffix).

## 2. Update the preference value whitelist to be a blacklist
This was added to ensure we don't inadvertently log PII such as a user's address but history
has shown that this is unnecessarily conservative. Review all current
preferences, flag any that could be a privacy concern, and add those to a blacklist instead.

Acceptance criteria: the whitelist is gone and instead there is a minimal blacklist for events
of genuine concern.

## 3. Update the GA implementation to modern standards.
Check dependencies and APIs and see if any need to be updated.

## 4. Gaps & Suggested Improvements
Review the following gaps and implement changes where appropriate.  Make each improvement a separate
git change so that it can be reviewed in isolation. For each suggested analysis I want you to ensure
that the correct data is logged and provide instructions on how to build the analysis in GA4 since
in some cases events will need to be registered. Make sure you consider how easy it is to slice
the data in GA4. For instance for many metrics (like session length) it is essential that we
can slice by app version.

### Missing events

| Gap                                                                  | Suggested event | Suggested parameters | Rationale |
|----------------------------------------------------------------------|-----------------|---------------------|-----------|
| **Time Travel usage**                                                | `time_travel_used_ev` | `delta_hours: int`, `direction: "past"\|"future"` | We track that the dialog was *opened* but not whether the user actually set a time or by how much. |
| **Manual mode entered/exited**                                       | `manual_mode_toggled_ev` | `enabled: boolean` | `TOGGLED_MANUAL_MODE_LABEL` constant exists but is never called. |
| **Gyroscope-vs-mag sensor path chosen**                              | extend `start_up_event_ev` or new `sensor_path_ev` | `sensor_path: "rotation_vector"\|"accel_mag"` | Knowing which sensor fusion path is active would help diagnose accuracy issues across the install base. |
| **Search with no results**                                           | already partially covered | — | `search_success=false` is logged, but the failed term is also logged — consider a dedicated `search_failed_ev` so funnels are cleaner. |
| **Object searched-and-locked**                                       | `object_locked_ev` | `object_id: String`, `object_type: String` | Distinguish between viewing info and actually locking the view onto an object. |
| **Gallery image viewed**                                             | `gallery_image_viewed_ev` | `image_name: String` | We know the gallery was opened but not which images engaged users. |
| Skip this for now (we don't do it): **App rating / review prompted** | `review_prompt_shown_ev`, `review_prompt_outcome_ev` | `outcome: "rated"\|"dismissed"\|"later"` | If an in-app review prompt is ever added. |
| **Crash / ANR**                                                      | (use Firebase Crashlytics) | — | There is no Crashlytics dependency; adding it would give symbolicated crash reports with zero custom instrumentation. |
| **Night mode state at launch**                                       | extend `start_up_event_ev` | `night_mode_on: boolean` | Useful context for interpreting session lengths and object views. |
| Skip this for now (we don't have it) **Constellation art toggled**   | (already covered by `layer_toggled_ev`) | — | Confirm `source_provider.constellation_boundaries` etc. are all emitting this event. |

### Parameter quality improvements

| Event | Issue | Suggestion |
|-------|-------|------------|
| `layer_toggled_ev` | `layer_name` uses internal pref key strings (e.g. `source_provider.constellations`) which are hard to read in the Firebase console | Map to human-readable names (`"constellations"`, `"messier"` …) or add a separate `layer_display_name` parameter. |
| `session_length_ev` | Raw seconds in an int parameter → hard to bucket in Explore | Consider also logging a `session_bucket` string (`"<1min"`, `"1-5min"`, `"5-15min"`, `"15-30min"`, `">30min"`) for easier funnel analysis. |
| `start_up_event_ev` | `hour` alone is ambiguous (is it UTC or local?) | Rename to `local_hour` or document clearly; also consider adding `day_of_week` (0–6) to distinguish weekday vs weekend stargazing. |
| `object_info_viewed_ev` | No position context | Add `magnitude: float` and `visible_without_aid: boolean` to understand whether users are exploring naked-eye or telescope objects. |

### User property improvements

| Property | Issue | Suggestion |
|----------|-------|------------|
| `DEVICE_SENSORS` | Single string is hard to filter in the Firebase console | Consider one boolean user property per key sensor (`has_gyro`, `has_rotation_vector`). Firebase supports up to 25 user properties. |
| Missing: **app version at install** | Firebase collects `app_version` automatically, but not the version the user first installed | Add `first_install_version_prop` set only when `NEW_USER = "true"` to track cohort retention across releases. |
| Missing: **language** | The original design doc listed language breakdown as a goal | Add `user_locale_prop` (e.g. `"en-US"`) set at startup. This directly addresses the translation investment question. |

## Suggested Analyses

### Engagement
- **Session length distribution** by `device_sensors` user property: do users with gyroscopes
  stay longer (smoother experience)?
- **Hour-of-day launch histogram**: confirm the expected evening peak; use to time push
  notifications or release announcements.
- **Search success rate** (`search_success=true / total searches`): a falling rate indicates
  missing catalog entries. Cross-reference failed `search_term` values against the catalog
  roadmap.
- **Top searched terms** (failed only): direct input for which objects to add next. Already
  capturable from current data.

### Feature utilisation
- **Layer toggle frequency**: which layers do users turn off? Candidates for hiding behind an
  "advanced" toggle or removing entirely.
- **Menu item open rates**: compare `gallery_opened`, `time_travel_opened`, `credits_opened`
  to assess which features are actually discovered and used.
- **Object info card views by type**: are users engaging with Messier objects, planets, or
  bright stars most? Informs which educational content to expand.
- **Object info card views**: which objects are most popular and should be enhanced?
- **Settings**: we're less interested in setting change _events_ in most cases, but rather which settings people prefer. e.g. do most users have the 'disable gyro' setting enabled?

### Device & sensor health
- **`sensor_liar_prop` rate by device model**: Firebase's default `device_model` dimension
  plus this property would identify specific hardware with broken sensor reporting.
- **`no_sensors_warning_ev` rate**: is this a significant portion of the install base? If
  high, a manual mode first-run experience may be warranted.
- **Calibration event rate**: ratio of `calibration_auto_triggered_ev` to total sessions
  indicates how often compass accuracy degrades and whether the current threshold is too
  sensitive.

### Retention & conversion
- **TOS acceptance rate**: `TOS_accepted_ev / (TOS_accepted_ev + TOS_rejected_ev)`. A
  significant rejection rate may indicate the EULA is too alarming.
- **New vs returning user session length**: do new users engage as long as returning users?
  If not, investigate the first-run experience.
- **Preference change events over time**: users who customise the app (night mode, sensor
  damping) likely have higher retention — worth verifying with a cohort analysis.
- **App version**: we want to understand the upgrade path - for a user on a particular app version which app version did they upgrade from? We want to see app upgrades in real time as a version rolls out.


## N.
If necessary, update the privacy policy to clearly convey what data is collected and why.



---

## Overview

Sky Map uses **Firebase Analytics** (via the `firebase-analytics` BOM) in the GMS Play Store
flavor. The F-Droid flavor uses a compile-time no-op stub — no data is collected from those
users. Users on GMS can opt out via Settings → `enable_analytics` (default: on).

### Key files

| File | Role |
|------|------|
| `app/src/main/java/…/util/AnalyticsInterface.java` | Single source of truth for all constant names |
| `app/src/gms/java/…/util/Analytics.java` | Firebase implementation (GMS only) |
| `app/src/fdroid/java/…/util/Analytics.java` | No-op stub (F-Droid) |
| `app/src/main/java/…/util/PreferenceChangeAnalyticsTracker.kt` | Global preference-change listener |
| `app/src/main/java/…/StardroidApplication.kt` | App startup: user properties, start event |
| `app/src/main/java/…/ApplicationModule.kt` | Dagger singleton binding |

---

## User Properties

Set once at app startup in `StardroidApplication.kt` and persisted by Firebase.

| Property constant | Firebase name | Values | Purpose |
|---|---|---|---|
| `NEW_USER` | `new_user_prop` | `"true"` / `"false"` | Distinguishes first install from returning user. Derived from absence of `PREVIOUS_APP_VERSION_PREF` and old `read_tos` key. |
| `DEVICE_SENSORS` | `device_sensors_prop` | Pipe-separated subset of `accel`, `gyro`, `mag`, `rot`, or `none` | Records which motion sensors are present (e.g. `"accel\|mag\|rot"`). Used to understand feature supportability across the install base. |
| `SENSOR_LIAR` | `sensor_liar_prop` | `"true"` (set only on failure) | Flags devices that advertise a sensor but refuse listener registration. |

---

## Events

### `TOS_accepted_ev`
- **Trigger**: User taps Accept in the EULA dialog (`EulaDialogFragment`)
- **Parameters**: none
- **Notes**: Only fires on first launch per install.

### `TOS_rejected_ev`
- **Trigger**: User taps Decline or dismisses the EULA dialog
- **Parameters**: none
- **Notes**: App exits after rejection; this event confirms the exit path was reached.

### `start_up_event_ev`
- **Trigger**: Application `onCreate` completes analytics setup
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `hour` | int (0–23) | `21` |

- **Notes**: `hour` reflects the device's local time. Useful for understanding when users
  star-gaze (expected peak: late evening / night).

### `menu_item_pressed_ev`
- **Trigger**: User selects an item from the main star map menu
- **Parameters**:

  | Key | Type | Possible values |
  |-----|------|----------------|
  | `menu_item` | String | `search_requested`, `settings_opened`, `credits_opened`, `help_opened`, `night_mode`, `time_travel_opened`, `gallery_opened`, `TOS_opened`, `calibration_opened`, `diagnostics_opened` |

### `session_length_ev`
- **Trigger**: `DynamicStarMapActivity.onStop()` (user backgrounds or leaves the app)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `session_length` | int (seconds) | `342` |

- **Notes**: Measures time from `onStart` to `onStop`. Firebase also collects
  `engagement_time_msec` natively; this custom event gives finer-grained bucketing control.

### `search` *(matches Firebase recommended event name)*
- **Trigger**: User submits a search query in the search activity
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `search_term` | String | `"andromeda"` |
  | `search_success` | boolean | `true` |

### `object_info_viewed_ev`
- **Trigger**: User taps a celestial object and the educational info card is displayed
  (`ObjectInfoTapHandler`)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `object_id` | String | `"m31"`, `"mars"` |
  | `object_type` | String | `"MESSIER"`, `"BRIGHT_STAR"`, `"PLANET"` … |

### `layer_toggled_ev`
- **Trigger**: User enables or disables a sky layer from the menu
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `layer_name` | String | `"source_provider.constellations"` |
  | `layer_enabled` | boolean | `false` |

### `preference_button_toggled_ev`
- **Trigger**: User taps a quick-toggle button in the UI (distinct from going into Settings)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `preference_toggle_value` | String | `"night_mode:true"` |

- **Notes**: Value is the preference key and new state joined by `:`.

### `preference_change_ev`
- **Trigger**: *Any* `SharedPreferences` change (caught by a global listener in
  `PreferenceChangeAnalyticsTracker`)
- **Parameters**:

  | Key | Type | Example |
  |-----|------|---------|
  | `value` | String | `"sensor_speed:50"` or `"some_key:PII"` |

- **Privacy**: Only three keys are logged with their real values (`sensor_speed`,
  `sensor_damping`, `lightmode`). All other preference changes use the literal string `"PII"`
  as the value to avoid inadvertently logging personal data.

### `calibration_auto_triggered_ev`
- **Trigger**: `SensorAccuracyMonitor` detects compass accuracy has fallen below threshold
  and automatically opens the calibration screen
- **Parameters**: none

### `calibration_toast_shown_ev`
- **Trigger**: Compass accuracy is low, but the auto-calibration dialog is disabled, so a
  toast is shown instead
- **Parameters**: none

### `no_sensors_warning_ev`
- **Trigger**: Device lacks required sensors (accelerometer or magnetometer); warning
  dialog shown in `DynamicStarMapActivity`
- **Parameters**: none

---

## Architecture Notes

- All constants live in `AnalyticsInterface.java`; no magic strings elsewhere.
- Injected as a Dagger singleton; activities and fragments receive it via constructor injection.
- The GMS/F-Droid split is handled entirely at the flavor source-set level — the rest of the
  app is unaware of which implementation is active.
- Opt-out disables the Firebase SDK itself (not just the custom events), so automatic
  Firebase events (screen views, first open, etc.) are also suppressed.




---

## Privacy Considerations

1. **Consent timing**: analytics fires from the first launch before the user has seen the
   EULA. The [original design doc](analytics.md) flagged this as a known gap. Consider
   initialising Firebase Analytics only *after* TOS acceptance.
2. **Search terms**: `search_term` is logged in full. Searches are unlikely to contain PII,
   but this should be called out in the privacy policy.
3. **Preference value masking**: the current whitelist (`sensor_speed`, `sensor_damping`,
   `lightmode`) is conservative. Expanding it to non-PII keys (see above) is safe but
   should be reviewed before each change.
4. **F-Droid users**: receive zero analytics — this is correct and should not be changed.
